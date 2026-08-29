package render;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import physics.Constants;

import static physics.Constants.*;
import static render.Xform.SPM;

/**
 * The table, the net, the floor and the room, built to ITTF dimensions.
 *
 * Every measurement here comes from {@link Constants}, the same numbers the physics collides
 * against. Nothing is sized by eye. If the rendered table and the collision boxes could drift
 * apart, the demo would be showing a ball bouncing off thin air, which is exactly the failure
 * mode a physics demo cannot afford.
 */
public final class Court {

    /** ITTF line width, 2 cm on the sides and ends. */
    private static final double LINE_W = 0.02;

    /** The centre line for doubles, 3 mm. */
    private static final double CENTRE_LINE_W = 0.003;

    /** How far above the surface the markings sit. Enough to beat depth-buffer fighting at
     *  this scale, small enough that the ball never visibly lands on top of a line. */
    private static final double LINE_LIFT = 0.0012;

    private static final Color TABLE_BLUE = Color.web("#12507f");
    private static final Color LINE_WHITE = Color.web("#f2f5f7");
    private static final Color FLOOR_GREY = Color.web("#1d2128");

    private Court() {}

    /** Everything static in the scene. */
    public static Group build() {
        Group g = new Group();
        g.getChildren().addAll(floor(), tableTop(), markings(), legs(), net());
        return g;
    }

    // ------------------------------------------------------------------ table

    private static Box tableTop() {
        Box top = new Box(TABLE_WIDTH * SPM, TABLE_THICK * SPM, TABLE_LENGTH * SPM);
        top.setMaterial(matte(TABLE_BLUE, 12));
        // The collision box has its TOP face on y = 0, so the centre sits half a thickness
        // below. Same arithmetic as World.TABLE, deliberately.
        Xform.place(top, 0, -TABLE_THICK / 2, 0);
        return top;
    }

    private static Group markings() {
        Group g = new Group();
        double halfW = TABLE_WIDTH / 2, halfL = TABLE_LENGTH / 2;

        // Side lines, running the length of the table.
        g.getChildren().add(line(LINE_W, TABLE_LENGTH, -halfW + LINE_W / 2, 0));
        g.getChildren().add(line(LINE_W, TABLE_LENGTH,  halfW - LINE_W / 2, 0));

        // End lines, across each end.
        g.getChildren().add(line(TABLE_WIDTH, LINE_W, 0, -halfL + LINE_W / 2));
        g.getChildren().add(line(TABLE_WIDTH, LINE_W, 0,  halfL - LINE_W / 2));

        // The doubles centre line, full length.
        g.getChildren().add(line(CENTRE_LINE_W, TABLE_LENGTH, 0, 0));
        return g;
    }

    /** A flat white marking of the given size in metres, centred at (x, z). */
    private static Box line(double width, double length, double x, double z) {
        Box b = new Box(width * SPM, 0.001 * SPM, length * SPM);
        b.setMaterial(matte(LINE_WHITE, 6));
        Xform.place(b, x, LINE_LIFT, z);
        return b;
    }

    private static Group legs() {
        Group g = new Group();
        double inX = TABLE_WIDTH / 2 - 0.12;
        double inZ = TABLE_LENGTH / 2 - 0.20;
        for (double sx : new double[] { -inX, inX }) {
            for (double sz : new double[] { -inZ, inZ }) {
                Box leg = new Box(0.05 * SPM, TABLE_HEIGHT * SPM, 0.05 * SPM);
                leg.setMaterial(matte(Color.web("#2a2f38"), 8));
                Xform.place(leg, sx, -TABLE_THICK - TABLE_HEIGHT / 2, sz);
                g.getChildren().add(leg);
            }
        }
        return g;
    }

    // ------------------------------------------------------------------ net

    /**
     * The net, drawn as actual mesh rather than a translucent slab.
     *
     * A flat semi-transparent box was the first attempt and it made the most important part
     * of the scene unreadable: you could not tell whether the ball passed in front of, behind
     * or through the net. Real netting reads correctly because you can see the ball between
     * the strands, so the strands are drawn.
     */
    private static Group net() {
        Group g = new Group();
        PhongMaterial cord = matte(Color.web("#dfe4ea"), 4);
        PhongMaterial tape = matte(Color.web("#fbfcfd"), 10);

        double halfW = NET_WIDTH / 2;
        double strand = 0.0015;          // 1.5 mm strands
        double mesh = 0.0175;            // ITTF meshes are 10-15 mm; 17.5 mm keeps the node
                                         // count sane while still reading as a net

        // Vertical strands.
        for (double x = -halfW; x <= halfW + 1e-9; x += mesh) {
            Box b = new Box(strand * SPM, NET_HEIGHT * SPM, strand * SPM);
            b.setMaterial(cord);
            Xform.place(b, x, NET_HEIGHT / 2, 0);
            g.getChildren().add(b);
        }

        // Horizontal strands.
        for (double y = mesh; y < NET_HEIGHT - 0.012; y += mesh) {
            Box b = new Box(NET_WIDTH * SPM, strand * SPM, strand * SPM);
            b.setMaterial(cord);
            Xform.place(b, 0, y, 0);
            g.getChildren().add(b);
        }

        // The white tape along the top, which is what a ball actually clips.
        Box top = new Box(NET_WIDTH * SPM, 0.012 * SPM, 0.004 * SPM);
        top.setMaterial(tape);
        Xform.place(top, 0, NET_HEIGHT - 0.006, 0);
        g.getChildren().add(top);

        // Posts, 15.25 cm outside each side line.
        for (double sx : new double[] { -halfW, halfW }) {
            Cylinder post = new Cylinder(0.008 * SPM, (NET_HEIGHT + 0.02) * SPM);
            post.setMaterial(matte(Color.web("#3a4048"), 20));
            Xform.place(post, sx, (NET_HEIGHT + 0.02) / 2 - 0.02, 0);
            g.getChildren().add(post);
        }
        return g;
    }

    // ------------------------------------------------------------------ room

    private static Box floor() {
        Box f = new Box(14 * SPM, 0.04 * SPM, 16 * SPM);
        f.setMaterial(matte(FLOOR_GREY, 4));
        Xform.place(f, 0, -TABLE_HEIGHT - 0.02, 0);
        return f;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A material with a controlled, low specular response.
     *
     * JavaFX defaults to a bright white highlight that makes every surface look like wet
     * plastic and, worse, blows out the small white ball against the white net.
     */
    private static PhongMaterial matte(Color base, double specularPower) {
        PhongMaterial m = new PhongMaterial(base);
        m.setSpecularColor(base.deriveColor(0, 1, 1.25, 1).interpolate(Color.WHITE, 0.15));
        m.setSpecularPower(specularPower);
        return m;
    }
}
