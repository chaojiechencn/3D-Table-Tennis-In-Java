package play;

import physics.BallState;
import physics.Paddle;
import physics.Vec3;

/**
 * The player's paddle: it follows the mouse, and that is the whole of it.
 *
 * There is no wind-up and no button. The cursor sets where the blade goes across and up the
 * table; the DEPTH follows the ball, so the blade steps in over the table to meet a short one
 * instead of being pinned to a plane behind the baseline. The shot is entirely in how you move
 * through the ball -- swing speed sets the pace, the direction you cut across it sets the spin.
 * Both fall out of the contact solver from the blade's own measured velocity; nothing here
 * tells the ball how fast to leave or how much spin to carry.
 *
 * (There used to be a charge-and-release swing here -- hold to wind up, drag back to aim the
 * stroke, let go to hit. It was pulled: the timed gesture was more fiddly than fun, and the
 * contact solver already turns plain mouse motion into pace and spin without it.)
 *
 * The blade's face is eased toward the ball each step rather than held at a fixed angle, so a
 * high ball is met with the face turned up at it and the tilt never snaps (which would also
 * spike the spin -- see FACE_TAU).
 *
 * Plain Java on purpose. It is advanced once per PHYSICS step, never per frame, so the same
 * mouse motion produces the same shot on a 30 Hz laptop and a 240 Hz monitor -- and it can be
 * exercised headlessly by {@link RallyTest}. Nothing here knows what a mouse is; it is handed a
 * point on the hitting plane and the ball, and it produces a blade pose.
 */
public final class Stroke {

    /**
     * How fast the blade may chase the cursor, m/s.
     *
     * This is a sampling guard, not a feel knob. The mouse is sampled once a FRAME and the
     * blade advanced once a STEP -- eight steps per frame at 60 Hz. Handed straight to the
     * cursor point, the blade covers a whole frame of mouse travel inside a single 1/480 s
     * step, and Paddle measures velocity by differencing its own pose: a 30 cm flick reads as
     * 144 m/s and sends the ball out at nearly 300. The mouse took a frame to travel that far,
     * so the blade has to take one too.
     *
     * TUNED: 13 m/s. It has to be quick enough to actually get across the table and into the
     * ball -- 8 was too slow to chase a wide return -- while staying under a real racket's
     * swing (an advanced player's is 17.8 m/s), so a thrown mouse still cannot out-hit a hand.
     * RallyTest asserts both: the clamp holds, and it sits below a real swing.
     */
    public static final double TRACK_SPEED = 13.0;

    /**
     * How much the face closes as the stroke steepens. A brushing stroke has the blade leaning
     * over the ball; a flat one has it square. This is what turns swing DIRECTION into spin.
     * TUNED: 0.55 puts a 30-degree upward brush at a face tilt of about 0.28, which is where
     * SelfTest measures a loop coming out at the published 21 m/s and ~120 rev/s.
     */
    private static final double FACE_CLOSE = 0.55;

    /** Below this blade speed there is no meaningful direction in the motion; the face sits
     *  square to the incoming ball rather than chasing noise in a near-still cursor. */
    private static final double STROKE_EPS = 0.20;

    /**
     * Time constant for easing the blade's face toward where it wants to point, in seconds.
     *
     * A real wrist does not flick between angles, and there is a physics reason as well as a
     * cosmetic one: Paddle derives the blade's angular velocity from how far its normal turned
     * in a single step, so a face that snapped from one orientation to another would read as an
     * enormous spin and dump it on any ball in contact. Easing keeps that honest.
     *
     * TUNED: 0.04 s reaches a new angle in about an eighth of a second -- fast enough to feel
     * responsive, slow enough that one frame's worth of turn is a fraction of a degree.
     */
    private static final double FACE_TAU = 0.04;

    /**
     * How far the blade may step off its rest plane to meet the ball, in metres: forward
     * (toward the net) and back (chasing a deep one). Generous on purpose -- the rule is
     * "you should always be able to reach it". While a ball is on our side and coming at us
     * the blade tracks toward its depth within this band, then eases back to rest.
     */
    private static final double REACH_FWD  = 1.20;   // out over the table, nearly to the net
    private static final double REACH_BACK = 0.80;   // behind the baseline for a long ball

    private Vec3 target;
    private final double restZ;
    private Vec3 strokeDir = new Vec3(0, 0, -1);   // square to the incoming ball until it moves

    public Stroke(Vec3 restingAt) {
        this.target = restingAt;
        this.restZ = restingAt.z();
    }

    /** Where the cursor currently points on the hitting plane. */
    public void aimAt(Vec3 point) { target = point; }

    /**
     * Advance one physics step: carry the blade toward the cursor (across and up) and toward
     * the ball's depth (in and out), held to one human tracking speed, and ease its face
     * toward the ball.
     *
     * @param ball the ball right now -- its position aims the face and its depth pulls the
     *             blade in to meet it
     * @param dt   the PHYSICS step, never a frame time -- Paddle derives its velocity from this,
     *             and that velocity is what the ball is struck with
     */
    public void advance(Paddle blade, BallState ball, double dt) {
        Vec3 from = blade.pos();

        // Depth: track toward the ball's own depth while it is on our side (a little past the
        // net counts) and heading at us, within the reach band; otherwise sit back on the rest
        // plane the cursor is measured on.
        double wantZ = (ball.pos().z() > -0.35 && ball.vel().z() > 0)
                     ? clamp(ball.pos().z(), restZ - REACH_FWD, restZ + REACH_BACK)
                     : restZ;

        Vec3 goal = new Vec3(target.x(), target.y(), wantZ);
        Vec3 pos = towards(from, goal, TRACK_SPEED * dt);

        // The direction the blade is actually travelling IS the stroke, so the face leans the
        // way you are cutting across the ball: move up through it and the face closes over the
        // top, which brushes topspin; move down and it opens under, which cuts backspin.
        Vec3 moved = pos.minus(from);
        if (moved.length() > STROKE_EPS * dt) strokeDir = moved.normalized();

        blade.moveTo(pos, faceToward(blade.normal(), ball.pos().minus(pos), dt), dt);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Move from {@code from} toward {@code to}, by at most {@code maxStep}. */
    private static Vec3 towards(Vec3 from, Vec3 to, double maxStep) {
        Vec3 step = to.minus(from);
        double len = step.length();
        return len <= maxStep ? to : from.plusScaled(step.scale(1.0 / len), maxStep);
    }

    /**
     * The face for this step: aim at the ball, lean it the way the blade is travelling, and
     * ease the current normal toward that target rather than snapping to it (see FACE_TAU).
     */
    private Vec3 faceToward(Vec3 currentNormal, Vec3 toBall, double dt) {
        // Point at the ball while it is genuinely down-table of the blade; otherwise just face
        // down the table, so the blade sits sensibly between rallies and when a ball is behind
        // it rather than swinging around to chase a dead one.
        Vec3 aim = (toBall.z() < -0.05 && toBall.lengthSquared() > 1e-6)
                 ? toBall.normalized()
                 : new Vec3(0, 0, -1);

        // Lean by the stroke: brushing up (strokeDir.y > 0) closes the face down over the ball
        // for topspin, brushing down opens it under for backspin. Added onto the aim rather
        // than onto a fixed -Z, so a high ball is met with the face actually turned up at it.
        Vec3 desired = new Vec3(aim.x() + strokeDir.x() * 0.5,
                                aim.y() - strokeDir.y() * FACE_CLOSE,
                                aim.z()).normalized();

        double k = 1 - Math.exp(-dt / FACE_TAU);
        return Vec3.lerp(currentNormal, desired, k).normalized();
    }
}
