package tabletennis.app.render;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import tabletennis.engine.Vec3;

import static tabletennis.engine.Constants.*;

/**
 * A racket: blade, rubbers, handle. The frame is built in SCENE space, after Xform, because the
 * physics-to-scene map flips handedness and a physics-side cross product would come out mirrored.
 */
public final class PaddleView {

    /** Red one side, black the other: ITTF Law 2.4.6. */
    private static final Color RubberRed = Color.web("#b5241f");
    private static final Color RubberBlack = Color.web("#1b1d21");
    private static final Color BladeEdge = Color.web("#c8a97a");
    private static final Color HandleColour = Color.web("#7b5730");

    private final Group Root = new Group();

    public PaddleView(boolean RedFacingAway) {
        Cylinder Blade = new Cylinder(Xform.Length(BladeR), Xform.Length(BladeThick), 36);
        Blade.setMaterial(Matte(BladeEdge));
        Cylinder Front = Face(RedFacingAway ? RubberRed : RubberBlack, +1);
        Cylinder Back = Face(RedFacingAway ? RubberBlack : RubberRed, -1);

        // Stands the Y-aligned discs along +Z. Only the discs: rotating the handle too turned it
        // into a spike through the face.
        Group Discs = new Group(Blade, Front, Back);
        Discs.getTransforms().add(new Rotate(90, Rotate.X_AXIS));

        Cylinder Handle = new Cylinder(Xform.Length(BallR * 0.7), Xform.Length(BallR * 5), 12);
        Handle.setMaterial(Matte(HandleColour));
        Handle.setTranslateY(Xform.Length(BladeR + BallR * 2.25));   // scene +Y is down

        Root.getChildren().addAll(Discs, Handle);
    }

    /** A hair proud of the blade, so it does not z-fight. */
    private static Cylinder Face(Color Colour, int Side) {
        Cylinder C = new Cylinder(Xform.Length(BladeR * 0.97), Xform.Length(BladeThick * 0.35), 36);
        C.setMaterial(SurfaceMaterials.Rubber(Colour));
        C.setTranslateY(Xform.Length(Side * BladeThick * 0.5));
        return C;
    }

    public Group Node() { return Root; }

    /**
     * Built, not solved as the shortest rotation onto the normal, which lets the roll fall anywhere
     * (the opponent's handle once stood straight up). Model +Z is the normal, +Y hangs down within
     * the face, +X completes a right-handed frame: a proper rotation, never a mirror.
     */
    public void Update(Vec3 Pos, Vec3 Normal) {
        Xform.Place(Root, Pos);
        Point3D N = Xform.ToScene(Normal).normalize();
        Point3D D = HandleDirection(N);
        Point3D R = D.crossProduct(N);
        Root.getTransforms().setAll(new Affine(
                R.getX(), D.getX(), N.getX(), 0,
                R.getY(), D.getY(), N.getY(), 0,
                R.getZ(), D.getZ(), N.getZ(), 0));
    }

    /** Down within the face; a blade lying flat has no down left, so it hangs toward the near end. */
    private static Point3D HandleDirection(Point3D N) {
        Point3D D = PerpendicularPart(new Point3D(0, 1, 0), N);
        if (D.magnitude() < 1e-6) D = PerpendicularPart(Xform.ToScene(new Vec3(0, 0, 1)), N);
        return D.normalize();
    }

    private static Point3D PerpendicularPart(Point3D V, Point3D UnitN) {
        return V.subtract(UnitN.multiply(V.dotProduct(UnitN)));
    }

    private static PhongMaterial Matte(Color Base) {
        PhongMaterial M = new PhongMaterial(Base);
        M.setSpecularColor(Base.interpolate(Color.WHITE, 0.25));
        M.setSpecularPower(22);
        return M;
    }
}
