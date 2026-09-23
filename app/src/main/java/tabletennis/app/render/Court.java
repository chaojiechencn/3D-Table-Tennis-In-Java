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
    private static final double LineW = BallR;
    private static final double CentreLineW = BallR * 0.15;

    /** TUNED: enough separation to beat depth fighting without a visibly floating marking. */
    private static final double LineLift = TableThick * 0.048;

    /** Shared with the floor's baked contact shadows, so feet and shadows cannot drift apart. */
    static final double LegX = TableWidth * 0.42;
    static final double LegZ = TableLength * 0.40;

    private Court() {}

    /** Static maps and shared materials are constructed once, not at frame rate. */
    public static Group Build() {
        return new Group(Room(), TableTop(), Markings(), Frame(), Net());
    }

    private static Box TableTop() {
        // The TOP stays exactly at y=0, matching World.TABLE, including the slab thickness.
        return Box(TableWidth, TableThick, TableLength,
                0, -TableThick / 2, 0, SurfaceMaterials.Table());
    }

    private static Group Markings() {
        Group G = new Group();
        PhongMaterial White = SurfaceMaterials.Solid("#e2e9e8", 0.025, 16);
        double HalfW = TableWidth / 2, HalfL = TableLength / 2;
        for (int Side : new int[] { -1, 1 }) {
            G.getChildren().add(Box(LineW, TableThick * 0.04, TableLength,
                    Side * (HalfW - LineW / 2), LineLift, 0, White));
            G.getChildren().add(Box(TableWidth, TableThick * 0.04, LineW,
                    0, LineLift, Side * (HalfL - LineW / 2), White));
        }
        G.getChildren().add(Box(CentreLineW, TableThick * 0.04, TableLength,
                0, LineLift, 0, White));
        return G;
    }

    /**
     * A dark apron under a thin silver rim makes slab thickness visible from the rally-cam.
     * Crossbars and feet give the table weight. They end at the physical floor rather than
     * running through it; all of this is decoration and never added to the collision world.
     */
    private static Group Frame() {
        Group G = new Group();
        PhongMaterial Metal = SurfaceMaterials.Solid("#242f3b", 0.25, 64);
        PhongMaterial Rim = SurfaceMaterials.Solid("#7f9ba5", 0.35, 72);
        PhongMaterial Rubber = SurfaceMaterials.Solid("#111820", 0.02, 12);
        double Apron = TableThick * 3;
        double Inset = TableThick * 0.4;
        double FootHeight = TableThick * 0.65;
        double LegHeight = TableHeight - TableThick - FootHeight;
        for (int Side : new int[] { -1, 1 }) {
            G.getChildren().add(Box(TableThick, Apron, TableLength - Inset * 2,
                    Side * (TableWidth / 2 - Inset), -TableThick - Apron / 2, 0, Metal));
            G.getChildren().add(Box(TableWidth - Inset * 2, Apron, TableThick,
                    0, -TableThick - Apron / 2, Side * (TableLength / 2 - Inset), Metal));
            G.getChildren().add(Box(TableThick * 0.25, TableThick * 0.18, TableLength,
                    Side * TableWidth / 2, -TableThick * 0.75, 0, Rim));
            G.getChildren().add(Box(TableWidth, TableThick * 0.18, TableThick * 0.25,
                    0, -TableThick * 0.75, Side * TableLength / 2, Rim));
            G.getChildren().add(Box(LegX * 2, TableThick * 1.4, TableThick * 1.4,
                    0, -TableHeight * 0.68, Side * LegZ, Metal));
            for (int End : new int[] { -1, 1 }) {
                G.getChildren().add(Box(TableThick * 2, LegHeight, TableThick * 2,
                        Side * LegX, -TableThick - LegHeight / 2, End * LegZ, Metal));
                G.getChildren().add(Box(TableThick * 3.2, FootHeight, TableThick * 3.2,
                        Side * LegX, -TableHeight + FootHeight / 2, End * LegZ, Rubber));
            }
        }
        return G;
    }

    /**
     * Real opaque strands preserve front/behind ordering through the net's holes. The mesh
     * stays a little coarser than regulation to survive pixel sampling at the far end; making
     * it denser or replacing it with a translucent slab would hurt the most useful depth cue.
     * TUNED strand/tape proportions derive from NetThick; the cord's top remains NetHeight.
     */
    private static Group Net() {
        Group G = new Group();
        PhongMaterial Cord = SurfaceMaterials.Solid("#75949f", 0.035, 16);
        PhongMaterial Tape = SurfaceMaterials.Tape();
        PhongMaterial Metal = SurfaceMaterials.Solid("#344655", 0.3, 64);
        PhongMaterial Cap = SurfaceMaterials.Solid("#aac2c7", 0.32, 64);
        double HalfW = NetWidth / 2;
        double Strand = NetThick / 4;
        double TapeHeight = NetThick * 2;
        int Columns = (int) Math.ceil(NetWidth / (NetHeight / 9));
        double Spacing = NetWidth / Columns;
        for (int I = 0; I <= Columns; I++) {
            G.getChildren().add(Box(Strand, NetHeight, Strand,
                    -HalfW + I * Spacing, NetHeight / 2, 0, Cord));
        }
        for (double Y = Spacing; Y < NetHeight - TapeHeight; Y += Spacing) {
            G.getChildren().add(Box(NetWidth, Strand, Strand, 0, Y, 0, Cord));
        }
        G.getChildren().add(Box(NetWidth, TapeHeight, NetThick * 2 / 3,
                0, NetHeight - TapeHeight / 2, 0, Tape));
        G.getChildren().add(Box(NetWidth, Strand * 1.4, Strand * 1.4,
                0, Strand, 0, Cord));
        for (int Side : new int[] { -1, 1 }) {
            double Sx = Side * HalfW;
            Cylinder Post = new Cylinder(Xform.Length(NetThick * 1.6),
                    Xform.Length(NetHeight + TableThick), 20);
            Post.setMaterial(Metal);
            Xform.Place(Post, Sx, (NetHeight - TableThick) / 2, 0);
            Cylinder Tip = new Cylinder(Xform.Length(NetThick * 1.65),
                    Xform.Length(NetThick * 0.6), 20);
            Tip.setMaterial(Cap);
            Xform.Place(Tip, Sx, NetHeight - NetThick * 0.3, 0);
            double Reach = (NetWidth - TableWidth) / 2;
            G.getChildren().addAll(Post, Tip,
                    Box(Reach + TableThick, TableThick * 0.6, TableThick * 2.5,
                            Side * (TableWidth / 2 + Reach / 2), -TableThick * 0.6, 0, Metal),
                    Box(TableThick, TableThick * 2, TableThick * 2.5,
                            Side * (TableWidth / 2 + TableThick / 2), -TableThick, 0, Metal));
        }
        return G;
    }

    /**
     * An open-topped hall leaves all five camera presets useful. Walls are single-sided and
     * face inward: a manual orbit outside the hall sees through the nearest wall instead of
     * losing the table behind a box. No camera bounds or controls need to know about the set.
     */
    private static Group Room() {
        double Half = TableLength * 3;
        double FloorY = -TableHeight;
        double WallTop = TableHeight * 5;
        PhongMaterial Wall = SurfaceMaterials.Wall();
        Group G = new Group(Horizontal(-Half, Half, -Half, Half, FloorY,
                SurfaceMaterials.Floor(Half * 2, Half * 2)));
        // Corner ordering gives an inward normal after Xform's rotation of physics space.
        Vec3[] Corners = { new Vec3(-Half, FloorY, -Half), new Vec3(Half, FloorY, -Half),
                new Vec3(Half, FloorY, Half), new Vec3(-Half, FloorY, Half) };
        for (int I = 0; I < Corners.length; I++) {
            Vec3 A = Corners[I], B = Corners[(I + 1) % Corners.length];
            G.getChildren().add(Quad(new Vec3(B.X(), WallTop, B.Z()),
                    new Vec3(A.X(), WallTop, A.Z()), A, B, Wall));
        }
        return G;
    }

    static Box Box(double Width, double Height, double Depth,
                   double X, double Y, double Z, PhongMaterial Material) {
        Box B = new Box(Xform.Length(Width), Xform.Length(Height), Xform.Length(Depth));
        B.setMaterial(Material);
        Xform.Place(B, X, Y, Z);
        return B;
    }

    /** Explicit UVs keep the baked floor shadow aligned with the physics-space footprint. */
    static MeshView Horizontal(double X0, double X1, double Z0, double Z1, double Y,
                               PhongMaterial Material) {
        return Quad(new Vec3(X0, Y, Z1), new Vec3(X1, Y, Z1),
                new Vec3(X1, Y, Z0), new Vec3(X0, Y, Z0), Material);
    }

    private static MeshView Quad(Vec3 A, Vec3 B, Vec3 C, Vec3 D, PhongMaterial Material) {
        TriangleMesh Mesh = new TriangleMesh();
        for (Vec3 P : new Vec3[] { A, B, C, D }) {
            Point3D S = Xform.ToScene(P);
            Mesh.getPoints().addAll((float) S.getX(), (float) S.getY(), (float) S.getZ());
        }
        Mesh.getTexCoords().setAll(0, 0, 1, 0, 1, 1, 0, 1);
        Mesh.getFaces().setAll(0, 0, 1, 1, 2, 2, 0, 0, 2, 2, 3, 3);
        MeshView View = new MeshView(Mesh);
        View.setMaterial(Material);
        return View;
    }
}
