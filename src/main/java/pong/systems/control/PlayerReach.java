package pong.systems.control;

import static pong.config.Physical.*;
import pong.core.math.Vec3;
import pong.game_objects.ball.Ball;
import pong.game_world.World;
import pong.core.math.Scalars;

/**
 * Where the player's racket is allowed to be, and how long it takes to get there.
 *
 * The control envelope, kept apart from the cursor geometry that feeds it ({@link
 * pong.MouseAim}, pure ray work, knows no rules) and the blade that obeys it ({@link Stroke},
 * never sees a ball). Splitting it out is what lets it be graded headlessly by
 * {@code pong._tests.RallyTest}.
 *
 * <pre>
 *   cursor X  ->  racket X      (across the table)
 *   cursor Y  ->  racket Z      (up and down the table, MONOTONICALLY)
 *                 racket Y      fixed at HIT_Y -- a gameplay parameter, not an input axis
 * </pre>
 *
 * The racket moves on ONE horizontal plane; the ball still flies in full 3D. See docs/DESIGN.md,
 * "The control mapping", for the two-defects-one-symptom bug this mapping replaced (a non-monotone
 * depth curve, and a racket that could not retreat past its own rest plane).
 */
public final class PlayerReach {

    private PlayerReach() {}

    /**
     * The racket's hitting height, metres above the table surface. Deliberately not derived
     * from the cursor, so "point deeper" and "lift the bat" can never be the same gesture again.
     *
     * TUNED: 0.16 m, read off a sweep against the worst feed's reachable window (peaks at 302 ms
     * here; see docs/DESIGN.md for the full table).
     */
    public static final double HIT_Y = 0.16;

    /**
     * How far up the table the racket may reach, metres -- the smallest z the blade may occupy
     * (+Z is toward the player). TUNED: 0.30 m, about a metre short of the net.
     */
    public static final double Z_NEAR = 0.30;

    /**
     * How far BEHIND the table the racket may retreat, metres. TUNED: 2.40 m, where the
     * reachable-window sweep against this limit flattens out (see docs/DESIGN.md) -- past it the
     * ball has already dropped out of the blade's vertical capture band, so more depth buys
     * nothing.
     */
    public static final double Z_FAR = 2.40;

    /** How far sideways the racket may stray, metres either side of the centre line -- a metre
     *  outside the table edge, so a ball run into the corner can still be chased. */
    public static final double MAX_X = TABLE_WIDTH / 2 + 1.0;

    /** A neutral stance: centred, at hitting height, a little behind the end line. TUNED: 1.90 m
     *  sits in the middle of where returns actually arrive, minimising the worst-case dash. */
    public static final Vec3 NEUTRAL = new Vec3(0, HIT_Y, 1.90);

    /**
     * The vertical half-band of ball heights a blade at HIT_Y can touch, metres: the blade is a
     * disc of radius BLADE_R, so its rim spans that far above and below centre, and the ball
     * touches it from BALL_R further out again. This is what makes a fixed hitting height
     * playable at all.
     */
    public static final double VERTICAL_CAPTURE = BLADE_R + BALL_R;

    // ------------------------------------------------------------------ the mapping

    /**
     * Put a raw world point -- wherever the cursor's ray landed -- onto the legal hitting plane.
     *
     * The Y of the argument is DISCARDED: it is structurally impossible for cursor height to
     * reach the racket's height, because the only path between them ends here. X and Z are
     * clamped independently, so the mapping stays monotone in both.
     */
    public static Vec3 clamp(Vec3 rawAim) {
        if (rawAim == null || !rawAim.isFinite()) return NEUTRAL;
        return new Vec3(Scalars.clamp(rawAim.x(), -MAX_X, MAX_X),
                        HIT_Y,
                        Scalars.clamp(rawAim.z(), Z_NEAR, Z_FAR));
    }

    /**
     * How far above and below the hitting plane the brush may carry the blade, metres. TUNED:
     * 0.18 m, roughly a real brushing forehand's vertical travel through the ball -- small
     * enough that the brush cannot become a second axis of REACH, only of stroke.
     */
    public static final double BRUSH_BAND = 0.18;

    /**
     * The blade while the brush modifier is held: height comes from the cursor, depth is frozen
     * at the value it had when the button went down. Height and depth are MODAL here -- the
     * button chooses which one the cursor's Y axis carries, and whichever is not selected holds
     * still -- which is what makes this legitimate rather than the two-meanings-on-one-axis bug
     * documented in docs/DESIGN.md.
     *
     * @param rawAim     the cursor's aim on the hitting plane, for its X only
     * @param heightFrac 0 at the bottom of the viewport, 1 at the top
     * @param holdZ      the depth the blade had when the brush began
     */
    public static Vec3 clampBrushed(Vec3 rawAim, double heightFrac, double holdZ) {
        if (rawAim == null || !rawAim.isFinite()) return NEUTRAL;
        // Screen Y grows downward, so the top of the viewport is the high blade.
        double up = 1 - 2 * Scalars.clamp(heightFrac, 0, 1);

        // The blade is a disc of radius BLADE_R: a centre below that hangs the bottom of the bat
        // through the table top, so the downward half of the band is cut short there. The full
        // band is available upward.
        double y = Math.max(BLADE_R, HIT_Y + up * BRUSH_BAND);

        return new Vec3(Scalars.clamp(rawAim.x(), -MAX_X, MAX_X),
                        y,
                        Scalars.clamp(holdZ, Z_NEAR, Z_FAR));
    }

    /**
     * Whether the blade could touch a ball at this position without leaving the envelope.
     * Used to VALIDATE reachability, in tests and the on-screen debug readout -- nothing on the
     * control path may call it, since the blade is moved by the cursor and nothing else.
     */
    public static boolean canTouch(Vec3 ballPos) {
        return ballPos.z() >= Z_NEAR && ballPos.z() <= Z_FAR
            && Math.abs(ballPos.x()) <= MAX_X
            && Math.abs(ballPos.y() - HIT_Y) <= VERTICAL_CAPTURE;
    }

    // ------------------------------------------------------------------ reachability

    /** How far the blade must travel from where it is to where the cursor is asking. */
    public static double travelDistance(Vec3 from, Vec3 to) {
        return to.minus(from).length();
    }

    /** How long that takes at the blade's tracking speed -- see {@link Stroke#TRACK_SPEED}. */
    public static double travelTime(Vec3 from, Vec3 to) {
        return travelDistance(from, to) / Stroke.TRACK_SPEED;
    }

    /**
     * How long until the ball arrives at a given depth, seconds, or NaN if it never does within
     * the horizon. Flown rather than extrapolated, through the real racket-free integrator
     * ({@link World#predict}), so this cannot be perturbed by the racket it validates against.
     */
    public static double timeToDepth(Ball ball, double depthZ) {
        final int stride = 4;                       // 120 Hz is ample for a readout
        var path = World.predict(ball, 3.0, stride);
        double prev = ball.pos().z();
        for (int i = 0; i < path.size(); i++) {
            double z = path.get(i).z();
            if ((prev < depthZ && z >= depthZ) || (prev > depthZ && z <= depthZ)) {
                // Linear inside one sample: the ball moves a few mm over 1/120 s.
                double f = (depthZ - prev) / (z - prev);
                return (i + f) * stride * DT;
            }
            prev = z;
        }
        return Double.NaN;
    }

}
