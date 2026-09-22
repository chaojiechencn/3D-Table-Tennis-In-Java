package play;

import physics.BallState;
import physics.Paddle;
import physics.Vec3;

import static physics.Constants.*;

/**
 * An unbeatable opponent that moves to where the ball IS and swings when it arrives. It cannot be
 * made beatable by slowing it down; that needs prediction ({@link physics.World#predict}).
 */
public final class Follower implements Opponent {

    /** A wall, not a player: far beyond a human's 17.8 m/s swing. */
    private static final double MAX_SPEED = 25.0;

    /** Just beyond the opponent's end of the table. */
    public static final double PLANE_Z = -(TABLE_LENGTH / 2 + 0.12);

    /** TUNED: without a ceiling the blade rides a rising ball up, hitting it every step. */
    private static final double MAX_REACH_Y = 0.55;

    public static final Vec3 READY = new Vec3(0, 0.20, PLANE_Z);
    public static final Vec3 SQUARE = new Vec3(0, 0, 1);

    /** Steps in over the table only for a LOW ball this close; always stepping in costs 7 of 10 feeds. */
    private static final double REACH_FWD = 0.55;
    private static final double STEP_IN_HEIGHT = 0.22;

    private static final double SWING_RANGE = 0.30;
    private static final double SWING_TIME = 0.10;

    // TUNED by sweep: no fixed stroke returns every preset legally AND lands most of them, so the
    // net wins. A closed face plus lift brushes topspin that brings the return down.
    private static final double FACE_CLOSED = 0.20;
    private static final double SWING_SPEED = 8.5;
    private static final double SWING_LIFT = 5.0;

    private static final Vec3 FACE = new Vec3(0, -FACE_CLOSED, 1).normalized();

    private double swingElapsed = -1;   // negative when not swinging

    /** Fixed when the stroke commits, or the blade follows the ball it just hit and hits it again. */
    private Vec3 swingFrom = READY;

    @Override
    public void advance(BallState ball, Paddle blade, double dt) {
        Vec3 b = ball.pos();
        boolean incoming = b.z() < 0 && ball.vel().z() < 0;

        if (incoming && b.z() - PLANE_Z < SWING_RANGE && swingElapsed < 0) {
            swingFrom = reachable(b);
            swingElapsed = 0;
        }

        Vec3 want;
        if (swingElapsed >= 0) want = nextSwingPoint(dt);
        else if (incoming) want = reachable(b);
        else want = READY;   // following a departing ball would chase it into the roof

        blade.moveTo(limitStep(blade.pos(), want, MAX_SPEED * dt), FACE, dt);
    }

    /** A half-sine stroke profile: an instant start would read as an impossible velocity. */
    private Vec3 nextSwingPoint(double dt) {
        swingElapsed += dt;
        double t = Math.min(1, swingElapsed / SWING_TIME);
        double s = (1 - Math.cos(Math.PI * t)) / 2;
        double travel = SWING_TIME * s * 2 / Math.PI;
        if (t >= 1) swingElapsed = -1;
        return new Vec3(swingFrom.x(),
                        swingFrom.y() + SWING_LIFT * travel,
                        swingFrom.z() + SWING_SPEED * travel);
    }

    private static Vec3 limitStep(Vec3 from, Vec3 to, double maxStep) {
        Vec3 step = to.minus(from);
        return step.length() > maxStep ? from.plusScaled(step.normalized(), maxStep) : to;
    }

    /** The ball's position, clamped to where the blade can be: above the table, below the roof. */
    private static Vec3 reachable(Vec3 b) {
        double toPlane = b.z() - PLANE_Z;
        boolean stepIn = b.y() < STEP_IN_HEIGHT && toPlane > 0 && toPlane < REACH_FWD;
        return new Vec3(clamp(b.x(), -TABLE_WIDTH, TABLE_WIDTH),
                        clamp(b.y(), 0.04, MAX_REACH_Y),
                        stepIn ? b.z() : PLANE_Z);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
