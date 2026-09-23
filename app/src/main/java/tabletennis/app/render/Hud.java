package tabletennis.app.render;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import tabletennis.game.Scoreboard;

/**
 * What a player needs on screen: the score, the feed name, the key legend, and the V and D debug
 * readouts while they are toggled on.
 */
public final class Hud {

    private static final String Mono = "Consolas, 'DejaVu Sans Mono', monospace";

    private static final String LegendText = """
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

    private final StackPane Root = new StackPane();
    private final Label Score = PanelLabel(20, "#ffffff");   // the one number a player looks up for
    private final Label Feed = PanelLabel(15, "#eaf2ff");
    private final Label Shot = PanelLabel(12, "#ff9a3c");
    private final Label Legend = PanelLabel(12, "#8d9bab");
    private final Label ControlReadout = PanelLabel(12, "#7fd4a8");

    public Hud() {
        Legend.setText(LegendText);
        SetShot(null);
        Root.getChildren().addAll(Corner(Pos.TOP_LEFT, Feed, Shot),
                                  Corner(Pos.TOP_CENTER, Score),
                                  Corner(Pos.TOP_RIGHT, ControlReadout),
                                  Corner(Pos.BOTTOM_LEFT, Legend));
        SetControl(null);                  // only once corner() has given the label a parent
        Root.setPickOnBounds(false);       // clicks must reach the SubScene to orbit and aim
        Root.setPadding(new Insets(14));
    }

    public Region Node() { return Root; }

    public void SetShown(boolean Shown) { Root.setVisible(Shown); }

    public void SetFeed(String Name) { Feed.setText("feed: " + Name); }

    public void SetScore(Scoreboard.Snapshot S) {
        Score.setText(ScoreLine(S));
    }

    /** The serving dot sits by the server's score; "deuce" is spelled out because the rule changes. */
    static String ScoreLine(Scoreboard.Snapshot S) {
        String Games = "games " + S.PlayerGames() + " - " + S.OpponentGames();
        if (S.MatchWinner() != null) {
            return (S.MatchWinner() == Scoreboard.Side.Player ? "YOU WIN" : "OPPONENT WINS") + "   " + Games;
        }
        String YouDot = S.Server() == Scoreboard.Side.Player ? "* " : "  ";
        String OppDot = S.Server() == Scoreboard.Side.Opponent ? " *" : "  ";
        return Games + "    " + YouDot + S.PlayerPoints() + " - " + S.OpponentPoints() + OppDot
             + (S.Deuce() ? "   deuce" : "");
    }

    /** Null hides the line; unmanaged as well as invisible, or it leaves a blank row. */
    public void SetShot(String Text) {
        ShowLabel(Shot, Text);
    }

    /** Null hides the whole D panel, not just its text. */
    public void SetControl(String Text) {
        boolean On = ShowLabel(ControlReadout, Text);
        ControlReadout.getParent().setVisible(On);
        ControlReadout.getParent().setManaged(On);
    }

    private static boolean ShowLabel(Label Line, String Text) {
        boolean On = Text != null && !Text.isEmpty();
        Line.setText(On ? Text : "");
        Line.setVisible(On);
        Line.setManaged(On);
        return On;
    }

    private static Label PanelLabel(double Size, String Colour) {
        Label L = new Label();
        L.setStyle(String.format("-fx-font-family: %s; -fx-font-size: %.1fpx; -fx-text-fill: %s;",
                                 Mono, Size, Colour));
        return L;
    }

    /** Max size pinned to preferred, or each translucent panel stretches across the window. */
    private static VBox Corner(Pos Where, Node... Children) {
        VBox V = new VBox(6, Children);
        V.setPadding(new Insets(12, 16, 12, 16));
        V.setStyle("-fx-background-color: rgba(10,14,20,0.74);"
                 + "-fx-background-radius: 8;"
                 + "-fx-border-color: rgba(140,170,205,0.22);"
                 + "-fx-border-radius: 8;");
        V.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        V.setAlignment(Pos.CENTER_LEFT);
        StackPane.setAlignment(V, Where);
        return V;
    }
}
