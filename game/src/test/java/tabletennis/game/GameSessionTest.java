package tabletennis.game;

import org.junit.jupiter.api.Test;
import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.PhysicsWorld;
import tabletennis.engine.world.Racket;
import tabletennis.engine.world.StepReport;
import tabletennis.engine.world.SurfaceHit;
import tabletennis.engine.world.SurfaceKind;
import tabletennis.game.rally.EventType;
import tabletennis.game.rally.RallyEvent;
import tabletennis.game.rally.Referee;

import java.util.ArrayList;
import java.util.List;

import static tabletennis.testing.Claims.Check;

/** The rally rules, replays and determinism, played through the same session the application runs. */
final class GameSessionTest {

    /** An opponent that never plays: its blade stands far behind the table, so feeds run out. */
    private static final Opponent Statue = (Ball, Blade, Seconds) -> Blade.PlaceAt(new Vec3(0, 0.20, -4.0), Follower.Square);

    /** Hit well past the far end without touching the table: out, and then the floor. */
    private static final Shots LongFeed = Feed("long", new Vec3(0, 0.30, 1.52), new Vec3(0, 1.5, -14));

    /** Dropped short on the player's own half, rising toward the player after its bounce. */
    private static final Shots Bouncer = Feed("bouncer", new Vec3(0, 0.30, 0.45), new Vec3(0, 0, 1.2));

    /**
     * An opponent that digs the ball off the table surface: as the ball falls onto the far half a
     * second time, the blade drops in 3 cm behind it at 3 cm up and pushes forward, so the racket
     * contact and the ball's table touch fall on the SAME physics step.
     */
    private static final class Digger implements Opponent {
        private boolean Bounced, Set;

        @Override public void Advance(BallState Ball, Racket Blade, double Seconds) {
            Vec3 At = Ball.Position();
            if (Ball.Velocity().Y() > 0) Bounced = true;
            if (Set) {
                Blade.MoveTo(Blade.Position().Plus(new Vec3(0, 0, 0.01)), Follower.Square, Seconds);
            } else if (Bounced && Ball.Velocity().Y() < 0 && At.Y() < 0.03) {
                Blade.PlaceAt(new Vec3(At.X(), At.Y(), At.Z() - 0.03), Follower.Square);
                Set = true;
            } else {
                Blade.PlaceAt(Follower.Ready, Follower.Square);
            }
        }
    }

    /** First bounce: whichever half it lands on opens that side's racket and no other. */
    @Test
    void TheFirstBounceOpensOnlyTheRacketOnThatHalf() {
        for (Shots Shot : new Shots[]{Shots.ByName("Serve"), Bouncer}) {
            GameSession Game = new GameSession();
            Game.Launch(Shot);
            List<RallyEvent> Events = new ArrayList<>();
            boolean ShutWhileInAir = true;
            while (Game.BounceCount() == 0 && Game.Time() < 2) {
                ShutWhileInAir &= !Game.PlayerMayHit() && !Game.OpponentMayHit();
                Events.addAll(Game.Step().Events());
            }
            boolean Near = Events.stream().filter(Event -> Event.Type() == EventType.TableBounce).findFirst()
                                 .map(Event -> Event.Half() == Side.Player).orElse(false);
            Check("the first bounce opens only the racket on that half (" + Shot.Name() + ")",
                  ShutWhileInAir && Game.PlayerMayHit() == Near && Game.OpponentMayHit() == !Near,
                  String.format("closed in the air=%b; landed %s; player=%b opponent=%b", ShutWhileInAir,
                                Near ? "near" : "far", Game.PlayerMayHit(), Game.OpponentMayHit()));
        }
    }

    /** Double bounce: a dead drop on the opponent's half that nobody plays. */
    @Test
    void ASecondBounceLosesThePoint() {
        GameSession Drop = new GameSession(Statue, new ShotAssist());
        Drop.Launch(Shots.ByName("ITTF drop test"));
        boolean OpenedAfterOne = false;
        GameSession.StepResult Decided = null;
        for (int Step = 0; Step < (int) (3.0 / Simulation.Step) && Decided == null; Step++) {
            GameSession.StepResult Result = Drop.Step();
            OpenedAfterOne |= Drop.OpponentMayHit();
            if (Result.PointAwarded()) Decided = Result;
        }
        Check("a second bounce on the receiver's half is the receiver's point lost",
              OpenedAfterOne && Decided != null && Decided.PointTo() == Side.Player,
              String.format("first bounce opened the opponent=%b; point to %s at t=%.3f s",
                            OpenedAfterOne, Decided == null ? "nobody" : Decided.PointTo(), Drop.Time()));
    }

    /** Own half: a shot that comes straight back off a still blade onto the player's own half. */
    @Test
    void AReturnOntoTheHittersOwnHalfLosesThePoint() {
        ShotTuning RawPhysics = ShotTuning.Builder()
                .QualityCore(0).QualityCoreMin(0).QualityFalloff(1e-9).AssistFloor(0).Build();
        GameSession Own = new GameSession(Statue, new ShotAssist(RawPhysics));
        Own.SetAim(PlayerReach.Clamp(new Vec3(0, 0, RisingThroughTheHittingPlane(Bouncer).Z())));
        Own.Launch(Bouncer);
        Side OwnHit = null, OwnPoint = null;
        for (int Step = 0; Step < (int) (3.0 / Simulation.Step) && OwnPoint == null; Step++) {
            GameSession.StepResult Result = Own.Step();
            if (Result.Contact()) OwnHit = Result.HitBy();
            if (Result.PointAwarded()) OwnPoint = Result.PointTo();
        }
        Check("a return that falls back on the hitter's own half loses the point",
              OwnHit == Side.Player && OwnPoint == Side.Opponent && Own.Ball().Position().Z() > 0,
              String.format("hit by %s, point to %s, ball at z=%+.2f", OwnHit, OwnPoint, Own.Ball().Position().Z()));
    }

    /** Out, then floor: two terminal events on one rally, exactly one point, against the hitter. */
    @Test
    void OutThenFloorAwardsExactlyOnce() {
        GameSession Out = new GameSession(Statue, new ShotAssist());
        Out.Launch(LongFeed);
        int Awards = 0;
        List<RallyEvent> Events = new ArrayList<>();
        for (int Step = 0; Step < (int) (3.0 / Simulation.Step); Step++) {
            GameSession.StepResult Result = Out.Step();
            Events.addAll(Result.Events());
            if (Result.PointAwarded()) Awards++;
        }
        boolean Both = Happened(Events, EventType.OutOfBounds) && Happened(Events, EventType.FloorTouch);
        Check("out and then the floor end the rally with exactly one point, against the hitter",
              Both && Awards == 1 && Out.Score().OpponentPoints() == 1 && Out.Score().PlayerPoints() == 0,
              String.format("out+floor both fired=%b; %d award(s); score %d-%d", Both, Awards,
                            Out.Score().PlayerPoints(), Out.Score().OpponentPoints()));
    }

    /** Net cord: a feed that clips the cord and still lands on the far half is a live ball. */
    @Test
    void ALegalNetCordStaysInPlay() {
        Shots Cord = NetCordFeed();
        boolean TouchedNet = false, Opened = false, EarlyPoint = false;
        if (Cord != null) {
            GameSession Net = new GameSession(Statue, new ShotAssist());
            Net.Launch(Cord);
            List<RallyEvent> Events = new ArrayList<>();
            for (int Step = 0; Step < (int) (2.0 / Simulation.Step) && !Net.OpponentMayHit(); Step++) {
                GameSession.StepResult Result = Net.Step();
                Events.addAll(Result.Events());
                EarlyPoint |= Result.PointAwarded();
            }
            TouchedNet = Happened(Events, EventType.NetTouch);
            Opened = Net.OpponentMayHit();
        }
        Check("a ball that clips the net and lands legally stays in play",
              TouchedNet && Opened && !EarlyPoint,
              Cord == null ? "no cord-clipping feed found"
                           : String.format("%s: net touched=%b, far bounce opened the opponent=%b, early point=%b",
                                           Cord.Name(), TouchedNet, Opened, EarlyPoint));
    }

    /** A decided point withdraws both rackets: a hand still chasing the ball cannot touch it. */
    @Test
    void NoRacketTouchesADecidedBall() {
        GameSession Dead = new GameSession();
        Dead.Launch(Shots.ByName("Into the net"));
        boolean Over = false;
        int LateContacts = 0;
        double Closest = Double.MAX_VALUE;
        for (int Step = 0; Step < (int) (4.0 / Simulation.Step); Step++) {
            PointAtTheBall(Dead);
            GameSession.StepResult Result = Dead.Step();
            if (Over && Result.Contact()) LateContacts++;
            if (Over) Closest = Math.min(Closest, Dead.PlayerBlade().Centre().Minus(Dead.Ball().Position()).Length());
            Over |= Result.PointAwarded();
        }
        Check("once a point is decided, no racket can touch the ball again",
              Over && LateContacts == 0,
              String.format("point decided=%b; %d contacts after it; blade came within %.3f m of the ball",
                            Over, LateContacts, Closest));
    }

    /** A push dug off the surface touches the table on (or right after) the racket contact. */
    @Test
    void ATableTouchOnTheContactsOwnStepIsNotABounce() {
        GameSession Dig = new GameSession(new Digger(), new ShotAssist());
        Dig.Launch(Shots.ByName("ITTF drop test"));
        double ContactAt = Double.NaN, BounceGap = Double.NaN;
        Side DigPoint = null;
        for (int Step = 0; Step < (int) (3.0 / Simulation.Step) && DigPoint == null; Step++) {
            int Bounces = Dig.BounceCount();
            GameSession.StepResult Result = Dig.Step();
            if (Result.Contact() && Double.isNaN(ContactAt)) ContactAt = Dig.Time();
            if (!Double.isNaN(ContactAt) && Double.isNaN(BounceGap) && Dig.BounceCount() > Bounces) {
                BounceGap = Dig.Time() - ContactAt;
            }
            if (Result.PointAwarded()) DigPoint = Result.PointTo();
            if (!Double.isNaN(ContactAt) && Dig.Time() - ContactAt > 0.1) break;
        }
        Check("a table touch on the contact's own step does not score as the hitter's own half",
              !Double.isNaN(ContactAt) && BounceGap <= Referee.ContactBounceWindow && DigPoint == null,
              String.format("contact at t=%.4f s, table touch %.1f ms after it (window %.1f ms), point: %s",
                            ContactAt, BounceGap * 1000, Referee.ContactBounceWindow * 1000,
                            DigPoint == null ? "none" : DigPoint));
    }

    /** Replays fire on the documented delay only when enabled; a new feed keeps the score. */
    @Test
    void ReplaysKeepTheScore() {
        GameSession On = new GameSession(Statue, new ShotAssist());
        On.Launch(LongFeed);
        double PointAt = Double.NaN, DueAt = Double.NaN;
        for (int Step = 0; Step < (int) (4.0 / Simulation.Step) && Double.isNaN(DueAt); Step++) {
            if (On.Step().PointAwarded()) PointAt = On.Time();
            if (On.ReplayDue()) DueAt = On.Time();
        }
        double Delay = DueAt - PointAt;
        Check("with auto-replay on, the next feed is due one point-end delay after the point",
              Delay >= GameSession.PointEndDelay - 1e-9 && Delay < GameSession.PointEndDelay + Simulation.Step + 1e-9,
              String.format("due %.4f s after the point (delay %.3f s, step %.4f s)",
                            Delay, GameSession.PointEndDelay, Simulation.Step));

        Scoreboard.Snapshot Before = On.Score();
        On.Launch(LongFeed);
        Check("a new feed keeps the match score and reopens the rally",
              On.Score().equals(Before) && !On.PointOver() && !On.PlayerMayHit() && !On.OpponentMayHit(),
              String.format("score %d-%d kept=%b; point over=%b", Before.PlayerPoints(),
                            Before.OpponentPoints(), On.Score().equals(Before), On.PointOver()));

        GameSession Off = new GameSession(Statue, new ShotAssist());
        Off.SetAutoReplay(false);
        Off.Launch(LongFeed);
        boolean EverDue = false;
        for (int Step = 0; Step < (int) (6.0 / Simulation.Step); Step++) {
            Off.Step();
            EverDue |= Off.ReplayDue();
        }
        Check("with auto-replay off, no feed is scheduled but the point still counts",
              !EverDue && Off.Score().OpponentPoints() == 1,
              String.format("replay due=%b over 6 s; score %d-%d", EverDue,
                            Off.Score().PlayerPoints(), Off.Score().OpponentPoints()));
    }

    /** The same inputs, step for step, give the same game -- bit for bit. */
    @Test
    void TheSessionIsDeterministic() {
        GameSession First = new GameSession(), Second = new GameSession();
        for (GameSession Game : new GameSession[]{First, Second}) {
            Game.SetDemoMode(true);
            Game.Launch(Shots.ByName("Serve"));
        }
        int Steps = (int) (10.0 / Simulation.Step), FirstDifference = -1, Contacts = 0;
        for (int Step = 0; Step < Steps && FirstDifference < 0; Step++) {
            GameSession.StepResult A = First.Step(), B = Second.Step();
            if (A.Contact()) Contacts++;
            if (!A.equals(B) || !First.Ball().equals(Second.Ball())) FirstDifference = Step;
        }
        Check("two sessions fed the same inputs stay identical",
              FirstDifference < 0 && Contacts > 0,
              FirstDifference < 0 ? String.format("%d steps and %d contacts, every ball state equal", Steps, Contacts)
                                  : "diverged at step " + FirstDifference);
    }

    private static Shots Feed(String Name, Vec3 Position, Vec3 Velocity) {
        return new Shots(Name, Name, BallState.At(Position, Velocity, Vec3.Zero), null);
    }

    private static boolean Happened(List<RallyEvent> Events, EventType Type) {
        return Events.stream().anyMatch(Event -> Event.Type() == Type);
    }

    /** Point the stand-in hand's cursor at the ball, through the real envelope. */
    private static void PointAtTheBall(GameSession Game) {
        Vec3 Ball = Game.Ball().Position();
        Game.SetAim(PlayerReach.Clamp(new Vec3(Ball.X(), 0, Ball.Z())));
    }

    /** Where a feed's ball first rises through the player's hitting plane after its bounce. */
    private static Vec3 RisingThroughTheHittingPlane(Shots Shot) {
        PhysicsWorld World = new PhysicsWorld();
        World.Launch(Shot.State());
        int Bounces = 0;
        for (int Step = 0; Step < (int) (2.0 / Simulation.Step); Step++) {
            double HeightBefore = World.Ball().Position().Y();
            Bounces += TableBounces(World.Step());
            double HeightAfter = World.Ball().Position().Y();
            if (Bounces > 0 && HeightBefore < PlayerReach.HitY && HeightAfter >= PlayerReach.HitY) {
                return World.Ball().Position();
            }
        }
        throw new IllegalStateException(Shot.Name() + " never rises through the hitting plane");
    }

    /**
     * A feed that touches the net cord and still lands first on the far half, found by search so it
     * does not hang on hand-tuned numbers: a paddle-free flight is the honest judge.
     */
    private static Shots NetCordFeed() {
        for (double Vz = 6; Vz <= 10; Vz += 1) {
            for (double Vy = -0.5; Vy <= 2.5; Vy += 0.02) {
                Shots Candidate = Feed(String.format("cord feed vz=-%.0f vy=%+.2f", Vz, Vy),
                                       new Vec3(0, 0.20, 1.2), new Vec3(0, Vy, -Vz));
                PhysicsWorld World = new PhysicsWorld();
                World.Launch(Candidate.State());
                boolean Touched = false;
                SurfaceHit Last = null;
                int Bounces = 0;
                for (int Step = 0; Step < (int) (1.5 / Simulation.Step) && Bounces == 0; Step++) {
                    StepReport Report = World.Step();
                    Bounces += TableBounces(Report);
                    List<SurfaceHit> Notable = Report.NotableHits();
                    if (!Notable.isEmpty()) Last = Notable.get(Notable.size() - 1);
                    Touched |= Last != null && Last.Kind() == SurfaceKind.Net;
                }
                if (Touched && Last != null && Last.Kind() == SurfaceKind.Table && Last.Point().Z() < 0) {
                    return Candidate;
                }
            }
        }
        return null;
    }

    private static int TableBounces(StepReport Report) {
        return (int) Report.NotableHits().stream().filter(Hit -> Hit.Kind() == SurfaceKind.Table).count();
    }
}
