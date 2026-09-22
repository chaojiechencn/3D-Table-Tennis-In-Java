package tabletennis.app.render;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import tabletennis.engine.Vec3;

import java.util.List;

import static tabletennis.engine.Constants.BALL_R;

/** Discs where the ball landed, so in-or-out can be judged against the lines at leisure. */
public final class BounceMarks {

    /** Just clear of the painted lines, so a mark on the end line still shows. */
    private static final double MARK_HEIGHT = 0.0022;

    private final Group group = new Group();
    private final Cylinder[] discs;

    public BounceMarks(int capacity) {
        discs = new Cylinder[capacity];
        for (int i = 0; i < capacity; i++) {
            double age = capacity == 1 ? 1 : i / (double) (capacity - 1);
            Cylinder c = new Cylinder(Xform.length(BALL_R * 1.05), Xform.length(BALL_R * 0.04), 16);
            // Old marks fade toward the table colour, so a rally's order stays readable.
            PhongMaterial m = new PhongMaterial(Color.web("#2f6da0").interpolate(Color.web("#ffd24a"), age));
            m.setSpecularColor(Color.TRANSPARENT);
            c.setMaterial(m);
            c.setVisible(false);
            discs[i] = c;
            group.getChildren().add(c);
        }
    }

    public Group node() { return group; }

    /** Oldest first. */
    public void setMarks(List<Vec3> marks) {
        int n = Math.min(marks.size(), discs.length);
        int skip = marks.size() - n;
        for (int i = 0; i < n; i++) {
            Cylinder c = discs[discs.length - n + i];
            Vec3 mark = marks.get(skip + i);
            Xform.place(c, mark.x(), MARK_HEIGHT, mark.z());
            c.setVisible(true);
        }
        for (int i = 0; i < discs.length - n; i++) discs[i].setVisible(false);
    }

    public void clear() {
        for (Cylinder c : discs) c.setVisible(false);
    }
}
