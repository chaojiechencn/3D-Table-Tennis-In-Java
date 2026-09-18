package pong.screens;

import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Rotate;

import static pong.config.Physical.TABLE_LENGTH;
import pong.core.math.Vec3;
import pong.helpers.Xform;
import static pong.core.math.Scalars.clamp;

/**
 * An orbiting camera on a gimbal, plus the preset views the demo actually needs.
 *
 * The side view is not a luxury. Magnus curve is a deflection perpendicular to the flight
 * path, so from directly behind the table a topspin dip is almost entirely hidden by
 * perspective -- the ball just appears to shrink. From the side the same shot draws an
 * obvious arc. A demo of curved flight that can only be watched from behind is not
 * demonstrating anything, so the camera is a first-class part of the deliverable.
 */
public final class CameraRig {

    /** Named viewpoints, cycled with C. */
    public enum View {
        BEHIND("Behind the near end", 0, 16, 3.85, 0.12),
        SIDE("Side on - best for seeing curve", 90, 9, 3.35, 0.18),
        HIGH("High three-quarter", 34, 42, 3.60, 0.05),
        LOW("Net level", 8, 1.5, 2.30, 0.16),
        TOP("Overhead - shows sidespin", 0, 86, 4.60, 0.0);

        final String label;
        final double yaw, pitch, distance, height;

        View(String label, double yaw, double pitch, double distance, double height) {
            this.label = label;
            this.yaw = yaw;
            this.pitch = pitch;
            this.distance = distance;
            this.height = height;
        }

        public String label() { return label; }
    }

    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final Group gimbal = new Group();
    private final Rotate yawRot = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate pitchRot = new Rotate(0, Rotate.X_AXIS);

    private double yaw, pitch, distance, height;
    private View current = View.BEHIND;

    // ------------------------------------------------------------------ rally-cam

    /**
     * When true the camera ignores yaw/pitch/distance above and holds one of two FIXED views,
     * both dead behind the near end -- a close one and a wide one -- cutting between them on
     * who last hit the ball: IN when the player hit it (their own feed counts), OUT when the
     * opponent did. It does not track the ball. This is the game default; any manual camera
     * input (orbit, zoom, cycling a preset) drops back out of it.
     */
    private boolean rallyCam = true;

    /** The two fixed views, as {pitch deg, distance m, pivot height m}. Same yaw (0). The wide
     *  one is higher and further back so the whole table is in shot; the close one drops low
     *  and near so the near half fills the frame. */
    private static final double[] RALLY_IN  = {  8.0, 3.45, 0.16 };
    private static final double[] RALLY_OUT = { 21.0, 4.75, 0.08 };

    /*
     * RALLY_IN's {pitch, distance} is a CONTROL constraint, not a framing preference: the
     * player aims by casting the cursor's ray onto the hitting plane, so the camera decides how
     * much of PlayerReach's envelope is on screen to aim into. For a camera at pitch p and
     * distance d with this 38-degree vertical FOV, the reachable depth is
     * z_reach = d * [cos(p) - sin(p)/tan(p+19)]. At the old {12, 2.35} that frustum bottomed out
     * at z = 1.485, well short of PlayerReach.Z_FAR = 2.40 -- invisible to RallyTest, which
     * grades PlayerReach.clamp directly and never looks through a camera. {8.0, 3.45} reaches
     * z = 2.47 while sitting nearer the table than raising distance alone would need (measured
     * through the real rig). TOP and HIGH have the same shortfall and are left alone
     * deliberately -- inspection views, not ones a rally is played from.
     */

    /** Seconds for the cut between the two views. Short -- it should read as a cut, not a
     *  drift -- but not instant, which snaps the whole table sideways and is horrible. */
    private static final double RALLY_TAU = 0.12;

    /** The view being eased toward, and the live pose easing toward it. */
    private double[] rallyTarget = RALLY_IN;
    private double rcPitch = RALLY_IN[0], rcDist = RALLY_IN[1], rcHeight = RALLY_IN[2];

    // ------------------------------------------------------------------ the swing

    /**
     * The rally-cam now SWINGS: it keeps the camera, the ball and the centre of the opponent's
     * half roughly in a line, so you are looking down the lane you are about to hit along.
     *
     * This is a partial reversal of the two-fixed-views decision, which was made because a
     * ball-tracking camera is disorienting to hit from. What makes this different is that it
     * does not track the ball's POSITION -- the camera never moves toward or away from the
     * ball, and the framing presets are untouched. It only rotates about the table's centre so
     * the shot line stays down-screen. The pivot, distance and pitch are still whatever
     * RALLY_IN / RALLY_OUT say.
     */
    private static final double OPP_CENTRE_Z = -TABLE_LENGTH / 4;

    /**
     * Hard cap on the swing, degrees -- the same control constraint as RALLY_IN's distance
     * above (yaw slides PlayerReach's envelope across the frame the same way distance does).
     * Measured through the real camera at this limit, the full depth range Z_NEAR 0.30 to
     * Z_FAR 2.40 is still addressable.
     */
    private static final double MAX_SWING_DEG = 22.0;

    /** Slower than the cut: the swing should feel like the camera following the play, not
     *  snapping to it. RALLY_TAU is tuned to read as a CUT, which is wrong for a pan. */
    private static final double SWING_TAU = 0.35;

    private double rcYaw = 0;

    public CameraRig() {
        camera.setNearClip(1);
        camera.setFarClip(20000);
        camera.setFieldOfView(38);

        // The camera sits behind the pivot and the pivot is what rotates. Orbiting by moving
        // the camera itself needs a look-at matrix, which JavaFX does not provide.
        gimbal.getTransforms().addAll(yawRot, pitchRot);
        gimbal.getChildren().add(camera);

        set(View.BEHIND);
    }

    public PerspectiveCamera camera() { return camera; }
    public Group gimbal() { return gimbal; }
    public View view() { return current; }

    /** F toggles the rally-cam. Leaving it restores the last preset cleanly. */
    public void toggleRallyCam() {
        rallyCam = !rallyCam;
        if (!rallyCam) set(current);
    }

    /** Screenshot mode pins a fixed preset, so it opts out of the rally-cam. */
    public void stopRallyCam() { rallyCam = false; set(current); }

    /**
     * Who just hit the ball. Player hit (or the player's feed) cuts the camera IN; opponent
     * hit cuts it OUT. No-op while the rally-cam is off.
     */
    public void onRallyHit(boolean playerHit) {
        rallyTarget = playerHit ? RALLY_IN : RALLY_OUT;
    }

    /** Choosing a named view is a deliberate camera move, so it drops the rally-cam. */
    public void apply(View v) {
        rallyCam = false;
        set(v);
    }

    public View next() {
        rallyCam = false;
        View[] all = View.values();
        set(all[(current.ordinal() + 1) % all.length]);
        return current;
    }

    private void set(View v) {
        current = v;
        yaw = v.yaw;
        pitch = v.pitch;
        distance = v.distance;
        height = v.height;
        refresh();
    }

    /** Drag to orbit. Once the user moves the camera it is no longer a named view -- or a
     *  rally-cam. */
    public void orbit(double dYawDeg, double dPitchDeg) {
        rallyCam = false;
        yaw += dYawDeg;
        pitch = clamp(pitch + dPitchDeg, -12, 89);
        refresh();
    }

    /** Scroll to dolly in and out. */
    public void zoom(double factor) {
        rallyCam = false;
        distance = clamp(distance * factor, 0.75, 12.0);
        refresh();
    }

    /**
     * Ease the live pose toward whichever fixed view the last hit selected. Called once per
     * FRAME off the real frame time -- this is a view, not physics, so unlike everything under
     * the simulation it may see a wall-clock dt.
     *
     * Nothing here tracks the ball: the camera only ever sits at RALLY_IN or RALLY_OUT (or
     * between them, mid-cut), both fixed behind the near end.
     */
    public void updateRally(double frameDt, Vec3 ball) {
        if (!rallyCam) return;

        double dt = Math.max(1e-3, frameDt);
        double k = 1 - Math.exp(-dt / RALLY_TAU);
        rcPitch  += (rallyTarget[0] - rcPitch)  * k;
        rcDist   += (rallyTarget[1] - rcDist)   * k;
        rcHeight += (rallyTarget[2] - rcHeight) * k;

        // The swing: put the camera on the line from the opponent's half through the ball
        // (atan2 of the horizontal offset, since yaw 0 already looks down the table). NEGATED
        // because Xform maps physics (x, y, z) to scene (x, -y, -z) -- a handedness flip, so a
        // physics-space rotation about Y is the opposite rotation once it reaches the JavaFX
        // gimbal. This is that conversion happening at the boundary, where it belongs.
        double wantYaw = 0;
        if (ball != null && ball.isFinite()) {
            wantYaw = clamp(-Math.toDegrees(Math.atan2(ball.x(), ball.z() - OPP_CENTRE_Z)),
                            -MAX_SWING_DEG, MAX_SWING_DEG);
        }
        rcYaw += (wantYaw - rcYaw) * (1 - Math.exp(-dt / SWING_TAU));

        yawRot.setAngle(rcYaw);
        pitchRot.setAngle(-rcPitch);
        Xform.place(gimbal, 0, rcHeight, 0);
        camera.setTranslateZ(Xform.z(rcDist));
    }

    private void refresh() {
        // Pitch is negated because JavaFX +Y points DOWN: a positive rotation about X would
        // swing the camera under the floor rather than up over the table.
        yawRot.setAngle(yaw);
        pitchRot.setAngle(-pitch);

        // The gimbal pivots a little above the table surface rather than on it, so raising
        // the view does not push the table out of frame.
        Xform.place(gimbal, 0, height, 0);
        camera.setTranslateZ(Xform.z(distance));
    }

    /**
     * Wire mouse orbit and scroll zoom onto the SubScene showing this camera.
     *
     * LEFT button only. This used to filter on no button at all, so ANY drag orbited -- which
     * collided head-on with bare mouse movement driving the racket. A left drag orbits (and
     * drops the rally-cam); bare movement aims.
     *
     * addEventHandler, not setOnMouseDragged. Those are single-slot properties, and MatchScreen
     * has to put its own aiming handler on this same SubScene; whichever assigned second would
     * silently unhook the other.
     */
    public void attachControls(SubScene sub) {
        final double[] anchor = new double[2];

        sub.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (!e.isPrimaryButtonDown()) return;
            anchor[0] = e.getSceneX();
            anchor[1] = e.getSceneY();
        });
        sub.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!e.isPrimaryButtonDown()) return;
            double dx = e.getSceneX() - anchor[0];
            double dy = e.getSceneY() - anchor[1];
            anchor[0] = e.getSceneX();
            anchor[1] = e.getSceneY();
            orbit(-dx * 0.3, dy * 0.3);
        });
        sub.addEventHandler(ScrollEvent.SCROLL, e -> zoom(e.getDeltaY() > 0 ? 0.92 : 1.087));
    }

}
