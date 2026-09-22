package tabletennis.engine;

import static tabletennis.engine.Constants.*;

/** Accelerations on a ball in flight: gravity, drag, Magnus lift and spin decay. */
public final class Aero {

    private Aero() {}

    public record Derivative(Vec3 dPos, Vec3 dVel, Vec3 dSpin) {}

    /** A parameter rather than a global so SelfTest's closed-form checks can fly constant C_d. */
    @FunctionalInterface
    public interface DragModel {
        double coefficient(double speed, double spinRatio);

        static DragModel constant(double cd) { return (speed, spinRatio) -> cd; }
    }

    public static final DragModel DEFAULT_DRAG = Aero::measuredDragCoefficient;

    /** Clamped, not extrapolated, outside the table: extrapolation goes negative at smash speed. */
    public static double measuredDragCoefficient(double speed, double spinRatio) {
        return bilinear(DRAG_TABLE, DRAG_SPEEDS, DRAG_SPIN_RATIOS, speed, spinRatio);
    }

    /** Volume-based C_M at a speed (m/s) and spin rate (rad/s). */
    public static double magnusCoefficient(double speed, double omega) {
        int hi = 1;
        while (hi < LIFT_SPEEDS.length - 1 && LIFT_SPEEDS[hi] < speed) hi++;
        int lo = hi - 1;
        double f = frac(speed, LIFT_SPEEDS[lo], LIFT_SPEEDS[hi]);
        return lerp(rowMagnus(lo, omega), rowMagnus(hi, omega), f);
    }

    private static double rowMagnus(int row, double omega) {
        double m = LIFT_LINEAR[row][0], c = LIFT_LINEAR[row][1], wb = LIFT_LINEAR[row][2];
        double a = LIFT_QUADRATIC[row][0], b = LIFT_QUADRATIC[row][1], q = LIFT_QUADRATIC[row][2];

        double linear = m * omega + c;
        double quad = a * omega * omega + b * omega + q;

        double lo = wb * (1 - LIFT_BLEND), hi = wb * (1 + LIFT_BLEND);
        if (omega <= lo) return linear;
        if (omega >= hi) return quad;
        return lerp(linear, quad, frac(omega, lo, hi));
    }

    private static double bilinear(double[][] table, double[] rows, double[] cols,
                                   double r, double c) {
        int ri = 1;
        while (ri < rows.length - 1 && rows[ri] < r) ri++;
        int ci = 1;
        while (ci < cols.length - 1 && cols[ci] < c) ci++;

        double fr = frac(r, rows[ri - 1], rows[ri]);
        double fc = frac(c, cols[ci - 1], cols[ci]);

        double top = lerp(table[ri - 1][ci - 1], table[ri - 1][ci], fc);
        double bot = lerp(table[ri][ci - 1], table[ri][ci], fc);
        return lerp(top, bot, fr);
    }

    private static double frac(double x, double a, double b) {
        if (b - a < 1e-12) return 0;
        double t = (x - a) / (b - a);
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }

    /** Smootherstep, not linear: RK4 keeps 4th order only on a C2 right-hand side. */
    private static double lerp(double a, double b, double t) {
        double smooth = t * t * t * (t * (t * 6 - 15) + 10);
        return a + (b - a) * smooth;
    }

    public static Vec3 drag(Vec3 vel) {
        return drag(vel, Vec3.ZERO, DEFAULT_DRAG);
    }

    public static Vec3 drag(Vec3 vel, Vec3 spin, DragModel model) {
        double speed = vel.length();
        if (speed < 1e-9) return Vec3.ZERO;
        double cd = model.coefficient(speed, spinRatio(vel, spin));
        return vel.scale(-HALF_RHO_A_OVER_M * cd * speed);
    }

    /** Along omega x v: topspin (about -X when heading -Z) gives (-X) x (-Z) = -Y, a dip. */
    public static Vec3 magnus(Vec3 vel, Vec3 spin) {
        double speed = vel.length(), omega = spin.length();
        if (speed < 1e-9 || omega < 1e-9) return Vec3.ZERO;

        Vec3 dir = spin.cross(vel);
        if (dir.lengthSquared() < 1e-18) return Vec3.ZERO;   // pure corkscrew: no lift
        return dir.normalized().scale(HALF_RHO_A_OVER_M * liftCoefficient(vel, spin)
                                      * speed * speed);
    }

    /** Area-based C_L = (8/3) C_M S, converted only here so drag and lift share one factor. */
    public static double liftCoefficient(Vec3 vel, Vec3 spin) {
        double speed = vel.length(), omega = spin.length();
        if (speed < 1e-9 || omega < 1e-9) return 0;
        return (8.0 / 3.0) * magnusCoefficient(speed, omega) * spinRatio(vel, spin);
    }

    /** S = r*omega/|v|. */
    public static double spinRatio(Vec3 vel, Vec3 spin) {
        double speed = vel.length();
        return speed < 1e-9 ? 0 : BALL_R * spin.length() / speed;
    }

    /** At the 12 m/s the decay constant was pinned at. */
    public static Vec3 spinDecay(Vec3 spin) {
        return spinDecay(spin, new Vec3(0, 0, -12));
    }

    /** Parallel to the spin, so the axis never moves. */
    public static Vec3 spinDecay(Vec3 spin, Vec3 vel) {
        return spin.scale(-SPIN_DECAY_PER_M * vel.length());
    }

    public static Vec3 acceleration(Vec3 vel, Vec3 spin) {
        return acceleration(vel, spin, DEFAULT_DRAG);
    }

    public static Vec3 acceleration(Vec3 vel, Vec3 spin, DragModel model) {
        return new Vec3(0, -G, 0).plus(drag(vel, spin, model)).plus(magnus(vel, spin));
    }

    public static Derivative derivative(Vec3 pos, Vec3 vel, Vec3 spin) {
        return derivative(pos, vel, spin, DEFAULT_DRAG);
    }

    public static Derivative derivative(Vec3 pos, Vec3 vel, Vec3 spin, DragModel model) {
        return new Derivative(vel, acceleration(vel, spin, model), spinDecay(spin, vel));
    }
}
