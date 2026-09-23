package tabletennis.game.feed;

import org.junit.jupiter.api.Test;
import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.TableSpec;
import tabletennis.engine.flight.LaunchSolver;
import tabletennis.engine.flight.TrialFlight;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.PhysicsWorld;
import tabletennis.engine.world.StepReport;
import tabletennis.engine.world.SurfaceHit;
import tabletennis.engine.world.SurfaceKind;
import tabletennis.game.rally.EventType;
import tabletennis.game.rally.RallyEvent;
import tabletennis.game.rally.RallyFacts;
import tabletennis.game.rally.Referee;

import java.util.List;

import static tabletennis.testing.Claims.Check;

/** Every named feed must be a legal, playable shot, and the physics must survive all of them. */
final class FeedsTest {

    private static final int StepsPerSecond = Simulation.StepsPerSecond;

    /** Every aimed preset: the solver converged, it clears the net, and it lands inside the lines. */
    @Test
    void EveryAimedShotIsLegal() {
        for (Feed Shot : Feeds.All) {
            if (Shot.Aim() == null) continue;
            LaunchSolver.Solution Solved = Shot.Aim();

            Check("aim solver converged: " + Shot.Name(), Solved.Converged(),
                  String.format("elevation %+.1f deg", Solved.ElevationDegrees()));

            Vec3 Landing = Solved.Landing();
            Check("first bounce is inside the lines: " + Shot.Name(),
                  Math.abs(Landing.X()) < TableSpec.Width / 2 && Math.abs(Landing.Z()) < TableSpec.Length / 2,
                  String.format("x=%+.2f z=%+.2f", Landing.X(), Landing.Z()));

            if (Shot.IsServe()) {
                // A serve clears the cord on its SECOND flight, so it is flown all the way through.
                ServeIsLegal(Shot);
            } else {
                Check("clears the net: " + Shot.Name(), Solved.NetClearance() > 0.01,
                      String.format("%+.1f cm over the cord", Solved.NetClearance() * 100));
            }
        }
    }

    /** A legal serve: its own half, over the net without touching it, then the receiver's half. */
    private static void ServeIsLegal(Feed Shot) {
        PhysicsWorld World = new PhysicsWorld();
        World.Launch(Shot.Ball());

        boolean TouchedNet = false;
        double NearZ = Double.NaN, FarZ = Double.NaN;
        for (int Step = 0; Step < StepsPerSecond * 4; Step++) {
            SurfaceHit Last = LastNotable(World.Step());
            if (Last == null) continue;
            if (Last.Kind() == SurfaceKind.Net) TouchedNet = true;
            if (Last.Kind() == SurfaceKind.Table) {
                if (Last.Point().Z() > 0 && Double.isNaN(NearZ)) NearZ = Last.Point().Z();
                if (Last.Point().Z() < 0 && Double.isNaN(FarZ)) FarZ = Last.Point().Z();
            }
        }

        Check("serve bounces on its own half first: " + Shot.Name(),
              !Double.isNaN(NearZ), String.format("near bounce at z=%+.2f", NearZ));
        // The detail prints on a pass too, so it has to describe what was actually measured.
        Check("serve clears the net without touching it: " + Shot.Name(),
              !TouchedNet, TouchedNet ? "it clipped the cord" : "no net contact");
        Check("serve lands on the receiver's half: " + Shot.Name(),
              !Double.isNaN(FarZ) && Math.abs(FarZ) < TableSpec.Length / 2,
              String.format("far bounce at z=%+.2f", FarZ));
    }

    /** Out-of-bounds detection, the other half of "hits the table or goes out". */
    @Test
    void OutOfBoundsIsDetected() {
        PhysicsWorld World = new PhysicsWorld();
        Referee Rules = new Referee();
        World.Launch(BallState.At(new Vec3(0, 0.35, 1.5), new Vec3(-6, 1.0, -9), Vec3.Zero));   // well wide

        boolean Out = false;
        for (int Step = 0; Step < StepsPerSecond * 4 && !Out; Step++) {
            Out = Judge(World, Rules).stream().anyMatch(Event -> Event.Type() == EventType.OutOfBounds);
        }
        Check("a ball missing the table wide is reported out of bounds", Out, "");

        // The negative case matters as much: a detector that fires on everything passes the check above.
        for (Feed Shot : Feeds.All) {
            if (Shot.Aim() == null) continue;
            Vec3 Landing = TrialFlight.LandingPoint(Shot.Ball());
            Check("aimed shot lands in: " + Shot.Name(), !LandsOut(Shot.Ball()),
                  String.format("landed at x=%+.2f z=%+.2f", Landing.X(), Landing.Z()));
        }
    }

    /** Ten minutes of simulated time over every feed: no NaNs, no runaway, no drift. */
    @Test
    void LongRunStaysStable() {
        PhysicsWorld World = new PhysicsWorld();
        World.Launch(Feeds.ByName("Topspin loop").Ball());

        int Steps = (int) (600 / Simulation.Step);
        int RelaunchEvery = StepsPerSecond * 12;
        for (int Step = 0; Step < Steps; Step++) {
            World.Step();
            if (Step % RelaunchEvery == 0 && Step > 0) World.Launch(Feeds.ByIndex(Step / RelaunchEvery).Ball());
        }

        BallState Ball = World.Ball();
        Check("10 simulated minutes leave the state finite", Ball.IsFinite(), Ball.Position().toString());
        Check("the ball has not escaped the room",
              Ball.Position().Length() < 40, String.format("|pos| = %.2f m", Ball.Position().Length()));
        Check("the ball is not moving impossibly fast",
              Ball.Speed() < 60, String.format("%.2f m/s", Ball.Speed()));
        Check("the orientation quaternion is still a unit quaternion",
              Math.abs(Ball.Orientation().Norm() - 1) < 1e-9,
              String.format("|q| = %.15f", Ball.Orientation().Norm()));
    }

    /** Called out before the first bounce. */
    private static boolean LandsOut(BallState Launch) {
        PhysicsWorld World = new PhysicsWorld();
        Referee Rules = new Referee();
        World.Launch(Launch);
        boolean Out = false;
        for (int Step = 0; Step < StepsPerSecond * 2; Step++) {
            List<RallyEvent> Events = Judge(World, Rules);
            Out |= Events.stream().anyMatch(Event -> Event.Type() == EventType.OutOfBounds);
            if (Events.stream().anyMatch(Event -> Event.Type() == EventType.TableBounce)) break;
        }
        return Out;
    }

    /** One step of a racket-free world through the referee's out rule. */
    private static List<RallyEvent> Judge(PhysicsWorld World, Referee Rules) {
        StepReport Report = World.Step();
        return Rules.Judge(RallyFacts.Of(Report, null), Report.Before(), Report.After(), World.Time()).Events();
    }

    /** The step's last contact hard enough to be an event, or null. */
    private static SurfaceHit LastNotable(StepReport Report) {
        List<SurfaceHit> Notable = Report.NotableHits();
        return Notable.isEmpty() ? null : Notable.get(Notable.size() - 1);
    }
}
