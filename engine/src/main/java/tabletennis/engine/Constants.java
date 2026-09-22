package tabletennis.engine;

/**
 * Every real-world number the simulation uses, each with its source. Derivations live in
 * docs/DESIGN.md. Source tags:
 *   [ITTF]  ITTF Technical Leaflet T3 "The Ball" + Laws of Table Tennis 2.01-2.03.
 *   [AERO]  Measured drag/lift on table tennis balls at Re ~ 1e4-1e5.
 *   [CONT]  Ball-table contact studies: restitution ~0.89-0.93, sliding friction ~0.2-0.3.
 *   [FIT]   arXiv:2606.28805, coefficients fitted to 277 recorded competitive matches.
 */
public final class Constants {

    private Constants() {}

    /** 40 mm diameter. [ITTF] */
    public static final double BALL_R = 0.020;

    /** 2.7 g. [ITTF] */
    public static final double BALL_M = 0.0027;

    /** Hollow shell, so (2/3)mr², not the solid sphere's (2/5)mr². [ITTF] */
    public static final double BALL_I = (2.0 / 3.0) * BALL_M * BALL_R * BALL_R;

    public static final double BALL_AREA = Math.PI * BALL_R * BALL_R;

    /** Standard gravity. */
    public static final double G = 9.80665;

    /** Air density at sea level, 15 degC, dry. */
    public static final double AIR_RHO = 1.225;

    /** The factor drag and lift share: 0.5 * rho * A / m. */
    public static final double HALF_RHO_A_OVER_M = 0.5 * AIR_RHO * BALL_AREA / BALL_M;

    /** Constant drag coefficient [AERO]; only SelfTest's closed-form free-fall check uses it. */
    public static final double C_DRAG = 0.40;

    /**
     * Spin decay per metre of flight, dw/dt = -k*w*|v| (James &amp; Haake, Engineering of Sport 7,
     * 2008). TUNED: 1/240 reproduces 5%/s at a typical 12 m/s rally speed.
     */
    public static final double SPIN_DECAY_PER_M = 1.0 / 240.0;

    /** 2.74 m. [ITTF] */
    public static final double TABLE_LENGTH = 2.74;

    /** 1.525 m. [ITTF] */
    public static final double TABLE_WIDTH = 1.525;

    /** 0.76 m. [ITTF] The origin sits on the surface, so the floor is at y = -TABLE_HEIGHT. */
    public static final double TABLE_HEIGHT = 0.76;

    /** Collision thickness of the table top. */
    public static final double TABLE_THICK = 0.025;

    /** 15.25 cm. [ITTF] */
    public static final double NET_HEIGHT = 0.1525;

    /** 1.83 m, overhanging the table by 15.25 cm each side. [ITTF] */
    public static final double NET_WIDTH = 1.83;

    /** Real netting is ~1 mm; thicker so a fast ball cannot tunnel between steps. */
    public static final double NET_THICK = 0.006;

    /** Drag coefficient [FIT]: rows are airspeed (m/s), columns spin ratio S. */
    public static final double[] DRAG_SPEEDS = { 2.5, 7.5, 12.5, 17.5 };
    public static final double[] DRAG_SPIN_RATIOS = { 0.0, 0.3, 0.7, 0.95, 1.5, 2.0 };
    public static final double[][] DRAG_TABLE = {
            { 0.55, 0.55, 0.55, 0.55, 0.55, 0.55 },   // 2.5 m/s
            { 0.49, 0.49, 0.55, 0.48, 0.53, 0.53 },   // 7.5 m/s
            { 0.47, 0.47, 0.53, 0.41, 0.48, 0.48 },   // 12.5 m/s
            { 0.47, 0.47, 0.51, 0.37, 0.45, 0.45 },   // 17.5 m/s
    };

    /**
     * Magnus coefficient [FIT], piecewise in spin rate at each speed. Volume-based C_M; {@link Aero}
     * converts to area-based lift. Captures the lift crisis near S ≈ 0.5-0.8 (Miyazaki et al.,
     * Eur. J. Phys. 38(2):024001, 2017).
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

    /** Blend width across the fit's breakpoint, whose branches disagree by up to 0.022 in C_M. */
    public static final double LIFT_BLEND = 0.05;

    /** ITTF Law 2.4.1 sets no size; a 75 mm disc matches a typical 157 x 150 mm head. */
    public static final double BLADE_R = 0.075;

    /** Blade (~6 mm) plus two rubbers capped at 4.05 mm each by ITTF Law 2.4.3. */
    public static final double BLADE_THICK = 0.015;

    /** Assembled rackets weigh 150-190 g. */
    public static final double RACKET_M = 0.170;

    /**
     * Inverted offensive rubber. e_n = 0.878 - 0.020|v_n| [FIT Table IV], corroborated by
     * arXiv:2604.11349. e_t springs the patch back, which is what reverses spin. Friction is TUNED
     * high so the elastic branch governs; drill damping applies to spin about the normal only.
     */
    public static final Material RACKET_MAT = new Material(
            0.878, 0.020, 0.45, 0.90,
            1.20,
            0.819, 0.010,
            0.805,
            1.00, 1.00);

    /**
     * Ball on table: e_n = 0.98 - 0.02|v_n| clamped to [0.75, 0.94] [FIT][CONT]; 0.931 at the ITTF
     * drop speed gives the 24-26 cm rebound. Friction 0.25 is centre of ITTF's 0.150-0.350 band.
     */
    public static final Material TABLE_MAT =
            new Material(0.98, 0.02, 0.75, 0.94, 0.25, 0.0, 0.0, 1.0, 1.00, 1.00);

    /** A hard indoor floor, slightly deader and grippier than the table. */
    public static final Material FLOOR_MAT = Material.rigid(0.80, 0.40, 1.00, 1.00);

    /** TUNED: no standard COR exists for netting; a ball into the net drops on the near side. */
    public static final Material NET_MAT = Material.rigid(0.12, 0.50, 0.55, 0.35);

    /**
     * Contact parameters for one surface. Restitution falls with approach speed because the shell
     * buckles above ~5 m/s [CONT]. Tangential restitution is the fraction of patch slip sprung back:
     * zero for rigid surfaces (perfect grip), nonzero only for rubber.
     */
    public record Material(double restitution, double restitutionFade,
                           double minRestitution, double maxRestitution,
                           double friction,
                           double tangentialRestitution, double tangentialRestitutionFade,
                           double drillSpinDamping,
                           double velDamping, double spinDamping) {

        public static Material rigid(double e, double friction,
                                     double velDamping, double spinDamping) {
            return new Material(e, 0, e, e, friction, 0, 0, 1, velDamping, spinDamping);
        }

        public double restitutionAt(double approach) {
            double e = restitution - restitutionFade * approach;
            return e < minRestitution ? minRestitution
                 : (e > maxRestitution ? maxRestitution : e);
        }

        public double tangentialRestitutionAt(double slip) {
            double e = tangentialRestitution - tangentialRestitutionFade * slip;
            return e < 0 ? 0 : (e > 1 ? 1 : e);
        }
    }

    /** 1/480 s: a 30 m/s smash moves 6 cm per step and cannot tunnel the 2.5 cm table slab. */
    public static final double DT = 1.0 / 480.0;

    /** Longest frame the accumulator honours; beyond it time is dropped to avoid a death spiral. */
    public static final double MAX_FRAME = 0.25;
}
