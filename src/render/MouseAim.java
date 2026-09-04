package render;

import javafx.geometry.Point3D;
import javafx.scene.Camera;
import javafx.scene.SubScene;
import physics.Vec3;

/**
 * Turns a mouse position into the point on the racket's hitting plane the player is pointing
 * at. Geometry only: one ray, one horizontal plane, one intersection.
 *
 * The problem it solves is that the cursor is two numbers on a screen and the paddle needs
 * three in metres. The naive fix is to map the cursor's fraction across the window straight
 * onto a rectangle of table, which is one line of code and feels correct from exactly one
 * camera angle -- orbit away from behind the near end and the paddle starts moving sideways
 * when you move the mouse up. So this does the real thing: build the ray the cursor points
 * along and intersect it with the world. That works from any preset view and any orbit,
 * because it asks the camera where it actually is rather than assuming.
 *
 * <h2>One plane, and what that buys</h2>
 *
 * The plane is HORIZONTAL and its height is given by the caller. Because it is a plane of
 * constant height rather than a surface that rises and falls, the two screen axes come apart
 * cleanly:
 *
 * <pre>
 *   cursor X  ->  world X       across the table
 *   cursor Y  ->  world Z       up and down the table, monotonically
 *   world Y                     is the plane's own height -- never read off the ray
 * </pre>
 *
 * That last line is the point. This used to return a point on a "reach surface" whose depth
 * came from where the ray crossed table height and whose height was then read on the plane
 * that depth had chosen -- so one cursor axis meant "go deeper" and "go higher" at the same
 * time, and the depth mapping doubled back on itself at full stretch. Both are gone. The
 * height is a parameter now, the caller owns it, and moving the cursor up-screen can only move
 * the blade up-table.
 *
 * <h2>What it deliberately does not do</h2>
 *
 * No clamping, no envelope, no idea where the racket is allowed to be -- {@link play.PlayerReach}
 * owns all of that, and owns it in plain Java so it can be graded headlessly. And nothing here
 * has ever seen the ball: the blade goes where the cursor points, never where the ball is.
 *
 * All the ray work happens in scene units and the result is converted once, through
 * {@link Xform}, which stays the only place the two spaces meet.
 */
public final class MouseAim {

    /**
     * How far along a ray to walk when it never meets the plane, in metres.
     *
     * A ray pointing at or above the horizon crosses the hitting plane at infinity, or behind
     * the camera. Returning the fallback there would freeze the blade mid-motion the instant
     * the cursor passed the horizon; walking a long way along the ray instead keeps the
     * mapping continuous and monotone, and the caller's clamp pins the result to the far edge
     * of the legal region -- which is exactly where "further up-table than the table goes"
     * ought to land.
     *
     * 30 m is well past any clamp the caller can reasonably apply, and small enough that the
     * arithmetic stays nowhere near overflow.
     */
    private static final double HORIZON = 30.0;

    private MouseAim() {}

    /**
     * Where the cursor's ray meets the horizontal plane at {@code planeY} metres.
     *
     * No state, and no dependence on where the blade currently is: the answer is a function of
     * the cursor and the camera alone. That matters more than it looks. Reading the cursor
     * against a plane whose height or depth was itself derived from the blade's position is a
     * loop WITH GAIN -- the blade creeps, the ray reads differently on the plane it just moved
     * to, and it creeps further, all the way to the stop. A fixed plane cannot do that.
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
            if (s <= 0) return fallback;                       // the plane is behind the camera
            if (horizontal > 1e-9) s = Math.min(s, HORIZON / horizontal);
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
