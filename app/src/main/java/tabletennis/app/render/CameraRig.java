package tabletennis.app.render;

import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Rotate;
import tabletennis.engine.Vec3;

import static tabletennis.engine.Constants.TABLE_LENGTH;

/**
 * An orbiting camera on a gimbal, preset views, and the default rally-cam. The side view matters:
 * from behind, perspective hides a Magnus dip almost entirely.
 */
public final class CameraRig {

    /** Cycled with C: yaw, pitch (degrees), distance and pivot height (metres). */
    public enum View {
        BEHIND(0, 16, 3.85, 0.12),
        SIDE(90, 9, 3.35, 0.18),
        HIGH(34, 42, 3.60, 0.05),
        LOW(8, 1.5, 2.30, 0.16),
        TOP(0, 86, 4.60, 0.0);

        final double yaw, pitch, distance, height;

        View(double yaw, double pitch, double distance, double height) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.distance = distance;
            this.height = height;
        }

        /** Case-insensitive, so the documented --view=SIDE spelling keeps working. */
        public static View fromArg(String name) {
            for (View v : values()) if (v.name().equalsIgnoreCase(name)) return v;
            throw new IllegalArgumentException("no view named " + name);
        }
    }

    // Rally-cam views as {pitch deg, distance m, pivot height m}: IN after the player hits (their
    // feed counts), OUT after the opponent. RALLY_IN is a CONTROL constraint: its frustum must
    // reach PlayerReach.Z_FAR, or part of the envelope cannot be aimed at. {8, 3.45} reaches 2.47.
    private static final double[] RALLY_IN  = {  8.0, 3.45, 0.16 };
    private static final double[] RALLY_OUT = { 21.0, 4.75, 0.08 };

    /** Reads as a cut, not a drift, without snapping the table sideways. */
    private static final double RALLY_TAU = 0.12;

    /** The swing keeps camera, ball and the opponent's half in line; it never moves toward the ball. */
    private static final double OPP_CENTRE_Z = -TABLE_LENGTH / 4;

    /** Measured: at this yaw the whole Z_NEAR..Z_FAR depth range is still addressable. */
    private static final double MAX_SWING_DEG = 22.0;

    /** A pan, slower than the cut. */
    private static final double SWING_TAU = 0.35;

    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final Group gimbal = new Group();
    private final Rotate yawRot = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate pitchRot = new Rotate(0, Rotate.X_AXIS);

    private double yaw, pitch, distance, height;
    private View current = View.BEHIND;

    /** Any manual camera input drops out of it. */
    private boolean rallyCam = true;
    private double[] rallyTarget = RALLY_IN;
    private double rcPitch = RALLY_IN[0], rcDist = RALLY_IN[1], rcHeight = RALLY_IN[2];
    private double rcYaw = 0;

    public CameraRig() {
        camera.setNearClip(1);
        camera.setFarClip(20000);
        camera.setFieldOfView(38);
        // The pivot rotates, not the camera: JavaFX has no look-at.
        gimbal.getTransforms().addAll(yawRot, pitchRot);
        gimbal.getChildren().add(camera);
        set(View.BEHIND);
    }

    public PerspectiveCamera camera() { return camera; }
    public Group gimbal() { return gimbal; }

    public void toggleRallyCam() {
        rallyCam = !rallyCam;
        if (!rallyCam) set(current);
    }

    /** Capture mode wants a fixed preset. */
    public void stopRallyCam() { rallyCam = false; set(current); }

    public void onRallyHit(boolean playerHit) {
        rallyTarget = playerHit ? RALLY_IN : RALLY_OUT;
    }

    public void apply(View v) {
        rallyCam = false;
        set(v);
    }

    public void next() {
        rallyCam = false;
        View[] all = View.values();
        set(all[(current.ordinal() + 1) % all.length]);
    }

    private void set(View v) {
        current = v;
        yaw = v.yaw;
        pitch = v.pitch;
        distance = v.distance;
        height = v.height;
        refresh();
    }

    private void orbit(double dYawDeg, double dPitchDeg) {
        rallyCam = false;
        yaw += dYawDeg;
        pitch = clamp(pitch + dPitchDeg, -12, 89);
        refresh();
    }

    private void zoom(double factor) {
        rallyCam = false;
        distance = clamp(distance * factor, 0.75, 12.0);
        refresh();
    }

    /** Once per FRAME on wall-clock time: a view, not physics. */
    public void updateRally(double frameDt, Vec3 ball) {
        if (!rallyCam) return;

        double dt = Math.max(1e-3, frameDt);
        double k = 1 - Math.exp(-dt / RALLY_TAU);
        rcPitch  += (rallyTarget[0] - rcPitch)  * k;
        rcDist   += (rallyTarget[1] - rcDist)   * k;
        rcHeight += (rallyTarget[2] - rcHeight) * k;
        rcYaw += (swingYaw(ball) - rcYaw) * (1 - Math.exp(-dt / SWING_TAU));

        yawRot.setAngle(rcYaw);
        pitchRot.setAngle(-rcPitch);
        Xform.place(gimbal, 0, rcHeight, 0);
        camera.setTranslateZ(Xform.z(rcDist));
    }

    /** Negated: Xform's axis flip turns a physics rotation about Y the other way in the scene. */
    private static double swingYaw(Vec3 ball) {
        if (ball == null || !ball.isFinite()) return 0;
        return clamp(-Math.toDegrees(Math.atan2(ball.x(), ball.z() - OPP_CENTRE_Z)),
                     -MAX_SWING_DEG, MAX_SWING_DEG);
    }

    /** Pitch is negated because scene +Y is down; the pivot sits a little above the table. */
    private void refresh() {
        yawRot.setAngle(yaw);
        pitchRot.setAngle(-pitch);
        Xform.place(gimbal, 0, height, 0);
        camera.setTranslateZ(Xform.z(distance));
    }

    /**
     * Left drag orbits and scroll zooms; bare movement is left to aiming. addEventHandler, not the
     * single-slot setOn* properties, so the application's aim handler can share this SubScene.
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

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
