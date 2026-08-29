package render;

import javafx.scene.Group;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import physics.BallState;
import physics.Quat;

import static physics.Constants.BALL_R;
import static render.Xform.SPM;

/**
 * The ball, painted so that its spin is visible.
 *
 * A plain white sphere is rotationally symmetric, so a ball spinning at 110 revolutions per
 * second and a ball spinning at zero look identical. Since "the ball flying with spin" is
 * half of what this checkpoint has to demonstrate, the ball gets a marked surface and is
 * actually rotated by the orientation the integrator has been carrying all along.
 *
 * The pattern is generated rather than loaded: no asset files to lose, and it can be tuned
 * for the one thing it has to do, which is read as rotation from any viewing angle.
 */
public final class BallView {

    private final Group group = new Group();
    private final Sphere sphere;

    /** Presentation-only magnification, off by default. See {@link #setMagnified}. */
    private boolean magnified = false;

    public BallView() {
        sphere = new Sphere(BALL_R * SPM, 32);

        PhongMaterial mat = new PhongMaterial(Color.WHITE);
        mat.setDiffuseMap(spinTexture());
        mat.setSpecularColor(Color.web("#fff6e8"));
        mat.setSpecularPower(28);
        sphere.setMaterial(mat);

        group.getChildren().add(sphere);
    }

    public Group node() { return group; }

    /** Place and orient the ball from an interpolated state. */
    public void update(BallState s) {
        Xform.place(group, s.pos());
        group.getTransforms().setAll(Xform.toRotate(s.orient()));
    }

    /** Orientation only, when position comes from an interpolated vector. */
    public void setOrientation(Quat q) {
        group.getTransforms().setAll(Xform.toRotate(q));
    }

    /**
     * Draw the ball at twice life size.
     *
     * A 40 mm ball at an honest camera distance is about nine pixels across, which is what it
     * genuinely looks like across a table -- but it makes the spin pattern too small to read
     * on a projector. This scales the SPRITE only: the collision radius, the aerodynamics and
     * the contact geometry all still use the real 20 mm. The HUD says when it is on, because a
     * physics demo that silently draws things the wrong size is not worth much.
     */
    public void setMagnified(boolean on) {
        magnified = on;
        sphere.setScaleX(on ? 2 : 1);
        sphere.setScaleY(on ? 2 : 1);
        sphere.setScaleZ(on ? 2 : 1);
    }

    public boolean isMagnified() { return magnified; }

    /**
     * A six-by-three chequer wrapped round the sphere, in the orange and white of a training
     * ball.
     *
     * Chequers rather than the more obvious stripe or single spot: a stripe vanishes when the
     * spin axis points at the camera, and one spot spends half of every revolution hidden
     * round the back. A chequer always has a visible edge crossing the silhouette, whatever
     * the axis, which is what makes the rotation legible from behind the table AND from the
     * side view.
     */
    private static WritableImage spinTexture() {
        final int w = 512, h = 256;
        final int uSegments = 6, vSegments = 3;

        Color light = Color.web("#fdfdfb");
        Color dark = Color.web("#e8622a");
        Color seam = Color.web("#8d8f93");

        WritableImage img = new WritableImage(w, h);
        PixelWriter px = img.getPixelWriter();

        double uStep = w / (double) uSegments;
        double vStep = h / (double) vSegments;

        for (int y = 0; y < h; y++) {
            int vi = (int) (y / vStep);
            // Distance to the nearest segment boundary, used to ink a thin seam.
            double vEdge = Math.min(y % vStep, vStep - (y % vStep));

            for (int x = 0; x < w; x++) {
                int ui = (int) (x / uStep);
                double uEdge = Math.min(x % uStep, uStep - (x % uStep));

                Color c = ((ui + vi) % 2 == 0) ? light : dark;

                // The seam sells the rotation: a hard colour edge alone can read as a
                // lighting change, a drawn line cannot.
                if (uEdge < 1.6 || vEdge < 1.6) c = c.interpolate(seam, 0.75);

                px.setColor(x, y, c);
            }
        }
        return img;
    }
}
