package play;

import physics.Paddle;
import physics.Vec3;

/**
 * The player's stroke: hold to charge, release to swing.
 *
 * Plain Java on purpose. It is advanced once per PHYSICS step, never per frame, so the swing
 * comes out the same on a 30 Hz laptop and a 240 Hz monitor -- and it can be exercised
 * headlessly. Nothing here knows what a mouse is; it is handed a point on the hitting plane
 * and a button state, and it produces a blade pose.
 *
 * The three phases:
 *
 *   IDLE      the blade sits at the cursor. Its velocity is whatever the mouse is doing, so a
 *             quick flick is still a real shot -- you are never forced to charge.
 *   CHARGING  the blade draws BACK along the stroke while charge builds. Where you drag the
 *             cursor while charging sets the direction it will swing.
 *   SWINGING  the blade sweeps forward through the cursor point and out the far side.
 *
 * The slingshot reading is the intuitive one and it happens to be the real one: drag back and
 * down, release, and the blade comes up and forward -- which is a topspin loop. Drag back and
 * up and it comes down and forward, which is a chop.
 *
 * NOTHING HERE SETS THE BALL'S SPEED OR SPIN. It only moves a blade. The pace and spin come
 * out of the contact solver, from the blade's real velocity and the tilt of its face -- which
 * is why charging harder genuinely hits harder rather than selecting a bigger number.
 */
public final class Stroke {

    public enum Phase { IDLE, CHARGING, SWINGING }

    /** Seconds of holding to reach a full-power swing. */
    private static final double CHARGE_FULL = 0.70;

    /**
     * How long the forward swing lasts, and how far the blade travels through it.
     *
     * These two set the peak blade speed, and they are chosen so that it lands on a measured
     * one. For a sine velocity profile the peak is pi*L/(2T) = pi*1.0/(2*0.09) = 17.5 m/s,
     * against a measured mean racket speed of 17.8 m/s for advanced players (12.4 m/s for
     * intermediates, which a half-charged swing lands on). So a fully charged stroke here is
     * about as fast as a good player actually swings, and no faster.
     */
    private static final double SWING_TIME = 0.09;
    private static final double SWING_LENGTH = 1.00;

    /** How far back the blade is drawn at full charge. Half the swing, so the cursor point
     *  sits at the middle of the stroke, where the blade is moving fastest. */
    private static final double DRAW_BACK = SWING_LENGTH / 2;

    /**
     * How much the face closes as the stroke steepens. A brushing stroke has the blade leaning
     * over the ball; a flat one has it square. This is what turns swing DIRECTION into spin,
     * and it is the only cosmetic-looking number in the class -- everything else is geometry.
     * TUNED: 0.55 puts a 30-degree upward brush at a face tilt of about 0.28, which is where
     * SelfTest measures the loop coming out at the published 21 m/s and ~120 rev/s.
     */
    private static final double FACE_CLOSE = 0.55;

    /**
     * How fast the blade may chase the cursor while it is NOT swinging, m/s.
     *
     * This exists because of a sampling mismatch, not for feel. The mouse is sampled once a
     * FRAME and the blade is advanced once a STEP -- eight steps per frame at 60 Hz. Handing
     * the blade straight to the cursor point makes it cover a whole frame of mouse travel
     * inside a single 1/480 s step, and Paddle measures velocity by differencing its own pose:
     * a 30 cm flick reads as 144 m/s and sends the ball out at nearly 300. The mouse took a
     * frame to travel that far, so the blade has to take one too.
     *
     * TUNED: 8 m/s stands in for the speed a player carries the bat around at between strokes.
     * What matters is where it sits relative to the two ends -- above ordinary aiming, so
     * tracking still feels one-to-one, and well below the 12.4 and 17.8 m/s measured SWING
     * speeds, so flicking the mouse can never out-hit a charged stroke. RallyTest asserts
     * both halves of that.
     */
    public static final double TRACK_SPEED = 8.0;

    /** Default stroke if the player charges without moving the mouse: a standard loop. */
    private static final Vec3 DEFAULT_STROKE =
            new Vec3(0, Math.sin(Math.toRadians(30)), -Math.cos(Math.toRadians(30)));

    private Phase phase = Phase.IDLE;
    private Vec3 target;
    private Vec3 anchor = Vec3.ZERO;      // where the cursor was when the button went down
    private Vec3 strokeDir = DEFAULT_STROKE;
    private double charge;
    private double swungFor;

    /**
     * Where the forward swing starts from, or null until the first step of one.
     *
     * Null rather than computed in release() for a reason that only shows up at low charge.
     * It used to be set to {@code target - strokeDir * swingLength/2}, which is exactly where
     * the backswing has drawn the blade to at FULL charge -- but the backswing draws back by
     * DRAW_BACK*charge while the swing is sized by max(0.15, charge), so below 15% charge the
     * two disagree and the blade teleported up to 7.5 cm backwards on the first step of the
     * swing. Paddle differences its own pose, so that read as 36 m/s, in the one phase that
     * is deliberately not speed-limited. A tap of the button was the hardest hit in the game.
     *
     * Taking it from the blade is also the more honest model: a stroke carries on from where
     * the backswing left the bat.
     */
    private Vec3 swingOrigin;
    private double swingLength;

    public Stroke(Vec3 restingAt) {
        this.target = restingAt;
    }

    /** Where the cursor currently points on the hitting plane. */
    public void aimAt(Vec3 point) { target = point; }

    /** Right button pressed: start winding up. */
    public void press() {
        if (phase == Phase.SWINGING) return;      // let the current swing finish
        phase = Phase.CHARGING;
        anchor = target;
        charge = 0;
    }

    /** Right button released: swing. */
    public void release() {
        if (phase != Phase.CHARGING) return;
        phase = Phase.SWINGING;
        swungFor = 0;
        swingLength = SWING_LENGTH * Math.max(0.15, charge);

        // The blade is already drawn back, so the swing sweeps THROUGH the cursor rather than
        // launching away from it: the backswing sits DRAW_BACK*charge behind the target and
        // the stroke travels further than that, so it always passes the point being aimed at,
        // and it passes it near the middle of the stroke where the blade is fastest. Aiming
        // at the ball and aiming at maximum speed are the same act.
        //
        // Where it starts is taken from the blade on the first step -- see swingOrigin.
        swingOrigin = null;
    }

    public Phase phase()  { return phase; }
    public double charge() { return charge; }

    /**
     * Advance one physics step and move the blade.
     *
     * @param dt the PHYSICS step, never a frame time -- Paddle derives its velocity from this,
     *           and that velocity is what the ball is struck with
     */
    public void advance(Paddle blade, double dt) {
        Phase was = phase;
        Vec3 pos;

        switch (phase) {
            case CHARGING -> {
                charge = Math.min(1, charge + dt / CHARGE_FULL);

                // The drag since the press IS the backswing, so the direction the blade will
                // travel is the opposite of it. Below a centimetre of movement there is no
                // meaningful direction in the gesture, so fall back to a standard loop.
                Vec3 drag = target.minus(anchor);
                if (drag.length() > 0.01) strokeDir = drag.normalized().negate();

                pos = target.plusScaled(strokeDir, -DRAW_BACK * charge);
            }
            case SWINGING -> {
                if (swingOrigin == null) swingOrigin = blade.pos();
                swungFor += dt;
                double t = Math.min(1, swungFor / SWING_TIME);

                // Displacement profile whose derivative is a half sine: the blade accelerates
                // from rest, peaks in the middle of the stroke, and decelerates to rest. A
                // constant-velocity swing would start and stop with infinite acceleration, and
                // since Paddle measures velocity by differencing the pose, that would read as
                // a single impossible step.
                double s = (1 - Math.cos(Math.PI * t)) / 2;
                pos = swingOrigin.plusScaled(strokeDir, swingLength * s);

                if (t >= 1) {
                    phase = Phase.IDLE;
                    charge = 0;
                }
            }
            default -> pos = target;
        }

        // Hold the cursor-chasing phases to a human tracking speed. The swing itself is left
        // alone: its half-sine profile is already a physical velocity, and clamping it would
        // quietly cap how hard a charged stroke can hit. Tested against the phase on ENTRY,
        // because the swing sets itself back to IDLE on its final step and that step's
        // displacement is still part of the swing.
        if (was != Phase.SWINGING) pos = towards(blade.pos(), pos, TRACK_SPEED * dt);

        blade.moveTo(pos, faceFor(strokeDir), dt);
    }

    /** Move from {@code from} toward {@code to}, by at most {@code maxStep}. */
    private static Vec3 towards(Vec3 from, Vec3 to, double maxStep) {
        Vec3 step = to.minus(from);
        double len = step.length();
        return len <= maxStep ? to : from.plusScaled(step.scale(1.0 / len), maxStep);
    }

    /**
     * The face angle implied by a stroke direction.
     *
     * The blade always faces roughly down the table (-Z, at the incoming ball), but it leans
     * according to how steep the stroke is: swing upward and the face closes over the ball,
     * which brushes it into topspin; swing downward and the face opens, which cuts backspin
     * under it. That single line is the whole of "how you move through the ball is the shot".
     */
    private static Vec3 faceFor(Vec3 stroke) {
        return new Vec3(stroke.x() * 0.5, -stroke.y() * FACE_CLOSE, -1).normalized();
    }
}
