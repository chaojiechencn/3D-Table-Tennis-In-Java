package render;

import javafx.geometry.Point3D;
import javafx.scene.Camera;
import javafx.scene.SubScene;
import physics.Vec3;

import static physics.Constants.*;

/**
 * Turns a mouse position into a point on the player's hitting plane.
 *
 * The problem: the cursor is two numbers on a screen and the paddle needs three in metres.
 * The naive fix is to map the cursor's fraction across the window straight onto a rectangle of
 * table, which is one line of code and feels correct from exactly one camera angle -- orbit
 * away from behind the near end and the paddle starts moving sideways when you move the mouse
 * up. So this does the real thing: build the ray the cursor points along, and intersect it
 * with the plane the paddle lives on. That works from any preset view and any orbit, because
 * it asks the camera where it actually is rather than assuming.
 *
 * All the ray work happens in scene units and the result is converted once, through
 * {@link Xform}, which stays the only place the two spaces meet.
 */
public final class MouseAim {

    /**
     * Where the player's paddle lives, in metres: just behind the near end of the table.
     *
     * The near edge is at z = +1.37, so this sits 12 cm behind it -- close enough to reach a
     * ball that has crossed the end line, far enough back that the blade is not permanently
     * inside the table.
     */
    public static final double PLAYER_PLANE_Z = TABLE_LENGTH / 2 + 0.12;

    /** How far the blade may stray, so a wild mouse cannot park the paddle in the ceiling. */
    private static final double MAX_X = TABLE_WIDTH / 2 + 0.45;
    private static final double MIN_Y = 0.02;
    private static final double MAX_Y = 0.85;

    private MouseAim() {}

    /**
     * The point on the hitting plane that the cursor is pointing at.
     *
     * @param sub    the SubScene the 3D world is drawn in
     * @param mouseX cursor position within that SubScene
     * @param mouseY cursor position within that SubScene
     * @param fallback returned unchanged if the ray runs parallel to the plane, which happens
     *                 when the camera is looking along it from the TOP view
     */
    public static Vec3 onPlayerPlane(SubScene sub, double mouseX, double mouseY, Vec3 fallback) {
        Camera cam = sub.getCamera();
        if (cam == null) return fallback;

        // The camera is a PerspectiveCamera with fixedEyeAtCameraZero, so the eye is the
        // camera's own origin and the view direction is +Z in ITS local space. Asking the
        // node for those two points in scene coordinates is what makes this work at any orbit
        // angle: the gimbal's rotations are already baked into the transform.
        Point3D eye = cam.localToScene(0, 0, 0);

        double halfH = Math.tan(Math.toRadians(cam instanceof javafx.scene.PerspectiveCamera pc
                                               ? pc.getFieldOfView() / 2 : 20));
        double w = sub.getWidth(), h = sub.getHeight();
        if (w <= 0 || h <= 0) return fallback;

        // JavaFX measures field of view along the SHORTER side of the viewport.
        double scale = Math.min(w, h) / 2.0;
        double localX = (mouseX - w / 2) / scale * halfH;
        double localY = (mouseY - h / 2) / scale * halfH;

        Point3D through = cam.localToScene(localX, localY, 1);
        Point3D dir = through.subtract(eye);

        // Intersect with the plane of constant physics-Z, which in scene units is a plane of
        // constant scene-Z (the map is diagonal, so planes stay planes and axes stay axes).
        double planeSceneZ = Xform.z(PLAYER_PLANE_Z);
        if (Math.abs(dir.getZ()) < 1e-9) return fallback;

        double t = (planeSceneZ - eye.getZ()) / dir.getZ();
        if (t <= 0) return fallback;              // the plane is behind the camera

        Point3D hit = eye.add(dir.multiply(t));
        Vec3 m = Xform.toPhysics(hit);

        return new Vec3(clamp(m.x(), -MAX_X, MAX_X),
                        clamp(m.y(), MIN_Y, MAX_Y),
                        PLAYER_PLANE_Z);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
