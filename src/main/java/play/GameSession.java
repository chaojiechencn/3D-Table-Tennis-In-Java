package play;

import physics.BallState;
import physics.Paddle;
import physics.Shots;
import physics.Vec3;
import physics.World;

import java.util.List;

import static physics.Constants.DT;

/**
 * One headless game of table tennis: the world, both rackets, the rally rules and the score.
 *
 * The JavaFX application and {@code play.RallyTest} both drive this, so the game that is tested is
 * the game that is played. It advances only in whole physics steps ({@link #step}) and never sees
 * a frame time. Rendering reads immutable snapshots; nothing session-owned is handed out mutable.
 *
 * The ITTF one-bounce rule is enforced by handing {@link World} a null racket for whoever may not
 * hit yet: the blade still tracks the ball, it just cannot touch it. A decided point withdraws both.
 */
public final class GameSession {

    /** How long to keep watching after the ball has died without a decision, seconds. */
    static final double REPLAY_DELAY = 1.8;

    /** Shorter pause after a DECIDED point: it is cut early, not left to trickle to a stop. */
    static final double POINT_END_DELAY = 0.9;

    /**
     * How long after a racket contact a table bounce still belongs to that contact. A push dug out
     * at surface height fires its table contact on the SAME step as the racket contact; without
     * this window that reads as "your own shot bounced on your own half". Two steps is enough --
     * the ball is long gone from the surface by then at any speed the shot model produces.
     */
    static final double CONTACT_BOUNCE_WINDOW = DT * 2;

    /** How recent an OUT or FLOOR event must be to still decide the point on this step. */
    private static final double TERMINAL_EVENT_WINDOW = DT * 2;

    /** Fallback end of a rally with no clean event: the ball has dropped this far... */
    private static final double DROPPED_BELOW_Y = -0.60;
    /** ...or has all but stopped once the rally has had time to start. */
    private static final double STOPPED_SPEED = 0.25;
    private static final double STOPPED_AFTER = 1.5;

    /** What one step produced, for the camera and HUD. Either field may be null. */
    public record StepResult(Scoreboard.Side hitBy, Scoreboard.Side pointTo) {
        static final StepResult NONE = new StepResult(null, null);
        public boolean contact()      { return hitBy != null; }
        public boolean pointAwarded() { return pointTo != null; }
    }

    private final World world = new World();
    // Both rackets are kinematic and advanced once per physics step, never per frame -- driven at
    // the frame rate, a slow machine would swing the same stroke harder.
    private final Paddle playerPaddle = new Paddle(PlayerReach.NEUTRAL, Stroke.SQUARE);
    private final Paddle opponentPaddle = new Paddle(Follower.READY, Follower.SQUARE);
    private final Stroke stroke = new Stroke(PlayerReach.NEUTRAL);
    private final DemoPlayer demo = new DemoPlayer();
    private final Opponent opponent;
    private final ShotAssist shotAssist;
    private final Scoreboard score = new Scoreboard();

    private Vec3 aim;                     // the mapped cursor, or null before the mouse has moved
    private boolean demoMode;
    private boolean autoReplay = true;
    private double replayAt = Double.NaN; // when to restart the rally, or NaN while it is live

    // Rally state, reset by every launch.
    private int lastPaddleHits;
    private int lastBounceSerial;
    private double lastHitTime;
    private Scoreboard.Side lastHitter;   // null while the feed is still the last thing that hit it
    private boolean playerMayHit;
    private boolean opponentMayHit;
    private boolean pointOver;
    private Scoreboard.Side awardedThisStep;

    public GameSession() { this(new Follower(), new ShotAssist()); }

    GameSession(Opponent opponent, ShotAssist shotAssist) {
        this.opponent = opponent;
        this.shotAssist = shotAssist;
        world.setPaddles(playerPaddle, opponentPaddle);
    }

    // ------------------------------------------------------------------ control

    /**
     * Put a ball in play, keeping the match score. Not a serve (not built yet) -- a feed that
     * stands in for the player's own shot. The one-bounce rule, not the launch geometry, is what
     * stops it rebounding off the player's own blade on the first step.
     */
    public void launch(Shots shot) {
        world.launch(shot.state());
        lastPaddleHits = 0;
        lastBounceSerial = world.bounceSerial();
        lastHitTime = -1;
        lastHitter = null;
        playerMayHit = false;
        opponentMayHit = false;
        pointOver = false;
        replayAt = Double.NaN;
    }

    /** The player's target on the hitting plane, already mapped and clamped by the caller. */
    public void setAim(Vec3 target) { aim = target; }

    /** The demo hand drives the cursor instead of the mouse -- exactly one source, never both. */
    public void setDemoMode(boolean on) { demoMode = on; }

    /** Toggling either way drops any replay already scheduled. */
    public void setAutoReplay(boolean on) {
        autoReplay = on;
        replayAt = Double.NaN;
    }

    /** True once the scheduled replay time has been reached; the caller launches the next feed. */
    public boolean replayDue() { return !Double.isNaN(replayAt) && world.time() >= replayAt; }

    // ------------------------------------------------------------------ the step

    /**
     * Advance exactly one {@code DT}. Both blades are posed BEFORE the world steps, so the contact
     * solver strikes the ball with the velocity the blade had while the ball was arriving.
     */
    public StepResult step() {
        awardedThisStep = null;

        if (demoMode) stroke.aimAt(demo.cursorFor(world.state(), playerMayHit && !pointOver, DT));
        else if (aim != null) stroke.aimAt(aim);
        stroke.advance(playerPaddle, DT);
        opponent.advance(world.state(), opponentPaddle, DT);

        world.setPaddles(playerMayHit && !pointOver ? playerPaddle : null,
                         opponentMayHit && !pointOver ? opponentPaddle : null);

        BallState beforeStep = world.state();   // ShotAssist wants the pre-contact velocity
        world.step();

        Scoreboard.Side hitBy = handleContact(beforeStep);
        handleBounce();
        handleOutOrFloor();
        scheduleFallbackReplay();

        return hitBy == null && awardedThisStep == null
                ? StepResult.NONE : new StepResult(hitBy, awardedThisStep);
    }

    /** A racket just hit it: author the shot, and hand the ball to the other side's bounce. */
    private Scoreboard.Side handleContact(BallState beforeStep) {
        if (world.paddleHits() <= lastPaddleHits) return null;
        lastPaddleHits = world.paddleHits();

        boolean playerHit = lastHitByPlayer();
        Paddle racket = playerHit ? playerPaddle : opponentPaddle;
        world.setState(shotAssist.assist(beforeStep, world.state(), racket, playerHit));

        lastHitter = playerHit ? Scoreboard.Side.PLAYER : Scoreboard.Side.OPPONENT;
        lastHitTime = world.time();
        playerMayHit = false;
        opponentMayHit = false;
        return lastHitter;
    }

    /**
     * A table bounce opens the receiver's racket -- unless it is the SECOND bounce on that side,
     * or the hitter's own shot fell back on their own half, either of which decides the point.
     * A bounce inside the contact window is the contact's own table touch, not a rally event,
     * but the serial still moves on or the next real bounce is measured against a stale one.
     */
    private void handleBounce() {
        if (world.bounceSerial() > lastBounceSerial
                && world.time() - lastHitTime > CONTACT_BOUNCE_WINDOW) {
            Scoreboard.Side half = lastBounceHalf();
            if (lastHitter == half) {
                endPoint(half.other());                        // never crossed the net
            } else if (half == Scoreboard.Side.PLAYER) {
                if (playerMayHit) endPoint(Scoreboard.Side.OPPONENT); else playerMayHit = true;
            } else {
                if (opponentMayHit) endPoint(Scoreboard.Side.PLAYER); else opponentMayHit = true;
            }
        }
        lastBounceSerial = world.bounceSerial();
    }

    /**
     * A ball out past the end line, or on the floor, decides the point against whoever hit it
     * last; the feed counts as the player's. NET is deliberately not here: under ITTF a rally
     * ball that clips the cord and lands legally is a good shot, and a cord that kills the ball
     * still decides the point a moment later through the own-half or floor rule.
     */
    private void handleOutOrFloor() {
        World.Event last = world.lastEvent();
        if (last != null && world.time() - last.time() < TERMINAL_EVENT_WINDOW
                && (last.type() == World.EventType.OUT_OF_BOUNDS
                 || last.type() == World.EventType.FLOOR)) {
            endPoint(lastHitter == Scoreboard.Side.OPPONENT
                     ? Scoreboard.Side.PLAYER : Scoreboard.Side.OPPONENT);
        }
    }

    /** The ball dropped toward the floor without a clean event, or died on the table. */
    private void scheduleFallbackReplay() {
        BallState b = world.state();
        boolean gone = b.pos().y() < DROPPED_BELOW_Y;
        boolean stopped = world.time() > STOPPED_AFTER && b.speed() < STOPPED_SPEED;
        if (autoReplay && Double.isNaN(replayAt) && (gone || stopped)) {
            replayAt = world.time() + REPLAY_DELAY;
        }
    }

    /**
     * Award the point once, however many rules fire on the same step, and withdraw both rackets.
     * The point counts even with auto-replay off: a score that silently stopped counting while a
     * rally is being inspected would be a trap.
     */
    private void endPoint(Scoreboard.Side winner) {
        if (pointOver) return;
        pointOver = true;
        awardedThisStep = winner;
        score.pointTo(winner);
        if (autoReplay && Double.isNaN(replayAt)) replayAt = world.time() + POINT_END_DELAY;
    }

    /** Whose racket made the latest contact: the near half is the player's. */
    private boolean lastHitByPlayer() {
        World.Event hit = latest(World.EventType.PADDLE_HIT);
        return hit == null || hit.side() < 0;
    }

    /** The half the latest table bounce landed on. */
    private Scoreboard.Side lastBounceHalf() {
        World.Event bounce = latest(World.EventType.TABLE_BOUNCE);
        return bounce != null && bounce.side() < 0 ? Scoreboard.Side.PLAYER : Scoreboard.Side.OPPONENT;
    }

    private World.Event latest(World.EventType type) {
        List<World.Event> events = world.events();
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).type() == type) return events.get(i);
        }
        return null;
    }

    // ------------------------------------------------------------------ read-only views

    public BallState ball()              { return world.state(); }
    public BallState previousBall()      { return world.previous(); }
    public double time()                 { return world.time(); }
    public Paddle.Blade playerBlade()    { return playerPaddle.collider(); }
    public Paddle.Blade opponentBlade()  { return opponentPaddle.collider(); }
    public int bounceSerial()            { return world.bounceSerial(); }
    public List<Vec3> bounceMarks()      { return world.bounceMarks(); }
    public List<World.Event> events()    { return world.events(); }
    public Scoreboard.Snapshot score()   { return score.snapshot(); }
    public boolean demoMode()            { return demoMode; }
    public boolean autoReplay()          { return autoReplay; }
    public boolean playerMayHit()        { return playerMayHit && !pointOver; }
    public boolean opponentMayHit()      { return opponentMayHit && !pointOver; }
    public boolean pointOver()           { return pointOver; }

    /** What the shot assist built the last shot from, for the overlay. */
    public ShotAssist.Debug lastShot()   { return shotAssist.debug(); }
    public double targetHalfWidth()      { return shotAssist.targetHalfWidth(); }
    public double targetNearDepth()      { return shotAssist.targetNearDepth(); }
    public double targetFarDepth()       { return shotAssist.targetFarDepth(); }
}
