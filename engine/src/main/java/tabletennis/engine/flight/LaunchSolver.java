package tabletennis.engine.flight;

import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;
import tabletennis.engine.NetSpec;
import tabletennis.engine.Simulation;
import tabletennis.engine.flight.TrialFlight.Flight;
import tabletennis.engine.flight.TrialFlight.Heading;
import tabletennis.engine.math.Vec3;

/**
 * Solves the launch elevation that lands a ball on a chosen spot, by bisection: range is monotone
 * in elevation over the searched band, and bisection cannot blow up on a diving topspin shot.
 */
public final class LaunchSolver {

    private LaunchSolver() {}

    /** NetClearance is negative into the net and NaN if the shot never reaches it. */
    public record Solution(BallState State, Vec3 Landing, double NetClearance,
                           double ElevationDegrees, boolean Converged) {}

    private static final double LowestElevation = Math.toRadians(-35);
    private static final double HighestElevation = Math.toRadians(45);

    /** 28 halvings of the 80 degree bracket is 14 nm of landing error; each contact solves many. */
    private static final int Halvings = 28;

    private static final int MaxFlightSteps = 6 * Simulation.StepsPerSecond;

    /** A launch of Speed from From, with the given spin in rev/s, that lands on Target. */
    public static Solution AtTarget(Vec3 From, Vec3 Target, double Speed, double TopRevs, double SideRevs) {
        Vec3 Flat = new Vec3(Target.X() - From.X(), 0, Target.Z() - From.Z());
        double Range = Flat.Length();
        if (Range < 1e-6) {
            return new Solution(BallState.At(From, Vec3.Zero, Vec3.Zero), From, Double.NaN, 0, false);
        }
        Vec3 Direction = Flat.Scale(1.0 / Range);
        Vec3 Spin = SpinVector.Of(Direction, TopRevs, SideRevs);

        double Low = LowestElevation, High = HighestElevation;
        boolean TooFastEvenAimedDown = RangeAt(From, Direction, Speed, Spin, Low) > Range;
        if (TooFastEvenAimedDown) return Finish(From, Direction, Speed, Spin, Low, false);
        boolean TooSlowEvenAimedUp = RangeAt(From, Direction, Speed, Spin, High) < Range;
        if (TooSlowEvenAimedUp) return Finish(From, Direction, Speed, Spin, High, false);

        for (int Halving = 0; Halving < Halvings; Halving++) {
            double Middle = 0.5 * (Low + High);
            if (RangeAt(From, Direction, Speed, Spin, Middle) < Range) Low = Middle; else High = Middle;
        }
        return Finish(From, Direction, Speed, Spin, 0.5 * (Low + High), true);
    }

    private static Solution Finish(Vec3 From, Vec3 Direction, double Speed, Vec3 Spin,
                                   double Elevation, boolean Converged) {
        BallState Launch = BallState.At(From, Velocity(Direction, Speed, Elevation), Spin);
        Flight Trial = Fly(Launch, Direction);
        double NetClearance = Trial.NetHeight() - BallSpec.Radius - NetSpec.Height;
        return new Solution(Launch, Trial.Landing(), NetClearance, Math.toDegrees(Elevation), Converged);
    }

    private static Vec3 Velocity(Vec3 Direction, double Speed, double Elevation) {
        return Direction.Scale(Speed * Math.cos(Elevation))
                      .Plus(Vec3.Up.Scale(Speed * Math.sin(Elevation)));
    }

    private static double RangeAt(Vec3 From, Vec3 Direction, double Speed, Vec3 Spin, double Elevation) {
        Flight Trial = Fly(BallState.At(From, Velocity(Direction, Speed, Elevation), Spin), Direction);
        Vec3 Travel = Trial.Landing().Minus(From);
        return Math.sqrt(Travel.X() * Travel.X() + Travel.Z() * Travel.Z());
    }

    private static Flight Fly(BallState Launch, Vec3 Direction) {
        Heading Toward = Direction.Z() < 0 ? Heading.TowardNegativeZ : Heading.TowardPositiveZ;
        return TrialFlight.Fly(Launch, Toward, Simulation.Step, MaxFlightSteps);
    }
}
