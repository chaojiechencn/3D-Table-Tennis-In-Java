package render;

import javafx.geometry.Point3D;
import javafx.scene.Camera;
import javafx.scene.SubScene;
import physics.Vec3;

import static physics.Constants.*;

/**
 * Turns a mouse position into the point the player's blade should stand on.
 *
 * The problem: the cursor is two numbers on a screen and the paddle needs three in metres.
 * The naive fix is to map the cursor's fraction across the window straight onto a rectangle of
 * table, which is one line of code and feels correct from exactly one camera angle -- orbit
 * away from behind the near end and the paddle starts moving sideways when you move the mouse
 * up. So this does the real thing: build the ray the cursor points along, and intersect it
 * with the world. That works from any preset view and any orbit, because it asks the camera
 * where it actually is rather than assuming.
 *
 * It is a SURFACE, not a plane. A plane behind the end line cannot reach a short ball that dies
 * over the table, and letting the blade reach for such a ball BY ITSELF is the auto-follow that
 * was removed -- so the reach has to come off the cursor. It does, geometrically:
 *
 *     the blade stands where the cursor's ray meets the table, and falls back to the rest plane
 *     only when the ray is not pointing at the near half of the table at all.
 *
 * Point at your own end line and the blade is where a fixed plane would have put it; slide the
 * cursor up-table and the blade walks out over the table with it, riding at blade height; keep
 * going and it comes back, rising, to meet a high or deep ball. One continuous curve, monotone
 * in the cursor, and every point of it is UNDER THE CURSOR -- which is what makes any depth
 * choice honest: the blade appears exactly where you are pointing, whatever depth this picks.
 * Nothing here has ever seen the ball.
 *
 * (Reaching in being an UP-screen gesture is not a choice. From a camera behind the near end, a
 * blade reaching in is further away and therefore higher on screen; no depth mapping can make
 * reaching in a downward motion. The gesture is "point at the ball", and a short ball you have
 * to reach for is up-screen.)
 *
 * All the ray work happens in scene units and the result is converted once, through
 * {@link Xform}, which stays the only place the two spaces meet.
 */
public final class MouseAim {

    /**
     * Where the player's paddle rests, in metres: just behind the near end of the table.
     *
     * The near edge is at z = +1.37, so this sits 20 cm behind it -- close enough to reach a
     * ball that has crossed the end line, far enough back that the blade is not permanently
     * inside the table.
     *
     * It was 12 cm, and 12 cm is wrong for a specific reason worth recording. The demo's feed
     * shots launch from z = 1.52 (Shots.FROM), so a plane at 1.49 sat directly in FRONT of
     * them: every feed crossed it on its first step and rebounded off the player's own bat
     * before it had gone anywhere. At 1.57 the plane is BEHIND the launch point, so a feed
     * flies away from the blade instead of into it -- the ball's centre starts 5.0 cm off the
     * plane against the 2.75 cm (half blade thickness + ball radius) it takes to touch.
     *
     * The extra 8 cm costs about 8 ms of flight and a centimetre of drop on a 10 m/s return,
     * which is nothing against the 83 cm of height the blade is allowed to cover.
     */
    public static final double PLAYER_PLANE_Z = TABLE_LENGTH / 2 + 0.20;

    /**
     * How far the blade may stray from the table. Wide, so a ball driven to the corner can
     * still be chased, but the bottom stays ON the table surface -- the blade dipping below
     * the top looked like a bug and was one.
     */
    private static final double MAX_X = TABLE_WIDTH / 2 + 1.0;
    private static final double MAX_Y = 1.40;     // stretch for a high one

    /**
     * The floor, and it is BLADE_R rather than the ball's resting height for a reason worth
     * writing down, because the obvious value is wrong twice over.
     *
     * This bounds the blade's CENTRE. The blade is a disc of radius BLADE_R, so a centre at
     * the ball's 0.02 m puts 5.5 cm of blade underneath the table top -- visibly through it,
     * which is exactly the bug this constant was last changed to fix and did not. A centre at
     * BLADE_R rests the bottom rim on the surface instead.
     *
     * It costs nothing in reach: the ball at rest sits 2 cm up and the disc still spans from
     * 0 to 15 cm, so the lower half of the blade covers a ball scraping the table.
     *
     * It is also the height the reach surface rides at, which is the same statement: the blade
     * skims the table on its way in.
     */
    private static final double MIN_Y = BLADE_R;

    /**
     * How far in over the table the cursor may take the blade, in metres from the rest plane.
     *
     * 1.10 m puts a fully extended blade at z = +0.47 -- past the middle of the player's own
     * half and short of the net, which is about as far in as a player leaning over the table
     * gets. The old ball-driven reach used 1.20 m, so this is the same envelope; the difference
     * is who decides to enter it.
     */
    private static final double REACH_IN = 1.10;

    /**
     * How far past the reach limit the aim travels before the blade is all the way back on the
     * rest plane, in metres of table.
     *
     * This is the "step back for a high one" half of the curve. Once the cursor points beyond
     * the blade's furthest reach, the ray crosses table height further and further down-table,
     * and the blade retreats along that scale instead of sticking at full stretch with nowhere
     * to go. 1.50 m spends the retreat over roughly the far half of the table, which is where
     * you are looking when a lob or a deep ball is coming. It only sets how QUICKLY the blade
     * comes back -- it stays under the cursor either way.
     */
    private static final double STEP_BACK_SPAN = 1.50;

    private MouseAim() {}

    /**
     * Where the blade should stand for this cursor position: a point on the reach surface.
     *
     * Two intersections of one ray, and no state. The answer depends on the cursor and the
     * camera and on nothing else, which is what keeps a feedback loop out of it: reading the
     * cursor on the plane the blade currently occupies, while the blade's depth is itself
     * derived from the cursor, is a loop WITH GAIN -- the blade creeps forward, the ray reads
     * lower on the plane it just moved to, and it creeps further, all the way to full stretch.
     * Depth is solved from the ray alone; x and y are then read on the plane that solve chose.
     *
     * @param sub      the SubScene the 3D world is drawn in
     * @param mouseX   cursor position within that SubScene
     * @param mouseY   cursor position within that SubScene
     * @param fallback returned unchanged if the ray degenerates -- parallel to the rest plane,
     *                 or pointing away from it, which the TOP view can produce
     */
    public static Vec3 onReachSurface(SubScene sub, double mouseX, double mouseY, Vec3 fallback) {
        Camera cam = sub.getCamera();
        if (cam == null) return fallback;

        Ray ray = ray(cam, sub, mouseX, mouseY);
        if (ray == null) return fallback;

        // 1. Where does the aim cross the height the blade rides at? That is the spot on the
        //    table being pointed at, and it is the entire depth signal.
        double zTable = crossesHeightAt(ray, MIN_Y);

        // 2. Turn that into a depth. Within reach: stand on it. Behind the rest plane -- which
        //    is what pointing at your own end line gives -- the clamp puts the blade exactly
        //    where the old fixed plane had it. Beyond the reach, or no crossing at all (a level
        //    or rising aim, i.e. looking up the table): retreat to the rest plane.
        double nearest = PLAYER_PLANE_Z - REACH_IN;
        double wantZ;
        if (Double.isNaN(zTable)) {
            wantZ = PLAYER_PLANE_Z;
        } else {
            double reach = clamp(zTable, nearest, PLAYER_PLANE_Z);
            double back = clamp((nearest - zTable) / STEP_BACK_SPAN, 0, 1);
            wantZ = reach + (PLAYER_PLANE_Z - reach) * back;
        }

        // 3. Read x and y on the plane that depth chose, so the blade lands under the cursor.
        return onPlane(ray, wantZ, fallback);
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

    /**
     * Where the ray crosses a plane of constant physics-Z, clamped into the blade's bounds.
     *
     * In scene units that is a plane of constant scene-Z: the map is diagonal, so planes stay
     * planes and axes stay axes.
     */
    private static Vec3 onPlane(Ray ray, double planeZ, Vec3 fallback) {
        double planeSceneZ = Xform.z(planeZ);
        if (Math.abs(ray.dir().getZ()) < 1e-9) return fallback;

        double t = (planeSceneZ - ray.eye().getZ()) / ray.dir().getZ();
        if (t <= 0) return fallback;              // the plane is behind the camera

        Vec3 m = Xform.toPhysics(ray.eye().add(ray.dir().multiply(t)));

        return new Vec3(clamp(m.x(), -MAX_X, MAX_X),
                        clamp(m.y(), MIN_Y, MAX_Y),
                        planeZ);
    }

    /**
     * The physics-Z at which the ray descends through a given height, or NaN if it never does:
     * a level or rising aim, or one that only gets there behind the camera.
     *
     * Scene Y points DOWN (see {@link Xform}), so descending in physics is INCREASING in scene
     * Y -- which is why the sign test below reads backwards and must stay that way.
     */
    private static double crossesHeightAt(Ray ray, double height) {
        if (ray.dir().getY() <= 1e-9) return Double.NaN;      // level, or looking upward

        double t = (Xform.y(height) - ray.eye().getY()) / ray.dir().getY();
        if (t <= 0) return Double.NaN;                        // that height is behind us

        return Xform.toPhysics(ray.eye().add(ray.dir().multiply(t))).z();
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
