package tabletennis.game;

import tabletennis.engine.BallState;
import tabletennis.engine.Paddle;
import tabletennis.engine.Vec3;
import tabletennis.engine.World;

import java.util.List;

import static tabletennis.engine.Constants.Dt;

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
    static final double ReplayDelay = 1.8;

    /** A decided point is cut short rather than left to trickle to a stop. */
    static final double PointEndDelay = 0.9;

    /** A push dug off the surface touches the table on the contact's own step; that is not a bounce. */
    static final double ContactBounceWindow = Dt * 2;

    private static final double TerminalEventWindow = Dt * 2;

    // With no clean event, the rally ends once the ball has dropped this far or all but stopped.
    private static final double DroppedBelowY = -0.60;
    private static final double StoppedSpeed = 0.25;
    private static final double StoppedAfter = 1.5;

    /** What one step produced; either side may be null. */
    public record StepResult(Scoreboard.Side HitBy, Scoreboard.Side PointTo) {
        static final StepResult None = new StepResult(null, null);
        public boolean Contact()      { return HitBy != null; }
        public boolean PointAwarded() { return PointTo != null; }
    }

    private final World Physics = new World();
    private final Paddle PlayerPaddle = new Paddle(PlayerReach.Neutral, Stroke.Square);
    private final Paddle OpponentPaddle = new Paddle(Follower.Ready, Follower.Square);
    private final Stroke PlayerStroke = new Stroke(PlayerReach.Neutral);
    private final DemoPlayer Demo = new DemoPlayer();
    private final Opponent OpponentPlayer;
    private final ShotAssist Assist;
    private final Scoreboard Score = new Scoreboard();

    private Vec3 Aim;                     // null until the mouse has moved
    private boolean DemoMode;
    private boolean AutoReplay = true;
    private double ReplayAt = Double.NaN; // NaN while the rally is live

    private int LastPaddleHits;
    private int LastBounceSerial;
    private double LastHitTime;
    private Scoreboard.Side LastHitter;   // null while the feed is the last thing that hit it
    private boolean PlayerMayHit;
    private boolean OpponentMayHit;
    private boolean PointOver;
    private Scoreboard.Side AwardedThisStep;

    public GameSession() { this(new Follower(), new ShotAssist()); }

    GameSession(Opponent OpponentPlayer, ShotAssist Assist) {
        this.OpponentPlayer = OpponentPlayer;
        this.Assist = Assist;
        Physics.SetPaddles(PlayerPaddle, OpponentPaddle);
    }

    /** Put a feed in play, keeping the match score. */
    public void Launch(Shots Shot) {
        Physics.Launch(Shot.State());
        LastPaddleHits = 0;
        LastBounceSerial = Physics.BounceSerial();
        LastHitTime = -1;
        LastHitter = null;
        PlayerMayHit = false;
        OpponentMayHit = false;
        PointOver = false;
        ReplayAt = Double.NaN;
    }

    /** Already mapped and clamped by the caller. */
    public void SetAim(Vec3 Target) { Aim = Target; }

    /** The demo hand replaces the mouse; never both. */
    public void SetDemoMode(boolean On) { DemoMode = On; }

    /** Toggling either way drops any replay already scheduled. */
    public void SetAutoReplay(boolean On) {
        AutoReplay = On;
        ReplayAt = Double.NaN;
    }

    public boolean ReplayDue() { return !Double.isNaN(ReplayAt) && Physics.Time() >= ReplayAt; }

    /** Advance exactly one DT. Both blades are posed first, so contact uses this step's swing. */
    public StepResult Step() {
        AwardedThisStep = null;
        MoveRackets();
        Physics.SetPaddles(PlayerMayHit && !PointOver ? PlayerPaddle : null,
                         OpponentMayHit && !PointOver ? OpponentPaddle : null);

        BallState BeforeStep = Physics.State();
        Physics.Step();

        Scoreboard.Side HitBy = HandleContact(BeforeStep);
        HandleBounce();
        HandleOutOrFloor();
        ScheduleFallbackReplay();

        return HitBy == null && AwardedThisStep == null
                ? StepResult.None : new StepResult(HitBy, AwardedThisStep);
    }

    private void MoveRackets() {
        if (DemoMode) PlayerStroke.AimAt(Demo.CursorFor(Physics.State(), PlayerMayHit && !PointOver, Dt));
        else if (Aim != null) PlayerStroke.AimAt(Aim);
        PlayerStroke.Advance(PlayerPaddle, Dt);
        OpponentPlayer.Advance(Physics.State(), OpponentPaddle, Dt);
    }

    private Scoreboard.Side HandleContact(BallState BeforeStep) {
        if (Physics.PaddleHits() <= LastPaddleHits) return null;
        LastPaddleHits = Physics.PaddleHits();

        boolean PlayerHit = LastHitByPlayer();
        Paddle Racket = PlayerHit ? PlayerPaddle : OpponentPaddle;
        Physics.SetState(Assist.Assist(BeforeStep, Physics.State(), Racket, PlayerHit));

        LastHitter = PlayerHit ? Scoreboard.Side.Player : Scoreboard.Side.Opponent;
        LastHitTime = Physics.Time();
        PlayerMayHit = false;
        OpponentMayHit = false;
        return LastHitter;
    }

    /**
     * A first bounce opens the receiver's racket; a second, or the hitter's own half, decides the
     * point. The serial always advances, or the next bounce is compared with a stale one.
     */
    private void HandleBounce() {
        boolean NewBounce = Physics.BounceSerial() > LastBounceSerial;
        if (NewBounce && Physics.Time() - LastHitTime > ContactBounceWindow) {
            Scoreboard.Side Half = LastBounceHalf();
            if (LastHitter == Half) {
                EndPoint(Half.Other());                        // never crossed the net
            } else if (Half == Scoreboard.Side.Player) {
                if (PlayerMayHit) EndPoint(Scoreboard.Side.Opponent); else PlayerMayHit = true;
            } else {
                if (OpponentMayHit) EndPoint(Scoreboard.Side.Player); else OpponentMayHit = true;
            }
        }
        LastBounceSerial = Physics.BounceSerial();
    }

    /**
     * Out or floor decides against the last hitter; the feed counts as the player's. A net cord is
     * not here: a ball that clips the cord and lands legally is a good shot.
     */
    private void HandleOutOrFloor() {
        World.Event Last = Physics.LastEvent();
        boolean Terminal = Last != null && Physics.Time() - Last.Time() < TerminalEventWindow
                && (Last.Type() == World.EventType.OutOfBounds || Last.Type() == World.EventType.Floor);
        if (Terminal) {
            EndPoint(LastHitter == Scoreboard.Side.Opponent
                     ? Scoreboard.Side.Player : Scoreboard.Side.Opponent);
        }
    }

    private void ScheduleFallbackReplay() {
        BallState B = Physics.State();
        boolean Gone = B.Pos().Y() < DroppedBelowY;
        boolean Stopped = Physics.Time() > StoppedAfter && B.Speed() < StoppedSpeed;
        if (AutoReplay && Double.isNaN(ReplayAt) && (Gone || Stopped)) {
            ReplayAt = Physics.Time() + ReplayDelay;
        }
    }

    /** Awards once however many rules fire, and counts even with replay off. */
    private void EndPoint(Scoreboard.Side Winner) {
        if (PointOver) return;
        PointOver = true;
        AwardedThisStep = Winner;
        Score.PointTo(Winner);
        if (AutoReplay && Double.isNaN(ReplayAt)) ReplayAt = Physics.Time() + PointEndDelay;
    }

    private boolean LastHitByPlayer() {
        World.Event Hit = Latest(World.EventType.PaddleHit);
        return Hit == null || Hit.Side() < 0;
    }

    private Scoreboard.Side LastBounceHalf() {
        World.Event Bounce = Latest(World.EventType.TableBounce);
        return Bounce != null && Bounce.Side() < 0 ? Scoreboard.Side.Player : Scoreboard.Side.Opponent;
    }

    private World.Event Latest(World.EventType Type) {
        List<World.Event> Events = Physics.Events();
        for (int I = Events.size() - 1; I >= 0; I--) {
            if (Events.get(I).Type() == Type) return Events.get(I);
        }
        return null;
    }

    public BallState Ball()              { return Physics.State(); }
    public BallState PreviousBall()      { return Physics.Previous(); }
    public double Time()                 { return Physics.Time(); }
    public Paddle.Blade PlayerBlade()    { return PlayerPaddle.Collider(); }
    public Paddle.Blade OpponentBlade()  { return OpponentPaddle.Collider(); }
    public int BounceSerial()            { return Physics.BounceSerial(); }
    public List<Vec3> BounceMarks()      { return Physics.BounceMarks(); }
    public List<World.Event> Events()    { return Physics.Events(); }
    public Scoreboard.Snapshot Score()   { return Score.Snapshot(); }
    public boolean DemoMode()            { return DemoMode; }
    public boolean AutoReplay()          { return AutoReplay; }
    public boolean PlayerMayHit()        { return PlayerMayHit && !PointOver; }
    public boolean OpponentMayHit()      { return OpponentMayHit && !PointOver; }
    public boolean PointOver()           { return PointOver; }

    public ShotAssist.Debug LastShot()   { return Assist.Debug(); }
    public double TargetHalfWidth()      { return Assist.TargetHalfWidth(); }
    public double TargetNearDepth()      { return Assist.TargetNearDepth(); }
    public double TargetFarDepth()       { return Assist.TargetFarDepth(); }
}
