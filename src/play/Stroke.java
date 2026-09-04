package play;

import physics.Paddle;
import physics.Vec3;

/**
 * The player's paddle: it follows the mouse, and that is the whole of it.
 *
 * There is no wind-up and no button, and -- this is the rule the class exists to keep -- the
 * BALL IS NOT AN INPUT. The cursor sets where the blade goes, in all three axes, and that is
 * the only thing that moves it. Nothing in here reads the ball's position, velocity or
 * predicted landing, which is why {@code advance} is not handed a ball at all: with the mouse
 * still, the blade is still. The shot is entirely in how you move through the ball -- swing
 * speed sets the pace, the direction you cut across it sets the spin. Both fall out of the
 * contact solver from the blade's own measured velocity; nothing here tells the ball how fast
 * to leave or how much spin to carry.
 *
 * (The blade used to track the BALL's depth within a reach band, and to aim its face at the
 * ball, so a short ball was stepped in for and a high one met with an open face. Both are gone:
 * they moved the paddle under no input at all, which is the game playing itself. The reach came
 * back, but as geometry the player drives -- {@link render.MouseAim} puts the cursor's own aim
 * out over the table, and this class simply goes where it is told.)
 *
 * (There used to be a charge-and-release swing here -- hold to wind up, drag back to aim the
 * stroke, let go to hit. It was pulled: the timed gesture was more fiddly than fun, and the
 * contact solver already turns plain mouse motion into pace and spin without it.)
 *
 * The blade's face points down the table, leaned by the direction the blade is TRAVELLING, and
 * eased rather than snapped (a snapped face reads to Paddle as an enormous angular velocity and
 * dumps that spin on any ball in contact -- see FACE_TAU).
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
     * Where the cursor points, in metres: a full 3D point on the reach surface, depth included.
     * Its depth is the cursor's, solved by {@link render.MouseAim} out of the aim ray -- which
     * is the whole reason this class can have reach again without reading the ball.
     */
    private Vec3 target;
    private Vec3 strokeDir = new Vec3(0, 0, -1);   // square to the incoming ball until it moves

    public Stroke(Vec3 restingAt) {
        this.target = restingAt;
    }

    /** Where the cursor currently points on the hitting plane. */
    public void aimAt(Vec3 point) { target = point; }

    /**
     * Advance one physics step: carry the blade toward the cursor, held to one human tracking
     * speed, and ease its face the way the blade is travelling.
     *
     * The cursor is the ONLY thing that moves it. If the cursor has not moved, the goal is
     * where the blade already is, {@code towards} returns it unchanged, and the blade sits
     * still -- and there is no ball parameter for anything else to sneak in through.
     *
     * @param dt the PHYSICS step, never a frame time -- Paddle derives its velocity from this,
     *           and that velocity is what the ball is struck with
     */
    public void advance(Paddle blade, double dt) {
        Vec3 from = blade.pos();

        // Straight at the cursor's point, depth and all, at one human tracking speed. The
        // speed clamp covers the reach too: a cursor flung from the baseline to over the table
        // is a lunge the blade has to travel, not a place it may appear at.
        Vec3 pos = towards(from, target, TRACK_SPEED * dt);

        // The direction the blade is actually travelling IS the stroke, so the face leans the
        // way you are cutting across the ball: move up through it and the face closes over the
        // top, which brushes topspin; move down and it opens under, which cuts backspin.
        Vec3 moved = pos.minus(from);
        if (moved.length() > STROKE_EPS * dt) strokeDir = moved.normalized();

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
     * and eased toward that rather than snapped to it (see FACE_TAU).
     *
     * It used to aim at the BALL, falling back to down-table only when the ball was behind the
     * blade. That was auto-aim -- the face turned to track a ball the player had not reacted to
     * -- so only the fall-back is left, plus the lean, which the cursor drives. With the cursor
     * still, strokeDir is fixed, so the desired normal is fixed and the ease converges and
     * stops rather than following anything.
     */
    private Vec3 faceToward(Vec3 currentNormal, double dt) {
        // Lean by the stroke: brushing up (strokeDir.y > 0) closes the face down over the ball
        // for topspin, brushing down opens it under for backspin.
        Vec3 desired = new Vec3(strokeDir.x() * 0.5,
                                -strokeDir.y() * FACE_CLOSE,
                                -1).normalized();

        double k = 1 - Math.exp(-dt / FACE_TAU);
        return Vec3.lerp(currentNormal, desired, k).normalized();
    }
}
