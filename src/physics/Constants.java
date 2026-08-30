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

    /**
     * Constant-coefficient drag reference. [AERO]
     *
     * NOT what the game flies with any more -- that is the measured DRAG_TABLE below. This
     * value survives for one specific job: free fall with drag has a closed-form solution
     * ONLY when C_d is constant, and SelfTest uses that closed form to check the integrator
     * against exact analysis to 1 mm over 3 s. Keeping a named constant law preserves that
     * check at full strength instead of trading it away for realism.
     */
    public static final double C_DRAG = 0.40;

    /**
     * Spin decay, per metre of flight rather than per second.
     *
     * The old form was dw/dt = -0.05*w, independent of airspeed, which says a ball drifting
     * at 1 m/s sheds spin as fast as one screaming past at 30 m/s. It does not. James and
     * Haake, "The Spin Decay of Sports Balls in Flight" (Engineering of Sport 7, 2008, pp.
     * 165-170), measured a strong linear relationship between spin decay and the PRODUCT of
     * spin and speed, so the shape here is dw/dt = -k*w*|v| and k has units of 1/m.
     *
     * Still TUNED, and deliberately so, because the magnitude is genuinely unsettled:
     *   - James and Haake measured real decay, but on tennis balls, footballs and oversize
     *     tennis balls. There is no table tennis in their data.
     *   - "Table Tennis and Physics" (IntechOpen ch. 83844) states flatly that the spin of a
     *     table tennis ball was experimentally shown to be CONSTANT during flight.
     * No table-tennis-specific time constant appears anywhere I could find; estimates from
     * the spin-down torque put tau somewhere in 2-10 s.
     *
     * So the value is pinned conservatively: 1/240 per metre reproduces the previous 5%/s at
     * a typical 12 m/s rally speed. That takes the fix to the SHAPE -- slow balls now hold
     * their spin and fast ones lose it quicker -- without smuggling in a magnitude change
     * that no measurement supports.
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

    /*
     * The two tables below replace a pair of guesses with measurements. Both come from
     * [FIT] = "Physics Models for Sim-to-Real Transfer in Professional-Level Robot Table
     * Tennis", arXiv:2606.28805, whose coefficients were fitted to 277 recorded competitive
     * matches and which the authors report as consistent with the wind-tunnel and CFD work.
     *
     * They are tables rather than formulas because the real coefficients are not monotonic
     * and no tidy closed form fits them. That non-monotonicity is the physics: see
     * LIFT_* below.
     */

    /**
     * Drag coefficient, measured. Rows are airspeed in m/s, columns are spin ratio S.
     *
     * The old model used a flat C_d = 0.40. Every table-tennis-specific published value is
     * 0.45-0.55, so 0.40 under-drags the ball by around 20% -- long loops flew further and
     * flatter than they should. The whole playing range (Re from 5e3 at 2 m/s to 9.3e4 at
     * 35 m/s) is SUB-CRITICAL, well below the smooth-sphere drag crisis at Re ~ 3e5, which
     * is why C_d stays high and only drifts down with speed instead of collapsing.
     *
     * The dip in the S = 0.95 column is not noise. It is the drag-side signature of the same
     * laminar-turbulent transition that produces the lift crisis. [FIT]
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
     * Magnus coefficient, measured, as a piecewise function of spin rate at each speed.
     *
     * [FIT] writes lift volume-based, F_M = C_M * rho * V * (v x omega). This project is
     * area-based, so the conversion happens once, in Aero:
     *
     *     1/2 C_L rho A v^2 = C_M rho V v omega,   A = pi r^2,  V = (4/3) pi r^3
     *                  C_L = (8/3) C_M S,          S = r omega / |v|
     *
     * WHY THIS REPLACED C_L = S/(2S+1). The old saturating curve rises monotonically toward
     * 0.5. Real lift does not: converting the table below gives C_L that is roughly FLAT at
     * 0.16-0.25 across the whole reachable range. Checked against the old model term by term,
     * the old one was ~15% too WEAK below S = 0.5, up to 1.9x too STRONG around S = 0.75-0.95,
     * and about right again above S = 1.1.
     *
     * That shape is the "lift crisis" -- Miyazaki, Sakai, Komatsu, Takahashi and Himeno,
     * "Lift crisis of a spinning table tennis ball", Eur. J. Phys. 38(2):024001 (2017), who
     * measured a deep valley in C_L near S = 0.5 with lift almost vanishing at Re = 9e4. A
     * monotonic saturating curve cannot represent a valley at all, which is why this is a
     * table and not a formula.
     *
     * And the valley is not an exotic edge case. Realistic play spans S = 0.1 (fast smash) to
     * S = 1.4 (slow heavy chop), with serves near 0.72 and loops near 0.92 -- so a rally
     * crosses the crisis constantly.
     *
     * The negative constant terms in the quadratic branch at high speed are the inverse
     * Magnus regime falling out of the fit. It is real (Ito and Ueshima, Trans. JSST 17(1),
     * put the zero crossing at S ~ 0.65; Miyazaki's experiment puts it at S ~ 0.48-0.50 --
     * the sources genuinely disagree, and both are recorded here rather than averaged away)
     * but small: it moves a trajectory by millimetres, because C_L is already near zero
     * wherever it goes negative.
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

    /**
     * The two branches of the fit do not quite meet -- at 13.5 m/s the step at the breakpoint
     * is 0.022 in C_M, and the others are smaller. Blending across a narrow window either side
     * makes the force continuous, which matters because RK4 samples the derivative four times
     * per step and a jump inside that window would be integrated as if it were real.
     */
    public static final double LIFT_BLEND = 0.05;

    // ---------------------------------------------------------------- racket

    /**
     * Blade radius. ITTF Law 2.4.1 puts NO restriction on racket size, shape or weight -- only
     * that the blade be flat and rigid, at least 85% natural wood by thickness. A typical
     * head is about 157 x 150 mm, so a 75 mm disc is the honest round equivalent.
     */
    public static final double BLADE_R = 0.075;

    /**
     * Blade thickness including both rubbers. A wooden blade is ~6 mm and ITTF Law 2.4.3 caps
     * each sandwich rubber at 4.05 mm including adhesive, so ~15 mm is a legal maximum-ish
     * racket. Being a little thick is deliberate: like the net, extra thickness is anti-
     * tunnelling margin, and 15 mm at a 20 m/s swing is still well inside one physics step.
     */
    public static final double BLADE_THICK = 0.015;

    /** Racket mass. 150-190 g is the usual assembled range; the effective mass at the impact
     *  point is what actually matters, and Paddle's class comment does that arithmetic. */
    public static final double RACKET_M = 0.170;

    /**
     * Inverted ("smooth") offensive rubber -- the covering most attacking players use.
     *
     * NORMAL. e_n = 0.878 - 0.020*|v_n|, from arXiv:2606.28805 Table IV, clamped to the range
     * the fit was measured over. arXiv:2604.11349 measured the same slope independently
     * across 8194 bounces and 10 racket configurations: -0.021 per m/s for offensive rubbers,
     * -0.017 for all-round, a total drop of about 0.15 across 2-12 m/s.
     *
     * TANGENTIAL -- and this is the one that matters. A rigid surface can only ever bring the
     * contact patch to rest; rubber stores tangential energy in the topsheet and SPRINGS IT
     * BACK. That is what reverses incoming spin instead of merely absorbing it, and
     * arXiv:2604.11349 states plainly that a table-style grip-or-slide model provably cannot
     * reproduce the spin inversion they measured. So e_t = 0.819 - 0.010*|v_T|.
     *
     * A NOTE ON A NUMBER THAT DID NOT SURVIVE CHECKING. The same paper also reports a
     * tangential stiffness k_p ~ 0.019, defined through a_1 = 1 - k_p/m. Taken at face value
     * that is impossible: perfect grip for a hollow shell is k_p = (2/5)m = 0.00108 kg, and
     * k_p = 0.019 implies a tangential restitution of 16.6 -- the contact patch leaving
     * sixteen times faster than it arrived. Working backwards from the paper's OWN e_t = 0.819
     * via J_t = -(2/5)m(1+e_t)v_s gives k_p = 0.00196 kg. The reported figure is a factor of
     * ten out, and the two agree once that is fixed. This model uses e_t, which is
     * dimensionless and independently reported and therefore cannot hide an error like that.
     *
     * FRICTION. There is no peer-reviewed coefficient of friction for inverted rubber, because
     * inverted rubber essentially never slides on the ball at realistic stroke speeds -- it
     * grips, and the literature describes it with a tangential stiffness instead. The only
     * published figure, 0.197-0.207, is for ANTI-SPIN, where sliding does happen. 1.2 here is
     * set high on purpose so the Coulomb cone almost never binds and the elastic branch is
     * what governs, which is the behaviour the measurements describe. TUNED, and flagged as
     * such because a tacky elastomer above mu = 1 is plausible but not something I can cite.
     *
     * DRILL DAMPING. e_s = 0.805 damps the spin component about the contact normal -- the
     * corkscrew component, which friction has little purchase on. It is deliberately NOT the
     * blanket spin damping: applying 0.805 to the whole spin vector would destroy a fifth of
     * the topspin the stroke had just generated.
     */
    public static final Material RACKET_MAT = new Material(
            0.878, 0.020, 0.45, 0.90,     // e_n = 0.878 - 0.020|v_n|, clamped
            1.20,                         // friction: grip-dominated, TUNED high on purpose
            0.819, 0.010,                 // e_t = 0.819 - 0.010|v_T|  <- spin reversal
            0.805,                        // drill damping about the normal
            1.00, 1.00);

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
     *
     * NOW SPEED-DEPENDENT. e_n = 0.98 - 0.02*|v_n|, clamped to [0.75, 0.94], fitted to real
     * trajectories in arXiv:2606.28805 and consistent with the cap-buckling roll-off above
     * ~5 m/s. At the ITTF drop speed of 2.43 m/s that is e = 0.931, which reproduces the
     * required 24-26 cm rebound; at a 5 m/s rally bounce it is 0.88, and at an 8 m/s smash
     * 0.82. The old flat 0.92 was that curve evaluated at one point and then applied
     * everywhere, which is why a smash used to bounce too lively.
     *
     * Friction 0.25 is unchanged and now has a second source: arXiv:2606.28805 fits exactly
     * 0.25, and ITTF's own acceptance band for table coefficient of friction is 0.150-0.350,
     * so it sits dead centre of both.
     *
     * The table's tangential restitution is left at zero, i.e. perfect grip. Rod Cross
     * ("Measurements of the horizontal coefficient of restitution...", Am. J. Phys. 70(5):482)
     * shows tangential restitution is real and non-zero for a bouncing ball, so a small value
     * would arguably be better -- but every figure he measured is for a TENNIS ball, and no
     * table-tennis-specific number exists. Zero is the standard rigid-body model and the one
     * the published oblique-bounce work for table tennis uses, so it stays until there is a
     * number to replace it with. The mechanism is in place either way; only rubber uses it.
     */
    public static final Material TABLE_MAT =
            new Material(0.98, 0.02, 0.75, 0.94, 0.25, 0.0, 0.0, 1.0, 1.00, 1.00);

    /**
     * The floor: a hard indoor sports floor. Slightly deader and grippier than the table.
     * Only here so a missed ball behaves instead of falling forever.
     */
    public static final Material FLOOR_MAT = Material.rigid(0.80, 0.40, 1.00, 1.00);

    /**
     * The net. Loose fabric on a cord: it absorbs almost everything. Low restitution, high
     * friction, and heavy extra damping of both velocity and spin because the netting
     * deforms and drags rather than rebounding. TUNED — there is no standard COR for
     * netting; calibrated so a ball into the net drops on the near side instead of
     * bouncing back, which is what actually happens.
     */
    public static final Material NET_MAT = Material.rigid(0.12, 0.50, 0.55, 0.35);

    /**
     * Contact parameters for one surface.
     *
     * Restitution is a function of approach speed, not a constant. A table tennis ball is a
     * thin shell and above roughly 5 m/s of normal impact the cap BUCKLES -- it dimples inward
     * instead of compressing uniformly -- and the coefficient of restitution falls away from
     * ~0.9 toward 0.8 and below (IntechOpen ch. 83844). A single number cannot describe a ball
     * that bounces at 0.93 off a gentle drop and 0.82 off a smash.
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
