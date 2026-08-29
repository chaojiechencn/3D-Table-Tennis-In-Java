package render;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import physics.*;

import static physics.Constants.*;

/**
 * The live readouts.
 *
 * The point of the panel on the left is that it makes the simulation falsifiable while you
 * watch it. Anyone can draw a ball on a curve; showing the spin ratio, the lift coefficient
 * it produces and the resulting sideways acceleration -- next to gravity, in the same units --
 * lets a viewer check that the curve on screen is the one those numbers imply.
 */
public final class Hud {

    private static final String MONO = "Consolas, 'DejaVu Sans Mono', monospace";

    private final StackPane root = new StackPane();
    private final Label shot = panelLabel(15, "#eaf2ff");
    private final Label readout = panelLabel(13, "#c9d6e4");
    private final Label events = panelLabel(12.5, "#c9d6e4");
    private final Label status = panelLabel(12.5, "#93a3b5");
    private final Label controls = panelLabel(12, "#8d9bab");

    public Hud() {
        VBox left = corner(Pos.TOP_LEFT, shot, readout);
        VBox right = corner(Pos.TOP_RIGHT, events);
        VBox bottom = corner(Pos.BOTTOM_LEFT, status, controls);

        root.getChildren().addAll(left, right, bottom);
        root.setPickOnBounds(false);      // clicks must reach the SubScene to orbit the camera
        root.setPadding(new Insets(14));

        controls.setText("""
            1-9,0 shot   N/P next,prev   R replay   SPACE pause   . step
            [ ] speed    G ghost (no-spin twin)     T trail       B ball x2
            C camera     drag orbit   scroll zoom   H hud         ESC quit
            A auto-replay""");
    }

    public Region node() { return root; }

    public void setShown(boolean shown) {
        root.setVisible(shown);
    }

    /**
     * @param interp   the interpolated state actually on screen, not the raw physics state,
     *                 so the numbers agree with the picture
     */
    public void update(World world, BallState interp, Shots current,
                       double timeScale, boolean paused, double fps, int stepsLastFrame,
                       boolean magnified, String viewLabel, boolean ghostOn) {

        shot.setText(current.name() + "\n" + current.detail());

        Vec3 v = interp.vel(), w = interp.spin();
        Vec3 magnus = Aero.magnus(v, w);
        Vec3 drag = Aero.drag(v);

        // Spin split the way a player would describe it: how much is top or back, how much is
        // side. The raw vector is correct but nobody reads (-412, 96, 0) as "heavy topspin".
        Vec3 heading = new Vec3(v.x(), 0, v.z()).normalized();
        double topRevs = 0, sideRevs = w.y() / (2 * Math.PI);
        if (heading.lengthSquared() > 0) {
            topRevs = w.dot(Vec3.UP.cross(heading)) / (2 * Math.PI);
        }

        readout.setText(String.format("""
            speed      %6.2f m/s   (%5.1f km/h)
            spin       %6.0f rev/s  top %+.0f  side %+.0f
            spin ratio %6.3f  ->  C_L %5.3f     C_d %5.3f
            Magnus     %6.2f m/s^2  (gravity %.2f)
            drag       %6.2f m/s^2
            height     %6.3f m      apex %5.3f m
            position   x %+5.2f  z %+5.2f
            bounces    %6d        t %6.2f s""",
            v.length(), v.length() * 3.6,
            interp.spinRevsPerSec(), topRevs, sideRevs,
            Aero.spinRatio(v, w), Aero.liftCoefficient(v, w), C_DRAG,
            magnus.length(), G,
            drag.length(),
            interp.pos().y(), world.apex(),
            interp.pos().x(), interp.pos().z(),
            world.tableBounces(), world.time()));

        StringBuilder log = new StringBuilder("events\n");
        var list = world.events();
        if (list.isEmpty()) {
            log.append("  (none yet)");
        } else {
            for (int i = Math.max(0, list.size() - 7); i < list.size(); i++) {
                World.Event e = list.get(i);
                log.append(String.format("  %5.2fs  %-17s %5.2f m/s%n",
                        e.time(), e.label(), e.speed()));
            }
        }
        events.setText(log.toString().stripTrailing());

        StringBuilder s = new StringBuilder();
        s.append(String.format("%s | %.0f fps | %d physics steps last frame | dt = 1/%.0f s",
                paused ? "PAUSED" : String.format("%.2fx speed", timeScale),
                fps, stepsLastFrame, 1 / DT));
        s.append("\ncamera: ").append(viewLabel);
        if (ghostOn) s.append("   |   grey trail = same shot with the spin removed");
        if (magnified) s.append("\nNOTE: ball drawn at 2x for visibility - physics still uses 40 mm");
        status.setText(s.toString());
    }

    // ------------------------------------------------------------------ styling

    private static Label panelLabel(double size, String colour) {
        Label l = new Label();
        l.setStyle(String.format("-fx-font-family: %s; -fx-font-size: %.1fpx; -fx-text-fill: %s;",
                                 MONO, size, colour));
        return l;
    }

    /**
     * A panel pinned to one corner.
     *
     * The max size has to be pinned to the preferred size. A StackPane child defaults to
     * filling the whole pane, so without this each translucent panel stretches across the
     * entire window and the readouts end up dimming the table they are describing.
     */
    private static VBox corner(Pos where, Label... labels) {
        VBox v = new VBox(6, labels);
        v.setPadding(new Insets(12, 16, 12, 16));
        v.setStyle("-fx-background-color: rgba(10,14,20,0.74);"
                 + "-fx-background-radius: 8;"
                 + "-fx-border-color: rgba(140,170,205,0.22);"
                 + "-fx-border-radius: 8;");
        v.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(v, where);
        return v;
    }
}
