package tabletennis.app.hud;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import tabletennis.game.match.ScoreSnapshot;

/**
 * What a player needs on screen: the score, the feed, the key legend, and the V and D readouts
 * while they are toggled on. Layout only; the text comes from the formatters beside it.
 */
public final class Hud {

    private static final String Mono = "Consolas, 'DejaVu Sans Mono', monospace";

    private final StackPane Root = new StackPane();
    private final Label Score = PanelLabel(20, "#ffffff");   // the one number a player looks up for
    private final Label Feed = PanelLabel(15, "#eaf2ff");
    private final Label Shot = PanelLabel(12, "#ff9a3c");
    private final Label Legend = PanelLabel(12, "#8d9bab");
    private final Label Control = PanelLabel(12, "#7fd4a8");

    public Hud(String LegendText) {
        Legend.setText(LegendText);
        SetShot(null);
        Root.getChildren().addAll(Corner(Pos.TOP_LEFT, Feed, Shot),
                                  Corner(Pos.TOP_CENTER, Score),
                                  Corner(Pos.TOP_RIGHT, Control),
                                  Corner(Pos.BOTTOM_LEFT, Legend));
        SetControl(null);                  // only once Corner has given the label a parent
        Root.setPickOnBounds(false);       // clicks must reach the 3D view to orbit and aim
        Root.setPadding(new Insets(14));
    }

    public Region Node() { return Root; }

    public void ToggleShown() { Root.setVisible(!Root.isVisible()); }

    public void SetFeed(String Name) { Feed.setText("feed: " + Name); }

    public void SetScore(ScoreSnapshot Now) { Score.setText(ScoreLine.Format(Now)); }

    /** Null hides the line; unmanaged as well as invisible, or it leaves a blank row. */
    public void SetShot(String Text) { ShowLabel(Shot, Text); }

    /** Null hides the whole D panel, not just its text. */
    public void SetControl(String Text) {
        boolean On = ShowLabel(Control, Text);
        Control.getParent().setVisible(On);
        Control.getParent().setManaged(On);
    }

    private static boolean ShowLabel(Label Line, String Text) {
        boolean On = Text != null && !Text.isEmpty();
        Line.setText(On ? Text : "");
        Line.setVisible(On);
        Line.setManaged(On);
        return On;
    }

    private static Label PanelLabel(double Size, String Colour) {
        Label Line = new Label();
        Line.setStyle(String.format("-fx-font-family: %s; -fx-font-size: %.1fpx; -fx-text-fill: %s;",
                                    Mono, Size, Colour));
        return Line;
    }

    /** Max size pinned to preferred, or each translucent panel stretches across the window. */
    private static VBox Corner(Pos Where, Node... Lines) {
        VBox Panel = new VBox(6, Lines);
        Panel.setPadding(new Insets(12, 16, 12, 16));
        Panel.setStyle("-fx-background-color: rgba(10,14,20,0.74);"
                     + "-fx-background-radius: 8;"
                     + "-fx-border-color: rgba(140,170,205,0.22);"
                     + "-fx-border-radius: 8;");
        Panel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Panel.setAlignment(Pos.CENTER_LEFT);
        StackPane.setAlignment(Panel, Where);
        return Panel;
    }
}
