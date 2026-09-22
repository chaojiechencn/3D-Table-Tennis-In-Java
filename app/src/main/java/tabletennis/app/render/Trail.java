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
    private static final int DOT_DIVISIONS = 6;

    private final Group group = new Group();
    private final Sphere[] dots;

    public Trail(int capacity, double radiusM, Color oldest, Color newest) {
        dots = new Sphere[capacity];
        for (int i = 0; i < capacity; i++) {
            double age = capacity == 1 ? 1 : i / (double) (capacity - 1);
            Sphere s = new Sphere(Xform.length(radiusM), DOT_DIVISIONS);
            PhongMaterial m = new PhongMaterial(oldest.interpolate(newest, age));
            m.setSpecularColor(Color.TRANSPARENT);   // a highlight reads as a second ball
            s.setMaterial(m);
            s.setVisible(false);
            dots[i] = s;
            group.getChildren().add(s);
        }
    }

    public Group node() { return group; }

    /** Oldest first; the newest point always lands on the last dot, so the ramp ends at the ball. */
    public void setPath(Collection<Vec3> path) {
        int n = Math.min(path.size(), dots.length);
        int skip = path.size() - n;
        int slot = dots.length - n;
        int seen = 0;
        for (Vec3 p : path) {
            if (seen++ < skip) continue;
            Sphere s = dots[slot++];
            Xform.place(s, p);
            s.setVisible(true);
        }
        for (int i = 0; i < dots.length - n; i++) dots[i].setVisible(false);
    }

    public void clear() {
        for (Sphere s : dots) s.setVisible(false);
    }

    public void setShown(boolean shown) { group.setVisible(shown); }
}
