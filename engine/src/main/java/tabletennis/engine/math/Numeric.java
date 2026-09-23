package tabletennis.engine.math;

/** The scalar helpers every layer shares, so each exists exactly once. */
public final class Numeric {

    private Numeric() {}

    /**
     * Value limited to [Low, High]. Not Math.clamp: that orders -0.0 below 0.0 and throws on
     * inverted bounds, and either would change results bit for bit.
     */
    public static double Clamp(double Value, double Low, double High) {
        return Value < Low ? Low : (Value > High ? High : Value);
    }

    /** Where Value sits between From and To, clamped to [0, 1]; 0 for a degenerate interval. */
    public static double Fraction(double Value, double From, double To) {
        if (To - From < 1e-12) return 0;
        return Clamp((Value - From) / (To - From), 0, 1);
    }

    /** Smootherstep, not linear: RK4 keeps its fourth order only on a C2 right-hand side. */
    public static double SmoothLerp(double From, double To, double Fraction) {
        double Smooth = Fraction * Fraction * Fraction * (Fraction * (Fraction * 6 - 15) + 10);
        return From + (To - From) * Smooth;
    }
}
