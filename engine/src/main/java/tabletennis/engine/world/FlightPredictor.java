package tabletennis.engine.world;

import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.TableSpec;
import tabletennis.engine.math.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The ball's future through the real world, bounces included, in a private world with no
 * rackets: a prediction that gets intercepted predicts nothing.
 */
public final class FlightPredictor {

    private FlightPredictor() {}

    private static final double RestingSpeed = 0.5;

    /** Seconds of flight, keeping one point every Stride steps; stops once the ball rests on the floor. */
    public static List<Vec3> Path(BallState Start, double Seconds, int Stride) {
        List<Vec3> Points = new ArrayList<>();
        PhysicsWorld Flight = new PhysicsWorld();
        Flight.Launch(Start);

        int Steps = (int) Math.round(Seconds / Simulation.Step);
        for (int Step = 0; Step < Steps; Step++) {
            if (Step % Stride == 0) Points.add(Flight.Ball().Position());
            Flight.Step();
            BallState Ball = Flight.Ball();
            boolean RestingOnFloor = Ball.Position().Y() < -TableSpec.Height + BallSpec.Radius
                                  && Ball.Speed() < RestingSpeed;
            if (RestingOnFloor) break;
        }
        return Points;
    }
}
