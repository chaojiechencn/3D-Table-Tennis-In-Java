package pong.game_objects.ball.view;

import javafx.scene.Group;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

import static pong.config.Physical.*;
import pong.core.math.Vec3;
import pong.game_world.view.CourtView;
import pong.helpers.Xform;

/**
 * A soft vertical projection makes ball height readable without a shadow-map render pass.
 * This is a TUNED contact cue for broad overhead lighting, not a second simulated object.
 * Two tiny quads are reused: the table projection is clipped at the actual table edges,
 * and the floor projection is naturally occluded by the solid tabletop. A wide ball thus
 * cannot leave a floating shadow outside the table, or teleport its entire shadow at an edge.
 */
final class BallShadow {

    private final Group group = new Group();
    private final MeshView table;
    private final MeshView floor;

    BallShadow() {
        WritableImage texture = new WritableImage(64, 64);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                double dx = (x - 31.5) / 31.5, dy = (y - 31.5) / 31.5;
                double r2 = dx * dx + dy * dy;
                double alpha = r2 >= 1 ? 0 : Math.pow(1 - r2, 3);
                texture.getPixelWriter().setColor(x, y, Color.color(0, 0, 0, alpha));
            }
        }
        PhongMaterial ink = new PhongMaterial(Color.WHITE);
        ink.setDiffuseMap(texture);
        ink.setSpecularColor(Color.BLACK);
        table = CourtView.horizontal(-BALL_R, BALL_R, -BALL_R, BALL_R,
                TABLE_THICK * 0.07, ink);
        floor = CourtView.horizontal(-BALL_R, BALL_R, -BALL_R, BALL_R,
                -TABLE_HEIGHT + TABLE_THICK * 0.04, ink);
        group.getChildren().addAll(floor, table);
        group.setMouseTransparent(true);
        group.setVisible(false);
    }

    Group node() { return group; }

    /** Interpolated position, just like the sphere, so the two cues cannot judder apart. */
    void update(Vec3 position, boolean magnified) {
        group.setVisible(true);
        project(table, position, 0, TABLE_WIDTH / 2, TABLE_LENGTH / 2, magnified);
        project(floor, position, -TABLE_HEIGHT, TABLE_LENGTH * 3, TABLE_LENGTH * 3, magnified);
    }

    private static void project(MeshView view, Vec3 p, double surfaceY,
                                double halfW, double halfL, boolean magnified) {
        double height = p.y() - surfaceY;
        if (height < 0) { view.setVisible(false); return; }
        // A higher ball has a larger, fainter penumbra; its darkest point stays directly
        // below the ball. Magnification follows B's visual size, never the collision radius.
        double radius = BALL_R * (magnified ? 2 : 1) * 2.2 + height * 0.16;
        double x0 = Math.max(-halfW, p.x() - radius), x1 = Math.min(halfW, p.x() + radius);
        double z0 = Math.max(-halfL, p.z() - radius), z1 = Math.min(halfL, p.z() + radius);
        if (x0 >= x1 || z0 >= z1) { view.setVisible(false); return; }
        view.setVisible(true);
        view.setOpacity(0.62 / (1 + Math.max(0, height - BALL_R) / TABLE_HEIGHT * 2.5));
        TriangleMesh mesh = (TriangleMesh) view.getMesh();
        float y = (float) Xform.y(surfaceY + TABLE_THICK * (surfaceY == 0 ? 0.07 : 0.04));
        float left = (float) Xform.x(x0), right = (float) Xform.x(x1);
        float front = (float) Xform.z(z1), back = (float) Xform.z(z0);
        mesh.getPoints().setAll(left, y, front, right, y, front, right, y, back, left, y, back);
        float u0 = (float) ((x0 - p.x() + radius) / (2 * radius));
        float u1 = (float) ((x1 - p.x() + radius) / (2 * radius));
        float v0 = (float) ((p.z() + radius - z1) / (2 * radius));
        float v1 = (float) ((p.z() + radius - z0) / (2 * radius));
        mesh.getTexCoords().setAll(u0, v0, u1, v0, u1, v1, u0, v1);
    }
}
