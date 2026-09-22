package render;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import physics.Vec3;

import static physics.Constants.*;

/**
 * A racket: blade, rubbers, handle. The frame is built in SCENE space, after Xform, because the
 * physics-to-scene map flips handedness and a physics-side cross product would come out mirrored.
 */
public final class PaddleView {

    /** Red one side, black the other: ITTF Law 2.4.6. */
    private static final Color RUBBER_RED = Color.web("#b5241f");
    private static final Color RUBBER_BLACK = Color.web("#1b1d21");
    private static final Color BLADE_EDGE = Color.web("#c8a97a");
    private static final Color HANDLE = Color.web("#7b5730");

    private final Group group = new Group();

    public PaddleView(boolean redFacingAway) {
        Cylinder blade = new Cylinder(Xform.length(BLADE_R), Xform.length(BLADE_THICK), 36);
        blade.setMaterial(matte(BLADE_EDGE));
        Cylinder front = face(redFacingAway ? RUBBER_RED : RUBBER_BLACK, +1);
        Cylinder back = face(redFacingAway ? RUBBER_BLACK : RUBBER_RED, -1);

        // Stands the Y-aligned discs along +Z. Only the discs: rotating the handle too turned it
        // into a spike through the face.
        Group discs = new Group(blade, front, back);
        discs.getTransforms().add(new Rotate(90, Rotate.X_AXIS));

        Cylinder handle = new Cylinder(Xform.length(BALL_R * 0.7), Xform.length(BALL_R * 5), 12);
        handle.setMaterial(matte(HANDLE));
        handle.setTranslateY(Xform.length(BLADE_R + BALL_R * 2.25));   // scene +Y is down

        group.getChildren().addAll(discs, handle);
    }

    /** A hair proud of the blade, so it does not z-fight. */
    private static Cylinder face(Color colour, int side) {
        Cylinder c = new Cylinder(Xform.length(BLADE_R * 0.97), Xform.length(BLADE_THICK * 0.35), 36);
        c.setMaterial(SurfaceMaterials.rubber(colour));
        c.setTranslateY(Xform.length(side * BLADE_THICK * 0.5));
        return c;
    }

    public Group node() { return group; }

    /**
     * Built, not solved as the shortest rotation onto the normal, which lets the roll fall anywhere
     * (the opponent's handle once stood straight up). Model +Z is the normal, +Y hangs down within
     * the face, +X completes a right-handed frame: a proper rotation, never a mirror.
     */
    public void update(Vec3 pos, Vec3 normal) {
        Xform.place(group, pos);
        Point3D n = Xform.toScene(normal).normalize();
        Point3D d = handleDirection(n);
        Point3D r = d.crossProduct(n);
        group.getTransforms().setAll(new Affine(
                r.getX(), d.getX(), n.getX(), 0,
                r.getY(), d.getY(), n.getY(), 0,
                r.getZ(), d.getZ(), n.getZ(), 0));
    }

    /** Down within the face; a blade lying flat has no down left, so it hangs toward the near end. */
    private static Point3D handleDirection(Point3D n) {
        Point3D d = perpendicularPart(new Point3D(0, 1, 0), n);
        if (d.magnitude() < 1e-6) d = perpendicularPart(Xform.toScene(new Vec3(0, 0, 1)), n);
        return d.normalize();
    }

    private static Point3D perpendicularPart(Point3D v, Point3D unitN) {
        return v.subtract(unitN.multiply(v.dotProduct(unitN)));
    }

    private static PhongMaterial matte(Color base) {
        PhongMaterial m = new PhongMaterial(base);
        m.setSpecularColor(base.interpolate(Color.WHITE, 0.25));
        m.setSpecularPower(22);
        return m;
    }
}
