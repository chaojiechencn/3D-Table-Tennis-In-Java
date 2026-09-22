package render;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import play.Scoreboard;

/**
 * What a player needs on screen: the score, the feed name, the key legend, and the V and D debug
 * readouts while they are toggled on.
 */
public final class Hud {

    private static final String MONO = "Consolas, 'DejaVu Sans Mono', monospace";

    private static final String LEGEND = """
            MOUSE   move to move the paddle -- swing through the ball to hit
                    (how you move through the ball aims the shot; hit it CLEAN or it goes out)
                    RIGHT hold = brush: mouse up/down lifts/cuts the bat for spin
                    LEFT drag orbits the camera      scroll zooms
            FEED    1-9,0 pick   N/P next,prev   R replay   A auto-replay
            TIME    SPACE pause   . step   [ ] slower,faster
            VIEW    F rally-cam on/off   C preset view
                    V shot debug   D control debug
                    G ghost   T trail   B ball x2   H hud   ESC quit
            DEMO    M watch it play itself""";

    private final StackPane root = new StackPane();
    private final Label score = panelLabel(20, "#ffffff");   // the one number a player looks up for
    private final Label feed = panelLabel(15, "#eaf2ff");
    private final Label shot = panelLabel(12, "#ff9a3c");
    private final Label legend = panelLabel(12, "#8d9bab");
    private final Label controlReadout = panelLabel(12, "#7fd4a8");

    public Hud() {
        legend.setText(LEGEND);
        setShot(null);
        root.getChildren().addAll(corner(Pos.TOP_LEFT, feed, shot),
                                  corner(Pos.TOP_CENTER, score),
                                  corner(Pos.TOP_RIGHT, controlReadout),
                                  corner(Pos.BOTTOM_LEFT, legend));
        setControl(null);                  // only once corner() has given the label a parent
        root.setPickOnBounds(false);       // clicks must reach the SubScene to orbit and aim
        root.setPadding(new Insets(14));
    }

    public Region node() { return root; }

    public void setShown(boolean shown) { root.setVisible(shown); }

    public void setFeed(String name) { feed.setText("feed: " + name); }

    public void setScore(Scoreboard.Snapshot s) {
        score.setText(scoreLine(s));
    }

    /** The serving dot sits by the server's score; "deuce" is spelled out because the rule changes. */
    static String scoreLine(Scoreboard.Snapshot s) {
        String games = "games " + s.playerGames() + " - " + s.opponentGames();
        if (s.matchWinner() != null) {
            return (s.matchWinner() == Scoreboard.Side.PLAYER ? "YOU WIN" : "OPPONENT WINS") + "   " + games;
        }
        String youDot = s.server() == Scoreboard.Side.PLAYER ? "* " : "  ";
        String oppDot = s.server() == Scoreboard.Side.OPPONENT ? " *" : "  ";
        return games + "    " + youDot + s.playerPoints() + " - " + s.opponentPoints() + oppDot
             + (s.deuce() ? "   deuce" : "");
    }

    /** Null hides the line; unmanaged as well as invisible, or it leaves a blank row. */
    public void setShot(String text) {
        showLabel(shot, text);
    }

    /** Null hides the whole D panel, not just its text. */
    public void setControl(String text) {
        boolean on = showLabel(controlReadout, text);
        controlReadout.getParent().setVisible(on);
        controlReadout.getParent().setManaged(on);
    }

    private static boolean showLabel(Label label, String text) {
        boolean on = text != null && !text.isEmpty();
        label.setText(on ? text : "");
        label.setVisible(on);
        label.setManaged(on);
        return on;
    }

    private static Label panelLabel(double size, String colour) {
        Label l = new Label();
        l.setStyle(String.format("-fx-font-family: %s; -fx-font-size: %.1fpx; -fx-text-fill: %s;",
                                 MONO, size, colour));
        return l;
    }

    /** Max size pinned to preferred, or each translucent panel stretches across the window. */
    private static VBox corner(Pos where, Node... children) {
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
