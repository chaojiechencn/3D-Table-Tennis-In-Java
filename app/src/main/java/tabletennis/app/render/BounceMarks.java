package tabletennis.app.render;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import tabletennis.engine.Vec3;

import java.util.List;

import static tabletennis.engine.Constants.BallR;

/** Discs where the ball landed, so in-or-out can be judged against the lines at leisure. */
public final class BounceMarks {

    /** Just clear of the painted lines, so a mark on the end line still shows. */
    private static final double MarkHeight = 0.0022;

    private final Group Root = new Group();
    private final Cylinder[] Discs;

    public BounceMarks(int Capacity) {
        Discs = new Cylinder[Capacity];
        for (int I = 0; I < Capacity; I++) {
            double Age = Capacity == 1 ? 1 : I / (double) (Capacity - 1);
            Cylinder C = new Cylinder(Xform.Length(BallR * 1.05), Xform.Length(BallR * 0.04), 16);
            // Old marks fade toward the table colour, so a rally's order stays readable.
            PhongMaterial M = new PhongMaterial(Color.web("#2f6da0").interpolate(Color.web("#ffd24a"), Age));
            M.setSpecularColor(Color.TRANSPARENT);
            C.setMaterial(M);
            C.setVisible(false);
            Discs[I] = C;
            Root.getChildren().add(C);
        }
    }

    public Group Node() { return Root; }

    /** Oldest first. */
    public void SetMarks(List<Vec3> Marks) {
        int N = Math.min(Marks.size(), Discs.length);
        int Skip = Marks.size() - N;
        for (int I = 0; I < N; I++) {
            Cylinder C = Discs[Discs.length - N + I];
            Vec3 Mark = Marks.get(Skip + I);
            Xform.Place(C, Mark.X(), MarkHeight, Mark.Z());
            C.setVisible(true);
        }
        for (int I = 0; I < Discs.length - N; I++) Discs[I].setVisible(false);
    }

    public void Clear() {
        for (Cylinder C : Discs) C.setVisible(false);
    }
}
