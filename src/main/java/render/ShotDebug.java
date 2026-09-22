package render;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import physics.Vec3;

/**
 * The V overlay for tuning play.ShotAssist, drawn from the last contact: racket velocity (yellow),
 * incoming ball (white), raw reflection (grey), intended shot (cyan), final shot (orange, length =
 * speed), target (magenta), predicted landing (green) and the legal target box (blue).
 */
public final class ShotDebug {

    /** Metres of arrow per m/s, shared so the velocity arrows compare. */
    private static final double VEL_SCALE = 0.045;

    private final Group root = new Group();
    private final Cylinder racketLine   = line("#f5d442", 2.0);
    private final Cylinder incomingLine = line("#e8eef6", 2.0);
    private final Cylinder reflectLine  = line("#8a8f99", 2.5);
    private final Cylinder intendLine   = line("#39c6d6", 2.5);
    private final Cylinder finalLine    = line("#ff9a3c", 3.5);
    private final Sphere targetDot  = dot(7, "#e05cc8");
    private final Sphere landingDot = dot(6, "#5ce07a");
    private final Group box = new Group();
    private String readout = "";

    public ShotDebug() {
        root.getChildren().addAll(box, racketLine, incomingLine, reflectLine,
                                  intendLine, finalLine, targetDot, landingDot);
        root.setVisible(false);
    }

    public Group node() { return root; }
    public void setShown(boolean s) { root.setVisible(s); }
    public boolean isShown() { return root.isVisible(); }

    /** The numbers that do not draw as arrows, for the HUD. */
    public String readout() { return readout; }

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

        double rev = 2 * Math.PI;
        readout = String.format(
                "shot  %.1f m/s   spin (%+.0f %+.0f %+.0f) rev/s   passes %d   %s",
                speed, spin.x() / rev, spin.y() / rev, spin.z() / rev, passes,
                legal ? "LEGAL" : "fallback");
    }

    /** Drawn from ShotAssist's own accessors, so the outline cannot drift from the code. */
    public void setTargetArea(double halfWidth, double nearZ, double farZ, boolean towardPlayer) {
        double s = towardPlayer ? 1 : -1;
        double z0 = s * nearZ, z1 = s * farZ;
        box.getChildren().setAll(
                edge(-halfWidth, z0,  halfWidth, z0),
                edge(-halfWidth, z1,  halfWidth, z1),
                edge(-halfWidth, z0, -halfWidth, z1),
                edge( halfWidth, z0,  halfWidth, z1));
    }

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

    private static Cylinder edge(double x0, double z0, double x1, double z1) {
        Cylinder c = line("#4a7fd6", 1.6);
        Vec3 dir = new Vec3(x1 - x0, 0, z1 - z0);
        orient(c, new Vec3(x0, 0.01, z0), dir, dir.length());
        return c;
    }

    /** Lay a +Y-aligned cylinder from fromM along dirM for lenM metres. */
    private static void orient(Cylinder c, Vec3 fromM, Vec3 dirM, double lenM) {
        Vec3 d = dirM.normalized();
        if (d.lengthSquared() < 1e-9 || lenM < 1e-4) { c.setVisible(false); return; }
        c.setVisible(true);

        double len = Xform.length(lenM);
        c.setHeight(len);
        Point3D direction = Xform.toScene(d).normalize();
        double dx = direction.getX(), dy = direction.getY(), dz = direction.getZ();
        Point3D start = Xform.toScene(fromM);
        c.setTranslateX(start.getX() + dx * len / 2);
        c.setTranslateY(start.getY() + dy * len / 2);
        c.setTranslateZ(start.getZ() + dz * len / 2);

        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dy))));
        boolean vertical = Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6;
        Point3D axis = vertical ? Rotate.X_AXIS : new Point3D(dz, 0, -dx);   // (0,1,0) x d
        c.getTransforms().setAll(new Rotate(angle, axis));
    }
}
