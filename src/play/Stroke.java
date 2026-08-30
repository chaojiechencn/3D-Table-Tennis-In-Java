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

    /** Default stroke if the player charges without moving the mouse: a standard loop. */
    private static final Vec3 DEFAULT_STROKE =
            new Vec3(0, Math.sin(Math.toRadians(30)), -Math.cos(Math.toRadians(30)));

    private Phase phase = Phase.IDLE;
    private Vec3 target;
    private Vec3 anchor = Vec3.ZERO;      // where the cursor was when the button went down
    private Vec3 strokeDir = DEFAULT_STROKE;
    private double charge;
    private double swungFor;
    private Vec3 swingOrigin = Vec3.ZERO;
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
        // Start the blade drawn back so the swing sweeps THROUGH the cursor rather than
        // launching away from it. The cursor point ends up at the middle of the stroke, which
        // is where the blade is travelling fastest -- so aiming at the ball and aiming at
        // maximum speed are the same act.
        swingOrigin = target.plusScaled(strokeDir, -swingLength / 2);
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

        blade.moveTo(pos, faceFor(strokeDir), dt);
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
