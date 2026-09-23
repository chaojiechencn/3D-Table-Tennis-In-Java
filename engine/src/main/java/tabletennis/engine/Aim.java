package tabletennis.engine;

import static tabletennis.engine.Constants.*;

/**
 * Solves the launch elevation that lands a ball on a chosen spot, by bisection: range is monotone
 * in elevation over the searched band, and bisection cannot blow up on a diving topspin shot.
 */
public final class Aim {

    private Aim() {}

    /** netClearance is negative into the net and NaN if the shot never reaches it. */
    public record Solution(BallState State, Vec3 Landing, double NetClearance,
                           double ElevationDeg, boolean Converged) {}

    private static final double MinElev = Math.toRadians(-35);
    private static final double MaxElev = Math.toRadians(45);

    /** 28 halvings of the 80 degree bracket is 14 nm of landing error; each contact solves many. */
    private static final int Iterations = 28;

    private static final int MaxFlightSteps = 480 * 6;

    /** Spin as a player describes it, in rev/s relative to the heading; negative top is backspin. */
    public static Vec3 Spin(Vec3 HeadingHoriz, double TopRevs, double SideRevs) {
        Vec3 F = new Vec3(HeadingHoriz.X(), 0, HeadingHoriz.Z()).Normalized();
        if (F.LengthSquared() == 0) return Vec3.Zero;

        Vec3 TopAxis = Vec3.Up.Cross(F);
        return TopAxis.Scale(TopRevs * 2 * Math.PI)
                      .Plus(Vec3.Up.Scale(SideRevs * 2 * Math.PI));
    }

    public static Solution AtTarget(Vec3 From, Vec3 Target, double Speed,
                                    double TopRevs, double SideRevs) {
        Vec3 Flat = new Vec3(Target.X() - From.X(), 0, Target.Z() - From.Z());
        double Range = Flat.Length();
        if (Range < 1e-6) {
            return new Solution(BallState.At(From, Vec3.Zero, Vec3.Zero), From, Double.NaN, 0, false);
        }
        Vec3 Heading = Flat.Scale(1.0 / Range);
        Vec3 SpinVec = Spin(Heading, TopRevs, SideRevs);

        double Lo = MinElev, Hi = MaxElev;
        boolean TooFast = RangeAt(From, Heading, Speed, SpinVec, Lo) > Range;
        if (TooFast) return Finish(From, Heading, Speed, SpinVec, Lo, false);
        boolean TooSlow = RangeAt(From, Heading, Speed, SpinVec, Hi) < Range;
        if (TooSlow) return Finish(From, Heading, Speed, SpinVec, Hi, false);

        for (int I = 0; I < Iterations; I++) {
            double Mid = 0.5 * (Lo + Hi);
            if (RangeAt(From, Heading, Speed, SpinVec, Mid) < Range) Lo = Mid; else Hi = Mid;
        }
        return Finish(From, Heading, Speed, SpinVec, 0.5 * (Lo + Hi), true);
    }

    private static Solution Finish(Vec3 From, Vec3 Heading, double Speed, Vec3 SpinVec,
                                   double Elev, boolean Converged) {
        BallState Launch = BallState.At(From, Velocity(Heading, Speed, Elev), SpinVec);
        Flight F = Fly(Launch);
        return new Solution(Launch, F.Landing, F.NetClearance, Math.toDegrees(Elev), Converged);
    }

    private static Vec3 Velocity(Vec3 Heading, double Speed, double Elevation) {
        return Heading.Scale(Speed * Math.cos(Elevation))
                      .Plus(Vec3.Up.Scale(Speed * Math.sin(Elevation)));
    }

    private static double RangeAt(Vec3 From, Vec3 Heading, double Speed, Vec3 SpinVec, double Elev) {
        Flight F = Fly(BallState.At(From, Velocity(Heading, Speed, Elev), SpinVec));
        Vec3 D = F.Landing.Minus(From);
        return Math.sqrt(D.X() * D.X() + D.Z() * D.Z());
    }

    /** Where a shot first meets the table plane, on the table or not. Never ask a World this. */
    public static Vec3 LandingPoint(BallState Launch) {
        return Fly(Launch).Landing();
    }

    private record Flight(Vec3 Landing, double NetClearance) {}

    /** Contact-free flight down to the descending crossing of the table plane. */
    private static Flight Fly(BallState S) {
        double NetClearance = Double.NaN;
        double PrevZ = S.Pos().Z();

        for (int I = 0; I < MaxFlightSteps; I++) {
            BallState Next = Integrator.Step(S, Dt);

            if (Double.isNaN(NetClearance) && PrevZ > 0 && Next.Pos().Z() <= 0) {
                double T = PrevZ / (PrevZ - Next.Pos().Z());
                double Y = S.Pos().Y() + (Next.Pos().Y() - S.Pos().Y()) * T;
                NetClearance = Y - BallR - NetHeight;
            }
            PrevZ = Next.Pos().Z();

            if (Next.Pos().Y() <= BallR && Next.Vel().Y() < 0) {
                double T = (S.Pos().Y() - BallR) / (S.Pos().Y() - Next.Pos().Y());
                return new Flight(Vec3.Lerp(S.Pos(), Next.Pos(), Math.max(0, Math.min(1, T))),
                                  NetClearance);
            }
            S = Next;
        }
        return new Flight(S.Pos(), NetClearance);
    }
}
