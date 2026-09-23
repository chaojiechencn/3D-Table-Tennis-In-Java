package tabletennis.engine.flight;

import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.math.Numeric;
import tabletennis.engine.math.Vec3;

/**
 * A contact-free flight to where the ball first comes down through the table plane, noting its
 * height where it crosses the net plane. Landing questions must be asked HERE: a world with a
 * table in it bounces the ball away before the descent is seen, so the first crossing it reports
 * is the SECOND descent, out past the end line.
 */
public final class TrialFlight {

    private TrialFlight() {}

    /** Which way along Z the flight is expected to cross the net plane. */
    public enum Heading { TowardNegativeZ, TowardPositiveZ }

    /** NetHeight is the ball centre's height at z = 0, or NaN if it never crossed heading that way. */
    public record Flight(Vec3 Landing, double NetHeight) {}

    private static final int LandingMaxSteps = 6 * Simulation.StepsPerSecond;

    /** Where a launch first comes down through the table plane, on the table or not. */
    public static Vec3 LandingPoint(BallState Launch) {
        return Fly(Launch, Heading.TowardNegativeZ, Simulation.Step, LandingMaxSteps).Landing();
    }

    public static Flight Fly(BallState Launch, Heading Toward, double StepSeconds, int MaxSteps) {
        BallState Current = Launch;
        double NetHeight = Double.NaN;
        double PreviousZ = Launch.Position().Z();

        for (int Step = 0; Step < MaxSteps; Step++) {
            BallState Next = Integrator.Step(Current, StepSeconds);
            Vec3 From = Current.Position(), To = Next.Position();

            if (Double.isNaN(NetHeight) && CrossedNetPlane(PreviousZ, To.Z(), Toward)) {
                double Along = PreviousZ / (PreviousZ - To.Z());
                NetHeight = From.Y() + (To.Y() - From.Y()) * Along;
            }
            PreviousZ = To.Z();

            if (To.Y() <= BallSpec.Radius && Next.Velocity().Y() < 0) {
                double Along = (From.Y() - BallSpec.Radius) / (From.Y() - To.Y());
                return new Flight(Vec3.Lerp(From, To, Numeric.Clamp(Along, 0, 1)), NetHeight);
            }
            Current = Next;
        }
        return new Flight(Current.Position(), NetHeight);
    }

    private static boolean CrossedNetPlane(double FromZ, double ToZ, Heading Toward) {
        return Toward == Heading.TowardNegativeZ ? FromZ > 0 && ToZ <= 0
                                                 : FromZ < 0 && ToZ >= 0;
    }
}
