package play;

import physics.Paddle;
import physics.Vec3;

/**
 * The player's paddle: follows the mouse and nothing else. {@code advance} takes no
 * {@code BallState} -- the ball is never an input, enforced by the signature rather than by
 * discipline. Swing speed sets pace, swing direction sets spin, both read off the blade's own
 * measured velocity by the contact solver.
 *
 * Advanced once per PHYSICS step, never per frame, so the same mouse motion produces the same
 * shot regardless of monitor refresh rate; that also lets it run headlessly under
 * {@code play.RallyTest}. See docs/DESIGN.md for why the old ball-driven reach and the
 * charge-and-release swing were both removed.
 */
public final class Stroke {

    /**
     * How fast the blade may chase the cursor, m/s.
     *
     * A sampling guard, not a feel knob: the mouse is sampled once a frame and the blade
     * advanced once a step, so an unclamped blade could cover a whole frame of mouse travel in
     * one step and read as an impossibly fast swing. TUNED: 13 m/s -- fast enough to cross the
     * table, under a real racket's swing (17.8 m/s) so a thrown mouse cannot out-hit a hand.
     * Deliberately left alone when the reachability envelope was fixed; see docs/DESIGN.md.
     */
    public static final double TRACK_SPEED = 13.0;

    /**
     * How much the face closes as the stroke drives through the ball, turning swing direction
     * into spin. TUNED: 0.55 puts a full-speed drive at the face tilt SelfTest measures a real
     * loop coming out at (~120 rev/s at 21 m/s).
     */
    private static final double FACE_CLOSE = 0.55;

    /** Below this blade speed there is no meaningful direction in the motion; the face sits
     *  square to the incoming ball rather than chasing noise in a near-still cursor. */
    private static final double STROKE_EPS = 0.20;

    /**
     * Time constant for easing the blade's face toward where it wants to point, seconds.
     *
     * Paddle derives angular velocity from how far the normal turned in one step, so a snapped
     * face reads as an enormous spin. TUNED: 0.04 s reaches a new angle in about an eighth of a
     * second -- responsive, but never a snap.
     */
    private static final double FACE_TAU = 0.04;

    /**
     * Where the cursor points, in metres: a full 3D point on the reach surface, depth included.
     * Solved by {@link render.MouseAim} out of the aim ray.
     */
    private Vec3 target;
    /** Square to the table, facing the opponent: where the face rests when the blade is still. */
    public static final Vec3 SQUARE = new Vec3(0, 0, -1);

    private Vec3 strokeDir = SQUARE;   // square to the incoming ball until it moves

    /**
     * Seconds since {@link #aimAt} last received a point that actually differed from the one
     * before it. Distinguishes "the cursor genuinely stopped" from "no mouse event has arrived
     * yet this step" -- see docs/DESIGN.md, "The racket-wobble fix".
     */
    private double idleTime = 0;

    /** TUNED: comfortably longer than a slow mouse's event interval, short enough that a hand
     *  that has genuinely stopped still relaxes to square almost at once. */
    private static final double AIM_STILL_DELAY = 0.05;

    /** Below this, two aim points count as the same point rather than motion. */
    private static final double AIM_MOVE_EPS = 1e-3;

    public Stroke(Vec3 restingAt) {
        this.target = restingAt;
    }

    /** Where the cursor currently points on the hitting plane. */
    public void aimAt(Vec3 point) {
        if (point.minus(target).length() > AIM_MOVE_EPS) idleTime = 0;
        target = point;
    }

    /**
     * Advance one physics step: carry the blade toward the cursor, held to one human tracking
     * speed, and ease its face the way the blade is travelling.
     *
     * @param dt the PHYSICS step, never a frame time -- Paddle derives its velocity from this,
     *           and that velocity is what the ball is struck with
     */
    public void advance(Paddle blade, double dt) {
        Vec3 from = blade.pos();
        idleTime += dt;

        // Straight at the cursor's point, depth and all, at one human tracking speed.
        Vec3 pos = towards(from, target, TRACK_SPEED * dt);

        // The direction the blade is actually travelling IS the stroke: move up through the
        // ball and the face closes for topspin, move down and it opens for backspin.
        Vec3 moved = pos.minus(from);
        if (moved.length() > STROKE_EPS * dt) {
            strokeDir = moved.normalized();
        } else if (idleTime > AIM_STILL_DELAY) {
            // A blade that has genuinely stopped relaxes back to square -- see docs/DESIGN.md,
            // "the paddle's face settling to square", for the 57-degree bug this replaced.
            strokeDir = Vec3.lerp(strokeDir, SQUARE,
                                  1 - Math.exp(-dt / FACE_TAU)).normalized();
        }
        // else: the blade reached this step's target early, but the hand moved recently enough
        // that this is still mid-stroke -- hold the lean rather than let it sag.

        blade.moveTo(pos, faceToward(blade.normal(), dt), dt);
    }

    /** Move from {@code from} toward {@code to}, by at most {@code maxStep}. */
    private static Vec3 towards(Vec3 from, Vec3 to, double maxStep) {
        Vec3 step = to.minus(from);
        double len = step.length();
        return len <= maxStep ? to : from.plusScaled(step.scale(1.0 / len), maxStep);
    }

    /**
     * The face for this step: square down the table, leaned the way the blade is travelling,
     * eased toward that rather than snapped (see {@link #FACE_TAU}).
     */
    private Vec3 faceToward(Vec3 currentNormal, double dt) {
        // Sideways lean follows the swipe. Vertical lean comes from DEPTH -- driving forward
        // (strokeDir.z < 0, since +Z is toward the player) closes the face for topspin, pulling
        // back opens it for backspin -- because depth is the only axis a player confined to one
        // horizontal plane still has for it.
        Vec3 desired = new Vec3(strokeDir.x() * 0.5,
                                strokeDir.z() * FACE_CLOSE,
                                -1).normalized();

        double k = 1 - Math.exp(-dt / FACE_TAU);
        return Vec3.lerp(currentNormal, desired, k).normalized();
    }
}
