package tabletennis.app.scene;

import javafx.scene.AmbientLight;
import javafx.scene.DirectionalLight;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import tabletennis.engine.BallSpec;
import tabletennis.engine.NetSpec;
import tabletennis.engine.TableSpec;
import tabletennis.engine.math.Vec3;

import static tabletennis.app.scene.Meshes.Box;

/**
 * The hall: a table in a quiet training room, and its lights. Collision surfaces keep the
 * engine's dimensions; decorative proportions are TUNED multiples of them. Keeping the room
 * darker than the playing surface gives the orange and white ball a reliable silhouette, while
 * the floor, frame and net clamps supply depth cues at grazing angles. Built once, never per frame.
 */
final class ArenaView {

    private ArenaView() {}

    /** ITTF Laws 2.1.4 and 2.1.6: side and end lines equal a ball radius; the doubles line is 15% of it. */
    private static final double LineWidth = BallSpec.Radius;
    private static final double CentreLineWidth = BallSpec.Radius * 0.15;

    /** TUNED: enough separation to beat depth fighting without a visibly floating marking. */
    private static final double LineLift = TableSpec.TopThickness * 0.048;
    private static final double LineThickness = TableSpec.TopThickness * 0.04;

    /** Shared with the floor's baked contact shadows, so feet and shadows cannot drift apart. */
    private static final double LegX = TableSpec.Width * 0.42;
    private static final double LegZ = TableSpec.Length * 0.40;

    private static final double[] BothSides = { -1, 1 };

    static Group Build() {
        return new Group(Room(), TableTop(), Markings(), Frame(), Net(), Lighting());
    }

    private static Box TableTop() {
        // The TOP sits exactly at y = 0, matching the engine's table slab, thickness included.
        return Box(TableSpec.Width, TableSpec.TopThickness, TableSpec.Length,
                   0, -TableSpec.TopThickness / 2, 0, Textures.Table());
    }

    private static Group Markings() {
        Group Lines = new Group();
        PhongMaterial White = Textures.Solid("#e2e9e8", 0.025, 16);
        double HalfWidth = TableSpec.Width / 2, HalfLength = TableSpec.Length / 2;
        for (double Side : BothSides) {
            Lines.getChildren().add(Box(LineWidth, LineThickness, TableSpec.Length,
                    Side * (HalfWidth - LineWidth / 2), LineLift, 0, White));
            Lines.getChildren().add(Box(TableSpec.Width, LineThickness, LineWidth,
                    0, LineLift, Side * (HalfLength - LineWidth / 2), White));
        }
        Lines.getChildren().add(Box(CentreLineWidth, LineThickness, TableSpec.Length, 0, LineLift, 0, White));
        return Lines;
    }

    /**
     * A dark apron under a thin silver rim makes the slab's thickness visible from the rally-cam;
     * crossbars and feet give the table weight. They end at the floor rather than running through
     * it, and none of it is part of the collision world.
     */
    private static Group Frame() {
        Group Parts = new Group();
        PhongMaterial Metal = Textures.Solid("#242f3b", 0.25, 64);
        PhongMaterial Rim = Textures.Solid("#7f9ba5", 0.35, 72);
        PhongMaterial Rubber = Textures.Solid("#111820", 0.02, 12);
        double Slab = TableSpec.TopThickness;
        double Apron = Slab * 3;
        double Inset = Slab * 0.4;
        double FootHeight = Slab * 0.65;
        double LegHeight = TableSpec.Height - Slab - FootHeight;
        for (double Side : BothSides) {
            Parts.getChildren().addAll(
                    Box(Slab, Apron, TableSpec.Length - Inset * 2,
                        Side * (TableSpec.Width / 2 - Inset), -Slab - Apron / 2, 0, Metal),
                    Box(TableSpec.Width - Inset * 2, Apron, Slab,
                        0, -Slab - Apron / 2, Side * (TableSpec.Length / 2 - Inset), Metal),
                    Box(Slab * 0.25, Slab * 0.18, TableSpec.Length,
                        Side * TableSpec.Width / 2, -Slab * 0.75, 0, Rim),
                    Box(TableSpec.Width, Slab * 0.18, Slab * 0.25,
                        0, -Slab * 0.75, Side * TableSpec.Length / 2, Rim),
                    Box(LegX * 2, Slab * 1.4, Slab * 1.4, 0, -TableSpec.Height * 0.68, Side * LegZ, Metal));
            for (double End : BothSides) {
                Parts.getChildren().addAll(
                        Box(Slab * 2, LegHeight, Slab * 2, Side * LegX, -Slab - LegHeight / 2, End * LegZ, Metal),
                        Box(Slab * 3.2, FootHeight, Slab * 3.2,
                            Side * LegX, -TableSpec.Height + FootHeight / 2, End * LegZ, Rubber));
            }
        }
        return Parts;
    }

    /**
     * Real opaque strands preserve front/behind ordering through the net's holes. The mesh stays
     * a little coarser than regulation to survive pixel sampling at the far end; a denser mesh or a
     * translucent slab would hurt the most useful depth cue. TUNED strand and tape proportions
     * derive from the net's thickness; the cord's top stays at the net height.
     */
    private static Group Net() {
        Group Netting = new Group();
        PhongMaterial Cord = Textures.Solid("#75949f", 0.035, 16);
        PhongMaterial Tape = Textures.Tape();
        double HalfWidth = NetSpec.Width / 2;
        double Strand = NetSpec.Thickness / 4;
        double TapeHeight = NetSpec.Thickness * 2;
        int Columns = (int) Math.ceil(NetSpec.Width / (NetSpec.Height / 9));
        double Spacing = NetSpec.Width / Columns;
        for (int Column = 0; Column <= Columns; Column++) {
            Netting.getChildren().add(Box(Strand, NetSpec.Height, Strand,
                    -HalfWidth + Column * Spacing, NetSpec.Height / 2, 0, Cord));
        }
        for (double Y = Spacing; Y < NetSpec.Height - TapeHeight; Y += Spacing) {
            Netting.getChildren().add(Box(NetSpec.Width, Strand, Strand, 0, Y, 0, Cord));
        }
        Netting.getChildren().add(Box(NetSpec.Width, TapeHeight, NetSpec.Thickness * 2 / 3,
                0, NetSpec.Height - TapeHeight / 2, 0, Tape));
        Netting.getChildren().add(Box(NetSpec.Width, Strand * 1.4, Strand * 1.4, 0, Strand, 0, Cord));
        PhongMaterial Metal = Textures.Solid("#344655", 0.3, 64);
        PhongMaterial Cap = Textures.Solid("#aac2c7", 0.32, 64);
        for (double Side : BothSides) Netting.getChildren().add(NetPost(Side * HalfWidth, Side, Metal, Cap));
        return Netting;
    }

    /** A post with its cap, and the clamp that holds it to the table edge. */
    private static Group NetPost(double X, double Side, PhongMaterial Metal, PhongMaterial Cap) {
        double Slab = TableSpec.TopThickness;

        Cylinder Post = new Cylinder(Xform.Length(NetSpec.Thickness * 1.6), Xform.Length(NetSpec.Height + Slab), 20);
        Post.setMaterial(Metal);
        Xform.Place(Post, X, (NetSpec.Height - Slab) / 2, 0);
        Cylinder Tip = new Cylinder(Xform.Length(NetSpec.Thickness * 1.65), Xform.Length(NetSpec.Thickness * 0.6), 20);
        Tip.setMaterial(Cap);
        Xform.Place(Tip, X, NetSpec.Height - NetSpec.Thickness * 0.3, 0);

        double Reach = (NetSpec.Width - TableSpec.Width) / 2;
        return new Group(Post, Tip,
                Box(Reach + Slab, Slab * 0.6, Slab * 2.5, Side * (TableSpec.Width / 2 + Reach / 2), -Slab * 0.6, 0, Metal),
                Box(Slab, Slab * 2, Slab * 2.5, Side * (TableSpec.Width / 2 + Slab / 2), -Slab, 0, Metal));
    }

    /**
     * An open-topped hall leaves all five camera presets useful. Walls are single-sided and face
     * inward: a manual orbit outside the hall sees through the nearest wall instead of losing the
     * table behind a box. No camera bounds or controls need to know about the set.
     */
    private static Group Room() {
        double Half = TableSpec.Length * 3;
        double FloorY = -TableSpec.Height;
        double WallTop = TableSpec.Height * 5;
        PhongMaterial Wall = Textures.Wall();
        Group Hall = new Group(Meshes.Horizontal(-Half, Half, -Half, Half, FloorY,
                                                 Textures.Floor(Half * 2, Half * 2, LegX, LegZ)));
        // Corner order gives an inward normal after Xform's rotation of physics space.
        Vec3[] Corners = { new Vec3(-Half, FloorY, -Half), new Vec3(Half, FloorY, -Half),
                           new Vec3(Half, FloorY, Half), new Vec3(-Half, FloorY, Half) };
        for (int Index = 0; Index < Corners.length; Index++) {
            Vec3 A = Corners[Index], B = Corners[(Index + 1) % Corners.length];
            Hall.getChildren().add(Meshes.Quad(new Vec3(B.X(), WallTop, B.Z()), new Vec3(A.X(), WallTop, A.Z()),
                                               A, B, Wall));
        }
        return Hall;
    }

    /** TUNED sports-hall lighting: warm key, cool cross-fill, coloured ambient for the underside. */
    private static Group Lighting() {
        DirectionalLight Key = new DirectionalLight(Color.web("#b9b1a4"));
        Key.setDirection(Xform.ToScene(new Vec3(0.35, -1, -0.28)).normalize());
        DirectionalLight Fill = new DirectionalLight(Color.web("#435c78"));
        Fill.setDirection(Xform.ToScene(new Vec3(-0.8, -0.55, 0.4)).normalize());
        return new Group(Key, Fill, new AmbientLight(Color.web("#303944")));
    }
}
