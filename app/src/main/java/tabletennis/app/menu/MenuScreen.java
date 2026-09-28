package tabletennis.app.menu;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import tabletennis.game.match.ScoreSnapshot;
import tabletennis.game.rally.Side;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The player's front door, laid over the 3D view: the main menu, the drill picker, how to play,
 * and the end of a match. Layout and wording only; what each choice does is the app's (Actions).
 * It covers the whole window while shown, so the mouse cannot swing the bat or orbit the camera
 * behind it.
 */
public final class MenuScreen {

    /** What the menu can ask the app to do. */
    public interface Actions {
        void Resume();
        void PlayMatch();
        void Practice(Drills.Drill Chosen);
        void WatchDemo();
        void MainMenu();
        void Quit();
    }

    public enum Page { Main, Practice, HowToPlay, Result }

    private static final String Style = """
            .menu-root { -fx-background-color: linear-gradient(to right, rgba(8,13,19,0.86) 0%, rgba(8,13,19,0.55) 60%, rgba(8,13,19,0.25) 100%); }
            .card { -fx-background-color: rgba(14,22,31,0.90); -fx-background-radius: 14; -fx-border-color: rgba(140,170,205,0.25); -fx-border-radius: 14; }
            .title { -fx-font-family: 'Segoe UI', 'Helvetica Neue', sans-serif; -fx-font-size: 52px; -fx-font-weight: bold; -fx-text-fill: #f3f7fb; }
            .page-title { -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #f3f7fb; }
            .tagline { -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 16px; -fx-text-fill: #9fb3c8; }
            .section { -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #ffd76a; }
            .body { -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 14px; -fx-text-fill: #d6e1ec; }
            .hint { -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 12px; -fx-text-fill: #7c8fa3; }
            .key { -fx-font-family: Consolas, 'DejaVu Sans Mono', monospace; -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #17232e; -fx-background-color: #d6e1ec; -fx-background-radius: 5; -fx-padding: 2 8 2 8; }
            .choice { -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 17px; -fx-text-fill: #eaf2ff; -fx-background-color: rgba(60,90,120,0.45); -fx-background-radius: 8; -fx-border-color: rgba(140,170,205,0.35); -fx-border-radius: 8; -fx-padding: 11 20 11 20; -fx-cursor: hand; }
            .choice:hover, .choice:focused { -fx-background-color: #2f7fbf; -fx-border-color: #8cc4f0; -fx-text-fill: white; }
            .choice.primary { -fx-background-color: #d9492c; -fx-border-color: #f08a6f; -fx-font-weight: bold; }
            .choice.primary:hover, .choice.primary:focused { -fx-background-color: #f0603f; }
            .drill { -fx-font-size: 14px; -fx-alignment: center-left; -fx-padding: 9 14 9 14; }
            .drill-title { -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #f3f7fb; }
            .drill-blurb { -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px; -fx-fill: #c4d2e0; }
            .result-win { -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 56px; -fx-font-weight: bold; -fx-text-fill: #7fe0a0; }
            .result-lose { -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 56px; -fx-font-weight: bold; -fx-text-fill: #ff9a7a; }
            """;

    private final Actions Do;
    private final StackPane Root = new StackPane();
    private final VBox Card = new VBox(14);
    private Page Current = Page.Main;
    private boolean Resumable;
    private ScoreSnapshot Final;

    public MenuScreen(Actions Do) {
        this.Do = Do;
        Root.getStylesheets().add("data:text/css;base64,"
                + Base64.getEncoder().encodeToString(Style.getBytes(StandardCharsets.UTF_8)));
        Root.getStyleClass().add("menu-root");
        Root.setPickOnBounds(true);
        Card.getStyleClass().add("card");
        Card.setPadding(new Insets(34, 42, 30, 42));
        Card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(Card, Pos.CENTER_LEFT);
        StackPane.setMargin(Card, new Insets(0, 0, 0, 80));
        Root.getChildren().add(Card);
        // Enter fires the focused choice, as Space already does.
        Root.addEventFilter(KeyEvent.KEY_PRESSED, Event -> {
            if (Event.getCode() == KeyCode.ENTER && Root.getScene().getFocusOwner() instanceof Button Focused) {
                Focused.fire();
                Event.consume();
            }
        });
        Show(Page.Main);
    }

    public Region Node() { return Root; }

    public boolean Shown() { return Root.isVisible(); }

    /** Whether the main page offers to resume a game left for the menu. */
    public void SetResumable(boolean On) { Resumable = On; }

    public void Show(Page Which) {
        Current = Which;
        Card.getChildren().setAll(switch (Which) {
            case Main -> MainPage();
            case Practice -> PracticePage();
            case HowToPlay -> HowToPlayPage();
            case Result -> ResultPage();
        });
        StackPane.setAlignment(Card, Which == Page.Main ? Pos.CENTER_LEFT : Pos.CENTER);
        StackPane.setMargin(Card, Which == Page.Main ? new Insets(0, 0, 0, 80) : Insets.EMPTY);
        Root.setVisible(true);
        Root.setManaged(true);
        Root.setDisable(false);
        FocusFirstChoice();
    }

    public void ShowResult(ScoreSnapshot Score) {
        Final = Score;
        Show(Page.Result);
    }

    /** Disabled as well as hidden: a hidden button that kept the focus would still fire on Space. */
    public void Hide() {
        Root.setVisible(false);
        Root.setManaged(false);
        Root.setDisable(true);
    }

    /** Escape: a sub-page goes back to the main page; on the main page it resumes, if it can. */
    public void Back() {
        if (Current == Page.Main) { if (Resumable) Do.Resume(); }
        else if (Current == Page.Result) Do.MainMenu();
        else Show(Page.Main);
    }

    private Node[] MainPage() {
        Label Title = Styled(new Label("MR. PONG"), "title");
        Label Tagline = Styled(new Label("3D table tennis with real ball physics - spin comes from how you swing."), "tagline");
        Tagline.setWrapText(true);
        Tagline.setMaxWidth(380);
        VBox Choices = new VBox(10);
        if (Resumable) Choices.getChildren().add(Choice("Resume", true, Do::Resume));
        Choices.getChildren().addAll(
                Choice("Play a match", !Resumable, Do::PlayMatch),
                Choice("Practice", false, () -> Show(Page.Practice)),
                Choice("Watch the demo", false, Do::WatchDemo),
                Choice("How to play", false, () -> Show(Page.HowToPlay)),
                Choice("Quit", false, Do::Quit));
        Label Hint = Styled(new Label("Mouse, or arrow keys and Enter.  Esc goes back."), "hint");
        return new Node[]{Title, Tagline, Gap(8), Choices, Gap(4), Hint};
    }

    private Node[] PracticePage() {
        Label Title = Styled(new Label("Practice"), "page-title");
        Label Tagline = Styled(new Label("Pick a ball to play against. It comes back until you change it (N / P)."), "tagline");
        GridPane Grid = new GridPane();
        Grid.setHgap(10);
        Grid.setVgap(10);
        for (int Index = 0; Index < Drills.All.size(); Index++) {
            Drills.Drill Each = Drills.All.get(Index);
            Button Pick = Choice("", false, () -> Do.Practice(Each));
            Pick.getStyleClass().add("drill");
            // A Text wraps at a fixed width and sizes its own height; a wrapping Label in a button
            // graphic did not, and stretched its row.
            Text Blurb = Styled(new Text(Each.Blurb()), "drill-blurb");
            Blurb.setWrappingWidth(296);
            Pick.setGraphic(new VBox(2, Styled(new Label(Each.Title()), "drill-title"), Blurb));
            Pick.setPrefWidth(330);
            Pick.setMinHeight(64);
            Grid.add(Pick, Index % 2, Index / 2);
        }
        return new Node[]{Title, Tagline, Grid, Choice("Back", false, () -> Show(Page.Main))};
    }

    private Node[] HowToPlayPage() {
        Label Title = Styled(new Label("How to play"), "page-title");
        GridPane Keys = new GridPane();
        Keys.setHgap(14);
        Keys.setVgap(8);
        int Row = 0;
        Row = Section(Keys, Row, "Hitting");
        Row = Line(Keys, Row, "MOUSE", "The bat follows the mouse. Swing THROUGH the ball to hit it; the way you move aims the shot.");
        Row = Line(Keys, Row, "CENTRE", "Hit it in the middle of the bat. An edge hit loses its spin and can go anywhere.");
        Row = Section(Keys, Row, "Spin");
        Row = Line(Keys, Row, "W  /  S", "Easiest spin: hold W and swing forward through the ball for topspin, S for backspin.");
        Row = Line(Keys, Row, "RIGHT + UP", "Loop: hold the right button as the ball comes, then flick the mouse UP as it reaches the bat. Add W.");
        Row = Line(Keys, Row, "RIGHT + DOWN", "Chop: the same, flicking DOWN through the ball. Add S. The bat stays put until you move.");
        Row = Line(Keys, Row, "A  /  D", "Hold to tilt the face right or left. A sideways swipe gives sidespin that curves.");
        Row = Section(Keys, Row, "Game");
        Row = Line(Keys, Row, "SPACE", "Pause.        H  hide the help        ESC  menu");
        Row = Line(Keys, Row, "N / P  R  M", "In practice: change the ball, replay it, or let the game play your side.");
        Label Features = Styled(new Label(
                "Real flight: air drag and the Magnus curve, measured for a 40 mm ball.   "
                + "Spin from your racket: brush, chop, swipe or close the face.   "
                + "ITTF scoring: games to 11, win by 2, best of 5.   "
                + "Ten practice balls, from a warm-up to a 30 m/s smash."), "body");
        Features.setWrapText(true);
        Features.setMaxWidth(620);
        return new Node[]{Title, Keys, Styled(new Label("Features"), "section"), Features,
                          Choice("Back", false, () -> Show(Page.Main))};
    }

    private Node[] ResultPage() {
        boolean Won = Final != null && Final.MatchWinner() == Side.Player;
        Label Verdict = Styled(new Label(Won ? "YOU WIN" : "YOU LOSE"), Won ? "result-win" : "result-lose");
        String Games = Final == null ? "" : "games " + Final.PlayerGames() + " - " + Final.OpponentGames();
        Label Score = Styled(new Label(Games), "page-title");
        HBox Choices = new HBox(10, Choice("Play again", true, Do::PlayMatch),
                                   Choice("Practice", false, () -> Show(Page.Practice)),
                                   Choice("Menu", false, Do::MainMenu));
        VBox.setVgrow(Choices, Priority.NEVER);
        return new Node[]{Verdict, Score, Gap(6), Choices};
    }

    private static int Section(GridPane Keys, int Row, String Name) {
        Label Heading = Styled(new Label(Name), "section");
        GridPane.setMargin(Heading, new Insets(Row == 0 ? 0 : 8, 0, 0, 0));
        Keys.add(Heading, 0, Row, 2, 1);
        return Row + 1;
    }

    private static int Line(GridPane Keys, int Row, String Key, String What) {
        Label Description = Styled(new Label(What), "body");
        Description.setWrapText(true);
        Description.setMaxWidth(520);
        Keys.add(Styled(new Label(Key), "key"), 0, Row);
        Keys.add(Description, 1, Row);
        return Row + 1;
    }

    private static Button Choice(String Text, boolean Primary, Runnable OnPick) {
        Button Pick = new Button(Text);
        Pick.getStyleClass().setAll("choice");
        if (Primary) Pick.getStyleClass().add("primary");
        Pick.setMaxWidth(Double.MAX_VALUE);
        Pick.setMinWidth(260);
        Pick.setOnAction(Event -> OnPick.run());
        return Pick;
    }

    private void FocusFirstChoice() {
        Card.lookupAll(".choice").stream().findFirst().ifPresent(Node::requestFocus);
    }

    private static Region Gap(double Height) {
        Region Space = new Region();
        Space.setMinHeight(Height);
        return Space;
    }

    private static <T extends Node> T Styled(T Item, String Class) {
        Item.getStyleClass().add(Class);
        return Item;
    }
}
