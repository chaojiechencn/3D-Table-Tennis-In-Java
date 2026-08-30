package render;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

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
 * What survives is what a PLAYER needs. The legend, because the controls are not guessable.
 * The feed name, because N and P otherwise change something invisible. And the power meter,
 * because charge-and-release is a timed gesture with no other feedback at all -- without it
 * the player is holding a button and hoping.
 */
public final class Hud {

    private static final String MONO = "Consolas, 'DejaVu Sans Mono', monospace";

    /** Power meter geometry, in pixels. */
    private static final double BAR_W = 240, BAR_H = 9;

    private final StackPane root = new StackPane();
    private final Label feed = panelLabel(15, "#eaf2ff");
    private final Label controls = panelLabel(12, "#8d9bab");
    private final Label power = panelLabel(11.5, "#c9d6e4");
    private final Rectangle fill = new Rectangle(0, BAR_H);
    private final VBox meter;

    public Hud() {
        controls.setText("""
            MOUSE   move to aim      hold RIGHT to charge, drag back to set the stroke, release to swing
                    LEFT drag orbits the camera      scroll zooms
            FEED    1-9,0 pick   N/P next,prev   R replay   A auto-replay
            TIME    SPACE pause   . step   [ ] slower,faster
            VIEW    C camera   G ghost (no-spin twin)   T trail   B ball x2   H hud   ESC quit""");

        Rectangle track = new Rectangle(BAR_W, BAR_H);
        track.setFill(Color.web("#1b2129"));
        track.setStroke(Color.web("#8caacd", 0.35));
        fill.setFill(Color.web("#ffc14d"));

        StackPane bar = new StackPane(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        bar.setMaxSize(BAR_W, BAR_H);

        meter = corner(Pos.BOTTOM_CENTER, power);
        meter.getChildren().add(bar);
        meter.setVisible(false);

        root.getChildren().addAll(corner(Pos.TOP_LEFT, feed),
                                  corner(Pos.BOTTOM_LEFT, controls),
                                  meter);
        root.setPickOnBounds(false);      // clicks must reach the SubScene to orbit and swing
        root.setPadding(new Insets(14));
    }

    public Region node() { return root; }

    public void setShown(boolean shown) { root.setVisible(shown); }

    /** The feed currently selected with the number keys. */
    public void setFeed(String name) { feed.setText("feed: " + name); }

    /**
     * The power meter.
     *
     * @param charge   0 to 1, how far the stroke is wound up
     * @param charging whether the button is still held -- the meter is hidden otherwise, so it
     *                 never sits at 0% telling the player something they already know
     */
    public void setCharge(double charge, boolean charging) {
        meter.setVisible(charging);
        if (!charging) return;

        fill.setWidth(BAR_W * Math.max(0, Math.min(1, charge)));
        // Amber to red as it fills, so peak power is readable at a glance rather than by
        // measuring the bar against its own track.
        fill.setFill(Color.web("#ffc14d").interpolate(Color.web("#e04b2f"), charge));
        power.setText(String.format("power %3.0f%%", charge * 100));
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
