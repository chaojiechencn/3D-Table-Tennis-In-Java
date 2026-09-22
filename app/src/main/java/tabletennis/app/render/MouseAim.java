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
     * continuous past the horizon; 30 m made lateral aim near-binary there, 6 m still out-runs Z_FAR.
     */
    private static final double HORIZON = 6.0;

    private MouseAim() {}

    private record Ray(Point3D eye, Point3D dir) {}

    /**
     * The point at exactly planeY under the cursor, X and Z unclamped, or {@code fallback} if the
     * ray degenerates. Independent of the blade's position, which would be a loop with gain.
     */
    public static Vec3 onHittingPlane(SubScene sub, double mouseX, double mouseY,
                                      double planeY, Vec3 fallback) {
        Camera cam = sub.getCamera();
        if (cam == null) return fallback;
        Ray ray = ray(cam, sub, mouseX, mouseY);
        if (ray == null) return fallback;

        Vec3 eye = Xform.toPhysics(ray.eye());
        Vec3 dir = Xform.toPhysics(ray.eye().add(ray.dir())).minus(eye);
        if (!eye.isFinite() || !dir.isFinite()) return fallback;

        double s = distanceAlongRay(eye, dir, planeY);
        if (Double.isNaN(s)) return fallback;
        Vec3 hit = eye.plusScaled(dir, s);
        if (!hit.isFinite()) return fallback;

        return new Vec3(hit.x(), planeY, hit.z());   // height from the plane, never the ray
    }

    /**
     * Where a descending ray meets the plane, capped at the horizon; a level or rising ray, or a
     * plane above the eye, walks out to the horizon so the blade never freezes. NaN if vertical.
     */
    private static double distanceAlongRay(Vec3 eye, Vec3 dir, double planeY) {
        double horizontal = Math.hypot(dir.x(), dir.z());
        boolean hasHeading = horizontal > 1e-9;
        if (dir.y() < -1e-9) {
            double s = (planeY - eye.y()) / dir.y();
            if (s <= 0) return hasHeading ? HORIZON / horizontal : Double.NaN;
            return hasHeading ? Math.min(s, HORIZON / horizontal) : s;
        }
        return hasHeading ? HORIZON / horizontal : Double.NaN;
    }

    /** The eye is the camera origin and it looks along its local +Z, at any orbit angle. */
    private static Ray ray(Camera cam, SubScene sub, double mouseX, double mouseY) {
        double w = sub.getWidth(), h = sub.getHeight();
        if (w <= 0 || h <= 0) return null;

        Point3D eye = cam.localToScene(0, 0, 0);
        double halfH = Math.tan(Math.toRadians(cam instanceof PerspectiveCamera pc
                                               ? pc.getFieldOfView() / 2 : 20));

        double scale = Math.min(w, h) / 2.0;   // JavaFX measures FOV along the shorter side
        double localX = (mouseX - w / 2) / scale * halfH;
        double localY = (mouseY - h / 2) / scale * halfH;

        Point3D through = cam.localToScene(localX, localY, 1);
        return new Ray(eye, through.subtract(eye));
    }
}
