package tabletennis.app.render;

import tabletennis.engine.TableSpec;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Rotate;
import tabletennis.engine.math.Vec3;


/**
 * An orbiting camera on a gimbal, preset views, and the default rally-cam. The side view matters:
 * from behind, perspective hides a Magnus dip almost entirely.
 */
public final class CameraRig {

    /** Cycled with C: yaw, pitch (degrees), distance and pivot height (metres). */
    public enum View {
        Behind(0, 16, 3.85, 0.12),
        Side(90, 9, 3.35, 0.18),
        High(34, 42, 3.60, 0.05),
        Low(8, 1.5, 2.30, 0.16),
        Top(0, 86, 4.60, 0.0);

        final double Yaw, Pitch, Distance, Height;

        View(double Yaw, double Pitch, double Distance, double Height) {
            this.Yaw = Yaw;
            this.Pitch = Pitch;
            this.Distance = Distance;
            this.Height = Height;
        }

        /** Case-insensitive, so the documented --view=SIDE spelling keeps working. */
        public static View FromArg(String Name) {
            for (View V : values()) if (V.name().equalsIgnoreCase(Name)) return V;
            throw new IllegalArgumentException("no view named " + Name);
        }
    }

    // Rally-cam views as {pitch deg, distance m, pivot height m}: IN after the player hits (their
    // feed counts), OUT after the opponent. RallyIn is a CONTROL constraint: its frustum must
    // reach PlayerReach.ZFar, or part of the envelope cannot be aimed at. {8, 3.45} reaches 2.47.
    private static final double[] RallyIn  = {  8.0, 3.45, 0.16 };
    private static final double[] RallyOut = { 21.0, 4.75, 0.08 };

    /** Reads as a cut, not a drift, without snapping the table sideways. */
    private static final double RallyTau = 0.12;

    /** The swing keeps camera, ball and the opponent's half in line; it never moves toward the ball. */
    private static final double OppCentreZ = -TableSpec.Length / 4;

    /** Measured: at this yaw the whole ZNear..ZFar depth range is still addressable. */
    private static final double MaxSwingDeg = 22.0;

    /** A pan, slower than the cut. */
    private static final double SwingTau = 0.35;

    private final PerspectiveCamera Camera = new PerspectiveCamera(true);
    private final Group Gimbal = new Group();
    private final Rotate YawRot = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate PitchRot = new Rotate(0, Rotate.X_AXIS);

    private double Yaw, Pitch, Distance, Height;
    private View Current = View.Behind;

    /** Any manual camera input drops out of it. */
    private boolean RallyCam = true;
    private double[] RallyTarget = RallyIn;
    private double RcPitch = RallyIn[0], RcDist = RallyIn[1], RcHeight = RallyIn[2];
    private double RcYaw = 0;

    public CameraRig() {
        Camera.setNearClip(1);
        Camera.setFarClip(20000);
        Camera.setFieldOfView(38);
        // The pivot rotates, not the camera: JavaFX has no look-at.
        Gimbal.getTransforms().addAll(YawRot, PitchRot);
        Gimbal.getChildren().add(Camera);
        Set(View.Behind);
    }

    public PerspectiveCamera Camera() { return Camera; }
    public Group Gimbal() { return Gimbal; }

    public void ToggleRallyCam() {
        RallyCam = !RallyCam;
        if (!RallyCam) Set(Current);
    }

    /** Capture mode wants a fixed preset. */
    public void StopRallyCam() { RallyCam = false; Set(Current); }

    public void OnRallyHit(boolean PlayerHit) {
        RallyTarget = PlayerHit ? RallyIn : RallyOut;
    }

    public void Apply(View V) {
        RallyCam = false;
        Set(V);
    }

    public void Next() {
        RallyCam = false;
        View[] All = View.values();
        Set(All[(Current.ordinal() + 1) % All.length]);
    }

    private void Set(View V) {
        Current = V;
        Yaw = V.Yaw;
        Pitch = V.Pitch;
        Distance = V.Distance;
        Height = V.Height;
        Refresh();
    }

    private void Orbit(double DYawDeg, double DPitchDeg) {
        RallyCam = false;
        Yaw += DYawDeg;
        Pitch = Clamp(Pitch + DPitchDeg, -12, 89);
        Refresh();
    }

    private void Zoom(double Factor) {
        RallyCam = false;
        Distance = Clamp(Distance * Factor, 0.75, 12.0);
        Refresh();
    }

    /** Once per FRAME on wall-clock time: a view, not physics. */
    public void UpdateRally(double FrameDt, Vec3 Ball) {
        if (!RallyCam) return;

        double Dt = Math.max(1e-3, FrameDt);
        double K = 1 - Math.exp(-Dt / RallyTau);
        RcPitch  += (RallyTarget[0] - RcPitch)  * K;
        RcDist   += (RallyTarget[1] - RcDist)   * K;
        RcHeight += (RallyTarget[2] - RcHeight) * K;
        RcYaw += (SwingYaw(Ball) - RcYaw) * (1 - Math.exp(-Dt / SwingTau));

        YawRot.setAngle(RcYaw);
        PitchRot.setAngle(-RcPitch);
        Xform.Place(Gimbal, 0, RcHeight, 0);
        Camera.setTranslateZ(Xform.Z(RcDist));
    }

    /** Negated: Xform's axis flip turns a physics rotation about Y the other way in the scene. */
    private static double SwingYaw(Vec3 Ball) {
        if (Ball == null || !Ball.IsFinite()) return 0;
        return Clamp(-Math.toDegrees(Math.atan2(Ball.X(), Ball.Z() - OppCentreZ)),
                     -MaxSwingDeg, MaxSwingDeg);
    }

    /** Pitch is negated because scene +Y is down; the pivot sits a little above the table. */
    private void Refresh() {
        YawRot.setAngle(Yaw);
        PitchRot.setAngle(-Pitch);
        Xform.Place(Gimbal, 0, Height, 0);
        Camera.setTranslateZ(Xform.Z(Distance));
    }

    /**
     * Left drag orbits and scroll zooms; bare movement is left to aiming. addEventHandler, not the
     * single-slot setOn* properties, so the application's aim handler can share this SubScene.
     */
    public void AttachControls(SubScene Sub) {
        final double[] Anchor = new double[2];
        Sub.addEventHandler(MouseEvent.MOUSE_PRESSED, E -> {
            if (!E.isPrimaryButtonDown()) return;
            Anchor[0] = E.getSceneX();
            Anchor[1] = E.getSceneY();
        });
        Sub.addEventHandler(MouseEvent.MOUSE_DRAGGED, E -> {
            if (!E.isPrimaryButtonDown()) return;
            double Dx = E.getSceneX() - Anchor[0];
            double Dy = E.getSceneY() - Anchor[1];
            Anchor[0] = E.getSceneX();
            Anchor[1] = E.getSceneY();
            Orbit(-Dx * 0.3, Dy * 0.3);
        });
        Sub.addEventHandler(ScrollEvent.SCROLL, E -> Zoom(E.getDeltaY() > 0 ? 0.92 : 1.087));
    }

    private static double Clamp(double V, double Lo, double Hi) {
        return V < Lo ? Lo : (V > Hi ? Hi : V);
    }
}
