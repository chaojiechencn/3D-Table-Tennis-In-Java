package tabletennis.engine.aero;

import tabletennis.engine.BallSpec;
import tabletennis.engine.Environment;
import tabletennis.engine.math.Vec3;

import static tabletennis.engine.aero.AeroData.*;
import static tabletennis.engine.math.Numeric.Fraction;
import static tabletennis.engine.math.Numeric.SmoothLerp;

/** Accelerations on a ball in flight: gravity, drag, Magnus lift and spin decay. */
public final class Aerodynamics {

    private Aerodynamics() {}

    /** The factor drag and lift share, 0.5 * rho * A / m, kept in one place so they cannot drift apart. */
    public static final double DragLiftFactor =
            0.5 * Environment.AirDensity * BallSpec.CrossSection / BallSpec.Mass;

    /** The ball's rates of change, for the integrator. */
    public record Derivative(Vec3 PositionRate, Vec3 VelocityRate, Vec3 SpinRate) {}

    /** Clamped, not extrapolated, outside the table: extrapolation goes negative at smash speed. */
    public static double MeasuredDragCoefficient(double Speed, double SpinRatio) {
        return TableLookup(DragTable, DragSpeeds, DragSpinRatios, Speed, SpinRatio);
    }

    /** Volume-based C_M at a speed (m/s) and spin rate (rad/s). */
    public static double MagnusCoefficient(double Speed, double Omega) {
        int Upper = 1;
        while (Upper < LiftSpeeds.length - 1 && LiftSpeeds[Upper] < Speed) Upper++;
        int Lower = Upper - 1;
        double Along = Fraction(Speed, LiftSpeeds[Lower], LiftSpeeds[Upper]);
        return SmoothLerp(MagnusAtRow(Lower, Omega), MagnusAtRow(Upper, Omega), Along);
    }

    /** The fit's linear branch below its breakpoint and quadratic above, blended across it. */
    private static double MagnusAtRow(int Row, double Omega) {
        double Slope = LiftLinear[Row][0], Intercept = LiftLinear[Row][1], Breakpoint = LiftLinear[Row][2];
        double A = LiftQuadratic[Row][0], B = LiftQuadratic[Row][1], C = LiftQuadratic[Row][2];

        double Linear = Slope * Omega + Intercept;
        double Quadratic = A * Omega * Omega + B * Omega + C;

        double BlendFrom = Breakpoint * (1 - LiftBranchBlend), BlendTo = Breakpoint * (1 + LiftBranchBlend);
        if (Omega <= BlendFrom) return Linear;
        if (Omega >= BlendTo) return Quadratic;
        return SmoothLerp(Linear, Quadratic, Fraction(Omega, BlendFrom, BlendTo));
    }

    private static double TableLookup(double[][] Table, double[] Rows, double[] Columns,
                                      double Row, double Column) {
        int R = 1;
        while (R < Rows.length - 1 && Rows[R] < Row) R++;
        int C = 1;
        while (C < Columns.length - 1 && Columns[C] < Column) C++;

        double AlongRows = Fraction(Row, Rows[R - 1], Rows[R]);
        double AlongColumns = Fraction(Column, Columns[C - 1], Columns[C]);

        double Above = SmoothLerp(Table[R - 1][C - 1], Table[R - 1][C], AlongColumns);
        double Below = SmoothLerp(Table[R][C - 1], Table[R][C], AlongColumns);
        return SmoothLerp(Above, Below, AlongRows);
    }

    public static Vec3 Drag(Vec3 Velocity, Vec3 Spin, DragModel Model) {
        double Speed = Velocity.Length();
        if (Speed < 1e-9) return Vec3.Zero;
        double Coefficient = Model.Coefficient(Speed, SpinRatio(Velocity, Spin));
        return Velocity.Scale(-DragLiftFactor * Coefficient * Speed);
    }

    /** Along omega x v: topspin (about -X when heading -Z) gives (-X) x (-Z) = -Y, a dip. */
    public static Vec3 Magnus(Vec3 Velocity, Vec3 Spin) {
        double Speed = Velocity.Length(), Omega = Spin.Length();
        if (Speed < 1e-9 || Omega < 1e-9) return Vec3.Zero;

        Vec3 Direction = Spin.Cross(Velocity);
        if (Direction.LengthSquared() < 1e-18) return Vec3.Zero;   // a pure corkscrew has no lift
        return Direction.Normalized().Scale(DragLiftFactor * LiftCoefficient(Velocity, Spin) * Speed * Speed);
    }

    /** Area-based C_L = (8/3) C_M S, converted only here so drag and lift share one factor. */
    public static double LiftCoefficient(Vec3 Velocity, Vec3 Spin) {
        double Speed = Velocity.Length(), Omega = Spin.Length();
        if (Speed < 1e-9 || Omega < 1e-9) return 0;
        return (8.0 / 3.0) * MagnusCoefficient(Speed, Omega) * SpinRatio(Velocity, Spin);
    }

    /** S = r * omega / |v|. */
    public static double SpinRatio(Vec3 Velocity, Vec3 Spin) {
        double Speed = Velocity.Length();
        return Speed < 1e-9 ? 0 : BallSpec.Radius * Spin.Length() / Speed;
    }

    /** Parallel to the spin, so the axis never moves; it scales with airspeed, not with time alone. */
    public static Vec3 SpinDecay(Vec3 Spin, Vec3 Velocity) {
        return Spin.Scale(-SpinDecayPerMetre * Velocity.Length());
    }

    public static Vec3 Acceleration(Vec3 Velocity, Vec3 Spin, DragModel Model) {
        return new Vec3(0, -Environment.Gravity, 0).Plus(Drag(Velocity, Spin, Model)).Plus(Magnus(Velocity, Spin));
    }

    public static Derivative DerivativeOf(Vec3 Velocity, Vec3 Spin, DragModel Model) {
        return new Derivative(Velocity, Acceleration(Velocity, Spin, Model), SpinDecay(Spin, Velocity));
    }
}
