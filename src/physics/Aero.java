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
     * Drag: -1/2 rho A C_d |v| v / m, always opposing motion.
     *
     * At 10 m/s this is ~11.4 m/s², slightly MORE than gravity. That is the thing people
     * get wrong about table tennis: the ball is so light relative to its frontal area that
     * air resistance dominates the trajectory. Ignoring drag (as the throwaway scaffold did)
     * overshoots the far end of the table by roughly a metre on a normal drive.
     */
    public static Vec3 drag(Vec3 vel) {
        double speed = vel.length();
        if (speed < 1e-9) return Vec3.ZERO;
        return vel.scale(-HALF_RHO_A_OVER_M * C_DRAG * speed);
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
        double spinRatio = BALL_R * omega / speed;
        double cl = spinRatio / (2.0 * spinRatio + 1.0);

        return dir.normalized().scale(HALF_RHO_A_OVER_M * cl * speed * speed);
    }

    /** Lift coefficient actually in use, exposed so the HUD can show it. */
    public static double liftCoefficient(Vec3 vel, Vec3 spin) {
        double speed = vel.length(), omega = spin.length();
        if (speed < 1e-9 || omega < 1e-9) return 0;
        double s = BALL_R * omega / speed;
        return s / (2.0 * s + 1.0);
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
        return spin.scale(-SPIN_DECAY);
    }

    /** Total acceleration on a ball in free flight. */
    public static Vec3 acceleration(Vec3 vel, Vec3 spin) {
        return new Vec3(0, -G, 0).plus(drag(vel)).plus(magnus(vel, spin));
    }

    /** The derivative the integrator samples. */
    public static Derivative derivative(Vec3 pos, Vec3 vel, Vec3 spin) {
        return new Derivative(vel, acceleration(vel, spin), spinDecay(spin));
    }
}
