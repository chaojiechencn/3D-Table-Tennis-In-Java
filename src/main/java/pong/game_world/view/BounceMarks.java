package pong.game_world.view;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;

import java.util.List;

import static pong.config.Physical.BALL_R;
import pong.core.math.Vec3;
import pong.helpers.Xform;

/**
 * Flat discs left where the ball touched down.
 *
 * These are the evidence that the demo is deciding correctly whether a shot is in or out. The
 * ball is past in a fraction of a second and the eye cannot pin down where it hit relative to
 * the end line; a mark sitting on the table can be compared with the painted line at leisure.
 */
public final class BounceMarks {

    private final Group group = new Group();
    private final Cylinder[] discs;

    public BounceMarks(int capacity) {
        discs = new Cylinder[capacity];
        for (int i = 0; i < capacity; i++) {
            // Oldest marks fade toward the table colour rather than vanishing, so the order
            // of a rally stays readable.
            double age = capacity == 1 ? 1 : i / (double) (capacity - 1);
            Cylinder c = new Cylinder(Xform.length(BALL_R * 1.05),
                    Xform.length(BALL_R * 0.04), 16);

            PhongMaterial m = new PhongMaterial(
                    Color.web("#2f6da0").interpolate(Color.web("#ffd24a"), age));
            m.setSpecularColor(Color.TRANSPARENT);
            c.setMaterial(m);
            c.setVisible(false);

            discs[i] = c;
            group.getChildren().add(c);
        }
    }

    public Group node() { return group; }

    /** @param marks bounce positions, oldest first */
    public void setMarks(List<Vec3> marks) {
        int n = Math.min(marks.size(), discs.length);
        int skip = marks.size() - n;

        for (int i = 0; i < n; i++) {
            Cylinder c = discs[discs.length - n + i];
            // Lifted just clear of the painted lines so a mark on the end line still shows.
            Xform.place(c, marks.get(skip + i).x(), 0.0022, marks.get(skip + i).z());
            c.setVisible(true);
        }
        for (int i = 0; i < discs.length - n; i++) discs[i].setVisible(false);
    }

    public void clear() {
        for (Cylinder c : discs) c.setVisible(false);
    }
}
