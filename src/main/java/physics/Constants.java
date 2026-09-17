package physics;

/**
 * Every real-world number the simulation uses, with the source it came from. Nothing here is a
 * guess; anything tuned by eye is labelled TUNED and says what it stands in for. See
 * docs/DESIGN.md, "Physics model (and where the numbers came from)", for the full derivations
 * and measurement history behind these -- this file keeps the citation and the number.
 *
 * Sources, referred to by tag below:
 *   [ITTF]  ITTF Technical Leaflet T3 "The Ball" + Laws of Table Tennis 2.01-2.03
 *           (40 mm, 2.7 g, table 2.74 x 1.525 x 0.76 m, net 15.25 cm high / 1.83 m wide,
 *           bounce test: drop 30.5 cm onto steel, rebound 24-26 cm).
 *   [AERO]  Measured drag/lift on table tennis balls at Re ~ 1e4-1e5. C_d settles near
 *           0.40-0.50 across the playing range; lift coefficient rises with spin ratio
 *           and saturates around 0.3-0.4.
 *   [CONT]  Ball-table contact studies: coefficient of restitution ~0.89-0.93,
 *           sliding friction coefficient ~0.2-0.3.
 *   [FIT]   "Physics Models for Sim-to-Real Transfer in Professional-Level Robot Table
 *           Tennis", arXiv:2606.28805 -- coefficients fitted to 277 recorded competitive
 *           matches, reported consistent with wind-tunnel and CFD work.
 */
public final class Constants {

    private Constants() {}

    // ---------------------------------------------------------------- ball

    /** Ball radius. 40 mm diameter. [ITTF] */
    public static final double BALL_R = 0.020;

    /** Ball mass, 2.7 g. [ITTF] */
    public static final double BALL_M = 0.0027;

    /** Moment of inertia. A table tennis ball is a HOLLOW SHELL, so I = (2/3)mr², not the solid
     *  sphere's (2/5)mr² -- this is what makes the grip impulse -(2/5)m*v_contact instead of
     *  -(2/7)m*v_contact, changing how much spin a bounce generates by ~40%. [ITTF] */
    public static final double BALL_I = (2.0 / 3.0) * BALL_M * BALL_R * BALL_R;

    /** Cross-sectional area presented to the airflow. */
    public static final double BALL_AREA = Math.PI * BALL_R * BALL_R;

    // ---------------------------------------------------------------- environment

    /** Standard gravity. */
    public static final double G = 9.80665;

    /** Air density at sea level, 15 degC, dry. */
    public static final double AIR_RHO = 1.225;

    /** The factor drag and lift SHARE: 0.5 * rho * A / m, ~0.285 1/m. Pulled out once so tuning
     *  one keeps both dimensionally consistent with the other. */
    public static final double HALF_RHO_A_OVER_M = 0.5 * AIR_RHO * BALL_AREA / BALL_M;

    /**
     * Constant-coefficient drag reference. [AERO] NOT what the game flies with (see
     * DRAG_TABLE below) -- kept because free fall with drag has a closed form only for
     * constant C_d, and SelfTest uses that form to check the integrator against exact
     * analysis to 1 mm over 3 s.
     */
    public static final double C_DRAG = 0.40;

    /**
     * Spin decay, per METRE of flight rather than per second: dw/dt = -k*w*|v|, following the
     * speed-coupled shape James &amp; Haake measured for other sports balls (Engineering of
     * Sport 7, 2008, pp. 165-170) -- no table-tennis-specific data exists. TUNED: 1/240 per
     * metre reproduces the previous 5%/s at a typical 12 m/s rally speed, fixing only the SHAPE
     * (slow balls hold spin, fast ones lose it quicker), not an unmeasured magnitude.
     */
    public static final double SPIN_DECAY_PER_M = 1.0 / 240.0;

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

    // ---------------------------------------------------------------- measured aerodynamics

    /**
     * Drag coefficient, measured [FIT]. Rows are airspeed in m/s, columns spin ratio S. The old
     * flat C_d = 0.40 under-drags by ~20% against every published table-tennis value
     * (0.45-0.55). The whole playing range is sub-critical (Re 5e3-9.3e4, well below the
     * smooth-sphere drag crisis at ~3e5), which is why C_d stays high and drifts down with
     * speed rather than collapsing. The dip at S = 0.95 is the drag-side signature of the same
     * transition behind the lift crisis below, not noise.
     */
    public static final double[] DRAG_SPEEDS = { 2.5, 7.5, 12.5, 17.5 };
    public static final double[] DRAG_SPIN_RATIOS = { 0.0, 0.3, 0.7, 0.95, 1.5, 2.0 };
    public static final double[][] DRAG_TABLE = {
            { 0.55, 0.55, 0.55, 0.55, 0.55, 0.55 },   // 2.5 m/s
            { 0.49, 0.49, 0.55, 0.48, 0.53, 0.53 },   // 7.5 m/s
            { 0.47, 0.47, 0.53, 0.41, 0.48, 0.48 },   // 12.5 m/s
            { 0.47, 0.47, 0.51, 0.37, 0.45, 0.45 },   // 17.5 m/s
    };

    /**
     * Magnus coefficient, measured [FIT], piecewise by spin rate at each speed. [FIT] writes
     * lift volume-based (F_M = C_M * rho * V * (v x omega)); this project is area-based, so the
     * conversion C_L = (8/3) C_M S happens once, in {@link Aero}.
     *
     * Replaces the old monotonic C_L = S/(2S+1): real lift has a valley near S ≈ 0.5-0.8 (the
     * "lift crisis" -- Miyazaki, Sakai, Komatsu, Takahashi &amp; Himeno, Eur. J. Phys.
     * 38(2):024001, 2017), which a saturating curve cannot represent and normal play (S ≈
     * 0.1-1.4) crosses constantly. The negative terms in the quadratic branch are the inverse
     * Magnus regime the fit finds at high speed; two sources disagree on where it crosses zero
     * (S ~ 0.48-0.65) and both are recorded in docs/DESIGN.md rather than averaged away, though
     * the effect is millimetres either way since C_L is already near zero there.
     */
    public static final double[] LIFT_SPEEDS = { 2.0, 3.5, 7.5, 10.5, 13.5, 17.0 };

    /** Below the breakpoint: C_M = m*omega + c. Columns are {m, c, omega_breakpoint}. */
    public static final double[][] LIFT_LINEAR = {
            {  0.0,       0.080, 150 },
            { -1.10e-3,   0.310, 200 },
            { -8.00e-4,   0.370, 350 },
            { -6.58e-4,   0.375, 440 },
            { -5.60e-4,   0.383, 550 },
            { -4.48e-4,   0.371, 650 },
    };

    /** Above the breakpoint: C_M = a*omega^2 + b*omega + c. */
    public static final double[][] LIFT_QUADRATIC = {
            { -1.852e-7, -1.296e-4,  0.0983 },
            { -1.667e-7, -3.333e-5,  0.1000 },
            { -2.000e-7,  1.700e-4,  0.0587 },
            { -2.604e-7,  3.646e-4, -0.0225 },
            { -3.571e-7,  5.357e-4, -0.0893 },
            { -1.000e-7,  2.300e-4, -0.0375 },
    };

    /** The two branches of the fit do not quite meet (up to 0.022 in C_M at the breakpoint), so
     *  this blends across a narrow window either side -- RK4 samples the derivative four times
     *  a step, and an unblended jump inside that window would be integrated as if it were real. */
    public static final double LIFT_BLEND = 0.05;

    // ---------------------------------------------------------------- racket

    /** Blade radius. ITTF Law 2.4.1 puts NO restriction on racket size, shape or weight -- only
     *  that it be flat and rigid, at least 85% natural wood by thickness. A typical head is
     *  about 157 x 150 mm, so a 75 mm disc is the honest round equivalent. */
    public static final double BLADE_R = 0.075;

    /** Blade thickness including both rubbers. A wooden blade is ~6 mm and ITTF Law 2.4.3 caps
     *  each sandwich rubber at 4.05 mm including adhesive, so ~15 mm is a legal maximum-ish
     *  racket -- a little thick on purpose, as anti-tunnelling margin, same reasoning as
     *  NET_THICK. */
    public static final double BLADE_THICK = 0.015;

    /** Racket mass. 150-190 g is the usual assembled range; the effective mass AT THE IMPACT
     *  POINT is what actually matters -- see {@link Paddle}'s class comment. */
    public static final double RACKET_M = 0.170;

    /**
     * Inverted ("smooth") offensive rubber, the covering most attacking players use.
     * e_n = 0.878 - 0.020*|v_n| [FIT Table IV], independently corroborated (arXiv:2604.11349,
     * 8194 bounces / 10 racket configurations). e_t = 0.819 - 0.010*|v_T| is the number that
     * matters: rubber SPRINGS the tangential contact back rather than only stopping it, which
     * is what reverses incoming spin -- a grip-or-slide model provably cannot do that
     * (arXiv:2604.11349). Friction 1.2 is TUNED high on purpose so the Coulomb cone almost never
     * binds and the elastic (e_t) branch governs, since no published friction coefficient exists
     * for inverted rubber at realistic stroke speeds. Drill damping 0.805 is scoped to spin about
     * the contact normal only -- applying it to the whole spin vector would destroy a fifth of
     * the topspin a stroke just generated. See docs/DESIGN.md for the k_p unit-error this
     * superseded.
     */
    public static final Material RACKET_MAT = new Material(
            0.878, 0.020, 0.45, 0.90,     // e_n = 0.878 - 0.020|v_n|, clamped
            1.20,                         // friction: grip-dominated, TUNED high on purpose
            0.819, 0.010,                 // e_t = 0.819 - 0.010|v_T|  <- spin reversal
            0.805,                        // drill damping about the normal
            1.00, 1.00);

    // ---------------------------------------------------------------- contact materials

    /**
     * Ball-on-table restitution, speed-dependent: e_n = 0.98 - 0.02*|v_n|, clamped to
     * [0.75, 0.94] [FIT], consistent with cap-buckling roll-off above ~5 m/s [CONT]. At the ITTF
     * drop speed (2.43 m/s) that gives e = 0.931, reproducing the required 24-26 cm rebound once
     * air drag over the 55 cm drop is accounted for (the drag-free arithmetic alone gives 0.905,
     * which undershoots the ITTF band -- see docs/DESIGN.md). At 8 m/s (a smash) it is 0.82; the
     * old flat 0.92 was this curve evaluated at one point and applied everywhere, so a smash used
     * to bounce too lively. Friction 0.25 sits dead centre of ITTF's own 0.150-0.350 acceptance
     * band and matches [FIT] exactly. Tangential restitution is left at 0 (perfect grip) -- real
     * and nonzero for a bouncing ball in general (Cross, Am. J. Phys. 70(5):482), but only ever
     * measured for a TENNIS ball, so it stays zero until a table-tennis-specific number exists.
     */
    public static final Material TABLE_MAT =
            new Material(0.98, 0.02, 0.75, 0.94, 0.25, 0.0, 0.0, 1.0, 1.00, 1.00);

    /** The floor: a hard indoor sports floor, slightly deader and grippier than the table. Only
     *  here so a missed ball behaves instead of falling forever. */
    public static final Material FLOOR_MAT = Material.rigid(0.80, 0.40, 1.00, 1.00);

    /** The net: loose fabric on a cord, absorbing almost everything. TUNED -- there is no
     *  standard COR for netting; calibrated so a ball into the net drops on the near side
     *  instead of bouncing back, which is what actually happens. */
    public static final Material NET_MAT = Material.rigid(0.12, 0.50, 0.55, 0.35);

    /**
     * Contact parameters for one surface. Restitution is a function of approach speed, not a
     * constant: above roughly 5 m/s of normal impact a table tennis ball's thin shell BUCKLES
     * (dimples inward instead of compressing uniformly) and restitution falls away from ~0.9
     * toward 0.8 and below [CONT] -- a single number cannot describe a ball that bounces at 0.93
     * off a gentle drop and 0.82 off a smash.
     *
     * @param restitution   normal bounce extrapolated to zero approach speed (the intercept)
     * @param restitutionFade how much restitution is lost per m/s of normal approach speed
     * @param minRestitution  floor on the above, so the fit cannot run off the end of its range
     * @param maxRestitution  ceiling, same reason
     * @param friction      Coulomb sliding coefficient at the contact patch
     * @param tangentialRestitution how much of the contact patch's sliding speed is SPRUNG
     *                      BACK rather than merely stopped. Zero for anything rigid, where the
     *                      patch is brought to rest and no further (perfect grip). Non-zero
     *                      only for rubber, which stores tangential energy in the topsheet and
     *                      returns it -- and that is the entire mechanism behind spin reversal.
     * @param velDamping    extra multiplier on velocity after the impulse (1 = none)
     * @param spinDamping   extra multiplier on spin after the impulse (1 = none)
     */
    public record Material(double restitution, double restitutionFade,
                           double minRestitution, double maxRestitution,
                           double friction,
                           double tangentialRestitution, double tangentialRestitutionFade,
                           double drillSpinDamping,
                           double velDamping, double spinDamping) {

        /** A surface with a speed-independent bounce and no tangential springback. */
        public static Material rigid(double e, double friction,
                                     double velDamping, double spinDamping) {
            return new Material(e, 0, e, e, friction, 0, 0, 1, velDamping, spinDamping);
        }

        /** Restitution at a given normal approach speed (positive, m/s). */
        public double restitutionAt(double approach) {
            double e = restitution - restitutionFade * approach;
            return e < minRestitution ? minRestitution
                 : (e > maxRestitution ? maxRestitution : e);
        }

        /**
         * Tangential restitution at a given contact-patch sliding speed.
         *
         * Zero means the patch is brought to rest and no further, which is perfect grip and
         * the right model for anything rigid. Above zero the surface springs the patch back,
         * which is what reverses spin.
         */
        public double tangentialRestitutionAt(double slip) {
            double e = tangentialRestitution - tangentialRestitutionFade * slip;
            return e < 0 ? 0 : (e > 1 ? 1 : e);
        }
    }

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
