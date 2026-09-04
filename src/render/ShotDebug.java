package render;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import physics.Vec3;

import static render.Xform.SPM;

/**
 * Debug overlay for the assisted shot model (toggle with V).
 *
 * This is the instrument for tuning play.ShotAssist. On the last racket hit it draws, from the
 * contact point:
 *
 * <pre>
 *   yellow   the racket's own velocity        -- what the player actually did
 *   white    the incoming ball's velocity     -- what it had to work with
 *   grey     the raw physical reflection      -- what an exact bounce would have done
 *   cyan     the intended shot                -- contact -> the target the assist chose
 *   orange   the final constrained shot       -- what the ball actually left with (length = speed)
 *   magenta  the target                       -- a dot on the opponent's half
 *   green    the predicted landing            -- where the assist's own validator says it lands
 *   blue box the legal target area            -- an outline the target can never leave
 * </pre>
 *
 * Reading it: magenta and green apart means the solve did not land where it aimed; cyan and
 * orange apart means a clamp is fighting the solve; a green dot outside the table means the
 * validator gave up and the fallback shot is in play. The numeric readout (speed, spin,
 * correction passes, and whether the finished shot was legal) goes to the HUD.
 */
public final class ShotDebug {

    private final Group root = new Group();

    private final Cylinder racketLine   = line("#f5d442", 2.0);
    private final Cylinder incomingLine = line("#e8eef6", 2.0);
    private final Cylinder reflectLine  = line("#8a8f99", 2.5);
    private final Cylinder intendLine   = line("#39c6d6", 2.5);
    private final Cylinder finalLine    = line("#ff9a3c", 3.5);

    private final Sphere targetDot  = dot(7, "#e05cc8");
    private final Sphere landingDot = dot(6, "#5ce07a");

    private final Group box = new Group();          // the legal target area, outlined on the table

    private String readout = "";

    public ShotDebug() {
        root.getChildren().addAll(box, racketLine, incomingLine, reflectLine,
                                  intendLine, finalLine, targetDot, landingDot);
        root.setVisible(false);
    }

    public Group node() { return root; }
    public void setShown(boolean s) { root.setVisible(s); }
    public boolean isShown() { return root.isVisible(); }

    /** The numbers that do not draw as arrows -- for the HUD to print. */
    public String readout() { return readout; }

    /**
     * Feed it the assist's last-shot numbers. Velocities are in m/s and their arrows are drawn
     * to scale ({@code VEL_SCALE} metres of arrow per m/s); directions are unit vectors.
     */
    public void set(Vec3 contact, Vec3 racketVel, Vec3 incomingVel, Vec3 reflectDir,
                    Vec3 intendDir, Vec3 finalDir, Vec3 target, Vec3 landing,
                    double speed, Vec3 spin, int passes, boolean legal) {

        orient(racketLine,   contact, racketVel,   racketVel.length() * VEL_SCALE);
        orient(incomingLine, contact, incomingVel, incomingVel.length() * VEL_SCALE);
        orient(reflectLine,  contact, reflectDir,  0.45);
        orient(intendLine,   contact, intendDir,   0.55);
        orient(finalLine,    contact, finalDir,    speed * VEL_SCALE);

        Xform.place(targetDot, target.x(), 0.02, target.z());
        Xform.place(landingDot, landing.x(), 0.03, landing.z());

        // Spin is reported as rev/s about each axis: +x = topspin for a shot going -Z, +y = the
        // sidespin that curves it, +z = the corkscrew a brushing stroke leaves.
        double rev = 2 * Math.PI;
        readout = String.format(
                "shot  %.1f m/s   spin (%+.0f %+.0f %+.0f) rev/s   passes %d   %s",
                speed, spin.x() / rev, spin.y() / rev, spin.z() / rev, passes,
                legal ? "LEGAL" : "fallback");
    }

    /** Outline the box the assist is allowed to aim into. Drawn once, from ShotAssist's own
     *  accessors, so the overlay cannot drift away from the code it is checking. */
    public void setTargetArea(double halfWidth, double nearZ, double farZ, boolean towardPlayer) {
        double s = towardPlayer ? 1 : -1;
        double z0 = s * nearZ, z1 = s * farZ;
        box.getChildren().setAll(
                edge(-halfWidth, z0,  halfWidth, z0),
                edge(-halfWidth, z1,  halfWidth, z1),
                edge(-halfWidth, z0, -halfWidth, z1),
                edge( halfWidth, z0,  halfWidth, z1));
    }

    // ------------------------------------------------------------------ drawing

    /** Metres of arrow per m/s of velocity. Keeps the racket, incoming and final arrows on one
     *  scale, so their relative lengths mean something. */
    private static final double VEL_SCALE = 0.045;

    private static Cylinder line(String web, double radius) {
        Cylinder c = new Cylinder(radius, 100);
        c.setMaterial(new PhongMaterial(Color.web(web)));
        return c;
    }

    private static Sphere dot(double r, String web) {
        Sphere s = new Sphere(r);
        s.setMaterial(new PhongMaterial(Color.web(web)));
        return s;
    }

    /** One edge of the target-area outline, lying flat on the table. */
    private static Cylinder edge(double x0, double z0, double x1, double z1) {
        Cylinder c = line("#4a7fd6", 1.6);
        Vec3 from = new Vec3(x0, 0.01, z0);
        Vec3 dir = new Vec3(x1 - x0, 0, z1 - z0);
        orient(c, from, dir, dir.length());
        return c;
    }

    /** Lay a cylinder from {@code fromM} along {@code dirM} for {@code lenM} metres. The
     *  cylinder's local long axis is +Y, so it is rotated from +Y onto the scene-space
     *  direction and shifted to the segment's midpoint. */
    private static void orient(Cylinder c, Vec3 fromM, Vec3 dirM, double lenM) {
        Vec3 d = dirM.normalized();
        if (d.lengthSquared() < 1e-9 || lenM < 1e-4) { c.setVisible(false); return; }
        c.setVisible(true);

        double len = lenM * SPM;
        c.setHeight(len);

        // Linear part of the physics->scene map is diag(1, -1, -1); it carries directions too.
        double dx = d.x(), dy = -d.y(), dz = -d.z();
        double fx = fromM.x() * SPM, fy = -fromM.y() * SPM, fz = -fromM.z() * SPM;

        c.setTranslateX(fx + dx * len / 2);
        c.setTranslateY(fy + dy * len / 2);
        c.setTranslateZ(fz + dz * len / 2);

        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dy))));
        Point3D axis = (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6)
                ? Rotate.X_AXIS
                : new Point3D(dz, 0, -dx);     // (0,1,0) x (dx,dy,dz)
        c.getTransforms().setAll(new Rotate(angle, axis));
    }
}
