package tabletennis.app.scene;

import javafx.scene.Group;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import tabletennis.engine.BallSpec;
import tabletennis.engine.TableSpec;
import tabletennis.engine.math.Vec3;

/**
 * TUNED soft vertical projection that makes the ball's height readable without a shadow pass.
 * The table quad is clipped at the table's edges; the floor quad is occluded by the tabletop.
 */
final class BallShadow {

    private static final int TextureSize = 64;
    private static final double TableLift = TableSpec.TopThickness * 0.07;
    private static final double FloorLift = TableSpec.TopThickness * 0.04;
    private static final double FloorHalfExtent = TableSpec.Length * 3;

    private final Group Root = new Group();
    private final MeshView OnTable;
    private final MeshView OnFloor;

    private record Bounds(double X0, double X1, double Z0, double Z1) {}

    BallShadow() {
        PhongMaterial Ink = new PhongMaterial(Color.WHITE);
        Ink.setDiffuseMap(PenumbraTexture());
        Ink.setSpecularColor(Color.BLACK);
        double R = BallSpec.Radius;
        OnTable = Meshes.Horizontal(-R, R, -R, R, TableLift, Ink);
        OnFloor = Meshes.Horizontal(-R, R, -R, R, -TableSpec.Height + FloorLift, Ink);
        Root.getChildren().addAll(OnFloor, OnTable);
        Root.setMouseTransparent(true);
        Root.setVisible(false);
    }

    private static WritableImage PenumbraTexture() {
        WritableImage Texture = new WritableImage(TextureSize, TextureSize);
        double Centre = (TextureSize - 1) / 2.0;
        for (int Y = 0; Y < TextureSize; Y++) {
            for (int X = 0; X < TextureSize; X++) {
                double Dx = (X - Centre) / Centre, Dy = (Y - Centre) / Centre;
                double RadiusSquared = Dx * Dx + Dy * Dy;
                double Alpha = RadiusSquared >= 1 ? 0 : Math.pow(1 - RadiusSquared, 3);
                Texture.getPixelWriter().setColor(X, Y, Color.color(0, 0, 0, Alpha));
            }
        }
        return Texture;
    }

    Group Node() { return Root; }

    /** The interpolated ball position, so shadow and ball cannot judder apart. */
    void Update(Vec3 Position, boolean Magnified) {
        Root.setVisible(true);
        Project(OnTable, Position, 0, TableSpec.Width / 2, TableSpec.Length / 2, Magnified);
        Project(OnFloor, Position, -TableSpec.Height, FloorHalfExtent, FloorHalfExtent, Magnified);
    }

    /** A higher ball casts a larger, fainter penumbra centred below it; size follows the drawn ball. */
    private static void Project(MeshView View, Vec3 Ball, double SurfaceY,
                                double HalfWidth, double HalfLength, boolean Magnified) {
        double Height = Ball.Y() - SurfaceY;
        double Radius = BallSpec.Radius * (Magnified ? 2 : 1) * 2.2 + Height * 0.16;
        Bounds Clipped = new Bounds(Math.max(-HalfWidth, Ball.X() - Radius), Math.min(HalfWidth, Ball.X() + Radius),
                                    Math.max(-HalfLength, Ball.Z() - Radius), Math.min(HalfLength, Ball.Z() + Radius));
        boolean Visible = Height >= 0 && Clipped.X0() < Clipped.X1() && Clipped.Z0() < Clipped.Z1();
        View.setVisible(Visible);
        if (!Visible) return;

        View.setOpacity(0.62 / (1 + Math.max(0, Height - BallSpec.Radius) / TableSpec.Height * 2.5));
        double Lift = SurfaceY == 0 ? TableLift : FloorLift;
        Reshape((TriangleMesh) View.getMesh(), Clipped, SurfaceY + Lift, Ball, Radius);
    }

    private static void Reshape(TriangleMesh Mesh, Bounds Clipped, double Y, Vec3 Ball, double Radius) {
        float SceneY = (float) Xform.Y(Y);
        float Left = (float) Xform.X(Clipped.X0()), Right = (float) Xform.X(Clipped.X1());
        float Front = (float) Xform.Z(Clipped.Z1()), Back = (float) Xform.Z(Clipped.Z0());
        Mesh.getPoints().setAll(Left, SceneY, Front, Right, SceneY, Front, Right, SceneY, Back, Left, SceneY, Back);

        float U0 = (float) ((Clipped.X0() - Ball.X() + Radius) / (2 * Radius));
        float U1 = (float) ((Clipped.X1() - Ball.X() + Radius) / (2 * Radius));
        float V0 = (float) ((Ball.Z() + Radius - Clipped.Z1()) / (2 * Radius));
        float V1 = (float) ((Ball.Z() + Radius - Clipped.Z0()) / (2 * Radius));
        Mesh.getTexCoords().setAll(U0, V0, U1, V0, U1, V1, U0, V1);
    }
}
