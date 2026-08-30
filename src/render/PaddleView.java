package render;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import physics.Paddle;
import physics.Vec3;

import static render.Xform.SPM;
import static physics.Constants.*;

/**
 * A racket: blade, rubber, handle.
 *
 * Follows the same shape as every other class in this package -- a Group, a node() accessor,
 * and an update() that pushes physics state into it once a frame. It never writes physics
 * state; the pose it draws is decided by Paddle and simply read here.
 *
 * Two JavaFX details that are easy to get wrong and expensive to debug:
 *
 * 1. A Cylinder is Y-axis aligned, so the blade needs a 90-degree base rotation to stand its
 *    axis up along +Z, which is the axis update() swings onto the face normal. That rotation
 *    goes on a CHILD group, because update() calls setAll() on this group's transform list
 *    every frame and would wipe out anything living there -- and the child wraps ONLY the
 *    discs. See the constructor for what happened when it wrapped the handle too.
 *
 * 2. Orienting the face is not a one-axis problem. A normal fixes two degrees of freedom and
 *    leaves the roll free, and the roll is what decides which way the handle hangs, so the
 *    frame has to be built rather than solved for. See update().
 *
 * Both directions are settled in SCENE space, after Xform. The physics-to-scene map flips two
 * axes, so it flips handedness with them: a cross product taken on the physics side of the
 * boundary and used on the scene side comes out mirrored.
 */
public final class PaddleView {

    /** Red rubber one side, black the other, as ITTF Law 2.4.6 requires. */
    private static final Color RUBBER_RED = Color.web("#b5241f");
    private static final Color RUBBER_BLACK = Color.web("#1b1d21");
    private static final Color BLADE_EDGE = Color.web("#c8a97a");
    private static final Color HANDLE = Color.web("#7b5730");

    private final Group group = new Group();

    public PaddleView(boolean redFacingAway) {
        // The blade: three flat cylinders sharing one axis -- the wooden edge, and a sheet of
        // rubber on each side of it.
        Cylinder blade = new Cylinder(BLADE_R * SPM, BLADE_THICK * SPM, 36);
        blade.setMaterial(matte(BLADE_EDGE));

        Cylinder front = face(redFacingAway ? RUBBER_RED : RUBBER_BLACK, +1);
        Cylinder back = face(redFacingAway ? RUBBER_BLACK : RUBBER_RED, -1);

        // Stand the Y-aligned cylinders up so the blade's axis is +Z.
        //
        // This wraps the discs and NOTHING ELSE. It used to sit on a group that held the
        // handle as well, and it rotated the handle with them: Rotate(90, X) maps (x, y, z)
        // to (x, -z, y), so the handle's downward offset (0, 0.12, 0) became (0, 0, 0.12)
        // and its own axis went from Y to Z. The handle came out as a spike through the face
        // of the bat, pointing down the table, at right angles to where a handle goes.
        Group discs = new Group(blade, front, back);
        discs.getTransforms().add(new Rotate(90, Rotate.X_AXIS));

        // Handle, hanging below the blade. Scene +Y is DOWN (see Xform), so a positive offset
        // is downward -- and staying OUT of the rotation above is what keeps it there.
        Cylinder handle = new Cylinder(0.014 * SPM, 0.10 * SPM, 12);
        handle.setMaterial(matte(HANDLE));
        handle.setTranslateY((BLADE_R + 0.045) * SPM);

        group.getChildren().addAll(discs, handle);
    }

    /** One rubber sheet, a hair proud of the blade so it does not z-fight with it. */
    private static Cylinder face(Color colour, int side) {
        Cylinder c = new Cylinder(BLADE_R * SPM * 0.97, BLADE_THICK * SPM * 0.35, 36);
        c.setMaterial(matte(colour));
        c.setTranslateY(side * BLADE_THICK * SPM * 0.5);
        return c;
    }

    public Group node() { return group; }

    public void setShown(boolean shown) { group.setVisible(shown); }

    /**
     * Push a pose onto the node. Position in metres, normal a unit vector in physics space.
     *
     * The frame is built outright rather than solved for as the shortest rotation from +Z
     * onto the normal. The shortest rotation is the obvious thing to reach for and it is
     * wrong here: it fixes where the FACE points and lets the roll fall where it may. The
     * opponent's blade faces back up the table, nearly 180 degrees from the player's, and
     * the shortest way to get there rolls the model over -- so its handle came out standing
     * straight up in the air. A player turning to face the other way turns about the
     * VERTICAL, and that is what has to be built:
     *
     *     model +Z -> the face normal
     *     model +Y -> down, less whatever part of down the face is already using
     *     model +X -> what is left, as X = Y x Z in a right-handed frame
     *
     * The three are unit and mutually orthogonal, so the matrix below is a proper rotation
     * (det = r . (d x n) = 1) and nothing gets scaled or mirrored.
     */
    public void update(Vec3 pos, Vec3 normal) {
        Xform.place(group, pos);

        Point3D n = Xform.toScene(normal).normalize();

        // Scene +Y is DOWN (see Xform), so this is the way the handle wants to hang.
        Point3D d = perpendicularPart(new Point3D(0, 1, 0), n);

        // A blade lying flat, facing straight up or down, has no "down" left in its plane and
        // the roll really is free. Hang the handle toward the near end instead, which is how
        // a player holds a bat out flat over the table.
        if (d.magnitude() < 1e-6) {
            d = perpendicularPart(Xform.toScene(new Vec3(0, 0, 1)), n);
        }
        d = d.normalize();

        Point3D r = d.crossProduct(n);

        group.getTransforms().setAll(new Affine(
                r.getX(), d.getX(), n.getX(), 0,
                r.getY(), d.getY(), n.getY(), 0,
                r.getZ(), d.getZ(), n.getZ(), 0));
    }

    /** The part of v lying in the plane of unit vector n. Not normalised: the caller has to
     *  check whether anything survived before it can be. */
    private static Point3D perpendicularPart(Point3D v, Point3D unitN) {
        return v.subtract(unitN.multiply(v.dotProduct(unitN)));
    }

    /** Convenience for driving straight off a Paddle. */
    public void update(Paddle paddle) {
        update(paddle.pos(), paddle.normal());
    }

    private static PhongMaterial matte(Color base) {
        PhongMaterial m = new PhongMaterial(base);
        m.setSpecularColor(base.interpolate(Color.WHITE, 0.25));
        m.setSpecularPower(22);
        return m;
    }
}
