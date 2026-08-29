package render;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import physics.Vec3;

import java.util.Collection;

import static render.Xform.SPM;

/**
 * A path through the air, drawn as a run of fading dots.
 *
 * This carries most of the visual argument of the checkpoint. A ball is 4 cm across and, at
 * honest scale, spends about half a second on screen -- watching it live, you cannot actually
 * tell a curved flight from a straight one. The trail freezes the shape of the flight so the
 * curve is there to be looked at.
 *
 * Implementation note: JavaFX has no 3D polyline, and a TriangleMesh ribbon has to be rebuilt
 * every frame and twists badly where the path turns sharply. A fixed pool of small spheres
 * costs one setTranslate per dot per frame and cannot degenerate.
 *
 * The pool is indexed by AGE, not by ring-buffer slot: dot 0 is always the oldest point on
 * the path. That is what lets each dot keep one immutable material for the whole run instead
 * of re-tinting every dot every frame.
 */
public final class Trail {

    private final Group group = new Group();
    private final Sphere[] dots;

    /**
     * @param capacity   how many points the trail can show
     * @param radiusM    dot radius in metres
     * @param oldest     colour of the far end of the trail
     * @param newest     colour at the ball
     */
    public Trail(int capacity, double radiusM, Color oldest, Color newest) {
        dots = new Sphere[capacity];
        for (int i = 0; i < capacity; i++) {
            double age = capacity == 1 ? 1 : i / (double) (capacity - 1);

            // 6 divisions, not the default 64. These are two-pixel dots and there are
            // hundreds of them per trail; at the default tessellation the trails alone cost
            // more triangles than the entire rest of the scene and the frame rate halves.
            Sphere s = new Sphere(radiusM * SPM, 6);
            PhongMaterial m = new PhongMaterial(oldest.interpolate(newest, age));
            // Trail dots are markers, not objects in the world: a specular highlight on them
            // reads as a second, smaller ball and is actively confusing.
            m.setSpecularColor(Color.TRANSPARENT);
            s.setMaterial(m);
            s.setVisible(false);
            dots[i] = s;
            group.getChildren().add(s);
        }
    }

    public Group node() { return group; }

    /**
     * Show a path, oldest point first. Extra points beyond capacity drop the oldest.
     *
     * Takes a Collection and walks it with an iterator rather than taking a List and
     * indexing it, so the caller can hand over its live deque instead of copying it into a
     * fresh list on every frame.
     */
    public void setPath(Collection<Vec3> path) {
        int n = Math.min(path.size(), dots.length);
        int skip = path.size() - n;                 // keep the newest points

        // Map the newest point to the LAST dot, so the colour ramp always ends at the ball
        // even while the trail is still filling up.
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
