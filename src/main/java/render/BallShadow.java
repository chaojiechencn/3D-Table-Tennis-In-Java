package render;

import javafx.scene.Group;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import physics.Vec3;

import static physics.Constants.*;

/**
 * TUNED soft vertical projection that makes ball height readable without a shadow pass. The table
 * quad is clipped at the table edges; the floor quad is occluded by the tabletop itself.
 */
final class BallShadow {

    private static final int TEXTURE_SIZE = 64;
    private static final double TABLE_LIFT = TABLE_THICK * 0.07;
    private static final double FLOOR_LIFT = TABLE_THICK * 0.04;

    private final Group group = new Group();
    private final MeshView table;
    private final MeshView floor;

    private record Bounds(double x0, double x1, double z0, double z1) {}

    BallShadow() {
        PhongMaterial ink = new PhongMaterial(Color.WHITE);
        ink.setDiffuseMap(penumbraTexture());
        ink.setSpecularColor(Color.BLACK);
        table = Court.horizontal(-BALL_R, BALL_R, -BALL_R, BALL_R, TABLE_LIFT, ink);
        floor = Court.horizontal(-BALL_R, BALL_R, -BALL_R, BALL_R, -TABLE_HEIGHT + FLOOR_LIFT, ink);
        group.getChildren().addAll(floor, table);
        group.setMouseTransparent(true);
        group.setVisible(false);
    }

    private static WritableImage penumbraTexture() {
        WritableImage texture = new WritableImage(TEXTURE_SIZE, TEXTURE_SIZE);
        double centre = (TEXTURE_SIZE - 1) / 2.0;
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                double dx = (x - centre) / centre, dy = (y - centre) / centre;
                double r2 = dx * dx + dy * dy;
                double alpha = r2 >= 1 ? 0 : Math.pow(1 - r2, 3);
                texture.getPixelWriter().setColor(x, y, Color.color(0, 0, 0, alpha));
            }
        }
        return texture;
    }

    Group node() { return group; }

    /** The interpolated ball position, so shadow and ball cannot judder apart. */
    void update(Vec3 position, boolean magnified) {
        group.setVisible(true);
        project(table, position, 0, TABLE_WIDTH / 2, TABLE_LENGTH / 2, magnified);
        project(floor, position, -TABLE_HEIGHT, TABLE_LENGTH * 3, TABLE_LENGTH * 3, magnified);
    }

    /** A higher ball casts a larger, fainter penumbra centred below it; size follows the drawn ball. */
    private static void project(MeshView view, Vec3 p, double surfaceY,
                                double halfW, double halfL, boolean magnified) {
        double height = p.y() - surfaceY;
        double radius = BALL_R * (magnified ? 2 : 1) * 2.2 + height * 0.16;
        Bounds b = new Bounds(Math.max(-halfW, p.x() - radius), Math.min(halfW, p.x() + radius),
                              Math.max(-halfL, p.z() - radius), Math.min(halfL, p.z() + radius));
        boolean visible = height >= 0 && b.x0() < b.x1() && b.z0() < b.z1();
        view.setVisible(visible);
        if (!visible) return;

        view.setOpacity(0.62 / (1 + Math.max(0, height - BALL_R) / TABLE_HEIGHT * 2.5));
        double lift = surfaceY == 0 ? TABLE_LIFT : FLOOR_LIFT;
        reshape((TriangleMesh) view.getMesh(), b, surfaceY + lift, p, radius);
    }

    private static void reshape(TriangleMesh mesh, Bounds b, double y, Vec3 p, double radius) {
        float sy = (float) Xform.y(y);
        float left = (float) Xform.x(b.x0()), right = (float) Xform.x(b.x1());
        float front = (float) Xform.z(b.z1()), back = (float) Xform.z(b.z0());
        mesh.getPoints().setAll(left, sy, front, right, sy, front, right, sy, back, left, sy, back);

        float u0 = (float) ((b.x0() - p.x() + radius) / (2 * radius));
        float u1 = (float) ((b.x1() - p.x() + radius) / (2 * radius));
        float v0 = (float) ((p.z() + radius - b.z1()) / (2 * radius));
        float v1 = (float) ((p.z() + radius - b.z0()) / (2 * radius));
        mesh.getTexCoords().setAll(u0, v0, u1, v0, u1, v1, u0, v1);
    }
}
