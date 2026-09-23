package tabletennis.app.input;

import javafx.geometry.Point3D;
import javafx.scene.Camera;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import tabletennis.app.scene.Xform;
import tabletennis.engine.math.Vec3;

/**
 * Where the cursor's ray meets a HORIZONTAL plane at a given height. Geometry only: no clamping
 * (the reach envelope owns that) and no ball. Because the height is a parameter, cursor X maps to
 * world X and cursor Y to world Z, each monotonically, from any camera angle.
 */
final class CursorRay {

    private CursorRay() {}

    /**
     * TUNED: how far a ray that never meets the plane is walked out, metres. Keeps the mapping
     * continuous past the horizon; 30 m made lateral aim near-binary there, 6 m still out-runs ZFar.
     */
    private static final double Horizon = 6.0;

    /** Used when the camera is not a perspective one, which the rig never builds. */
    private static final double FallbackHalfFieldOfView = 20;

    private record Ray(Point3D Eye, Point3D Direction) {}

    /**
     * The point at exactly PlaneY under the cursor, X and Z unclamped, or Fallback if the ray
     * degenerates. Independent of the blade's position, which would be a loop with gain.
     */
    static Vec3 OnPlane(SubScene Viewport, double CursorX, double CursorY, double PlaneY, Vec3 Fallback) {
        Camera Lens = Viewport.getCamera();
        if (Lens == null) return Fallback;
        Ray Sight = Through(Lens, Viewport, CursorX, CursorY);
        if (Sight == null) return Fallback;

        Vec3 Eye = Xform.ToPhysics(Sight.Eye());
        Vec3 Direction = Xform.ToPhysics(Sight.Eye().add(Sight.Direction())).Minus(Eye);
        if (!Eye.IsFinite() || !Direction.IsFinite()) return Fallback;

        double Along = DistanceAlongRay(Eye, Direction, PlaneY);
        if (Double.isNaN(Along)) return Fallback;
        Vec3 Hit = Eye.PlusScaled(Direction, Along);
        if (!Hit.IsFinite()) return Fallback;

        return new Vec3(Hit.X(), PlaneY, Hit.Z());   // height from the plane, never from the ray
    }

    /**
     * Where a descending ray meets the plane, capped at the horizon; a level or rising ray, or a
     * plane above the eye, walks out to the horizon so the blade never freezes. NaN if vertical.
     */
    private static double DistanceAlongRay(Vec3 Eye, Vec3 Direction, double PlaneY) {
        double Horizontal = Math.hypot(Direction.X(), Direction.Z());
        boolean HasHeading = Horizontal > 1e-9;
        if (Direction.Y() < -1e-9) {
            double Along = (PlaneY - Eye.Y()) / Direction.Y();
            if (Along <= 0) return HasHeading ? Horizon / Horizontal : Double.NaN;
            return HasHeading ? Math.min(Along, Horizon / Horizontal) : Along;
        }
        return HasHeading ? Horizon / Horizontal : Double.NaN;
    }

    /** The eye is the camera origin and it looks along its local +Z, at any orbit angle. */
    private static Ray Through(Camera Lens, SubScene Viewport, double CursorX, double CursorY) {
        double Width = Viewport.getWidth(), Height = Viewport.getHeight();
        if (Width <= 0 || Height <= 0) return null;

        Point3D Eye = Lens.localToScene(0, 0, 0);
        double HalfFieldOfView = Lens instanceof PerspectiveCamera Perspective
                               ? Perspective.getFieldOfView() / 2 : FallbackHalfFieldOfView;
        double HalfHeight = Math.tan(Math.toRadians(HalfFieldOfView));

        double Scale = Math.min(Width, Height) / 2.0;   // JavaFX measures FOV along the shorter side
        double LocalX = (CursorX - Width / 2) / Scale * HalfHeight;
        double LocalY = (CursorY - Height / 2) / Scale * HalfHeight;

        Point3D Through = Lens.localToScene(LocalX, LocalY, 1);
        return new Ray(Eye, Through.subtract(Eye));
    }
}
