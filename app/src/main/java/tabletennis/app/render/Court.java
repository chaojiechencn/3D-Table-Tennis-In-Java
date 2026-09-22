package tabletennis.app.render;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import tabletennis.engine.Constants;
import tabletennis.engine.Vec3;

import static tabletennis.engine.Constants.*;

/**
 * A table in a quiet training hall. Collision surfaces retain the dimensions in
 * {@link Constants}; decorative proportions are TUNED multiples of those dimensions.
 * Keeping the room darker than the playing surface gives the orange/white ball a reliable
 * silhouette, while the floor, frame and net clamps supply depth cues at grazing angles.
 */
public final class Court {

    /** ITTF Laws 2.1.4 and 2.1.6: side/end lines equal a ball radius; doubles line is 15% of it. */
    private static final double LINE_W = BALL_R;
    private static final double CENTRE_LINE_W = BALL_R * 0.15;

    /** TUNED: enough separation to beat depth fighting without a visibly floating marking. */
    private static final double LINE_LIFT = TABLE_THICK * 0.048;

    /** Shared with the floor's baked contact shadows, so feet and shadows cannot drift apart. */
    static final double LEG_X = TABLE_WIDTH * 0.42;
    static final double LEG_Z = TABLE_LENGTH * 0.40;

    private Court() {}

    /** Static maps and shared materials are constructed once, not at frame rate. */
    public static Group build() {
        return new Group(room(), tableTop(), markings(), frame(), net());
    }

    private static Box tableTop() {
        // The TOP stays exactly at y=0, matching World.TABLE, including the slab thickness.
        return box(TABLE_WIDTH, TABLE_THICK, TABLE_LENGTH,
                0, -TABLE_THICK / 2, 0, SurfaceMaterials.table());
    }

    private static Group markings() {
        Group g = new Group();
        PhongMaterial white = SurfaceMaterials.solid("#e2e9e8", 0.025, 16);
        double halfW = TABLE_WIDTH / 2, halfL = TABLE_LENGTH / 2;
        for (int side : new int[] { -1, 1 }) {
            g.getChildren().add(box(LINE_W, TABLE_THICK * 0.04, TABLE_LENGTH,
                    side * (halfW - LINE_W / 2), LINE_LIFT, 0, white));
            g.getChildren().add(box(TABLE_WIDTH, TABLE_THICK * 0.04, LINE_W,
                    0, LINE_LIFT, side * (halfL - LINE_W / 2), white));
        }
        g.getChildren().add(box(CENTRE_LINE_W, TABLE_THICK * 0.04, TABLE_LENGTH,
                0, LINE_LIFT, 0, white));
        return g;
    }

    /**
     * A dark apron under a thin silver rim makes slab thickness visible from the rally-cam.
     * Crossbars and feet give the table weight. They end at the physical floor rather than
     * running through it; all of this is decoration and never added to the collision world.
     */
    private static Group frame() {
        Group g = new Group();
        PhongMaterial metal = SurfaceMaterials.solid("#242f3b", 0.25, 64);
        PhongMaterial rim = SurfaceMaterials.solid("#7f9ba5", 0.35, 72);
        PhongMaterial rubber = SurfaceMaterials.solid("#111820", 0.02, 12);
        double apron = TABLE_THICK * 3;
        double inset = TABLE_THICK * 0.4;
        double footHeight = TABLE_THICK * 0.65;
        double legHeight = TABLE_HEIGHT - TABLE_THICK - footHeight;
        for (int side : new int[] { -1, 1 }) {
            g.getChildren().add(box(TABLE_THICK, apron, TABLE_LENGTH - inset * 2,
                    side * (TABLE_WIDTH / 2 - inset), -TABLE_THICK - apron / 2, 0, metal));
            g.getChildren().add(box(TABLE_WIDTH - inset * 2, apron, TABLE_THICK,
                    0, -TABLE_THICK - apron / 2, side * (TABLE_LENGTH / 2 - inset), metal));
            g.getChildren().add(box(TABLE_THICK * 0.25, TABLE_THICK * 0.18, TABLE_LENGTH,
                    side * TABLE_WIDTH / 2, -TABLE_THICK * 0.75, 0, rim));
            g.getChildren().add(box(TABLE_WIDTH, TABLE_THICK * 0.18, TABLE_THICK * 0.25,
                    0, -TABLE_THICK * 0.75, side * TABLE_LENGTH / 2, rim));
            g.getChildren().add(box(LEG_X * 2, TABLE_THICK * 1.4, TABLE_THICK * 1.4,
                    0, -TABLE_HEIGHT * 0.68, side * LEG_Z, metal));
            for (int end : new int[] { -1, 1 }) {
                g.getChildren().add(box(TABLE_THICK * 2, legHeight, TABLE_THICK * 2,
                        side * LEG_X, -TABLE_THICK - legHeight / 2, end * LEG_Z, metal));
                g.getChildren().add(box(TABLE_THICK * 3.2, footHeight, TABLE_THICK * 3.2,
                        side * LEG_X, -TABLE_HEIGHT + footHeight / 2, end * LEG_Z, rubber));
            }
        }
        return g;
    }

    /**
     * Real opaque strands preserve front/behind ordering through the net's holes. The mesh
     * stays a little coarser than regulation to survive pixel sampling at the far end; making
     * it denser or replacing it with a translucent slab would hurt the most useful depth cue.
     * TUNED strand/tape proportions derive from NET_THICK; the cord's top remains NET_HEIGHT.
     */
    private static Group net() {
        Group g = new Group();
        PhongMaterial cord = SurfaceMaterials.solid("#75949f", 0.035, 16);
        PhongMaterial tape = SurfaceMaterials.tape();
        PhongMaterial metal = SurfaceMaterials.solid("#344655", 0.3, 64);
        PhongMaterial cap = SurfaceMaterials.solid("#aac2c7", 0.32, 64);
        double halfW = NET_WIDTH / 2;
        double strand = NET_THICK / 4;
        double tapeHeight = NET_THICK * 2;
        int columns = (int) Math.ceil(NET_WIDTH / (NET_HEIGHT / 9));
        double spacing = NET_WIDTH / columns;
        for (int i = 0; i <= columns; i++) {
            g.getChildren().add(box(strand, NET_HEIGHT, strand,
                    -halfW + i * spacing, NET_HEIGHT / 2, 0, cord));
        }
        for (double y = spacing; y < NET_HEIGHT - tapeHeight; y += spacing) {
            g.getChildren().add(box(NET_WIDTH, strand, strand, 0, y, 0, cord));
        }
        g.getChildren().add(box(NET_WIDTH, tapeHeight, NET_THICK * 2 / 3,
                0, NET_HEIGHT - tapeHeight / 2, 0, tape));
        g.getChildren().add(box(NET_WIDTH, strand * 1.4, strand * 1.4,
                0, strand, 0, cord));
        for (int side : new int[] { -1, 1 }) {
            double sx = side * halfW;
            Cylinder post = new Cylinder(Xform.length(NET_THICK * 1.6),
                    Xform.length(NET_HEIGHT + TABLE_THICK), 20);
            post.setMaterial(metal);
            Xform.place(post, sx, (NET_HEIGHT - TABLE_THICK) / 2, 0);
            Cylinder tip = new Cylinder(Xform.length(NET_THICK * 1.65),
                    Xform.length(NET_THICK * 0.6), 20);
            tip.setMaterial(cap);
            Xform.place(tip, sx, NET_HEIGHT - NET_THICK * 0.3, 0);
            double reach = (NET_WIDTH - TABLE_WIDTH) / 2;
            g.getChildren().addAll(post, tip,
                    box(reach + TABLE_THICK, TABLE_THICK * 0.6, TABLE_THICK * 2.5,
                            side * (TABLE_WIDTH / 2 + reach / 2), -TABLE_THICK * 0.6, 0, metal),
                    box(TABLE_THICK, TABLE_THICK * 2, TABLE_THICK * 2.5,
                            side * (TABLE_WIDTH / 2 + TABLE_THICK / 2), -TABLE_THICK, 0, metal));
        }
        return g;
    }

    /**
     * An open-topped hall leaves all five camera presets useful. Walls are single-sided and
     * face inward: a manual orbit outside the hall sees through the nearest wall instead of
     * losing the table behind a box. No camera bounds or controls need to know about the set.
     */
    private static Group room() {
        double half = TABLE_LENGTH * 3;
        double floorY = -TABLE_HEIGHT;
        double wallTop = TABLE_HEIGHT * 5;
        PhongMaterial wall = SurfaceMaterials.wall();
        Group g = new Group(horizontal(-half, half, -half, half, floorY,
                SurfaceMaterials.floor(half * 2, half * 2)));
        // Corner ordering gives an inward normal after Xform's rotation of physics space.
        Vec3[] corners = { new Vec3(-half, floorY, -half), new Vec3(half, floorY, -half),
                new Vec3(half, floorY, half), new Vec3(-half, floorY, half) };
        for (int i = 0; i < corners.length; i++) {
            Vec3 a = corners[i], b = corners[(i + 1) % corners.length];
            g.getChildren().add(quad(new Vec3(b.x(), wallTop, b.z()),
                    new Vec3(a.x(), wallTop, a.z()), a, b, wall));
        }
        return g;
    }

    static Box box(double width, double height, double depth,
                   double x, double y, double z, PhongMaterial material) {
        Box b = new Box(Xform.length(width), Xform.length(height), Xform.length(depth));
        b.setMaterial(material);
        Xform.place(b, x, y, z);
        return b;
    }

    /** Explicit UVs keep the baked floor shadow aligned with the physics-space footprint. */
    static MeshView horizontal(double x0, double x1, double z0, double z1, double y,
                               PhongMaterial material) {
        return quad(new Vec3(x0, y, z1), new Vec3(x1, y, z1),
                new Vec3(x1, y, z0), new Vec3(x0, y, z0), material);
    }

    private static MeshView quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, PhongMaterial material) {
        TriangleMesh mesh = new TriangleMesh();
        for (Vec3 p : new Vec3[] { a, b, c, d }) {
            Point3D s = Xform.toScene(p);
            mesh.getPoints().addAll((float) s.getX(), (float) s.getY(), (float) s.getZ());
        }
        mesh.getTexCoords().setAll(0, 0, 1, 0, 1, 1, 0, 1);
        mesh.getFaces().setAll(0, 0, 1, 1, 2, 2, 0, 0, 2, 2, 3, 3);
        MeshView view = new MeshView(mesh);
        view.setMaterial(material);
        return view;
    }
}
