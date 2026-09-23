package tabletennis.engine.aero;

/**
 * Measured aerodynamics of a 40 mm ball, each value with its source:
 *   [AERO] Measured drag and lift on table tennis balls at Re ~ 1e4-1e5.
 *   [FIT]  arXiv:2606.28805, coefficients fitted to 277 recorded competitive matches.
 */
public final class AeroData {

    private AeroData() {}

    /** Constant drag coefficient [AERO]; only the closed-form free-fall checks fly with it. */
    public static final double ConstantDragCoefficient = 0.40;

    /**
     * Spin decay per metre of flight, dw/dt = -k*w*|v| (James &amp; Haake, Engineering of Sport 7,
     * 2008). TUNED: 1/240 reproduces 5%/s at a typical 12 m/s rally speed.
     */
    public static final double SpinDecayPerMetre = 1.0 / 240.0;

    /** Drag coefficient [FIT]: rows are airspeed (m/s), columns spin ratio S. */
    static final double[] DragSpeeds = { 2.5, 7.5, 12.5, 17.5 };
    static final double[] DragSpinRatios = { 0.0, 0.3, 0.7, 0.95, 1.5, 2.0 };
    static final double[][] DragTable = {
            { 0.55, 0.55, 0.55, 0.55, 0.55, 0.55 },   // 2.5 m/s
            { 0.49, 0.49, 0.55, 0.48, 0.53, 0.53 },   // 7.5 m/s
            { 0.47, 0.47, 0.53, 0.41, 0.48, 0.48 },   // 12.5 m/s
            { 0.47, 0.47, 0.51, 0.37, 0.45, 0.45 },   // 17.5 m/s
    };

    /**
     * Magnus coefficient [FIT], piecewise in spin rate at each speed. Volume-based C_M;
     * Aerodynamics converts to area-based lift. Captures the lift crisis near S = 0.5-0.8
     * (Miyazaki et al., Eur. J. Phys. 38(2):024001, 2017).
     */
    static final double[] LiftSpeeds = { 2.0, 3.5, 7.5, 10.5, 13.5, 17.0 };

    /** Below the breakpoint: C_M = m*omega + c. Columns are {m, c, omega_breakpoint}. */
    static final double[][] LiftLinear = {
            {  0.0,       0.080, 150 },
            { -1.10e-3,   0.310, 200 },
            { -8.00e-4,   0.370, 350 },
            { -6.58e-4,   0.375, 440 },
            { -5.60e-4,   0.383, 550 },
            { -4.48e-4,   0.371, 650 },
    };

    /** Above the breakpoint: C_M = a*omega^2 + b*omega + c. */
    static final double[][] LiftQuadratic = {
            { -1.852e-7, -1.296e-4,  0.0983 },
            { -1.667e-7, -3.333e-5,  0.1000 },
            { -2.000e-7,  1.700e-4,  0.0587 },
            { -2.604e-7,  3.646e-4, -0.0225 },
            { -3.571e-7,  5.357e-4, -0.0893 },
            { -1.000e-7,  2.300e-4, -0.0375 },
    };

    /** Blend width across the fit's breakpoint, whose branches disagree by up to 0.022 in C_M. */
    static final double LiftBranchBlend = 0.05;
}
