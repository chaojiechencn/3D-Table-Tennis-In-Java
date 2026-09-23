package tabletennis.app.render;

import javafx.geometry.Point3D;
import javafx.scene.Node;
import javafx.scene.transform.Rotate;
import tabletennis.engine.Quat;
import tabletennis.engine.Vec3;

/**
 * The ONLY place physics space becomes scene space, in both directions. Physics is right-handed
 * with +Y up; JavaFX is left-handed with +Y down: sceneX = x*SPM, sceneY = -y*SPM, sceneZ = -z*SPM.
 */
public final class Xform {

    private Xform() {}

    /** JavaFX clip planes and light attenuation fall apart at metre scale. */
    public static final double Spm = 300.0;

    /** Unsigned sizes: the same scale, no axis flip. */
    public static double Length(double Metres) { return Metres * Spm; }

    public static double X(double Metres) { return  Metres * Spm; }
    public static double Y(double Metres) { return -Metres * Spm; }
    public static double Z(double Metres) { return -Metres * Spm; }

    public static Point3D ToScene(Vec3 V) {
        return new Point3D(X(V.X()), Y(V.Y()), Z(V.Z()));
    }

    /** diag(1,-1,-1) is its own inverse up to the scale. */
    public static Vec3 ToPhysics(Point3D P) {
        return new Vec3(P.getX() / Spm, -P.getY() / Spm, -P.getZ() / Spm);
    }

    public static void Place(Node Target, Vec3 P) {
        Place(Target, P.X(), P.Y(), P.Z());
    }

    public static void Place(Node Target, double Mx, double My, double Mz) {
        Target.setTranslateX(X(Mx));
        Target.setTranslateY(Y(My));
        Target.setTranslateZ(Z(Mz));
    }

    /** The map is a 180-degree rotation about X: same angle, axis with Y and Z flipped. */
    public static Rotate ToRotate(Quat Q) {
        Vec3 Axis = Q.Axis();
        return new Rotate(Math.toDegrees(Q.Angle()),
                          new Point3D(Axis.X(), -Axis.Y(), -Axis.Z()));
    }
}
