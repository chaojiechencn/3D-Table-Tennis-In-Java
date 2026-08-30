package render;

import javafx.geometry.Point3D;
import javafx.scene.Node;
import javafx.scene.transform.Rotate;
import physics.Quat;
import physics.Vec3;

/**
 * The ONE place physics space becomes scene space. Nothing else in the program may do this.
 *
 * Physics is metres, right-handed, +Y up, +Z toward the near end. JavaFX is left-handed with
 * +Y DOWN and +Z into the screen. So:
 *
 *     sceneX = +x * SPM
 *     sceneY = -y * SPM
 *     sceneZ = -z * SPM
 *
 * Scattering that conversion across the renderer is how a project ends up with a ball that
 * curves the wrong way and a "fix" that flips a sign in the physics to compensate. Keeping it
 * here means the physics stays the authority on which way is up.
 */
public final class Xform {

    private Xform() {}

    /**
     * Scene units per metre.
     *
     * JavaFX is not happy doing 3D at metre scale: the default camera near/far clip planes are
     * 0.1 and 100, so a 2.74 m table would sit almost entirely inside the near plane, and
     * point-light attenuation is tuned for values in the hundreds. Working at 300 units per
     * metre puts the whole scene in the range JavaFX expects. It is a rendering detail and it
     * stops at this class.
     */
    public static final double SPM = 300.0;

    public static double x(double metres) { return  metres * SPM; }
    public static double y(double metres) { return -metres * SPM; }
    public static double z(double metres) { return -metres * SPM; }

    public static Point3D toScene(Vec3 v) {
        return new Point3D(x(v.x()), y(v.y()), z(v.z()));
    }

    /**
     * Scene space back to physics space.
     *
     * The only reason an inverse exists at all: a mouse position is a fact about the SCREEN,
     * and driving a paddle with it means turning it back into metres. Doing that here keeps
     * the class comment above true -- the conversion still happens in exactly one place, it
     * just now runs in both directions. Nowhere else may divide by SPM.
     *
     * The map is its own inverse up to the scale, since diag(1,-1,-1) squares to the identity.
     */
    public static Vec3 toPhysics(Point3D p) {
        return new Vec3(p.getX() / SPM, -p.getY() / SPM, -p.getZ() / SPM);
    }

    /** Move a node to a point given in physics metres. */
    public static void place(Node node, Vec3 p) {
        node.setTranslateX(x(p.x()));
        node.setTranslateY(y(p.y()));
        node.setTranslateZ(z(p.z()));
    }

    public static void place(Node node, double mx, double my, double mz) {
        node.setTranslateX(x(mx));
        node.setTranslateY(y(my));
        node.setTranslateZ(z(mz));
    }

    /**
     * Convert a physics rotation into the equivalent JavaFX one.
     *
     * The physics-to-scene map is diag(1,-1,-1), which is a 180-degree rotation about X and
     * therefore a proper rotation. Conjugating a rotation by a rotation gives the same angle
     * about the transformed axis, so the axis flips its Y and Z and the angle is unchanged.
     * That is the whole derivation, and it is why the ball tumbles the way the spin vector
     * says it should instead of mirrored.
     */
    public static Rotate toRotate(Quat q) {
        Vec3 axis = q.axis();
        return new Rotate(Math.toDegrees(q.angle()),
                          new Point3D(axis.x(), -axis.y(), -axis.z()));
    }
}
