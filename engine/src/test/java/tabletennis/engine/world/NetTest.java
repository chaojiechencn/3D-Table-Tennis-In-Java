package tabletennis.engine.world;

import org.junit.jupiter.api.Test;
import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.flight.SpinVector;
import tabletennis.engine.math.Vec3;

import static tabletennis.testing.Claims.Check;

/** The net is loose fabric on a cord: it absorbs a ball, it does not bounce one back like a wall. */
final class NetTest {

    /** The Into the net feed: 8 m/s, low and flat, a little topspin. */
    private static final BallState IntoTheNet = BallState.At(new Vec3(0, 0.17, 0.95), new Vec3(0, -0.20, -8.0),
                                                             SpinVector.Of(new Vec3(0, 0, -1), 25, 0));

    @Test
    void NetKillsTheBall() {
        PhysicsWorld World = new PhysicsWorld();
        World.Launch(IntoTheNet);

        boolean HitNet = false;
        double SpeedAfter = 0;
        for (int Step = 0; Step < Simulation.StepsPerSecond * 3; Step++) {
            StepReport Report = World.Step();
            if (!HitNet && Report.Touched(SurfaceKind.Net)) {
                HitNet = true;
                SpeedAfter = World.Ball().Speed();
            }
        }
        Check("the net shot actually reaches the net", HitNet, "");
        Check("the net kills most of the speed",
              HitNet && SpeedAfter < 4.0, String.format("%.2f m/s leaving the net", SpeedAfter));
        Check("the ball ends up on the near side of the net",
              World.Ball().Position().Z() > -0.05,
              String.format("final z=%.3f m", World.Ball().Position().Z()));
    }
}
