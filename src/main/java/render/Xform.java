package render;

import javafx.geometry.Point3D;
import javafx.scene.Node;
import javafx.scene.transform.Rotate;
import physics.Quat;
import physics.Vec3;

/**
 * The ONLY place physics space becomes scene space, in both directions. Physics is right-handed
 * with +Y up; JavaFX is left-handed with +Y down: sceneX = x*SPM, sceneY = -y*SPM, sceneZ = -z*SPM.
 */
public final class Xform {

    private Xform() {}

    /** JavaFX clip planes and light attenuation fall apart at metre scale. */
    public static final double SPM = 300.0;

    /** Unsigned sizes: the same scale, no axis flip. */
    public static double length(double metres) { return metres * SPM; }

    public static double x(double metres) { return  metres * SPM; }
    public static double y(double metres) { return -metres * SPM; }
    public static double z(double metres) { return -metres * SPM; }

    public static Point3D toScene(Vec3 v) {
        return new Point3D(x(v.x()), y(v.y()), z(v.z()));
    }

    /** diag(1,-1,-1) is its own inverse up to the scale. */
    public static Vec3 toPhysics(Point3D p) {
        return new Vec3(p.getX() / SPM, -p.getY() / SPM, -p.getZ() / SPM);
    }

    public static void place(Node node, Vec3 p) {
        place(node, p.x(), p.y(), p.z());
    }

    public static void place(Node node, double mx, double my, double mz) {
        node.setTranslateX(x(mx));
        node.setTranslateY(y(my));
        node.setTranslateZ(z(mz));
    }

    /** The map is a 180-degree rotation about X: same angle, axis with Y and Z flipped. */
    public static Rotate toRotate(Quat q) {
        Vec3 axis = q.axis();
        return new Rotate(Math.toDegrees(q.angle()),
                          new Point3D(axis.x(), -axis.y(), -axis.z()));
    }
}
