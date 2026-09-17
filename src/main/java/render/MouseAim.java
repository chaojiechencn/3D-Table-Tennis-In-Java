package render;

import javafx.geometry.Point3D;
import javafx.scene.Camera;
import javafx.scene.SubScene;
import physics.Vec3;

/**
 * Turns a mouse position into the point on the racket's hitting plane the player is pointing
 * at. Geometry only: build the ray the cursor points along, in scene units, and intersect it
 * with a HORIZONTAL plane at a caller-supplied height -- not the naive "map the cursor's screen
 * fraction onto a rectangle of table", which only reads correctly from one fixed camera angle.
 *
 * Because the plane's height is a parameter rather than something read off the ray, the two
 * screen axes come apart cleanly (cursor X -> world X, cursor Y -> world Z, monotonically); see
 * docs/DESIGN.md, "The control mapping", for the one-screen-axis-meant-two-things bug this
 * replaced. No clamping and no envelope -- {@link play.PlayerReach} owns that -- and nothing
 * here has ever seen the ball. The ray work happens in scene units; the result is converted
 * once, through {@link Xform}, which stays the only place the two spaces meet.
 */
public final class MouseAim {

    /**
     * How far along a ray to walk when it never meets the plane (at or above the horizon),
     * metres. Walking out keeps the mapping continuous and monotone instead of freezing the
     * blade the instant the cursor crosses the horizon; the caller's clamp then pins the result
     * to the edge of the legal region. TUNED: 6 m. This also sets the LATERAL sensitivity above
     * the horizon (saturates at asin(MAX_X / distance)) -- 30 m was measured to collapse that
     * whole band into a near-binary left/right switch (0.56 m of blade travel per pixel in the
     * LOW view); 6 m gives a five-fold gentler ridge while still out-running
     * {@code PlayerReach.Z_FAR}.
     */
    private static final double HORIZON = 6.0;

    private MouseAim() {}

    /**
     * Where the cursor's ray meets the horizontal plane at {@code planeY} metres.
     *
     * No state, and no dependence on where the blade currently is -- deriving the plane from the
     * blade's own position would be a loop WITH GAIN (the blade creeps, the ray reads
     * differently on the plane it just moved to, and it creeps further to the stop).
     *
     * @param sub      the SubScene the 3D world is drawn in
     * @param mouseX   cursor position within that SubScene
     * @param mouseY   cursor position within that SubScene
     * @param planeY   the height of the hitting plane, in metres above the table
     * @param fallback returned unchanged if the ray degenerates entirely
     * @return a point at exactly {@code planeY}; its X and Z are the cursor's, unclamped
     */
    public static Vec3 onHittingPlane(SubScene sub, double mouseX, double mouseY,
                                      double planeY, Vec3 fallback) {
        Camera cam = sub.getCamera();
        if (cam == null) return fallback;

        Ray ray = ray(cam, sub, mouseX, mouseY);
        if (ray == null) return fallback;

        // Convert the ray to physics space ONCE, then do the intersection there. Solving it in
        // scene units and converting the answer would work equally well, but this way the
        // plane test reads in the units the plane is quoted in, and no arithmetic anywhere
        // outside Xform has to know what a scene unit is worth.
        Vec3 eye = Xform.toPhysics(ray.eye());
        Vec3 dir = Xform.toPhysics(ray.eye().add(ray.dir())).minus(eye);
        if (!eye.isFinite() || !dir.isFinite()) return fallback;

        double horizontal = Math.hypot(dir.x(), dir.z());

        double s;
        if (dir.y() < -1e-9) {
            // Descending: it meets the plane. Cap the distance so a near-horizon ray produces
            // a far point rather than an astronomical one.
            s = (planeY - eye.y()) / dir.y();
            if (s <= 0) {
                // The plane is ABOVE the eye (orbiting down past it is real), so a descending
                // ray never reaches it. Walking out to the horizon -- the same answer the
                // level-or-rising branch gives -- keeps the mapping continuous through that
                // crossing instead of freezing the blade dead, which measured as 50% of the
                // screen going unresponsive in the LOW view.
                if (horizontal <= 1e-9) return fallback;
                s = HORIZON / horizontal;
            } else if (horizontal > 1e-9) {
                s = Math.min(s, HORIZON / horizontal);
            }
        } else if (horizontal > 1e-9) {
            // Level or rising: the crossing is at infinity. Walk out to the horizon instead,
            // which keeps the mapping monotone across the point where the cursor passes it.
            s = HORIZON / horizontal;
        } else {
            return fallback;                                   // straight up: no depth at all
        }

        Vec3 hit = eye.plusScaled(dir, s);
        if (!hit.isFinite()) return fallback;

        // Y comes from the plane, never from the ray. This is the decoupling, in one line.
        return new Vec3(hit.x(), planeY, hit.z());
    }

    /** The cursor's ray in SCENE units: where the eye is, and the way it is looking. */
    private record Ray(Point3D eye, Point3D dir) {}

    /**
     * Build the ray the cursor points along.
     *
     * The camera is a PerspectiveCamera with fixedEyeAtCameraZero, so the eye is the camera's
     * own origin and the view direction is +Z in ITS local space. Asking the node for those two
     * points in scene coordinates is what makes this work at any orbit angle: the gimbal's
     * rotations are already baked into the transform.
     */
    private static Ray ray(Camera cam, SubScene sub, double mouseX, double mouseY) {
        double w = sub.getWidth(), h = sub.getHeight();
        if (w <= 0 || h <= 0) return null;

        Point3D eye = cam.localToScene(0, 0, 0);

        double halfH = Math.tan(Math.toRadians(cam instanceof javafx.scene.PerspectiveCamera pc
                                               ? pc.getFieldOfView() / 2 : 20));

        // JavaFX measures field of view along the SHORTER side of the viewport.
        double scale = Math.min(w, h) / 2.0;
        double localX = (mouseX - w / 2) / scale * halfH;
        double localY = (mouseY - h / 2) / scale * halfH;

        Point3D through = cam.localToScene(localX, localY, 1);
        return new Ray(eye, through.subtract(eye));
    }
}
