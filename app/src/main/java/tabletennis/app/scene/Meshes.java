package tabletennis.app.scene;

import javafx.geometry.Point3D;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import tabletennis.engine.math.Vec3;

/** Scene shapes sized and placed in metres, so no caller converts to scene units itself. */
final class Meshes {

    private Meshes() {}

    static Box Box(double Width, double Height, double Depth,
                   double X, double Y, double Z, PhongMaterial Finish) {
        Box Shape = new Box(Xform.Length(Width), Xform.Length(Height), Xform.Length(Depth));
        Shape.setMaterial(Finish);
        Xform.Place(Shape, X, Y, Z);
        return Shape;
    }

    /** A flat quad at height Y; explicit UVs keep a baked texture aligned with its physics footprint. */
    static MeshView Horizontal(double X0, double X1, double Z0, double Z1, double Y, PhongMaterial Finish) {
        return Quad(new Vec3(X0, Y, Z1), new Vec3(X1, Y, Z1),
                    new Vec3(X1, Y, Z0), new Vec3(X0, Y, Z0), Finish);
    }

    /** Single-sided: the corner order sets which way it faces after Xform's axis flip. */
    static MeshView Quad(Vec3 A, Vec3 B, Vec3 C, Vec3 D, PhongMaterial Finish) {
        TriangleMesh Mesh = new TriangleMesh();
        for (Vec3 Corner : new Vec3[] { A, B, C, D }) {
            Point3D At = Xform.ToScene(Corner);
            Mesh.getPoints().addAll((float) At.getX(), (float) At.getY(), (float) At.getZ());
        }
        Mesh.getTexCoords().setAll(0, 0, 1, 0, 1, 1, 0, 1);
        Mesh.getFaces().setAll(0, 0, 1, 1, 2, 2, 0, 0, 2, 2, 3, 3);
        MeshView View = new MeshView(Mesh);
        View.setMaterial(Finish);
        return View;
    }
}
