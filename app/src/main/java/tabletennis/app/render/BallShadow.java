package tabletennis.app.render;

import tabletennis.engine.TableSpec;
import tabletennis.engine.BallSpec;
import javafx.scene.Group;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import tabletennis.engine.math.Vec3;


/**
 * TUNED soft vertical projection that makes ball height readable without a shadow pass. The table
 * quad is clipped at the table edges; the floor quad is occluded by the tabletop itself.
 */
final class BallShadow {

    private static final int TextureSize = 64;
    private static final double TableLift = TableSpec.TopThickness * 0.07;
    private static final double FloorLift = TableSpec.TopThickness * 0.04;

    private final Group Root = new Group();
    private final MeshView Table;
    private final MeshView Floor;

    private record Bounds(double X0, double X1, double Z0, double Z1) {}

    BallShadow() {
        PhongMaterial Ink = new PhongMaterial(Color.WHITE);
        Ink.setDiffuseMap(PenumbraTexture());
        Ink.setSpecularColor(Color.BLACK);
        Table = Court.Horizontal(-BallSpec.Radius, BallSpec.Radius, -BallSpec.Radius, BallSpec.Radius, TableLift, Ink);
        Floor = Court.Horizontal(-BallSpec.Radius, BallSpec.Radius, -BallSpec.Radius, BallSpec.Radius, -TableSpec.Height + FloorLift, Ink);
        Root.getChildren().addAll(Floor, Table);
        Root.setMouseTransparent(true);
        Root.setVisible(false);
    }

    private static WritableImage PenumbraTexture() {
        WritableImage Texture = new WritableImage(TextureSize, TextureSize);
        double Centre = (TextureSize - 1) / 2.0;
        for (int Y = 0; Y < TextureSize; Y++) {
            for (int X = 0; X < TextureSize; X++) {
                double Dx = (X - Centre) / Centre, Dy = (Y - Centre) / Centre;
                double R2 = Dx * Dx + Dy * Dy;
                double Alpha = R2 >= 1 ? 0 : Math.pow(1 - R2, 3);
                Texture.getPixelWriter().setColor(X, Y, Color.color(0, 0, 0, Alpha));
            }
        }
        return Texture;
    }

    Group Node() { return Root; }

    /** The interpolated ball position, so shadow and ball cannot judder apart. */
    void Update(Vec3 Position, boolean Magnified) {
        Root.setVisible(true);
        Project(Table, Position, 0, TableSpec.Width / 2, TableSpec.Length / 2, Magnified);
        Project(Floor, Position, -TableSpec.Height, TableSpec.Length * 3, TableSpec.Length * 3, Magnified);
    }

    /** A higher ball casts a larger, fainter penumbra centred below it; size follows the drawn ball. */
    private static void Project(MeshView View, Vec3 P, double SurfaceY,
                                double HalfW, double HalfL, boolean Magnified) {
        double Height = P.Y() - SurfaceY;
        double Radius = BallSpec.Radius * (Magnified ? 2 : 1) * 2.2 + Height * 0.16;
        Bounds B = new Bounds(Math.max(-HalfW, P.X() - Radius), Math.min(HalfW, P.X() + Radius),
                              Math.max(-HalfL, P.Z() - Radius), Math.min(HalfL, P.Z() + Radius));
        boolean Visible = Height >= 0 && B.X0() < B.X1() && B.Z0() < B.Z1();
        View.setVisible(Visible);
        if (!Visible) return;

        View.setOpacity(0.62 / (1 + Math.max(0, Height - BallSpec.Radius) / TableSpec.Height * 2.5));
        double Lift = SurfaceY == 0 ? TableLift : FloorLift;
        Reshape((TriangleMesh) View.getMesh(), B, SurfaceY + Lift, P, Radius);
    }

    private static void Reshape(TriangleMesh Mesh, Bounds B, double Y, Vec3 P, double Radius) {
        float Sy = (float) Xform.Y(Y);
        float Left = (float) Xform.X(B.X0()), Right = (float) Xform.X(B.X1());
        float Front = (float) Xform.Z(B.Z1()), Back = (float) Xform.Z(B.Z0());
        Mesh.getPoints().setAll(Left, Sy, Front, Right, Sy, Front, Right, Sy, Back, Left, Sy, Back);

        float U0 = (float) ((B.X0() - P.X() + Radius) / (2 * Radius));
        float U1 = (float) ((B.X1() - P.X() + Radius) / (2 * Radius));
        float V0 = (float) ((P.Z() + Radius - B.Z1()) / (2 * Radius));
        float V1 = (float) ((P.Z() + Radius - B.Z0()) / (2 * Radius));
        Mesh.getTexCoords().setAll(U0, V0, U1, V0, U1, V1, U0, V1);
    }
}
