package tabletennis.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import tabletennis.app.camera.CameraRig;
import tabletennis.app.hud.Hud;
import tabletennis.app.input.Controls;
import tabletennis.app.input.FaceKeys;
import tabletennis.app.input.MouseControl;
import tabletennis.app.menu.Drills;
import tabletennis.app.menu.MenuScreen;
import tabletennis.app.scene.TableScene;
import tabletennis.game.GameSession;
import tabletennis.game.feed.Feed;
import tabletennis.game.feed.Feeds;
import tabletennis.game.match.ScoreSnapshot;
import tabletennis.game.rally.Side;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Mr. Pong, a 3D table tennis game. The composition root: it builds one game session and the
 * views around it, wires the keys, the mouse and the menu, and starts the loop. The session decides
 * the game; everything here only drives it and draws it.
 */
public final class TableTennisApp extends Application {

    /** What the player is doing: at the menu (the game plays itself behind it), or playing. */
    private enum Mode { Menu, Match, Practice, Demo }

    private static final Color Background = Color.web("#17232e");
    private static final int WindowWidth = 1280, WindowHeight = 780;

    private final GameSession Session = new GameSession();
    private final CameraRig Rig = new CameraRig();
    private final TableScene World = new TableScene(Session.Snapshot());
    private final Hud Overlay = new Hud(Controls.MatchLegend);
    private final FaceKeys Face = new FaceKeys();
    private GameLoop Loop;
    private MenuScreen Menu;
    private SubScene Viewport;
    private LaunchOptions Options;

    private Mode Now = Mode.Menu;
    private Mode LeftFor = Mode.Menu;             // the game a pause-to-menu will resume
    private Drills.Drill Drill = Drills.All.get(0);
    private boolean HudWanted = true;

    @Override
    public void start(Stage Window) {
        Options = LaunchOptions.Parse(getParameters().getRaw());

        Viewport = new SubScene(new Group(World.Root(), Rig.Gimbal()), WindowWidth, WindowHeight,
                                true, SceneAntialiasing.BALANCED);
        Viewport.setFill(Background);
        Viewport.setCamera(Rig.Camera());
        Rig.AttachControls(Viewport);
        MouseControl Mouse = new MouseControl(Viewport, Session);
        Mouse.Attach();
        Loop = new GameLoop(Session, World, Rig, Overlay, Mouse);
        Loop.SetOnMatchOver(this::MatchOver);
        Loop.SetShotLog(Options.Developer());
        Menu = new MenuScreen(new MenuScreen.Actions() {
            @Override public void Resume() { ResumeGame(); }
            @Override public void PlayMatch() { StartMatch(); }
            @Override public void Practice(Drills.Drill Chosen) { StartPractice(Chosen); }
            @Override public void WatchDemo() { StartDemo(); }
            @Override public void MainMenu() { EnterMenu(); }
            @Override public void Quit() { Platform.exit(); }
        });
        World.SetGhostShown(Options.Developer());

        Scene MainScene = new Scene(new StackPane(Viewport, Overlay.Node(), Menu.Node()),
                                    WindowWidth, WindowHeight, Background);
        Viewport.widthProperty().bind(MainScene.widthProperty());
        Viewport.heightProperty().bind(MainScene.heightProperty());
        MainScene.setOnKeyPressed(Event -> OnKey(Event.getCode()));
        MainScene.setOnKeyReleased(Event -> { if (Face.Release(Event.getCode())) SendFaceTilt(); });
        Window.focusedProperty().addListener((Property, Was, Is) -> {
            if (!Is) { Face.ReleaseAll(); SendFaceTilt(); }
        });

        Window.setScene(MainScene);
        Window.setTitle("Mr. Pong");
        Window.show();

        OpenOn(Options.Screen());
        Apply(Options);
        if (Options.Capturing()) Loop.Capture(MainScene, Options.CapturePath(), Options.CaptureAt());
        else Loop.Start();
    }

    /** The first screen: the menu by default; straight into practice with --screen=none. */
    private void OpenOn(String Screen) {
        switch (Screen) {
            case "none" -> StartPractice(Drills.For(Options.FirstFeed()), Options.FirstFeed());
            case "practice" -> { EnterMenu(); Menu.Show(MenuScreen.Page.Practice); }
            case "howtoplay" -> { EnterMenu(); Menu.Show(MenuScreen.Page.HowToPlay); }
            case "result" -> { EnterMenu(); Menu.ShowResult(new ScoreSnapshot(0, 0, 3, 1, Side.Player, false, Side.Player)); }
            default -> EnterMenu();
        }
    }

    private void Apply(LaunchOptions Options) {
        if (Options.View() != null) Rig.Apply(Options.View());
        World.SetBallMagnified(Options.BallMagnified());
        if (Options.ControlReadout()) Loop.ShowControlReadout();
        if (Options.DemoMode()) Session.SetDemoMode(true);
        if (Options.Capturing()) {
            Session.SetAutoReplay(false);   // a replay would reset the clock before a late capture time
            if (!Options.RallyCamInCapture()) Rig.StopRallyCam();
        }
    }

    /** The main menu, with nothing in progress: the game plays itself behind it. */
    private void EnterMenu() {
        Now = Mode.Menu;
        Menu.SetResumable(false);
        Play(true, this::NextInRotation, Shot -> "");
        Overlay.SetShown(false);
        Loop.Launch(Drills.All.get(0).Ball());
        Menu.Show(MenuScreen.Page.Main);
    }

    /** Escape mid-game: freeze it and offer to resume. */
    private void PauseToMenu() {
        LeftFor = Now;
        Now = Mode.Menu;
        Loop.Clock().SetPaused(true);
        ReleaseFace();
        Overlay.SetShown(false);
        Menu.SetResumable(true);
        Menu.Show(MenuScreen.Page.Main);
    }

    private void ResumeGame() {
        Now = LeftFor;
        HideMenu();
        Loop.Clock().SetPaused(false);
    }

    private void StartMatch() {
        Now = Mode.Match;
        Play(false, this::NextInRotation, Shot -> "MATCH  -  best of 5");
        Overlay.SetLegend(Legend(Controls.MatchLegend));
        HideMenu();
        Loop.Launch(Drills.All.get(0).Ball());
    }

    private void StartPractice(Drills.Drill Chosen) { StartPractice(Chosen, Chosen.Ball()); }

    /** Practice one ball, which in developer mode may be any feed, not only a drill. */
    private void StartPractice(Drills.Drill Chosen, Feed Ball) {
        Now = Mode.Practice;
        Drill = Chosen;
        Play(false, Same -> Same, this::PracticeHeading);
        Overlay.SetLegend(Legend(Controls.PracticeLegend));
        HideMenu();
        Loop.Launch(Ball);
    }

    private void StartDemo() {
        Now = Mode.Demo;
        Play(true, this::NextInRotation, Shot -> "DEMO  -  M to take over");
        Overlay.SetLegend(Legend(Controls.PracticeLegend));
        HideMenu();
        Loop.Launch(Drills.All.get(0).Ball());
    }

    /** Common to every mode: a fresh score, who holds the bat, what comes next and what the HUD says. */
    private void Play(boolean Demo, UnaryOperator<Feed> Order, Function<Feed, String> Heading) {
        Session.NewMatch();
        Session.SetDemoMode(Demo);
        Session.SetAutoReplay(true);
        Loop.SetRallyOrder(Order);
        Loop.SetHeading(Heading);
        Loop.Clock().SetPaused(false);
        ReleaseFace();
    }

    private void MatchOver(ScoreSnapshot Final) {
        Session.SetAutoReplay(false);
        Now = Mode.Menu;
        ReleaseFace();
        Menu.SetResumable(false);
        Menu.ShowResult(Final);
    }

    private void HideMenu() {
        Menu.Hide();
        Overlay.SetShown(HudWanted);
        Viewport.requestFocus();
    }

    /** Developer mode: every feed, including the engine checks that are not drills. */
    private void PracticeFeed(Feed Ball) { StartPractice(Drills.For(Ball), Ball); }

    private Feed NextInRotation(Feed Last) { return Drills.Next(Drills.For(Last), +1).Ball(); }

    /** Players see the drill; developer mode shows the feed's own name, as it always did. */
    private String PracticeHeading(Feed Shot) {
        if (Options.Developer()) {
            return "feed: " + Shot.Name() + (Session.Snapshot().DemoMode() ? "   [DEMO -- M to take over]" : "");
        }
        String Take = Session.Snapshot().DemoMode() ? "  (auto - M)" : "";
        return "PRACTICE  -  " + Drill.Title() + Take;
    }

    private String Legend(String ForPlayers) {
        return Options.Developer() ? Controls.DeveloperLegend : ForPlayers;
    }

    private void OnKey(KeyCode Key) {
        if (Menu.Shown()) {
            if (Key == KeyCode.ESCAPE) Menu.Back();
            return;
        }
        if (Face.Press(Key)) { SendFaceTilt(); return; }
        if (Options.Developer()) {
            var Index = Controls.FeedIndexFor(Key);
            if (Index.isPresent()) {
                if (Index.getAsInt() < Feeds.All.size()) PracticeFeed(Feeds.All.get(Index.getAsInt()));
                return;
            }
        }
        Controls.CommandFor(Key, Options.Developer()).ifPresent(this::Execute);
    }

    private void Execute(Controls.Command Action) {
        if (Now == Mode.Match && Controls.PracticeOnly.contains(Action)) return;
        switch (Action) {
            case NextFeed -> { if (Options.Developer()) PracticeFeed(Feeds.Next(Loop.Current(), +1)); else StartPractice(Drills.Next(Drill, +1)); }
            case PreviousFeed -> { if (Options.Developer()) PracticeFeed(Feeds.Next(Loop.Current(), -1)); else StartPractice(Drills.Next(Drill, -1)); }
            case Replay -> Loop.Launch(Loop.Current());
            case ToggleDemo -> { if (Now == Mode.Demo) StartPractice(Drills.For(Loop.Current())); else Loop.ToggleDemo(); }
            case ToggleAutoReplay -> Session.SetAutoReplay(!Session.Snapshot().AutoReplay());
            case Pause -> Loop.Clock().TogglePause();
            case SingleStep -> Loop.Clock().QueueSingleStep();
            case Slower -> Loop.Clock().Slower();
            case Faster -> Loop.Clock().Faster();
            case ToggleRallyCam -> Rig.ToggleRallyCam();
            case NextView -> Rig.Next();
            case ToggleShotOverlay -> Loop.ToggleShotOverlay();
            case ToggleControlReadout -> Loop.ToggleControlReadout();
            case ToggleGhost -> World.ToggleGhost();
            case ToggleTrail -> World.ToggleTrail();
            case ToggleBallSize -> World.SetBallMagnified(!World.BallMagnified());
            case ToggleHud -> { HudWanted = !HudWanted; Overlay.SetShown(HudWanted); }
            case Menu -> PauseToMenu();
        }
    }

    private void ReleaseFace() {
        Face.ReleaseAll();
        SendFaceTilt();
    }

    private void SendFaceTilt() { Session.SetFaceTilt(Face.CloseTilt(), Face.SideTilt()); }

    public static void main(String[] Arguments) { launch(Arguments); }
}
