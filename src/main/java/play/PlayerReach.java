package play;

import physics.BallState;
import physics.Vec3;
import physics.World;

import static physics.Constants.*;

/**
 * Where the player's racket may be and how long it takes to get there. Cursor X maps to racket X,
 * cursor Y to racket Z (monotonically), and racket Y is fixed at {@link #HIT_Y}: one horizontal
 * plane for the bat, full 3D for the ball.
 */
public final class PlayerReach {

    private PlayerReach() {}

    /** TUNED: peaks the worst feed's reachable window (302 ms). Never derived from the cursor. */
    public static final double HIT_Y = 0.16;

    /** TUNED: how far up the table the blade may reach, about a metre short of the net. */
    public static final double Z_NEAR = 0.30;

    /** TUNED: past this the ball has dropped out of the blade's capture band anyway. */
    public static final double Z_FAR = 2.40;

    /** A metre outside the table edge, so a ball run into the corner can be chased. */
    public static final double MAX_X = TABLE_WIDTH / 2 + 1.0;

    /** TUNED: 1.90 m sits where returns arrive, minimising the worst-case dash. */
    public static final Vec3 NEUTRAL = new Vec3(0, HIT_Y, 1.90);

    /** Ball heights a blade at HIT_Y can touch: its rim, plus the ball's own radius. */
    public static final double VERTICAL_CAPTURE = BLADE_R + BALL_R;

    /** TUNED: a real brushing forehand's vertical travel; too small to become a reach axis. */
    public static final double BRUSH_BAND = 0.18;

    /** The legal hitting-plane point for a raw aim. The aim's Y is discarded, structurally. */
    public static Vec3 clamp(Vec3 rawAim) {
        if (rawAim == null || !rawAim.isFinite()) return NEUTRAL;
        return new Vec3(clampX(rawAim.x()), HIT_Y, clampZ(rawAim.z()));
    }

    /**
     * The blade while the brush is held: height from the cursor (heightFrac 0 is the top of the
     * viewport), depth frozen at holdZ. Modal, so the cursor's Y still means one thing at a time.
     */
    public static Vec3 clampBrushed(Vec3 rawAim, double heightFrac, double holdZ) {
        if (rawAim == null || !rawAim.isFinite()) return NEUTRAL;
        double up = 1 - 2 * clamp(heightFrac, 0, 1);
        double y = Math.max(BLADE_R, HIT_Y + up * BRUSH_BAND);   // never through the table top
        return new Vec3(clampX(rawAim.x()), y, clampZ(holdZ));
    }

    /** For validation only; nothing on the control path may read the ball. */
    public static boolean canTouch(Vec3 ballPos) {
        return ballPos.z() >= Z_NEAR && ballPos.z() <= Z_FAR
            && Math.abs(ballPos.x()) <= MAX_X
            && Math.abs(ballPos.y() - HIT_Y) <= VERTICAL_CAPTURE;
    }

    public static double travelDistance(Vec3 from, Vec3 to) {
        return to.minus(from).length();
    }

    public static double travelTime(Vec3 from, Vec3 to) {
        return travelDistance(from, to) / Stroke.TRACK_SPEED;
    }

    /** Seconds until the ball reaches depthZ on a paddle-free flight, or NaN within 3 s. */
    public static double timeToDepth(BallState ball, double depthZ) {
        final int stride = 4;
        var path = World.predict(ball, 3.0, stride);
        double prev = ball.pos().z();
        for (int i = 0; i < path.size(); i++) {
            double z = path.get(i).z();
            boolean crossed = (prev < depthZ && z >= depthZ) || (prev > depthZ && z <= depthZ);
            if (crossed) {
                double f = (depthZ - prev) / (z - prev);
                return (i + f) * stride * DT;
            }
            prev = z;
        }
        return Double.NaN;
    }

    private static double clampX(double x) { return clamp(x, -MAX_X, MAX_X); }

    private static double clampZ(double z) { return clamp(z, Z_NEAR, Z_FAR); }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
