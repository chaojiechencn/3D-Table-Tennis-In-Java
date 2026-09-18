package pong.core.math;

/**
 * Scalar helpers with nothing table-tennis about them.
 *
 * {@code clamp} had five identical private copies across the tree -- in the contact solver, the
 * control envelope, the opponent, the shot model and the camera. Five copies of three lines is
 * not a maintenance cost so much as a question every reader has to answer again ("is THIS one
 * the same as the others?"), and the answer was always yes.
 *
 * Kept in {@code core} because that is the rule for this directory: code that would transplant
 * into another game unchanged. Nothing here knows what a ball is.
 */
public final class Scalars {

    private Scalars() {}

    /** {@code v} held inside [lo, hi]. */
    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Where {@code x} sits between {@code a} and {@code b}, held to [0, 1] so a table built on
     *  this can never extrapolate past its measured ends. */
    public static double frac(double x, double a, double b) {
        if (b - a < 1e-12) return 0;
        return clamp((x - a) / (b - a), 0, 1);
    }
}
