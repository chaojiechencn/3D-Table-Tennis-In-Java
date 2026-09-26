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

    /** Aim corrections for a curving shot; each converges the miss by roughly its own curve error. */
    private static final int CurveCorrections = 3;

    /** Within this the landing is the target: under half the 5 cm margin a legal shot keeps from the lines. */
    private static final double CurveTolerance = 0.02;

    /** A launch of Speed from From, with the given spin in rev/s, that lands on Target. */
    public static Solution AtTarget(Vec3 From, Vec3 Target, double Speed, double TopRevs, double SideRevs) {
        return AtTarget(From, Target, Speed, TopRevs, SideRevs, 1.0);
    }

    /**
     * As AtTarget, flown under a sideways-lift gain, and aimed off the target so the curve carries
     * the ball back onto it: each pass moves the aim point by the last landing's miss.
     */
    public static Solution Curving(Vec3 From, Vec3 Target, double Speed, double TopRevs, double SideRevs,
                                   double SideLift) {
        Vec3 AimAt = Target;
        Solution Best = AtTarget(From, AimAt, Speed, TopRevs, SideRevs, SideLift);
        for (int Pass = 0; Pass < CurveCorrections; Pass++) {
            Vec3 Miss = new Vec3(Target.X() - Best.Landing().X(), 0, Target.Z() - Best.Landing().Z());
            if (Miss.Length() < CurveTolerance) break;
            AimAt = AimAt.Plus(Miss);
            Best = AtTarget(From, AimAt, Speed, TopRevs, SideRevs, SideLift);
        }
        return Best;
    }

    private static Solution AtTarget(Vec3 From, Vec3 Target, double Speed, double TopRevs, double SideRevs,
                                     double SideLift) {
        Vec3 Flat = new Vec3(Target.X() - From.X(), 0, Target.Z() - From.Z());
        double Range = Flat.Length();
        if (Range < 1e-6) {
            return new Solution(BallState.At(From, Vec3.Zero, Vec3.Zero), From, Double.NaN, 0, false);
        }
        Vec3 Direction = Flat.Scale(1.0 / Range);
        Vec3 Spin = SpinVector.Of(Direction, TopRevs, SideRevs);

        double Low = LowestElevation, High = HighestElevation;
        boolean TooFastEvenAimedDown = RangeAt(From, Direction, Speed, Spin, Low, SideLift) > Range;
        if (TooFastEvenAimedDown) return Finish(From, Direction, Speed, Spin, Low, false, SideLift);
        boolean TooSlowEvenAimedUp = RangeAt(From, Direction, Speed, Spin, High, SideLift) < Range;
        if (TooSlowEvenAimedUp) return Finish(From, Direction, Speed, Spin, High, false, SideLift);

        for (int Halving = 0; Halving < Halvings; Halving++) {
            double Middle = 0.5 * (Low + High);
            if (RangeAt(From, Direction, Speed, Spin, Middle, SideLift) < Range) Low = Middle; else High = Middle;
        }
        return Finish(From, Direction, Speed, Spin, 0.5 * (Low + High), true, SideLift);
    }

    private static Solution Finish(Vec3 From, Vec3 Direction, double Speed, Vec3 Spin,
                                   double Elevation, boolean Converged, double SideLift) {
        BallState Launch = BallState.At(From, Velocity(Direction, Speed, Elevation), Spin);
        Flight Trial = Fly(Launch, Direction, SideLift);
        double NetClearance = Trial.NetHeight() - BallSpec.Radius - NetSpec.Height;
        return new Solution(Launch, Trial.Landing(), NetClearance, Math.toDegrees(Elevation), Converged);
    }

    private static Vec3 Velocity(Vec3 Direction, double Speed, double Elevation) {
        return Direction.Scale(Speed * Math.cos(Elevation))
                      .Plus(Vec3.Up.Scale(Speed * Math.sin(Elevation)));
    }

    private static double RangeAt(Vec3 From, Vec3 Direction, double Speed, Vec3 Spin, double Elevation,
                                  double SideLift) {
        Flight Trial = Fly(BallState.At(From, Velocity(Direction, Speed, Elevation), Spin), Direction, SideLift);
        Vec3 Travel = Trial.Landing().Minus(From);
        return Math.sqrt(Travel.X() * Travel.X() + Travel.Z() * Travel.Z());
    }

    private static Flight Fly(BallState Launch, Vec3 Direction, double SideLift) {
        Heading Toward = Direction.Z() < 0 ? Heading.TowardNegativeZ : Heading.TowardPositiveZ;
        return TrialFlight.Fly(Launch, Toward, Simulation.Step, MaxFlightSteps, SideLift);
    }
}
