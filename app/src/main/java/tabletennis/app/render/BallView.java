package tabletennis.app.render;

import javafx.scene.Group;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import tabletennis.engine.BallState;

import static tabletennis.engine.Constants.BALL_R;

/** The ball, painted and rotated so its spin is visible; a plain sphere looks the same at any spin. */
public final class BallView {

    private static final int TEXTURE_WIDTH = 512, TEXTURE_HEIGHT = 256;
    private static final int U_SEGMENTS = 6, V_SEGMENTS = 3;
    private static final double SEAM_WIDTH = 1.6;

    private final Group group = new Group();
    private final Sphere sphere;
    private final BallShadow shadow = new BallShadow();
    private boolean magnified = false;

    public BallView() {
        sphere = new Sphere(Xform.length(BALL_R), 32);
        PhongMaterial mat = new PhongMaterial(Color.WHITE);
        mat.setDiffuseMap(spinTexture(1));
        // A faint self-lit copy stands in for room bounce, keeping the underside trackable.
        mat.setSelfIlluminationMap(spinTexture(0.14));
        mat.setSpecularColor(Color.gray(0.32));
        mat.setSpecularPower(42);
        sphere.setMaterial(mat);
        group.getChildren().add(sphere);
    }

    public Group node() { return group; }

    /** A sibling of the rotating group: the shadow must never inherit ball spin. */
    public Group shadowNode() { return shadow.node(); }

    public void update(BallState s) {
        Xform.place(group, s.pos());
        group.getTransforms().setAll(Xform.toRotate(s.orient()));
        shadow.update(s.pos(), magnified);
    }

    /** Twice life size for projectors; the sprite only, never the physics radius. */
    public void setMagnified(boolean on) {
        magnified = on;
        double scale = on ? 2 : 1;
        sphere.setScaleX(scale);
        sphere.setScaleY(scale);
        sphere.setScaleZ(scale);
    }

    public boolean isMagnified() { return magnified; }

    /**
     * An inked six-by-three chequer: a stripe vanishes end-on and a spot hides round the back,
     * but a chequer always has an edge crossing the silhouette.
     */
    private static WritableImage spinTexture(double brightness) {
        Color light = Color.web("#fdfdfb");
        Color dark = Color.web("#e8622a");
        Color seam = Color.web("#8d8f93");

        WritableImage img = new WritableImage(TEXTURE_WIDTH, TEXTURE_HEIGHT);
        PixelWriter px = img.getPixelWriter();
        double uStep = TEXTURE_WIDTH / (double) U_SEGMENTS;
        double vStep = TEXTURE_HEIGHT / (double) V_SEGMENTS;

        for (int y = 0; y < TEXTURE_HEIGHT; y++) {
            int vi = (int) (y / vStep);
            double vEdge = Math.min(y % vStep, vStep - (y % vStep));
            for (int x = 0; x < TEXTURE_WIDTH; x++) {
                int ui = (int) (x / uStep);
                double uEdge = Math.min(x % uStep, uStep - (x % uStep));
                Color c = ((ui + vi) % 2 == 0) ? light : dark;
                if (uEdge < SEAM_WIDTH || vEdge < SEAM_WIDTH) c = c.interpolate(seam, 0.75);
                px.setColor(x, y, c.deriveColor(0, 1, brightness, 1));
            }
        }
        return img;
    }
}
