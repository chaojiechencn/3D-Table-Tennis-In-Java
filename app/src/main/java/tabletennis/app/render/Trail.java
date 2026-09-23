package tabletennis.app.render;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import tabletennis.engine.Vec3;

import java.util.Collection;

/**
 * A flight path as a fixed pool of fading dots, which freezes the curve so it can be seen. Dots
 * are indexed by age (dot 0 oldest), so each keeps one material for the whole run.
 */
public final class Trail {

    /** Two-pixel dots: the default 64 divisions would cost more triangles than the scene. */
    private static final int DotDivisions = 6;

    private final Group Root = new Group();
    private final Sphere[] Dots;

    public Trail(int Capacity, double RadiusM, Color Oldest, Color Newest) {
        Dots = new Sphere[Capacity];
        for (int I = 0; I < Capacity; I++) {
            double Age = Capacity == 1 ? 1 : I / (double) (Capacity - 1);
            Sphere S = new Sphere(Xform.Length(RadiusM), DotDivisions);
            PhongMaterial M = new PhongMaterial(Oldest.interpolate(Newest, Age));
            M.setSpecularColor(Color.TRANSPARENT);   // a highlight reads as a second ball
            S.setMaterial(M);
            S.setVisible(false);
            Dots[I] = S;
            Root.getChildren().add(S);
        }
    }

    public Group Node() { return Root; }

    /** Oldest first; the newest point always lands on the last dot, so the ramp ends at the ball. */
    public void SetPath(Collection<Vec3> Path) {
        int N = Math.min(Path.size(), Dots.length);
        int Skip = Path.size() - N;
        int Slot = Dots.length - N;
        int Seen = 0;
        for (Vec3 P : Path) {
            if (Seen++ < Skip) continue;
            Sphere S = Dots[Slot++];
            Xform.Place(S, P);
            S.setVisible(true);
        }
        for (int I = 0; I < Dots.length - N; I++) Dots[I].setVisible(false);
    }

    public void Clear() {
        for (Sphere S : Dots) S.setVisible(false);
    }

    public void SetShown(boolean Shown) { Root.setVisible(Shown); }
}
