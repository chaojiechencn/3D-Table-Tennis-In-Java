package tabletennis.app.scene;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import tabletennis.engine.BallSpec;
import tabletennis.engine.math.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Discs where the ball landed this rally, so in-or-out can be judged against the lines at leisure. */
final class BounceMarks {

    /** Just clear of the painted lines, so a mark on the end line still shows. */
    private static final double MarkHeight = 0.0022;
    private static final int DiscDivisions = 16;

    private final Group Root = new Group();
    private final Cylinder[] Discs;
    private final List<Vec3> Marks = new ArrayList<>();

    BounceMarks(int Capacity) {
        Discs = new Cylinder[Capacity];
        for (int Index = 0; Index < Capacity; Index++) {
            double Age = Capacity == 1 ? 1 : Index / (double) (Capacity - 1);
            Cylinder Disc = new Cylinder(Xform.Length(BallSpec.Radius * 1.05), Xform.Length(BallSpec.Radius * 0.04),
                                         DiscDivisions);
            // Old marks fade toward the table colour, so a rally's order stays readable.
            PhongMaterial Paint = new PhongMaterial(Color.web("#2f6da0").interpolate(Color.web("#ffd24a"), Age));
            Paint.setSpecularColor(Color.TRANSPARENT);
            Disc.setMaterial(Paint);
            Disc.setVisible(false);
            Discs[Index] = Disc;
            Root.getChildren().add(Disc);
        }
    }

    Group Node() { return Root; }

    /** The newest mark takes the brightest disc; the oldest beyond capacity is dropped. */
    void Add(Vec3 Landing) {
        Marks.add(Landing);
        while (Marks.size() > Discs.length) Marks.remove(0);
        Redraw();
    }

    void Clear() {
        Marks.clear();
        for (Cylinder Disc : Discs) Disc.setVisible(false);
    }

    private void Redraw() {
        int Shown = Marks.size();
        for (int Index = 0; Index < Shown; Index++) {
            Cylinder Disc = Discs[Discs.length - Shown + Index];
            Vec3 Mark = Marks.get(Index);
            Xform.Place(Disc, Mark.X(), MarkHeight, Mark.Z());
            Disc.setVisible(true);
        }
        for (int Index = 0; Index < Discs.length - Shown; Index++) Discs[Index].setVisible(false);
    }
}
