package tabletennis.app.scene;

import javafx.scene.Group;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;

/** The ball, painted and rotated so its spin is visible; a plain sphere looks the same at any spin. */
final class BallView {

    private static final int TextureWidth = 512, TextureHeight = 256;
    private static final int USegments = 6, VSegments = 3;
    private static final double SeamWidth = 1.6;
    private static final int SphereDivisions = 32;

    private final Group Root = new Group();
    private final Sphere Sprite = new Sphere(Xform.Length(BallSpec.Radius), SphereDivisions);
    private final BallShadow Shadow = new BallShadow();
    private boolean Magnified = false;

    BallView() {
        PhongMaterial Paint = new PhongMaterial(Color.WHITE);
        Paint.setDiffuseMap(SpinTexture(1));
        // A faint self-lit copy stands in for room bounce, keeping the underside trackable.
        Paint.setSelfIlluminationMap(SpinTexture(0.14));
        Paint.setSpecularColor(Color.gray(0.32));
        Paint.setSpecularPower(42);
        Sprite.setMaterial(Paint);
        Root.getChildren().add(Sprite);
    }

    Group Node() { return Root; }

    /** A sibling of the rotating group: the shadow must never inherit the ball's spin. */
    Group ShadowNode() { return Shadow.Node(); }

    void Update(BallState Ball) {
        Xform.Place(Root, Ball.Position());
        Root.getTransforms().setAll(Xform.ToRotate(Ball.Orientation()));
        Shadow.Update(Ball.Position(), Magnified);
    }

    /** Twice life size for projectors; the sprite only, never the physics radius. */
    void SetMagnified(boolean On) {
        Magnified = On;
        double Scale = On ? 2 : 1;
        Sprite.setScaleX(Scale);
        Sprite.setScaleY(Scale);
        Sprite.setScaleZ(Scale);
    }

    boolean IsMagnified() { return Magnified; }

    /**
     * An inked six-by-three chequer: a stripe vanishes end-on and a spot hides round the back,
     * but a chequer always has an edge crossing the silhouette.
     */
    private static WritableImage SpinTexture(double Brightness) {
        Color Light = Color.web("#fdfdfb");
        Color Dark = Color.web("#e8622a");
        Color Seam = Color.web("#8d8f93");

        WritableImage Image = new WritableImage(TextureWidth, TextureHeight);
        PixelWriter Pixels = Image.getPixelWriter();
        double UStep = TextureWidth / (double) USegments;
        double VStep = TextureHeight / (double) VSegments;

        for (int Y = 0; Y < TextureHeight; Y++) {
            int Row = (int) (Y / VStep);
            double VEdge = Math.min(Y % VStep, VStep - (Y % VStep));
            for (int X = 0; X < TextureWidth; X++) {
                int Column = (int) (X / UStep);
                double UEdge = Math.min(X % UStep, UStep - (X % UStep));
                Color Pixel = ((Column + Row) % 2 == 0) ? Light : Dark;
                if (UEdge < SeamWidth || VEdge < SeamWidth) Pixel = Pixel.interpolate(Seam, 0.75);
                Pixels.setColor(X, Y, Pixel.deriveColor(0, 1, Brightness, 1));
            }
        }
        return Image;
    }
}
