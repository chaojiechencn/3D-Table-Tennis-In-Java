package render;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The key legend, and the one readout the controls cannot work without.
 *
 * This used to be a panel of live physics numbers -- spin ratio, lift coefficient, the Magnus
 * acceleration next to gravity -- which made the simulation falsifiable while you watched it.
 * That was the right thing for a physics demo with no game around it. It is the wrong thing
 * now: the numbers are debug output on what is meant to be a playable screen, and
 * physics.SelfTest checks them far harder than a human reading a panel ever could, against
 * published values rather than against plausibility.
 *
 * What survives is what a PLAYER needs: the legend, because the controls are not guessable, and
 * the feed name, because N and P otherwise change something invisible. (There was a power meter
 * here too, for the charge-and-release stroke. The stroke is gone -- the paddle just follows
 * the mouse now -- and the meter went with it.)
 *
 * The one exception is the shot line, and it is only on while V is: it prints the numbers from
 * the shot-assist overlay that cannot be drawn as an arrow in the 3D scene -- launch speed,
 * spin, how many correction passes the shot needed, and whether it came out legal.
 */
public final class Hud {

    private static final String MONO = "Consolas, 'DejaVu Sans Mono', monospace";

    private final StackPane root = new StackPane();
    private final Label feed = panelLabel(15, "#eaf2ff");
    private final Label shot = panelLabel(12, "#ff9a3c");
    private final Label controls = panelLabel(12, "#8d9bab");
    private final Label control = panelLabel(12, "#7fd4a8");

    public Hud() {
        controls.setText("""
            MOUSE   move to move the paddle -- swing through the ball to hit
                    (how you move through the ball aims the shot; the game keeps it in)
                    LEFT drag orbits the camera      scroll zooms
            FEED    1-9,0 pick   N/P next,prev   R replay   A auto-replay
            TIME    SPACE pause   . step   [ ] slower,faster
            VIEW    F rally-cam on/off   C preset view
                    V shot debug   D control debug
                    G ghost   T trail   B ball x2   H hud   ESC quit""");

        setShot(null);
        root.getChildren().addAll(corner(Pos.TOP_LEFT, feed, shot),
                                  corner(Pos.TOP_RIGHT, control),
                                  corner(Pos.BOTTOM_LEFT, controls));

        // After the panels are built, not before: setControl hides the whole panel, which it
        // reaches through the label's parent, and the label has no parent until corner() has
        // wrapped it.
        setControl(null);
        root.setPickOnBounds(false);      // clicks must reach the SubScene to orbit and aim
        root.setPadding(new Insets(14));
    }

    public Region node() { return root; }

    public void setShown(boolean shown) { root.setVisible(shown); }

    /** The feed currently selected with the number keys. */
    public void setFeed(String name) { feed.setText("feed: " + name); }

    /** The shot-assist readout, or null to hide the line entirely (V off, or nothing hit yet).
     *  It has to go unmanaged as well as invisible or it leaves a blank row in the panel. */
    public void setShot(String text) {
        boolean on = text != null && !text.isEmpty();
        shot.setText(on ? text : "");
        shot.setVisible(on);
        shot.setManaged(on);
    }

    /**
     * The control/reachability readout, or null to hide it (D off).
     *
     * Its own panel rather than another line under the feed, because it is nine lines of
     * numbers refreshed every frame and it would otherwise shove the feed name around as it
     * grew and shrank. Same unmanaged-when-hidden trick as the shot line: invisible alone
     * leaves an empty panel sitting on the table.
     */
    public void setControl(String text) {
        boolean on = text != null && !text.isEmpty();
        control.setText(on ? text : "");
        control.setVisible(on);
        control.setManaged(on);
        control.getParent().setVisible(on);
        control.getParent().setManaged(on);
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
     * entire window and ends up dimming the table it is sitting on.
     */
    private static VBox corner(Pos where, javafx.scene.Node... children) {
        VBox v = new VBox(6, children);
        v.setPadding(new Insets(12, 16, 12, 16));
        v.setStyle("-fx-background-color: rgba(10,14,20,0.74);"
                 + "-fx-background-radius: 8;"
                 + "-fx-border-color: rgba(140,170,205,0.22);"
                 + "-fx-border-radius: 8;");
        v.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        v.setAlignment(Pos.CENTER_LEFT);
        StackPane.setAlignment(v, where);
        return v;
    }
}
