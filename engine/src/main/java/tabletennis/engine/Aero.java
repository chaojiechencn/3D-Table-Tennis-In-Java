package tabletennis.engine;

import static tabletennis.engine.Constants.*;

/** Accelerations on a ball in flight: gravity, drag, Magnus lift and spin decay. */
public final class Aero {

    private Aero() {}

    public record Derivative(Vec3 DPos, Vec3 DVel, Vec3 DSpin) {}

    /** A parameter rather than a global so SelfTest's closed-form checks can fly constant C_d. */
    @FunctionalInterface
    public interface DragModel {
        double Coefficient(double Speed, double SpinRatio);

        static DragModel Constant(double Cd) { return (Speed, SpinRatio) -> Cd; }
    }

    public static final DragModel DefaultDrag = Aero::MeasuredDragCoefficient;

    /** Clamped, not extrapolated, outside the table: extrapolation goes negative at smash speed. */
    public static double MeasuredDragCoefficient(double Speed, double SpinRatio) {
        return Bilinear(DragTable, DragSpeeds, DragSpinRatios, Speed, SpinRatio);
    }

    /** Volume-based C_M at a speed (m/s) and spin rate (rad/s). */
    public static double MagnusCoefficient(double Speed, double Omega) {
        int Hi = 1;
        while (Hi < LiftSpeeds.length - 1 && LiftSpeeds[Hi] < Speed) Hi++;
        int Lo = Hi - 1;
        double F = Frac(Speed, LiftSpeeds[Lo], LiftSpeeds[Hi]);
        return Lerp(RowMagnus(Lo, Omega), RowMagnus(Hi, Omega), F);
    }

    private static double RowMagnus(int Row, double Omega) {
        double M = LiftLinear[Row][0], C = LiftLinear[Row][1], Wb = LiftLinear[Row][2];
        double A = LiftQuadratic[Row][0], B = LiftQuadratic[Row][1], Q = LiftQuadratic[Row][2];

        double Linear = M * Omega + C;
        double Quad = A * Omega * Omega + B * Omega + Q;

        double Lo = Wb * (1 - LiftBlend), Hi = Wb * (1 + LiftBlend);
        if (Omega <= Lo) return Linear;
        if (Omega >= Hi) return Quad;
        return Lerp(Linear, Quad, Frac(Omega, Lo, Hi));
    }

    private static double Bilinear(double[][] Table, double[] Rows, double[] Cols,
                                   double R, double C) {
        int Ri = 1;
        while (Ri < Rows.length - 1 && Rows[Ri] < R) Ri++;
        int Ci = 1;
        while (Ci < Cols.length - 1 && Cols[Ci] < C) Ci++;

        double Fr = Frac(R, Rows[Ri - 1], Rows[Ri]);
        double Fc = Frac(C, Cols[Ci - 1], Cols[Ci]);

        double Top = Lerp(Table[Ri - 1][Ci - 1], Table[Ri - 1][Ci], Fc);
        double Bot = Lerp(Table[Ri][Ci - 1], Table[Ri][Ci], Fc);
        return Lerp(Top, Bot, Fr);
    }

    private static double Frac(double X, double A, double B) {
        if (B - A < 1e-12) return 0;
        double T = (X - A) / (B - A);
        return T < 0 ? 0 : (T > 1 ? 1 : T);
    }

    /** Smootherstep, not linear: RK4 keeps 4th order only on a C2 right-hand side. */
    private static double Lerp(double A, double B, double T) {
        double Smooth = T * T * T * (T * (T * 6 - 15) + 10);
        return A + (B - A) * Smooth;
    }

    public static Vec3 Drag(Vec3 Vel) {
        return Drag(Vel, Vec3.Zero, DefaultDrag);
    }

    public static Vec3 Drag(Vec3 Vel, Vec3 Spin, DragModel Model) {
        double Speed = Vel.Length();
        if (Speed < 1e-9) return Vec3.Zero;
        double Cd = Model.Coefficient(Speed, SpinRatio(Vel, Spin));
        return Vel.Scale(-HalfRhoAOverM * Cd * Speed);
    }

    /** Along omega x v: topspin (about -X when heading -Z) gives (-X) x (-Z) = -Y, a dip. */
    public static Vec3 Magnus(Vec3 Vel, Vec3 Spin) {
        double Speed = Vel.Length(), Omega = Spin.Length();
        if (Speed < 1e-9 || Omega < 1e-9) return Vec3.Zero;

        Vec3 Dir = Spin.Cross(Vel);
        if (Dir.LengthSquared() < 1e-18) return Vec3.Zero;   // pure corkscrew: no lift
        return Dir.Normalized().Scale(HalfRhoAOverM * LiftCoefficient(Vel, Spin)
                                      * Speed * Speed);
    }

    /** Area-based C_L = (8/3) C_M S, converted only here so drag and lift share one factor. */
    public static double LiftCoefficient(Vec3 Vel, Vec3 Spin) {
        double Speed = Vel.Length(), Omega = Spin.Length();
        if (Speed < 1e-9 || Omega < 1e-9) return 0;
        return (8.0 / 3.0) * MagnusCoefficient(Speed, Omega) * SpinRatio(Vel, Spin);
    }

    /** S = r*omega/|v|. */
    public static double SpinRatio(Vec3 Vel, Vec3 Spin) {
        double Speed = Vel.Length();
        return Speed < 1e-9 ? 0 : BallR * Spin.Length() / Speed;
    }

    /** At the 12 m/s the decay constant was pinned at. */
    public static Vec3 SpinDecay(Vec3 Spin) {
        return SpinDecay(Spin, new Vec3(0, 0, -12));
    }

    /** Parallel to the spin, so the axis never moves. */
    public static Vec3 SpinDecay(Vec3 Spin, Vec3 Vel) {
        return Spin.Scale(-SpinDecayPerM * Vel.Length());
    }

    public static Vec3 Acceleration(Vec3 Vel, Vec3 Spin) {
        return Acceleration(Vel, Spin, DefaultDrag);
    }

    public static Vec3 Acceleration(Vec3 Vel, Vec3 Spin, DragModel Model) {
        return new Vec3(0, -G, 0).Plus(Drag(Vel, Spin, Model)).Plus(Magnus(Vel, Spin));
    }

    public static Derivative Derivative(Vec3 Pos, Vec3 Vel, Vec3 Spin) {
        return Derivative(Pos, Vel, Spin, DefaultDrag);
    }

    public static Derivative Derivative(Vec3 Pos, Vec3 Vel, Vec3 Spin, DragModel Model) {
        return new Derivative(Vel, Acceleration(Vel, Spin, Model), SpinDecay(Spin, Vel));
    }
}
