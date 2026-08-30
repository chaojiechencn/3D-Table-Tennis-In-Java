package physics;

import static physics.Constants.*;

/**
 * The forces on a ball in flight: gravity, drag, Magnus lift, and spin decay.
 *
 * This is the part of the project the contract calls out first — "How a spinning ball moves
 * through the air". Everything here is acceleration (force/mass) because the integrator only
 * ever wants accelerations, and dividing by a constant mass in four RK4 stages is wasted work.
 *
 * The whole curve of a topspin loop comes out of ONE cross product. Get its sign wrong and
 * the ball floats instead of dipping, which is why physics space is documented as
 * right-handed in three separate places.
 */
public final class Aero {

    private Aero() {}

    /** Time derivative of the RK4 state: d(pos), d(vel), d(spin). */
    public record Derivative(Vec3 dPos, Vec3 dVel, Vec3 dSpin) {}

    /**
     * How the drag coefficient is obtained. An argument, not a global.
     *
     * Real C_d is a function of Reynolds number and of spin, and modelling it that way is a
     * genuine improvement -- but it also destroys the two strongest checks in SelfTest, which
     * compare against the closed-form solutions for free fall with drag. Those closed forms
     * (v_t = sqrt(g/kC_d), and the tanh / ln cosh solution) EXIST ONLY FOR CONSTANT C_d. The
     * free-fall check is exact to 1 mm over 3 s and is the only thing testing RK4 against real
     * analysis rather than against itself, so losing it to gain realism would be a bad trade.
     *
     * Making the law a parameter means we do not have to choose: the analytic checks run
     * against CONSTANT and stay exactly as strong as they were, the game runs against the
     * measured law, and the measured law gets its own checks against measured values.
     */
    @FunctionalInterface
    public interface DragModel {
        /**
         * @param speed     airspeed, m/s
         * @param spinRatio S = r*omega/|v|, dimensionless
         */
        double coefficient(double speed, double spinRatio);

        /** A fixed coefficient, for the closed-form checks and for comparison. */
        static DragModel constant(double cd) { return (speed, spinRatio) -> cd; }
    }

    /** The drag law the game itself flies with: measured, speed- and spin-dependent. */
    public static final DragModel DEFAULT_DRAG = Aero::measuredDragCoefficient;

    /**
     * Measured drag coefficient, bilinear over DRAG_TABLE in (airspeed, spin ratio).
     *
     * Clamped rather than extrapolated outside the fitted range. The table stops at 17.5 m/s
     * and the C_d columns are already flat between 12.5 and 17.5, so clamping and linear
     * extrapolation agree to within a couple of percent at smash speed anyway -- and clamping
     * cannot produce a negative coefficient at 40 m/s, which extrapolation eventually would.
     */
    public static double measuredDragCoefficient(double speed, double spinRatio) {
        return bilinear(DRAG_TABLE, DRAG_SPEEDS, DRAG_SPIN_RATIOS, speed, spinRatio);
    }

    /**
     * Magnus coefficient C_M at a given speed and spin rate, interpolated between the fitted
     * rows and blended across the breakpoint between the two branches.
     *
     * @param omega spin rate in rad/s
     */
    public static double magnusCoefficient(double speed, double omega) {
        int hi = 1;
        while (hi < LIFT_SPEEDS.length - 1 && LIFT_SPEEDS[hi] < speed) hi++;
        int lo = hi - 1;
        double f = frac(speed, LIFT_SPEEDS[lo], LIFT_SPEEDS[hi]);
        return lerp(rowMagnus(lo, omega), rowMagnus(hi, omega), f);
    }

    /** One fitted speed row: linear branch, quadratic branch, blended where they meet. */
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

    // ---------------------------------------------------------------- interpolation

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

    /** Position of x between a and b, clamped to [0,1] so the tables never extrapolate. */
    private static double frac(double x, double a, double b) {
        if (b - a < 1e-12) return 0;
        double t = (x - a) / (b - a);
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }

    /**
     * Interpolation between adjacent table entries -- smooth, not linear, and the reason is
     * numerical rather than cosmetic.
     *
     * Straight-line interpolation puts a kink in the force field at every table node. RK4
     * only achieves fourth-order accuracy if the function it is sampling is smooth, so a ball
     * whose speed sweeps across a node during flight quietly drops the integrator to about
     * second order -- SelfTest's convergence check caught exactly that, measuring 30x error
     * reduction for a 4x smaller step where fourth order demands 256x.
     *
     * The classic smootherstep, 6t^5 - 15t^4 + 10t^3, has zero first AND second derivative at
     * both ends, so the assembled curve is C2 across every node while still passing exactly
     * through the measured values. The integrator gets its order back and the table is still
     * the table.
     */
    private static double lerp(double a, double b, double t) {
        double smooth = t * t * t * (t * (t * 6 - 15) + 10);
        return a + (b - a) * smooth;
    }

    /**
     * Drag: -1/2 rho A C_d |v| v / m, always opposing motion.
     *
     * At 10 m/s this is ~11.4 m/s², slightly MORE than gravity. That is the thing people
     * get wrong about table tennis: the ball is so light relative to its frontal area that
     * air resistance dominates the trajectory. Ignoring drag (as the throwaway scaffold did)
     * overshoots the far end of the table by roughly a metre on a normal drive.
     */
    public static Vec3 drag(Vec3 vel) {
        return drag(vel, Vec3.ZERO, DEFAULT_DRAG);
    }

    public static Vec3 drag(Vec3 vel, Vec3 spin, DragModel model) {
        double speed = vel.length();
        if (speed < 1e-9) return Vec3.ZERO;
        double cd = model.coefficient(speed, spinRatio(vel, spin));
        return vel.scale(-HALF_RHO_A_OVER_M * cd * speed);
    }

    /**
     * Magnus lift: 1/2 rho A C_L |v|² in the direction of (omega x v), divided by m.
     *
     * C_L saturates with the spin ratio S = r*omega/|v|:
     *
     *     C_L = 1 / (2 + |v|/(r*omega)) = S / (2S + 1)
     *
     * which gives 0.17 at S=0.25, 0.25 at S=0.5 and 0.33 at S=1, matching the measured
     * range for table tennis balls [AERO]. A plain linear C_L = k*S is the usual shortcut
     * and it blows up on serves, where the ball is slow and spinning at 150 rev/s — S goes
     * past 1 and the lift exceeds anything ever measured. The saturating form stays sane.
     *
     * Direction check, ball heading toward the far end (-Z). Topspin means the TOP of the
     * ball moves the way the ball is going, i.e. omega x (r*Y) points along -Z, which makes
     * topspin a rotation about -X (not +X — this is the easy one to get backwards):
     *   omega x v = (-X) x (-Z) = (X x Z) = -Y  ->  force points DOWN.
     * So topspin dips and backspin (+X) floats, which is the correct sign.
     */
    public static Vec3 magnus(Vec3 vel, Vec3 spin) {
        double speed = vel.length(), omega = spin.length();
        if (speed < 1e-9 || omega < 1e-9) return Vec3.ZERO;

        Vec3 dir = spin.cross(vel);           // right-handed; see class comment
        if (dir.lengthSquared() < 1e-18) return Vec3.ZERO;   // spin parallel to velocity: pure
                                                             // corkscrew, no lift at all
        return dir.normalized().scale(HALF_RHO_A_OVER_M * liftCoefficient(vel, spin)
                                      * speed * speed);
    }

    /**
     * Lift coefficient actually in use, in this project's area-based convention.
     *
     * The measured fit is volume-based, so the conversion C_L = (8/3) * C_M * S happens here
     * and only here -- see the derivation on Constants.LIFT_SPEEDS. Doing it at this boundary
     * is what keeps drag and lift sharing the single HALF_RHO_A_OVER_M factor: the shared
     * factor is untouched, only the coefficient it multiplies has changed.
     */
    public static double liftCoefficient(Vec3 vel, Vec3 spin) {
        double speed = vel.length(), omega = spin.length();
        if (speed < 1e-9 || omega < 1e-9) return 0;
        return (8.0 / 3.0) * magnusCoefficient(speed, omega) * spinRatio(vel, spin);
    }

    /** Spin ratio S = r*omega/|v|, the number the lift model is really a function of. */
    public static double spinRatio(Vec3 vel, Vec3 spin) {
        double speed = vel.length();
        return speed < 1e-9 ? 0 : BALL_R * spin.length() / speed;
    }

    /**
     * Air torque, modelled as first-order spin decay. Small: ~5%/s, so a 0.6 s shot arrives
     * with 97% of the spin it left with. Included because the contract asks for a simulation
     * that does not "break after running a while" — without any sink, a ball that gains spin
     * off every bounce would ratchet upward forever.
     */
    public static Vec3 spinDecay(Vec3 spin) {
        return spinDecay(spin, new Vec3(0, 0, -12));   // the speed the constant was pinned at
    }

    /**
     * dw/dt = -k * w * |v|. Always parallel to the spin, so the axis never moves -- which is
     * what lets the integrator advance orientation about a fixed axis (see Integrator).
     */
    public static Vec3 spinDecay(Vec3 spin, Vec3 vel) {
        return spin.scale(-SPIN_DECAY_PER_M * vel.length());
    }

    /** Total acceleration on a ball in free flight. */
    public static Vec3 acceleration(Vec3 vel, Vec3 spin) {
        return acceleration(vel, spin, DEFAULT_DRAG);
    }

    public static Vec3 acceleration(Vec3 vel, Vec3 spin, DragModel model) {
        return new Vec3(0, -G, 0).plus(drag(vel, spin, model)).plus(magnus(vel, spin));
    }

    /** The derivative the integrator samples. */
    public static Derivative derivative(Vec3 pos, Vec3 vel, Vec3 spin) {
        return derivative(pos, vel, spin, DEFAULT_DRAG);
    }

    public static Derivative derivative(Vec3 pos, Vec3 vel, Vec3 spin, DragModel model) {
        return new Derivative(vel, acceleration(vel, spin, model), spinDecay(spin, vel));
    }
}
