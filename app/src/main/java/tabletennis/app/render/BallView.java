package tabletennis.app.render;

import javafx.scene.Group;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import tabletennis.engine.BallState;

import static tabletennis.engine.Constants.BallR;

/** The ball, painted and rotated so its spin is visible; a plain sphere looks the same at any spin. */
public final class BallView {

    private static final int TextureWidth = 512, TextureHeight = 256;
    private static final int USegments = 6, VSegments = 3;
    private static final double SeamWidth = 1.6;

    private final Group Root = new Group();
    private final Sphere Sprite;
    private final BallShadow Shadow = new BallShadow();
    private boolean Magnified = false;

    public BallView() {
        Sprite = new Sphere(Xform.Length(BallR), 32);
        PhongMaterial Mat = new PhongMaterial(Color.WHITE);
        Mat.setDiffuseMap(SpinTexture(1));
        // A faint self-lit copy stands in for room bounce, keeping the underside trackable.
        Mat.setSelfIlluminationMap(SpinTexture(0.14));
        Mat.setSpecularColor(Color.gray(0.32));
        Mat.setSpecularPower(42);
        Sprite.setMaterial(Mat);
        Root.getChildren().add(Sprite);
    }

    public Group Node() { return Root; }

    /** A sibling of the rotating group: the shadow must never inherit ball spin. */
    public Group ShadowNode() { return Shadow.Node(); }

    public void Update(BallState S) {
        Xform.Place(Root, S.Pos());
        Root.getTransforms().setAll(Xform.ToRotate(S.Orient()));
        Shadow.Update(S.Pos(), Magnified);
    }

    /** Twice life size for projectors; the sprite only, never the physics radius. */
    public void SetMagnified(boolean On) {
        Magnified = On;
        double Scale = On ? 2 : 1;
        Sprite.setScaleX(Scale);
        Sprite.setScaleY(Scale);
        Sprite.setScaleZ(Scale);
    }

    public boolean IsMagnified() { return Magnified; }

    /**
     * An inked six-by-three chequer: a stripe vanishes end-on and a spot hides round the back,
     * but a chequer always has an edge crossing the silhouette.
     */
    private static WritableImage SpinTexture(double Brightness) {
        Color Light = Color.web("#fdfdfb");
        Color Dark = Color.web("#e8622a");
        Color Seam = Color.web("#8d8f93");

        WritableImage Img = new WritableImage(TextureWidth, TextureHeight);
        PixelWriter Px = Img.getPixelWriter();
        double UStep = TextureWidth / (double) USegments;
        double VStep = TextureHeight / (double) VSegments;

        for (int Y = 0; Y < TextureHeight; Y++) {
            int Vi = (int) (Y / VStep);
            double VEdge = Math.min(Y % VStep, VStep - (Y % VStep));
            for (int X = 0; X < TextureWidth; X++) {
                int Ui = (int) (X / UStep);
                double UEdge = Math.min(X % UStep, UStep - (X % UStep));
                Color C = ((Ui + Vi) % 2 == 0) ? Light : Dark;
                if (UEdge < SeamWidth || VEdge < SeamWidth) C = C.interpolate(Seam, 0.75);
                Px.setColor(X, Y, C.deriveColor(0, 1, Brightness, 1));
            }
        }
        return Img;
    }
}
