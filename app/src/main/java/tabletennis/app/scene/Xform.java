package tabletennis.app.scene;

import javafx.geometry.Point3D;
import javafx.scene.Node;
import javafx.scene.transform.Rotate;
import tabletennis.engine.math.Quat;
import tabletennis.engine.math.Vec3;

/**
 * The ONLY place physics space becomes scene space, in both directions. Physics is right-handed
 * with +Y up; JavaFX is left-handed with +Y down: sceneX = x*S, sceneY = -y*S, sceneZ = -z*S.
 */
public final class Xform {

    private Xform() {}

    /** Scene units per metre: JavaFX clip planes and light attenuation fall apart at metre scale. */
    public static final double ScenePerMetre = 300.0;

    /** Unsigned sizes: the same scale, no axis flip. */
    public static double Length(double Metres) { return Metres * ScenePerMetre; }

    public static double X(double Metres) { return  Metres * ScenePerMetre; }
    public static double Y(double Metres) { return -Metres * ScenePerMetre; }
    public static double Z(double Metres) { return -Metres * ScenePerMetre; }

    public static Point3D ToScene(Vec3 Point) {
        return new Point3D(X(Point.X()), Y(Point.Y()), Z(Point.Z()));
    }

    /** diag(1,-1,-1) is its own inverse up to the scale. */
    public static Vec3 ToPhysics(Point3D Point) {
        return new Vec3(Point.getX() / ScenePerMetre, -Point.getY() / ScenePerMetre, -Point.getZ() / ScenePerMetre);
    }

    public static void Place(Node Target, Vec3 Point) {
        Place(Target, Point.X(), Point.Y(), Point.Z());
    }

    public static void Place(Node Target, double X, double Y, double Z) {
        Target.setTranslateX(X(X));
        Target.setTranslateY(Y(Y));
        Target.setTranslateZ(Z(Z));
    }

    /** The map is a 180-degree rotation about X: same angle, axis with Y and Z flipped. */
    public static Rotate ToRotate(Quat Orientation) {
        Vec3 Axis = Orientation.Axis();
        return new Rotate(Math.toDegrees(Orientation.Angle()), new Point3D(Axis.X(), -Axis.Y(), -Axis.Z()));
    }
}
