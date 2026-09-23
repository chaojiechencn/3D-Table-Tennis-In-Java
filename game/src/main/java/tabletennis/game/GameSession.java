package tabletennis.game;

import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.PhysicsWorld;
import tabletennis.engine.world.Racket;
import tabletennis.engine.world.StepReport;
import tabletennis.game.ai.BallFollower;
import tabletennis.game.ai.Opponent;
import tabletennis.game.control.CursorFollower;
import tabletennis.game.control.DemoHand;
import tabletennis.game.control.ReachEnvelope;
import tabletennis.game.feed.Feed;
import tabletennis.game.match.Scoreboard;
import tabletennis.game.rally.EventType;
import tabletennis.game.rally.RallyEvent;
import tabletennis.game.rally.RallyFacts;
import tabletennis.game.rally.Referee;
import tabletennis.game.rally.Side;
import tabletennis.game.shot.ShotAssist;

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

    private final PhysicsWorld Physics = new PhysicsWorld();
    private final Racket PlayerRacket = new Racket(ReachEnvelope.Neutral, CursorFollower.Square);
    private final Racket OpponentRacket = new Racket(BallFollower.Ready, BallFollower.Square);
    private final CursorFollower PlayerHand = new CursorFollower(ReachEnvelope.Neutral);
    private final DemoHand Demo = new DemoHand();
    private final Opponent OpponentPlayer;
    private final ShotAssist Assist;
    private final Referee Rules = new Referee();
    private final Scoreboard Score = new Scoreboard();

    private Vec3 Aim;                     // null until the mouse has moved
    private boolean DemoMode;
    private boolean AutoReplay = true;
    private double ReplayAt = Double.NaN; // NaN while the rally is live

    public GameSession() { this(new BallFollower(), new ShotAssist()); }

    GameSession(Opponent OpponentPlayer, ShotAssist Assist) {
        this.OpponentPlayer = OpponentPlayer;
        this.Assist = Assist;
    }

    /** Put a feed in play, keeping the match score. */
    public void Launch(Feed Shot) {
        Physics.Launch(Shot.Ball());
        Rules.StartRally();
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

    public GameSnapshot Snapshot() {
        return new GameSnapshot(Physics.Ball(), Physics.PreviousBall(), Physics.Time(),
                                PlayerRacket.Blade(), OpponentRacket.Blade(), Score.Snapshot(),
                                Rules.MayHit(Side.Player), Rules.MayHit(Side.Opponent), Rules.PointOver(),
                                DemoMode, AutoReplay, Assist.LastDecision());
    }

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

        Referee.Ruling Verdict = Rules.Judge(Contacts, Report.Before(), Report.After(), Physics.Time());
        if (Verdict.PointTo() != null) AwardPoint(Verdict.PointTo());
        ScheduleFallbackReplay();
        return new StepResult(HitBy, Verdict.PointTo(), Verdict.Events());
    }

    private void MoveRackets() {
        if (DemoMode) PlayerHand.AimAt(Demo.CursorFor(Physics.Ball(), Rules.MayHit(Side.Player), Simulation.Step));
        else if (Aim != null) PlayerHand.AimAt(Aim);
        PlayerHand.Advance(PlayerRacket, Simulation.Step);
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
}
