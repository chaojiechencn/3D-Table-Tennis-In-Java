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
import tabletennis.app.input.MouseControl;
import tabletennis.app.scene.TableScene;
import tabletennis.game.GameSession;
import tabletennis.game.feed.Feeds;

/**
 * Mr. Pong, a 3D table tennis game. The composition root: it builds one game session and the
 * views around it, wires the keys and the mouse, and starts the loop. The session decides the
 * game; everything here only drives it and draws it.
 */
public final class TableTennisApp extends Application {

    private static final Color Background = Color.web("#17232e");
    private static final int WindowWidth = 1280, WindowHeight = 780;

    private final GameSession Session = new GameSession();
    private final CameraRig Rig = new CameraRig();
    private final TableScene World = new TableScene(Session.Snapshot());
    private final Hud Overlay = new Hud(Controls.Legend);
    private GameLoop Loop;

    @Override
    public void start(Stage Window) {
        LaunchOptions Options = LaunchOptions.Parse(getParameters().getRaw());

        SubScene Viewport = new SubScene(new Group(World.Root(), Rig.Gimbal()), WindowWidth, WindowHeight,
                                         true, SceneAntialiasing.BALANCED);
        Viewport.setFill(Background);
        Viewport.setCamera(Rig.Camera());
        Rig.AttachControls(Viewport);
        MouseControl Mouse = new MouseControl(Viewport, Session);
        Mouse.Attach();
        Loop = new GameLoop(Session, World, Rig, Overlay, Mouse);
        Apply(Options);

        Scene MainScene = new Scene(new StackPane(Viewport, Overlay.Node()), WindowWidth, WindowHeight, Background);
        Viewport.widthProperty().bind(MainScene.widthProperty());
        Viewport.heightProperty().bind(MainScene.heightProperty());
        MainScene.setOnKeyPressed(Event -> OnKey(Event.getCode()));

        Window.setScene(MainScene);
        Window.setTitle("Mr. Pong");
        Window.show();
        Viewport.requestFocus();

        Loop.Launch(Options.FirstFeed());
        if (Options.Capturing()) Loop.Capture(MainScene, Options.CapturePath(), Options.CaptureAt());
        else Loop.Start();
    }

    private void Apply(LaunchOptions Options) {
        if (Options.View() != null) Rig.Apply(Options.View());
        World.SetBallMagnified(Options.BallMagnified());
        if (Options.ControlReadout()) Loop.ShowControlReadout();
        Session.SetDemoMode(Options.DemoMode());
        if (Options.Capturing()) {
            Session.SetAutoReplay(false);   // a replay would reset the clock before a late capture time
            if (!Options.RallyCamInCapture()) Rig.StopRallyCam();
        }
    }

    private void OnKey(KeyCode Key) {
        Controls.FeedIndexFor(Key).ifPresentOrElse(
                Index -> { if (Index < Feeds.All.size()) Loop.Launch(Feeds.All.get(Index)); },
                () -> Controls.CommandFor(Key).ifPresent(this::Execute));
    }

    private void Execute(Controls.Command Action) {
        switch (Action) {
            case NextFeed -> Loop.Launch(Feeds.Next(Loop.Current(), +1));
            case PreviousFeed -> Loop.Launch(Feeds.Next(Loop.Current(), -1));
            case Replay -> Loop.Launch(Loop.Current());
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
            case ToggleHud -> Overlay.ToggleShown();
            case ToggleDemo -> Loop.ToggleDemo();
            case Quit -> Platform.exit();
        }
    }

    public static void main(String[] Arguments) { launch(Arguments); }
}
