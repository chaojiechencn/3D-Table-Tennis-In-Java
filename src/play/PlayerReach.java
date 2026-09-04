package play;

import physics.BallState;
import physics.Vec3;
import physics.World;

import static physics.Constants.*;

/**
 * Where the player's racket is allowed to be, and how long it takes to get there.
 *
 * This is the CONTROL ENVELOPE, kept apart from both the cursor geometry that feeds it
 * ({@link render.MouseAim}, which is pure ray work and knows no rules) and the blade that
 * obeys it ({@link Stroke}, which is handed a point and goes there). Splitting it out is what
 * lets the envelope be graded headlessly by {@link RallyTest} -- the two neighbours are a
 * JavaFX class and a class that must never see a ball, so neither could carry these numbers.
 *
 * <h2>The control model</h2>
 *
 * <pre>
 *   cursor X  ->  racket X      (across the table)
 *   cursor Y  ->  racket Z      (up and down the table, MONOTONICALLY)
 *                 racket Y      fixed at HIT_Y -- a gameplay parameter, not an input axis
 * </pre>
 *
 * The racket moves on ONE horizontal plane. The ball does not: it still flies in a full 3D
 * simulation, with spin curving it and the bounce coupling spin to speed. Only the player's
 * CONTROL is two-dimensional.
 *
 * <h2>What this replaced, and why</h2>
 *
 * The previous mapping ("the reach surface") solved the blade's depth from where the cursor's
 * ray crossed table height, then read x and y on the plane that depth chose. One cursor axis
 * therefore meant two different things at once -- move deeper toward the net AND raise the
 * racket -- because both came out of the same ray. Two measured consequences:
 *
 * <ol>
 *   <li><b>The depth mapping was not monotone.</b> Sliding the cursor up-screen walked the
 *       blade out over the table to a furthest point and then brought it BACK to the rest
 *       plane again, because past full stretch the ray's crossing ran away down-table and the
 *       code ramped the blade backwards along it. The same cursor motion meant "forward" and
 *       then "backward".</li>
 *   <li><b>The blade could not retreat past its rest plane at all.</b> Depth was clamped to
 *       [0.47, 1.57], so a ball that had travelled behind z = 1.57 was unreachable at every
 *       cursor position. Measured over the nine feeds that produce a player-side bounce, that
 *       left a worst-case reachable window of 98 ms after the bounce -- which is the "the ball
 *       becomes impossible to hit about 0.3 s after it bounces" this class was written to
 *       fix.</li>
 * </ol>
 *
 * Both numbers, and every constant below, come from flying the actual feeds and measuring;
 * the checks in {@link RallyTest} re-measure them on every run.
 */
public final class PlayerReach {

    private PlayerReach() {}

    /**
     * The racket's hitting height, in metres above the table surface. THE gameplay parameter
     * this class exists to isolate: it is deliberately not derived from the cursor, so that
     * "point deeper" and "lift the bat" can never again be the same gesture.
     *
     * 0.16 m is measured, not chosen by eye. Flying all nine feeds that reach the player and
     * sampling the ball's height as it travels back through the player's region gives a band
     * of roughly 0.10-0.22 m between z = 1.2 and z = 1.9. Sweeping this constant over
     * 0.12-0.26 m in 1 cm steps and scoring each by the WORST feed's reachable window peaks
     * here:
     *
     * <pre>
     *   y = 0.14 -> worst 258 ms      y = 0.18 -> worst 281 ms
     *   y = 0.15 -> worst 275 ms      y = 0.20 -> worst 252 ms
     *   y = 0.16 -> worst 302 ms  &lt;-- y = 0.22 -> worst 221 ms
     *   y = 0.17 -> worst 294 ms      y = 0.26 -> worst 135 ms
     * </pre>
     *
     * The blade is a disc of radius BLADE_R, so a fixed centre height still covers a band of
     * ball heights {@link #VERTICAL_CAPTURE} either side of it. Against an unrestricted racket
     * height, which would score 352 ms, fixing it costs 50 ms of the worst case -- the price
     * of the decoupling, and worth it against the 98 ms the old envelope actually delivered.
     */
    public static final double HIT_Y = 0.16;

    /**
     * How far up the table the racket may reach, in metres. Positive Z is toward the player,
     * so this is the SMALLEST z the blade may occupy -- the deepest reach in over the table.
     *
     * 0.30 m is about a metre short of the net, which is as far in as a player leaning over
     * the table gets. The old envelope stopped at 0.47; the extra 17 cm lets a short ball be
     * taken earlier, and costs nothing because the one-bounce rule keeps the blade out of the
     * collision set until the ball has bounced on this side anyway.
     */
    public static final double Z_NEAR = 0.30;

    /**
     * How far BEHIND the table the racket may retreat, in metres. The far end of the same
     * axis, and the single number that fixes the reported bug.
     *
     * The end line is at z = 1.37 and the old envelope stopped at 1.57, twenty centimetres
     * behind it -- so a ball that had crossed the end line and was still perfectly playable
     * could not be followed. Real players stand back for exactly these balls. Sweeping the
     * limit against the worst feed's reachable window, at HIT_Y:
     *
     * <pre>
     *   1.57 ->  98 ms   (the old envelope)      2.20 -> 260 ms
     *   1.80 -> 154 ms                           2.40 -> 281 ms
     *   2.00 -> 206 ms                           2.60 -> 281 ms  (no further gain)
     * </pre>
     *
     * 2.40 m is where the curve flattens: past it the ball has dropped out of the blade's
     * vertical capture band anyway, so more depth buys nothing. That is 1.03 m behind the end
     * line, which is a normal stance for a lob.
     */
    public static final double Z_FAR = 2.40;

    /**
     * How far sideways the racket may stray, in metres either side of the centre line.
     *
     * A metre outside the table edge, so a ball driven to the corner and running away can
     * still be chased. Unchanged from the old envelope -- lateral reach was never the problem.
     */
    public static final double MAX_X = TABLE_WIDTH / 2 + 1.0;

    /**
     * A neutral stance: centred, at hitting height, a little behind the end line. Where the
     * blade starts, and the point the reachability checks measure a dash from.
     *
     * 1.90 m sits in the middle of the depth the returns actually arrive at, so the worst
     * dash to any feed is 1.34 m rather than the 2.1 m a baseline stance would cost.
     */
    public static final Vec3 NEUTRAL = new Vec3(0, HIT_Y, 1.90);

    /**
     * The vertical half-band of ball heights a blade at HIT_Y can touch, in metres.
     *
     * The blade is a disc of radius BLADE_R standing roughly face-on to the ball, so its rim
     * spans BLADE_R above and below its centre, and the ball touches it from BALL_R further
     * out again. This is the quantity that makes a FIXED hitting height playable at all, and
     * the reason the cost of fixing it is 50 ms rather than everything.
     */
    public static final double VERTICAL_CAPTURE = BLADE_R + BALL_R;

    // ------------------------------------------------------------------ the mapping

    /**
     * Put a raw world point -- wherever the cursor's ray landed -- onto the legal hitting
     * plane.
     *
     * The Y of the argument is DISCARDED, and that discard is the whole point of the class:
     * it is structurally impossible for cursor height to reach the racket's height, because
     * the only path between them ends here. X and Z are clamped, each on its own axis, so the
     * mapping stays monotone in both -- sliding the cursor further up-table can only ever move
     * the blade further up-table or leave it where it is, never bring it back.
     */
    public static Vec3 clamp(Vec3 rawAim) {
        if (rawAim == null || !rawAim.isFinite()) return NEUTRAL;
        return new Vec3(clamp(rawAim.x(), -MAX_X, MAX_X),
                        HIT_Y,
                        clamp(rawAim.z(), Z_NEAR, Z_FAR));
    }

    /** Whether a point is inside the legal racket region (on the hitting plane, to 1 mm). */
    public static boolean contains(Vec3 p) {
        return Math.abs(p.y() - HIT_Y) < 1e-3
            && p.x() >= -MAX_X - 1e-9 && p.x() <= MAX_X + 1e-9
            && p.z() >= Z_NEAR - 1e-9 && p.z() <= Z_FAR + 1e-9;
    }

    /**
     * Whether the blade could touch a ball at this position without leaving the envelope --
     * inside the depth and width bounds, and within the vertical capture band of HIT_Y.
     *
     * Used to VALIDATE reachability, in the tests and in the on-screen debug readout. Nothing
     * on the control path may call it: the blade is moved by the cursor and by nothing else.
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
     * How long until the ball arrives at a given depth, in seconds, or NaN if it never does
     * within the horizon.
     *
     * Flown rather than extrapolated: {@link World#predict} is the real integrator with drag
     * and Magnus, and it is paddle-free by construction, so this cannot be perturbed by the
     * very racket it is being compared against. Ball awareness, used for validation only.
     */
    public static double timeToDepth(BallState ball, double depthZ) {
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

    /**
     * Can the blade be where the cursor is asking before the ball gets to that depth?
     *
     * The question the debug overlay exists to answer, and the one that separates "I lost
     * because the control mapping cannot express what I wanted" from "I lost because that ball
     * was genuinely unplayable".
     */
    public static boolean reachableInTime(Vec3 bladeAt, Vec3 target, BallState ball) {
        double arrive = timeToDepth(ball, target.z());
        if (Double.isNaN(arrive)) return true;         // not coming here; nothing to be late for
        return travelTime(bladeAt, target) <= arrive;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
