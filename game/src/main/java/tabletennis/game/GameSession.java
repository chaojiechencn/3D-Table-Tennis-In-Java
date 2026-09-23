package tabletennis.game;

import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.contact.BladeCollider;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.PhysicsWorld;
import tabletennis.engine.world.Racket;
import tabletennis.engine.world.StepReport;
import tabletennis.game.rally.EventType;
import tabletennis.game.rally.RallyEvent;
import tabletennis.game.rally.RallyFacts;
import tabletennis.game.rally.Referee;

import java.util.ArrayList;
import java.util.List;

/**
 * One headless game: the world, both rackets, the referee and the score. The application and the
 * tests both drive it, so the game that is tested is the game that is played. It advances only in
 * whole physics steps and hands out immutable values.
 */
public final class GameSession {

    /** Watching time after a ball dies without a decision. */
    static final double ReplayDelay = 1.8;

    /** A decided point is cut short rather than left to trickle to a stop. */
    static final double PointEndDelay = 0.9;

    // With no clean event, the rally ends once the ball has dropped this far or all but stopped.
    private static final double DroppedBelowY = -0.60;
    private static final double StoppedSpeed = 0.25;
    private static final double StoppedAfter = 1.5;

    private static final int BounceMarksKept = 24;

    /** What one step produced; HitBy and PointTo are null when nothing of the kind happened. */
    public record StepResult(Side HitBy, Side PointTo, List<RallyEvent> Events) {
        public boolean Contact()      { return HitBy != null; }
        public boolean PointAwarded() { return PointTo != null; }
    }

    private final PhysicsWorld Physics = new PhysicsWorld();
    private final Racket PlayerRacket = new Racket(PlayerReach.Neutral, Stroke.Square);
    private final Racket OpponentRacket = new Racket(Follower.Ready, Follower.Square);
    private final Stroke PlayerStroke = new Stroke(PlayerReach.Neutral);
    private final DemoPlayer Demo = new DemoPlayer();
    private final Opponent OpponentPlayer;
    private final ShotAssist Assist;
    private final Referee Rules = new Referee();
    private final Scoreboard Score = new Scoreboard();

    private Vec3 Aim;                     // null until the mouse has moved
    private boolean DemoMode;
    private boolean AutoReplay = true;
    private double ReplayAt = Double.NaN; // NaN while the rally is live

    private final List<Vec3> BounceMarks = new ArrayList<>();
    private int BounceCount;              // never reset: "has anything landed since I looked?"

    public GameSession() { this(new Follower(), new ShotAssist()); }

    GameSession(Opponent OpponentPlayer, ShotAssist Assist) {
        this.OpponentPlayer = OpponentPlayer;
        this.Assist = Assist;
    }

    /** Put a feed in play, keeping the match score. */
    public void Launch(Shots Shot) {
        Physics.Launch(Shot.State());
        Rules.StartRally();
        ReplayAt = Double.NaN;
        BounceMarks.clear();
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

    /**
     * Advance exactly one Simulation.Step. Both blades are posed first, so a contact uses this
     * step's swing; a racket that may not strike yet is simply absent, so the ball phases through.
     */
    public StepResult Step() {
        MoveRackets();
        Physics.SetRackets(StrikableRackets());

        BallState BeforeStep = Physics.Ball();
        StepReport Report = Physics.Step();
        List<RallyEvent> Contacts = RallyFacts.Of(Report, PlayerRacket);

        Side HitBy = LastHitter(Contacts);
        if (HitBy != null) {
            Racket Struck = HitBy == Side.Player ? PlayerRacket : OpponentRacket;
            Physics.ReplaceBall(Assist.Assist(BeforeStep, Physics.Ball(), Struck, HitBy == Side.Player));
        }

        Referee.Ruling Ruling = Rules.Judge(Contacts, Report.Before(), Report.After(), Physics.Time());
        if (Ruling.PointTo() != null) AwardPoint(Ruling.PointTo());
        KeepBounceMarks(Ruling.Events());
        ScheduleFallbackReplay();
        return new StepResult(HitBy, Ruling.PointTo(), Ruling.Events());
    }

    private void MoveRackets() {
        if (DemoMode) PlayerStroke.AimAt(Demo.CursorFor(Physics.Ball(), Rules.MayHit(Side.Player), Simulation.Step));
        else if (Aim != null) PlayerStroke.AimAt(Aim);
        PlayerStroke.Advance(PlayerRacket, Simulation.Step);
        OpponentPlayer.Advance(Physics.Ball(), OpponentRacket, Simulation.Step);
    }

    /** Player first, then opponent: the order equal times of impact resolve in. */
    private List<Racket> StrikableRackets() {
        List<Racket> Strikable = new ArrayList<>(2);
        if (Rules.MayHit(Side.Player)) Strikable.add(PlayerRacket);
        if (Rules.MayHit(Side.Opponent)) Strikable.add(OpponentRacket);
        return Strikable;
    }

    private static Side LastHitter(List<RallyEvent> Contacts) {
        Side Hitter = null;
        for (RallyEvent Each : Contacts) if (Each.Type() == EventType.RacketHit) Hitter = Each.HitBy();
        return Hitter;
    }

    /** Counts even with replay off. */
    private void AwardPoint(Side Winner) {
        Score.PointTo(Winner);
        if (AutoReplay && Double.isNaN(ReplayAt)) ReplayAt = Physics.Time() + PointEndDelay;
    }

    private void ScheduleFallbackReplay() {
        BallState Ball = Physics.Ball();
        boolean Gone = Ball.Position().Y() < DroppedBelowY;
        boolean Stopped = Physics.Time() > StoppedAfter && Ball.Speed() < StoppedSpeed;
        if (AutoReplay && Double.isNaN(ReplayAt) && (Gone || Stopped)) {
            ReplayAt = Physics.Time() + ReplayDelay;
        }
    }

    private void KeepBounceMarks(List<RallyEvent> Events) {
        for (RallyEvent Each : Events) {
            if (Each.Type() != EventType.TableBounce) continue;
            BounceCount++;
            BounceMarks.add(new Vec3(Each.Point().X(), 0.001, Each.Point().Z()));
            while (BounceMarks.size() > BounceMarksKept) BounceMarks.remove(0);
        }
    }

    public BallState Ball()               { return Physics.Ball(); }
    public BallState PreviousBall()       { return Physics.PreviousBall(); }
    public double Time()                  { return Physics.Time(); }
    public BladeCollider PlayerBlade()    { return PlayerRacket.Blade(); }
    public BladeCollider OpponentBlade()  { return OpponentRacket.Blade(); }
    public int BounceCount()              { return BounceCount; }
    public List<Vec3> BounceMarks()       { return List.copyOf(BounceMarks); }
    public Scoreboard.Snapshot Score()    { return Score.Snapshot(); }
    public boolean DemoMode()             { return DemoMode; }
    public boolean AutoReplay()           { return AutoReplay; }
    public boolean PlayerMayHit()         { return Rules.MayHit(Side.Player); }
    public boolean OpponentMayHit()       { return Rules.MayHit(Side.Opponent); }
    public boolean PointOver()            { return Rules.PointOver(); }

    public ShotAssist.Debug LastShot()    { return Assist.Debug(); }
    public double TargetHalfWidth()       { return Assist.TargetHalfWidth(); }
    public double TargetNearDepth()       { return Assist.TargetNearDepth(); }
    public double TargetFarDepth()        { return Assist.TargetFarDepth(); }
}
