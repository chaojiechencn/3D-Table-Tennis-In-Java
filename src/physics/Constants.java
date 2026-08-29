package physics;

/**
 * Every real-world number the simulation uses, with the source it came from.
 *
 * The contract says: "Research papers on table tennis ball trajectories, so I have real
 * numbers to check my simulation against instead of guessing." So nothing in this file is
 * a guess. Anything tuned by eye is labelled TUNED and explains what it is standing in for.
 *
 * Sources, referred to by tag below:
 *   [ITTF]  ITTF Technical Leaflet T3 "The Ball" + Laws of Table Tennis 2.01-2.03
 *           (40 mm, 2.7 g, table 2.74 x 1.525 x 0.76 m, net 15.25 cm high / 1.83 m wide,
 *           bounce test: drop 30.5 cm onto steel, rebound 24-26 cm).
 *   [AERO]  Measured drag/lift on table tennis balls at Re ~ 1e4-1e5. C_d settles near
 *           0.40-0.50 across the playing range; lift coefficient rises with spin ratio
 *           and saturates around 0.3-0.4. Consistent with the standard sphere-with-spin
 *           results reported in the table tennis trajectory literature.
 *   [CONT]  Ball-table contact studies: coefficient of restitution ~0.89-0.93,
 *           sliding friction coefficient ~0.2-0.3.
 */
public final class Constants {

    private Constants() {}

    // ---------------------------------------------------------------- ball

    /** Ball radius. 40 mm diameter. [ITTF] */
    public static final double BALL_R = 0.020;

    /** Ball mass, 2.7 g. [ITTF] */
    public static final double BALL_M = 0.0027;

    /**
     * Moment of inertia. A table tennis ball is a HOLLOW SHELL, so I = (2/3)mr²,
     * not the solid sphere's (2/5)mr². This is not a nitpick: it is what makes the
     * grip impulse -(2/5)m*v_contact instead of -(2/7)m*v_contact, i.e. it changes how
     * much spin a bounce can generate by ~40%. [ITTF] (ball is a hollow celluloid/ABS shell)
     */
    public static final double BALL_I = (2.0 / 3.0) * BALL_M * BALL_R * BALL_R;

    /** Cross-sectional area presented to the airflow. */
    public static final double BALL_AREA = Math.PI * BALL_R * BALL_R;

    // ---------------------------------------------------------------- environment

    /** Standard gravity. */
    public static final double G = 9.80665;

    /** Air density at sea level, 15 degC, dry. */
    public static final double AIR_RHO = 1.225;

    /**
     * The factor drag and lift SHARE: 0.5 * rho * A / m. Pulling it out once keeps the two
     * aerodynamic forces dimensionally consistent — if you ever tune one, the other moves
     * with it. Value works out to ~0.285 1/m.
     */
    public static final double HALF_RHO_A_OVER_M = 0.5 * AIR_RHO * BALL_AREA / BALL_M;

    /** Drag coefficient. [AERO] */
    public static final double C_DRAG = 0.40;

    /**
     * Spin decay. Air torque bleeds spin slowly — a few percent per second of flight, so a
     * ~1 s rally shot keeps essentially all of its spin. Modelled as first-order decay.
     * TUNED within the range implied by [AERO]; the literature reports the effect as small
     * enough that most trajectory papers neglect it entirely.
     */
    public static final double SPIN_DECAY = 0.05;

    // ---------------------------------------------------------------- table geometry

    /** Playing surface, 2.74 m long. [ITTF] */
    public static final double TABLE_LENGTH = 2.74;

    /** Playing surface, 1.525 m wide. [ITTF] */
    public static final double TABLE_WIDTH = 1.525;

    /** Surface height above the floor. [ITTF] Physics origin sits ON the surface, so the
     *  floor lives at y = -TABLE_HEIGHT. */
    public static final double TABLE_HEIGHT = 0.76;

    /** Visual thickness of the table top (the slab we collide against). */
    public static final double TABLE_THICK = 0.025;

    // ---------------------------------------------------------------- net geometry

    /** Net height above the surface, 15.25 cm. [ITTF] */
    public static final double NET_HEIGHT = 0.1525;

    /** Net width, 1.83 m — it overhangs the table by 15.25 cm each side. [ITTF] */
    public static final double NET_WIDTH = 1.83;

    /** Collision thickness of the net sheet. Real netting is ~1 mm; we give it a little
     *  more so a fast ball cannot tunnel between two physics steps. */
    public static final double NET_THICK = 0.006;

    // ---------------------------------------------------------------- contact materials

    /**
     * Ball-on-table restitution. Measured range is 0.89-0.93 [CONT]; this sits near the top
     * of it, and the reason is worth writing down.
     *
     * [ITTF] specify a drop of 30.5 cm rebounding to 24-26 cm. The obvious arithmetic gives
     * e = sqrt(25/30.5) = 0.905 -- but that arithmetic assumes a vacuum. Over the 55 cm the
     * ball actually travels, air drag costs it about 1.5 cm of rebound, so a ball with
     * e = 0.905 lands *below* the ITTF band. Solving with drag included puts the value that
     * reproduces the specified bounce at 0.92, which is still inside the independently
     * measured ball-table range -- two unrelated sources agreeing, which is the useful part.
     *
     * SelfTest checks both halves of that: the rebound lands in the ITTF band, and the naive
     * drag-free value would have missed it.
     */
    public static final Material TABLE_MAT = new Material(0.92, 0.25, 1.00, 1.00);

    /**
     * The floor: a hard indoor sports floor. Slightly deader and grippier than the table.
     * Only here so a missed ball behaves instead of falling forever.
     */
    public static final Material FLOOR_MAT = new Material(0.80, 0.40, 1.00, 1.00);

    /**
     * The net. Loose fabric on a cord: it absorbs almost everything. Low restitution, high
     * friction, and heavy extra damping of both velocity and spin because the netting
     * deforms and drags rather than rebounding. TUNED — there is no standard COR for
     * netting; calibrated so a ball into the net drops on the near side instead of
     * bouncing back, which is what actually happens.
     */
    public static final Material NET_MAT = new Material(0.12, 0.50, 0.55, 0.35);

    /**
     * Contact parameters for one surface.
     *
     * @param restitution   normal bounce, 1 = perfectly elastic
     * @param friction      Coulomb sliding coefficient at the contact patch
     * @param velDamping    extra multiplier on velocity after the impulse (1 = none)
     * @param spinDamping   extra multiplier on spin after the impulse (1 = none)
     */
    public record Material(double restitution, double friction,
                           double velDamping, double spinDamping) {}

    // ---------------------------------------------------------------- integration

    /**
     * Fixed physics timestep, 1/480 s. Deliberately much smaller than a display frame:
     * a 30 m/s smash moves 6 cm per step at this rate, so a 4 cm ball cannot tunnel
     * through the 2.5 cm table slab. Gaffer On Games, "Fix Your Timestep!".
     */
    public static final double DT = 1.0 / 480.0;

    /** Longest frame the accumulator will honour. Beyond this we drop time on the floor
     *  rather than entering a spiral of death trying to catch up. */
    public static final double MAX_FRAME = 0.25;
}
