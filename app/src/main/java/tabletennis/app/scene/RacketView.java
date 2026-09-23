package tabletennis.app.scene;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import tabletennis.engine.BallSpec;
import tabletennis.engine.RacketSpec;
import tabletennis.engine.contact.BladeCollider;
import tabletennis.engine.math.Vec3;

/**
 * A racket: blade, rubbers and handle. The frame is built in SCENE space, after Xform, because the
 * physics-to-scene map flips handedness and a physics-side cross product would come out mirrored.
 */
final class RacketView {

    /** Red one side, black the other: ITTF Law 2.4.6. The black side faces the net. */
    private static final Color RubberRed = Color.web("#b5241f");
    private static final Color RubberBlack = Color.web("#1b1d21");
    private static final Color BladeEdge = Color.web("#c8a97a");
    private static final Color HandleColour = Color.web("#7b5730");
    private static final int DiscDivisions = 36;
    private static final int HandleDivisions = 12;

    private final Group Root = new Group();

    RacketView() {
        Cylinder Blade = new Cylinder(Xform.Length(RacketSpec.BladeRadius), Xform.Length(RacketSpec.BladeThickness),
                                      DiscDivisions);
        Blade.setMaterial(Matte(BladeEdge));

        // Stands the Y-aligned discs along +Z. Only the discs: rotating the handle too turned it
        // into a spike through the face.
        Group Discs = new Group(Blade, Face(RubberBlack, +1), Face(RubberRed, -1));
        Discs.getTransforms().add(new Rotate(90, Rotate.X_AXIS));

        Cylinder Handle = new Cylinder(Xform.Length(BallSpec.Radius * 0.7), Xform.Length(BallSpec.Radius * 5),
                                       HandleDivisions);
        Handle.setMaterial(Matte(HandleColour));
        Handle.setTranslateY(Xform.Length(RacketSpec.BladeRadius + BallSpec.Radius * 2.25));   // scene +Y is down

        Root.getChildren().addAll(Discs, Handle);
    }

    Group Node() { return Root; }

    /** Between two step poses, so the blade moves as smoothly as the ball. */
    void Update(BladeCollider From, BladeCollider To, double Fraction) {
        // The normal is lerped, not slerped: under a degree per step, the two agree to 1e-6.
        Pose(Vec3.Lerp(From.Centre(), To.Centre(), Fraction),
             Vec3.Lerp(From.Normal(), To.Normal(), Fraction).Normalized());
    }

    /**
     * Built, not solved as the shortest rotation onto the normal, which lets the roll fall anywhere
     * (the opponent's handle once stood straight up). Model +Z is the normal, +Y hangs down within
     * the face, +X completes a right-handed frame: a proper rotation, never a mirror.
     */
    private void Pose(Vec3 Centre, Vec3 Normal) {
        Xform.Place(Root, Centre);
        Point3D Out = Xform.ToScene(Normal).normalize();
        Point3D Down = HandleDirection(Out);
        Point3D Across = Down.crossProduct(Out);
        Root.getTransforms().setAll(new Affine(
                Across.getX(), Down.getX(), Out.getX(), 0,
                Across.getY(), Down.getY(), Out.getY(), 0,
                Across.getZ(), Down.getZ(), Out.getZ(), 0));
    }

    /** Down within the face; a blade lying flat has no down left, so it hangs toward the near end. */
    private static Point3D HandleDirection(Point3D Out) {
        Point3D Down = PerpendicularPart(new Point3D(0, 1, 0), Out);
        if (Down.magnitude() < 1e-6) Down = PerpendicularPart(Xform.ToScene(new Vec3(0, 0, 1)), Out);
        return Down.normalize();
    }

    private static Point3D PerpendicularPart(Point3D Vector, Point3D UnitNormal) {
        return Vector.subtract(UnitNormal.multiply(Vector.dotProduct(UnitNormal)));
    }

    /** A hair proud of the blade, so it does not z-fight. */
    private static Cylinder Face(Color Colour, int Side) {
        Cylinder Rubber = new Cylinder(Xform.Length(RacketSpec.BladeRadius * 0.97),
                                       Xform.Length(RacketSpec.BladeThickness * 0.35), DiscDivisions);
        Rubber.setMaterial(Textures.Rubber(Colour));
        Rubber.setTranslateY(Xform.Length(Side * RacketSpec.BladeThickness * 0.5));
        return Rubber;
    }

    private static PhongMaterial Matte(Color Base) {
        PhongMaterial Finish = new PhongMaterial(Base);
        Finish.setSpecularColor(Base.interpolate(Color.WHITE, 0.25));
        Finish.setSpecularPower(22);
        return Finish;
    }
}
