package tabletennis.app.scene;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import tabletennis.engine.math.Vec3;

import java.util.Collection;

/**
 * A flight path as a fixed pool of fading dots, which freezes the curve so it can be seen. Dots
 * are indexed by age (dot 0 oldest), so each keeps one material for the whole run.
 */
final class Trail {

    /** Two-pixel dots: the default 64 divisions would cost more triangles than the scene. */
    private static final int DotDivisions = 6;

    private final Group Root = new Group();
    private final Sphere[] Dots;

    Trail(int Capacity, double RadiusMetres, Color Oldest, Color Newest) {
        Dots = new Sphere[Capacity];
        for (int Index = 0; Index < Capacity; Index++) {
            double Age = Capacity == 1 ? 1 : Index / (double) (Capacity - 1);
            Sphere Dot = new Sphere(Xform.Length(RadiusMetres), DotDivisions);
            PhongMaterial Paint = new PhongMaterial(Oldest.interpolate(Newest, Age));
            Paint.setSpecularColor(Color.TRANSPARENT);   // a highlight reads as a second ball
            Dot.setMaterial(Paint);
            Dot.setVisible(false);
            Dots[Index] = Dot;
            Root.getChildren().add(Dot);
        }
    }

    Group Node() { return Root; }

    /** Oldest first; the newest point always lands on the last dot, so the ramp ends at the ball. */
    void SetPath(Collection<Vec3> Path) {
        int Shown = Math.min(Path.size(), Dots.length);
        int Skip = Path.size() - Shown;
        int Slot = Dots.length - Shown;
        int Seen = 0;
        for (Vec3 Point : Path) {
            if (Seen++ < Skip) continue;
            Sphere Dot = Dots[Slot++];
            Xform.Place(Dot, Point);
            Dot.setVisible(true);
        }
        for (int Index = 0; Index < Dots.length - Shown; Index++) Dots[Index].setVisible(false);
    }

    void Clear() {
        for (Sphere Dot : Dots) Dot.setVisible(false);
    }

    void SetShown(boolean Shown) { Root.setVisible(Shown); }
}
