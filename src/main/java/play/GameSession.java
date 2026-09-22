package play;

import physics.BallState;
import physics.Paddle;
import physics.Shots;
import physics.Vec3;
import physics.World;

import java.util.List;

import static physics.Constants.DT;

/**
 * One headless game: the world, both rackets, the rally rules and the score. The application and
 * RallyTest both drive it, so the game that is tested is the game that is played. It advances
 * only in whole physics steps and hands out immutable snapshots.
 *
 * The one-bounce rule is enforced by giving {@link World} a null racket for whoever may not hit
 * yet; a decided point withdraws both.
 */
public final class GameSession {

    /** Watching time after a ball dies without a decision. */
    static final double REPLAY_DELAY = 1.8;

    /** A decided point is cut short rather than left to trickle to a stop. */
    static final double POINT_END_DELAY = 0.9;

    /** A push dug off the surface touches the table on the contact's own step; that is not a bounce. */
    static final double CONTACT_BOUNCE_WINDOW = DT * 2;

    private static final double TERMINAL_EVENT_WINDOW = DT * 2;

    // With no clean event, the rally ends once the ball has dropped this far or all but stopped.
    private static final double DROPPED_BELOW_Y = -0.60;
    private static final double STOPPED_SPEED = 0.25;
    private static final double STOPPED_AFTER = 1.5;

    /** What one step produced; either side may be null. */
    public record StepResult(Scoreboard.Side hitBy, Scoreboard.Side pointTo) {
        static final StepResult NONE = new StepResult(null, null);
        public boolean contact()      { return hitBy != null; }
        public boolean pointAwarded() { return pointTo != null; }
    }

    private final World world = new World();
    private final Paddle playerPaddle = new Paddle(PlayerReach.NEUTRAL, Stroke.SQUARE);
    private final Paddle opponentPaddle = new Paddle(Follower.READY, Follower.SQUARE);
    private final Stroke stroke = new Stroke(PlayerReach.NEUTRAL);
    private final DemoPlayer demo = new DemoPlayer();
    private final Opponent opponent;
    private final ShotAssist shotAssist;
    private final Scoreboard score = new Scoreboard();

    private Vec3 aim;                     // null until the mouse has moved
    private boolean demoMode;
    private boolean autoReplay = true;
    private double replayAt = Double.NaN; // NaN while the rally is live

    private int lastPaddleHits;
    private int lastBounceSerial;
    private double lastHitTime;
    private Scoreboard.Side lastHitter;   // null while the feed is the last thing that hit it
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

    /** Put a feed in play, keeping the match score. */
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

    /** Already mapped and clamped by the caller. */
    public void setAim(Vec3 target) { aim = target; }

    /** The demo hand replaces the mouse; never both. */
    public void setDemoMode(boolean on) { demoMode = on; }

    /** Toggling either way drops any replay already scheduled. */
    public void setAutoReplay(boolean on) {
        autoReplay = on;
        replayAt = Double.NaN;
    }

    public boolean replayDue() { return !Double.isNaN(replayAt) && world.time() >= replayAt; }

    /** Advance exactly one DT. Both blades are posed first, so contact uses this step's swing. */
    public StepResult step() {
        awardedThisStep = null;
        moveRackets();
        world.setPaddles(playerMayHit && !pointOver ? playerPaddle : null,
                         opponentMayHit && !pointOver ? opponentPaddle : null);

        BallState beforeStep = world.state();
        world.step();

        Scoreboard.Side hitBy = handleContact(beforeStep);
        handleBounce();
        handleOutOrFloor();
        scheduleFallbackReplay();

        return hitBy == null && awardedThisStep == null
                ? StepResult.NONE : new StepResult(hitBy, awardedThisStep);
    }

    private void moveRackets() {
        if (demoMode) stroke.aimAt(demo.cursorFor(world.state(), playerMayHit && !pointOver, DT));
        else if (aim != null) stroke.aimAt(aim);
        stroke.advance(playerPaddle, DT);
        opponent.advance(world.state(), opponentPaddle, DT);
    }

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
     * A first bounce opens the receiver's racket; a second, or the hitter's own half, decides the
     * point. The serial always advances, or the next bounce is compared with a stale one.
     */
    private void handleBounce() {
        boolean newBounce = world.bounceSerial() > lastBounceSerial;
        if (newBounce && world.time() - lastHitTime > CONTACT_BOUNCE_WINDOW) {
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
     * Out or floor decides against the last hitter; the feed counts as the player's. A net cord is
     * not here: a ball that clips the cord and lands legally is a good shot.
     */
    private void handleOutOrFloor() {
        World.Event last = world.lastEvent();
        boolean terminal = last != null && world.time() - last.time() < TERMINAL_EVENT_WINDOW
                && (last.type() == World.EventType.OUT_OF_BOUNDS || last.type() == World.EventType.FLOOR);
        if (terminal) {
            endPoint(lastHitter == Scoreboard.Side.OPPONENT
                     ? Scoreboard.Side.PLAYER : Scoreboard.Side.OPPONENT);
        }
    }

    private void scheduleFallbackReplay() {
        BallState b = world.state();
        boolean gone = b.pos().y() < DROPPED_BELOW_Y;
        boolean stopped = world.time() > STOPPED_AFTER && b.speed() < STOPPED_SPEED;
        if (autoReplay && Double.isNaN(replayAt) && (gone || stopped)) {
            replayAt = world.time() + REPLAY_DELAY;
        }
    }

    /** Awards once however many rules fire, and counts even with replay off. */
    private void endPoint(Scoreboard.Side winner) {
        if (pointOver) return;
        pointOver = true;
        awardedThisStep = winner;
        score.pointTo(winner);
        if (autoReplay && Double.isNaN(replayAt)) replayAt = world.time() + POINT_END_DELAY;
    }

    private boolean lastHitByPlayer() {
        World.Event hit = latest(World.EventType.PADDLE_HIT);
        return hit == null || hit.side() < 0;
    }

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

    public ShotAssist.Debug lastShot()   { return shotAssist.debug(); }
    public double targetHalfWidth()      { return shotAssist.targetHalfWidth(); }
    public double targetNearDepth()      { return shotAssist.targetNearDepth(); }
    public double targetFarDepth()       { return shotAssist.targetFarDepth(); }
}
