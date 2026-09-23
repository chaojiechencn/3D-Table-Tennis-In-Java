package tabletennis.app.render;

import javafx.geometry.Point3D;
import javafx.scene.Camera;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import tabletennis.engine.Vec3;

/**
 * Where the cursor's ray meets a HORIZONTAL plane at a given height. Geometry only: no clamping
 * ({@link tabletennis.game.PlayerReach} owns that) and no ball. Because the height is a parameter, cursor X
 * maps to world X and cursor Y to world Z, each monotonically, from any camera angle.
 */
public final class MouseAim {

    /**
     * TUNED: how far a ray that never meets the plane is walked out, metres. Keeps the mapping
     * continuous past the horizon; 30 m made lateral aim near-binary there, 6 m still out-runs ZFar.
     */
    private static final double Horizon = 6.0;

    private MouseAim() {}

    private record Ray(Point3D Eye, Point3D Dir) {}

    /**
     * The point at exactly planeY under the cursor, X and Z unclamped, or {@code fallback} if the
     * ray degenerates. Independent of the blade's position, which would be a loop with gain.
     */
    public static Vec3 OnHittingPlane(SubScene Sub, double MouseX, double MouseY,
                                      double PlaneY, Vec3 Fallback) {
        Camera Cam = Sub.getCamera();
        if (Cam == null) return Fallback;
        Ray Sight = Ray(Cam, Sub, MouseX, MouseY);
        if (Sight == null) return Fallback;

        Vec3 Eye = Xform.ToPhysics(Sight.Eye());
        Vec3 Dir = Xform.ToPhysics(Sight.Eye().add(Sight.Dir())).Minus(Eye);
        if (!Eye.IsFinite() || !Dir.IsFinite()) return Fallback;

        double S = DistanceAlongRay(Eye, Dir, PlaneY);
        if (Double.isNaN(S)) return Fallback;
        Vec3 Hit = Eye.PlusScaled(Dir, S);
        if (!Hit.IsFinite()) return Fallback;

        return new Vec3(Hit.X(), PlaneY, Hit.Z());   // height from the plane, never the ray
    }

    /**
     * Where a descending ray meets the plane, capped at the horizon; a level or rising ray, or a
     * plane above the eye, walks out to the horizon so the blade never freezes. NaN if vertical.
     */
    private static double DistanceAlongRay(Vec3 Eye, Vec3 Dir, double PlaneY) {
        double Horizontal = Math.hypot(Dir.X(), Dir.Z());
        boolean HasHeading = Horizontal > 1e-9;
        if (Dir.Y() < -1e-9) {
            double S = (PlaneY - Eye.Y()) / Dir.Y();
            if (S <= 0) return HasHeading ? Horizon / Horizontal : Double.NaN;
            return HasHeading ? Math.min(S, Horizon / Horizontal) : S;
        }
        return HasHeading ? Horizon / Horizontal : Double.NaN;
    }

    /** The eye is the camera origin and it looks along its local +Z, at any orbit angle. */
    private static Ray Ray(Camera Cam, SubScene Sub, double MouseX, double MouseY) {
        double W = Sub.getWidth(), H = Sub.getHeight();
        if (W <= 0 || H <= 0) return null;

        Point3D Eye = Cam.localToScene(0, 0, 0);
        double HalfH = Math.tan(Math.toRadians(Cam instanceof PerspectiveCamera Pc
                                               ? Pc.getFieldOfView() / 2 : 20));

        double Scale = Math.min(W, H) / 2.0;   // JavaFX measures FOV along the shorter side
        double LocalX = (MouseX - W / 2) / Scale * HalfH;
        double LocalY = (MouseY - H / 2) / Scale * HalfH;

        Point3D Through = Cam.localToScene(LocalX, LocalY, 1);
        return new Ray(Eye, Through.subtract(Eye));
    }
}
