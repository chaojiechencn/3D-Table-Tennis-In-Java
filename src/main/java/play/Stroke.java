package play;

import physics.Paddle;
import physics.Vec3;

/**
 * The player's paddle: follows the cursor and nothing else. {@code advance} takes no BallState, so
 * the ball can never steer the blade. Advanced once per physics step, never per frame.
 */
public final class Stroke {

    /**
     * A sampling guard, not a feel knob: without it one step could cover a whole frame of mouse
     * travel. TUNED: crosses the table, yet stays under a real 17.8 m/s swing.
     */
    public static final double TRACK_SPEED = 13.0;

    /** TUNED: a full drive closes the face to the tilt of a real ~120 rev/s loop. */
    private static final double FACE_CLOSE = 0.55;

    /** Below this blade speed the motion has no meaningful direction. */
    private static final double STROKE_EPS = 0.20;

    /** TUNED: reaches a new face angle in ~1/8 s; a snapped face reads as enormous spin. */
    private static final double FACE_TAU = 0.04;

    /** TUNED: longer than a slow mouse's event gap, so "no event yet" is not "stopped". */
    private static final double AIM_STILL_DELAY = 0.05;

    private static final double AIM_MOVE_EPS = 1e-3;

    /** Square to the table, facing the opponent. */
    public static final Vec3 SQUARE = new Vec3(0, 0, -1);

    private Vec3 target;
    private Vec3 strokeDir = SQUARE;
    private double idleTime = 0;

    public Stroke(Vec3 restingAt) {
        this.target = restingAt;
    }

    public void aimAt(Vec3 point) {
        if (point.minus(target).length() > AIM_MOVE_EPS) idleTime = 0;
        target = point;
    }

    /** Carry the blade toward the cursor at TRACK_SPEED and lean its face the way it travels. */
    public void advance(Paddle blade, double dt) {
        Vec3 from = blade.pos();
        idleTime += dt;

        Vec3 pos = towards(from, target, TRACK_SPEED * dt);
        Vec3 moved = pos.minus(from);
        if (moved.length() > STROKE_EPS * dt) {
            strokeDir = moved.normalized();
        } else if (idleTime > AIM_STILL_DELAY) {
            strokeDir = Vec3.lerp(strokeDir, SQUARE, 1 - Math.exp(-dt / FACE_TAU)).normalized();
        }
        // Otherwise the hand moved recently: still mid-stroke, so hold the lean.

        blade.moveTo(pos, faceToward(blade.normal(), dt), dt);
    }

    private static Vec3 towards(Vec3 from, Vec3 to, double maxStep) {
        Vec3 step = to.minus(from);
        double len = step.length();
        return len <= maxStep ? to : from.plusScaled(step.scale(1.0 / len), maxStep);
    }

    /** Sideways lean follows the swipe; driving forward (-Z) closes the face for topspin. */
    private Vec3 faceToward(Vec3 currentNormal, double dt) {
        Vec3 desired = new Vec3(strokeDir.x() * 0.5, strokeDir.z() * FACE_CLOSE, -1).normalized();
        double k = 1 - Math.exp(-dt / FACE_TAU);
        return Vec3.lerp(currentNormal, desired, k).normalized();
    }
}
