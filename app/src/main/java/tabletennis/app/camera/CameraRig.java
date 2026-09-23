package tabletennis.app.camera;

import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Rotate;
import tabletennis.app.scene.Xform;
import tabletennis.engine.TableSpec;
import tabletennis.engine.math.Vec3;

import static tabletennis.engine.math.Numeric.Clamp;

/**
 * An orbiting camera on a gimbal, the preset views, and the default rally-cam. The rally-cam does
 * not follow the ball: it cuts between two fixed views on who last hit, close after the player's
 * shot and wide after the opponent's, and pans a little to keep ball and far half in line.
 */
public final class CameraRig {

    /**
     * After the player hits (the feed counts). A CONTROL constraint, not only a view: its frustum
     * must reach the envelope's far depth, or part of the envelope cannot be aimed at. {8, 3.45}
     * reaches 2.47.
     */
    private static final CameraPose RallyIn = new CameraPose(8.0, 3.45, 0.16);

    /** After the opponent hits. */
    private static final CameraPose RallyOut = new CameraPose(21.0, 4.75, 0.08);

    /** Reads as a cut, not a drift, without snapping the table sideways. */
    private static final double RallyTau = 0.12;

    /** The pan keeps camera, ball and the opponent's half in line; it never moves toward the ball. */
    private static final double OpponentHalfCentreZ = -TableSpec.Length / 4;

    /** Measured: at this yaw the whole reachable depth range is still addressable. */
    private static final double MaxPanDegrees = 22.0;

    /** A pan, slower than the cut. */
    private static final double PanTau = 0.35;

    private static final double MinPitch = -12, MaxPitch = 89;
    private static final double MinDistance = 0.75, MaxDistance = 12.0;
    private static final double OrbitDegreesPerPixel = 0.3;
    private static final double ZoomIn = 0.92, ZoomOut = 1.087;
    private static final double NearClip = 1, FarClip = 20000, FieldOfView = 38;

    private final PerspectiveCamera Camera = new PerspectiveCamera(true);
    private final Group Gimbal = new Group();
    private final Rotate YawRotation = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate PitchRotation = new Rotate(0, Rotate.X_AXIS);

    private double Yaw, Pitch, Distance, Height;
    private CameraView Current = CameraView.Behind;

    /** On by default; any manual camera input drops out of it. */
    private boolean RallyCam = true;
    private CameraPose RallyTarget = RallyIn;
    private CameraPose Rally = RallyIn;
    private double RallyYaw = 0;

    public CameraRig() {
        Camera.setNearClip(NearClip);
        Camera.setFarClip(FarClip);
        Camera.setFieldOfView(FieldOfView);
        // The pivot rotates, not the camera: JavaFX has no look-at.
        Gimbal.getTransforms().addAll(YawRotation, PitchRotation);
        Gimbal.getChildren().add(Camera);
        Show(CameraView.Behind);
    }

    public PerspectiveCamera Camera() { return Camera; }
    public Group Gimbal() { return Gimbal; }

    public void ToggleRallyCam() {
        RallyCam = !RallyCam;
        if (!RallyCam) Show(Current);
    }

    /** Capture mode wants a fixed preset. */
    public void StopRallyCam() {
        RallyCam = false;
        Show(Current);
    }

    public void OnRallyHit(boolean PlayerHit) {
        RallyTarget = PlayerHit ? RallyIn : RallyOut;
    }

    public void Apply(CameraView View) {
        RallyCam = false;
        Show(View);
    }

    public void Next() {
        RallyCam = false;
        Show(Current.Next());
    }

    /** Once per FRAME on wall-clock time: a view, not physics. */
    public void UpdateRally(double FrameSeconds, Vec3 Ball) {
        if (!RallyCam) return;

        double Seconds = Math.max(1e-3, FrameSeconds);
        Rally = Rally.EasedToward(RallyTarget, 1 - Math.exp(-Seconds / RallyTau));
        RallyYaw += (PanYaw(Ball) - RallyYaw) * (1 - Math.exp(-Seconds / PanTau));
        Pose(RallyYaw, Rally.Pitch(), Rally.Distance(), Rally.Height());
    }

    /**
     * Left drag orbits and scroll zooms; bare movement is left to aiming. addEventHandler, not the
     * single-slot setOn* properties, so the mouse control can share this SubScene.
     */
    public void AttachControls(SubScene Viewport) {
        final double[] Anchor = new double[2];
        Viewport.addEventHandler(MouseEvent.MOUSE_PRESSED, Event -> {
            if (!Event.isPrimaryButtonDown()) return;
            Anchor[0] = Event.getSceneX();
            Anchor[1] = Event.getSceneY();
        });
        Viewport.addEventHandler(MouseEvent.MOUSE_DRAGGED, Event -> {
            if (!Event.isPrimaryButtonDown()) return;
            double Dx = Event.getSceneX() - Anchor[0];
            double Dy = Event.getSceneY() - Anchor[1];
            Anchor[0] = Event.getSceneX();
            Anchor[1] = Event.getSceneY();
            Orbit(-Dx * OrbitDegreesPerPixel, Dy * OrbitDegreesPerPixel);
        });
        Viewport.addEventHandler(ScrollEvent.SCROLL, Event -> Zoom(Event.getDeltaY() > 0 ? ZoomIn : ZoomOut));
    }

    private void Show(CameraView View) {
        Current = View;
        Yaw = View.Yaw;
        Pitch = View.Pitch;
        Distance = View.Distance;
        Height = View.Height;
        Pose(Yaw, Pitch, Distance, Height);
    }

    private void Orbit(double YawDegrees, double PitchDegrees) {
        RallyCam = false;
        Yaw += YawDegrees;
        Pitch = Clamp(Pitch + PitchDegrees, MinPitch, MaxPitch);
        Pose(Yaw, Pitch, Distance, Height);
    }

    private void Zoom(double Factor) {
        RallyCam = false;
        Distance = Clamp(Distance * Factor, MinDistance, MaxDistance);
        Pose(Yaw, Pitch, Distance, Height);
    }

    /** Pitch is negated because scene +Y is down; the pivot sits a little above the table. */
    private void Pose(double YawDegrees, double PitchDegrees, double DistanceMetres, double HeightMetres) {
        YawRotation.setAngle(YawDegrees);
        PitchRotation.setAngle(-PitchDegrees);
        Xform.Place(Gimbal, 0, HeightMetres, 0);
        Camera.setTranslateZ(Xform.Z(DistanceMetres));
    }

    /** Negated: Xform's axis flip turns a physics rotation about Y the other way in the scene. */
    private static double PanYaw(Vec3 Ball) {
        if (Ball == null || !Ball.IsFinite()) return 0;
        return Clamp(-Math.toDegrees(Math.atan2(Ball.X(), Ball.Z() - OpponentHalfCentreZ)), -MaxPanDegrees, MaxPanDegrees);
    }
}
