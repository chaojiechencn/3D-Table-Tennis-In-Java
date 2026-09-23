package tabletennis.app;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.scene.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.contact.BladeCollider;
import tabletennis.engine.math.Quat;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.FlightPredictor;
import tabletennis.game.GameSession;
import tabletennis.game.StepResult;
import tabletennis.game.control.CursorFollower;
import tabletennis.game.control.ReachEnvelope;
import tabletennis.game.control.ReachTiming;
import tabletennis.game.feed.Feed;
import tabletennis.game.feed.Feeds;
import tabletennis.game.rally.EventType;
import tabletennis.game.rally.RallyEvent;
import tabletennis.game.rally.Side;
import tabletennis.game.shot.ShotDecision;
import tabletennis.app.render.*;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;


/**
 * Mr. Pong, a 3D table tennis game. The JavaFX entry point and composition root: frame loop,
 * input, camera, views and capture mode around one {@link GameSession}, which decides the game.
 * The session only advances in whole fixed physics steps and never sees a frame time.
 */
public class TableTennisIn3D extends Application {

    private static final Color Background = Color.web("#17232e");
    private static final int WindowWidth = 1280, WindowHeight = 780;

    /** Stops a catch-up spiral after a stall; MaxFrameSeconds already bounds a normal frame. */
    private static final int MaxStepsPerFrame = 4000;

    /** Longest frame the accumulator honours; beyond it time is dropped to avoid a death spiral. */
    private static final double MaxFrameSeconds = 0.25;

    /** TUNED: whole physics steps per trail dot (4.2 ms), shared by trail and ghost. */
    private static final int TrailStride = 2;

    /** 1.25 s of flight at TrailStride: any shot end to end. */
    private static final int TrailDots = 300;

    private static final int BounceMarksKept = 24;

    private final GameSession Session = new GameSession();
    private Feed CurrentShot = Feeds.Default();

    private double Accumulator = 0;
    private long LastNanos = 0;
    /** Opens in slow motion: at 1:1 a first rally is a blur. */
    private double TimeScale = 0.45;
    private boolean Paused = false;
    private int PendingSingleSteps = 0;

    private final BallView BallModel = new BallView();
    private final Trail FlightTrail = new Trail(TrailDots, 0.0060, Color.web("#a8401a"), Color.web("#ffe08a"));
    private final Trail Ghost = new Trail(TrailDots, 0.0042, Color.web("#454b54"), Color.web("#9aa5b2"));
    private final BounceMarks Marks = new BounceMarks(BounceMarksKept);
    private final CameraRig Rig = new CameraRig();
    private final Hud HudLayer = new Hud();
    private final ShotDebug ShotOverlay = new ShotDebug();
    private final PaddleView PlayerView = new PaddleView(false);
    private final PaddleView OpponentView = new PaddleView(false);

    /** Poses at the start of the last step, so blades interpolate with the same alpha as the ball. */
    private BladeCollider PrevPlayerPose = Session.Snapshot().PlayerBlade();
    private BladeCollider PrevOpponentPose = Session.Snapshot().OpponentBlade();

    private final Deque<Vec3> TrailPoints = new ArrayDeque<>();
    private int StepsSinceTrailPoint = 0;
    private final List<Vec3> BounceMarkPoints = new ArrayList<>();
    private boolean MarksChanged = false;

    private boolean ShowGhost = true;
    private boolean ShowTrail = true;
    private boolean ShowHud = true;
    private boolean ShowControlDebug = false;

    /** The mapped cursor, consumed by the session once per physics step, never per frame. */
    private Vec3 PendingAim = null;
    private double CursorX = Double.NaN, CursorY = Double.NaN;
    private Vec3 RawAim = null;   // before clamping, so the D overlay can show a held blade

    /** While the right button is held the cursor's Y means height and depth is frozen. */
    private boolean Brushing = false;
    private double BrushHoldZ = ReachEnvelope.Neutral.Z();

    private String ScreenshotPath = null;
    private double ScreenshotAt = 0;
    private boolean RallyCamInCapture = false;

    @Override
    public void start(Stage Window) {
        ParseArgs();

        SubScene Viewport = BuildViewport();
        BindMouse(Viewport);

        StackPane Layers = new StackPane(Viewport, HudLayer.Node());
        Scene MainScene = new Scene(Layers, WindowWidth, WindowHeight, Background);
        Viewport.widthProperty().bind(MainScene.widthProperty());
        Viewport.heightProperty().bind(MainScene.heightProperty());
        MainScene.setOnKeyPressed(E -> OnKey(E.getCode()));

        Window.setScene(MainScene);
        Window.setTitle("Mr. Pong");
        Window.show();
        Viewport.requestFocus();

        LaunchShot(CurrentShot);
        StartLoop(MainScene);
    }

    private SubScene BuildViewport() {
        Group World3d = new Group(
                Court.Build(),
                BallModel.ShadowNode(),
                Marks.Node(),
                Ghost.Node(),
                FlightTrail.Node(),
                ShotOverlay.Node(),
                OpponentView.Node(),
                PlayerView.Node(),
                BallModel.Node(),
                Lighting());

        SubScene Viewport = new SubScene(new Group(World3d, Rig.Gimbal()), WindowWidth, WindowHeight,
                                         true, SceneAntialiasing.BALANCED);
        Viewport.setFill(Background);
        Viewport.setCamera(Rig.Camera());
        Rig.AttachControls(Viewport);
        return Viewport;
    }

    /** TUNED sports-hall lighting: warm key, cool cross-fill, coloured ambient for the underside. */
    private Group Lighting() {
        DirectionalLight Key = new DirectionalLight(Color.web("#b9b1a4"));
        Key.setDirection(Xform.ToScene(new Vec3(0.35, -1, -0.28)).normalize());
        DirectionalLight Fill = new DirectionalLight(Color.web("#435c78"));
        Fill.setDirection(Xform.ToScene(new Vec3(-0.8, -0.55, 0.4)).normalize());
        return new Group(Key, Fill, new AmbientLight(Color.web("#303944")));
    }

    private void StartLoop(Scene MainScene) {
        if (ScreenshotPath != null) AdvanceToCaptureTime();
        new AnimationTimer() {
            /** A few pulses let the scene lay out and upload its textures before the snapshot. */
            private static final int PulsesBeforeCapture = 3;
            private int CapturePulses = 0;

            @Override public void handle(long Now) {
                if (ScreenshotPath != null) {
                    Render(0);
                    if (++CapturePulses >= PulsesBeforeCapture) TakeScreenshot(MainScene);
                    return;
                }
                if (LastNanos == 0) { LastNanos = Now; return; }   // first frame has no dt
                // Clamped before scaling, so a stall cannot hand the accumulator a second of work.
                double Frame = Math.min((Now - LastNanos) / 1e9, MaxFrameSeconds);
                LastNanos = Now;

                StepFrame(Frame);
                Render(Frame);
            }
        }.start();
    }

    /** Capture runs whole physics steps, not wall-clock frames, so the same arguments give the same image. */
    private void AdvanceToCaptureTime() {
        long Steps = Math.round(ScreenshotAt / Simulation.Step);
        for (long Step = 0; Step < Steps; Step++) AdvanceOne();
    }

    private void StepFrame(double FrameSeconds) {
        if (Paused) {
            RunSingleSteps();
            return;
        }
        int Steps = 0;
        Accumulator += FrameSeconds * TimeScale;
        while (Accumulator >= Simulation.Step) {
            AdvanceOne();
            Accumulator -= Simulation.Step;
            if (++Steps > MaxStepsPerFrame) { Accumulator = 0; break; }
            if (Session.ReplayDue()) {
                LaunchShot(CurrentShot);   // resets the accumulator
                break;
            }
        }
    }

    /** Through the same fixed step, so a paused frame matches the one produced live. */
    private void RunSingleSteps() {
        while (PendingSingleSteps > 0) {
            AdvanceOne();
            PendingSingleSteps--;
        }
    }

    private void AdvanceOne() {
        PrevPlayerPose = Session.Snapshot().PlayerBlade();
        PrevOpponentPose = Session.Snapshot().OpponentBlade();

        StepResult Result = Session.Step();
        if (Result.Contact()) ShowContact(Result.HitBy());
        if (Result.PointAwarded()) HudLayer.SetScore(Session.Snapshot().Score());
        KeepBounceMarks(Result.Events());
        SampleTrail();
    }

    private void KeepBounceMarks(List<RallyEvent> Events) {
        for (RallyEvent Each : Events) {
            if (Each.Type() != EventType.TableBounce) continue;
            BounceMarkPoints.add(Each.Point());
            while (BounceMarkPoints.size() > BounceMarksKept) BounceMarkPoints.remove(0);
            MarksChanged = true;
        }
    }

    private void SampleTrail() {
        if (++StepsSinceTrailPoint < TrailStride) return;
        StepsSinceTrailPoint = 0;
        TrailPoints.addLast(Session.Snapshot().Ball().Position());
        while (TrailPoints.size() > TrailDots) TrailPoints.removeFirst();
    }

    private void ShowContact(Side HitBy) {
        boolean PlayerHit = HitBy == Side.Player;
        Rig.OnRallyHit(PlayerHit);

        ShotDecision D = Session.Snapshot().LastShot();
        ShotOverlay.Set(D.Contact(), D.RacketVelocity(), D.IncomingVelocity(), D.ReflectDirection(),
                      D.IntendedDirection(), D.FinalDirection(), D.Target(), D.Landing(),
                      D.Speed(), D.Spin(), D.Passes(), D.Legal());
        ShotOverlay.SetTargetArea(D.Area().HalfWidth(), D.Area().NearDepth(), D.Area().FarDepth(), !PlayerHit);
        RefreshShotReadout();
    }

    /** Interpolated between the last two physics states, or 480 Hz against 60 Hz stutters. */
    private void Render(double FrameSeconds) {
        double Alpha = Paused ? 0 : Math.min(1, Accumulator / Simulation.Step);
        BallState From = Session.Snapshot().PreviousBall(), To = Session.Snapshot().Ball();
        BallModel.Update(new BallState(
                Vec3.Lerp(From.Position(), To.Position(), Alpha),
                Vec3.Lerp(From.Velocity(), To.Velocity(), Alpha),
                Vec3.Lerp(From.Spin(), To.Spin(), Alpha),
                Quat.Slerp(From.Orientation(), To.Orientation(), Alpha)));

        Rig.UpdateRally(FrameSeconds, To.Position());
        DrawPaddle(PlayerView, PrevPlayerPose, Session.Snapshot().PlayerBlade(), Alpha);
        DrawPaddle(OpponentView, PrevOpponentPose, Session.Snapshot().OpponentBlade(), Alpha);

        if (ShowTrail) FlightTrail.SetPath(TrailPoints);
        if (MarksChanged) {
            MarksChanged = false;
            Marks.SetMarks(BounceMarkPoints);
        }
        if (ShowControlDebug) HudLayer.SetControl(ControlReadout());
    }

    /** The normal is lerped, not slerped: under a degree per step, the two agree to 1e-6. */
    private static void DrawPaddle(PaddleView PaddleModel, BladeCollider From, BladeCollider To, double Alpha) {
        PaddleModel.Update(Vec3.Lerp(From.Centre(), To.Centre(), Alpha),
                    Vec3.Lerp(From.Normal(), To.Normal(), Alpha).Normalized());
    }

    private void RefreshShotReadout() {
        HudLayer.SetShot(ShotOverlay.IsShown() ? ShotOverlay.Readout() : null);
    }

    private void RefreshGhost() {
        Ghost.SetShown(ShowGhost && CurrentShot.Ball().SpinRate() > 1e-6);
    }

    /**
     * The D overlay: did the control mapping fail, or was the ball unplayable? Read-only and
     * downstream of the blade's target, so the ball never steers the control.
     */
    private String ControlReadout() {
        BallState B = Session.Snapshot().Ball();
        Vec3 BladeCentre = Session.Snapshot().PlayerBlade().Centre();
        Vec3 Target = PendingAim != null ? PendingAim : BladeCentre;

        double Dist = ReachTiming.TravelDistance(BladeCentre, Target);
        double Travel = ReachTiming.TravelTime(BladeCentre, Target);
        double Arrive = ReachTiming.TimeToDepth(B, Target.Z());
        boolean Clamped = RawAim != null
                && (Math.abs(RawAim.X() - Target.X()) > 1e-6 || Math.abs(RawAim.Z() - Target.Z()) > 1e-6);

        return String.format("""
            CONTROL  [D]
              cursor     %s px%s
              racket     x %+.3f  y %+.3f  z %+.3f
              target     x %+.3f  y %+.3f  z %+.3f%s
              bounds     x [%+.2f, %+.2f]   y %.3f fixed   z [%.2f, %.2f]
              travel     %.3f m  ->  %.0f ms at %.1f m/s
              ball       x %+.3f  y %+.3f  z %+.3f
              arrival    %s  (to racket depth z %+.3f)
              reachable  %s""",
            Double.isNaN(CursorX) ? "(none yet)" : String.format("(%4.0f,%4.0f)", CursorX, CursorY),
            RawAim == null ? "" : String.format("   ray -> x %+.3f  z %+.3f", RawAim.X(), RawAim.Z()),
            BladeCentre.X(), BladeCentre.Y(), BladeCentre.Z(),
            Target.X(), Target.Y(), Target.Z(), Clamped ? "   (clamped)" : "",
            -ReachEnvelope.MaxX, ReachEnvelope.MaxX, ReachEnvelope.HitY, ReachEnvelope.ZNear, ReachEnvelope.ZFar,
            Dist, Travel * 1000, CursorFollower.TrackSpeed,
            B.Position().X(), B.Position().Y(), B.Position().Z(),
            Double.isNaN(Arrive) ? "  --  " : String.format("%.0f ms", Arrive * 1000), Target.Z(),
            ReachVerdict(Travel, Arrive));
    }

    private static String ReachVerdict(double Travel, double Arrive) {
        if (Double.isNaN(Arrive)) return "n/a  (ball not coming to this depth)";
        if (Travel <= Arrive) return String.format("YES  (%.0f ms to spare)", (Arrive - Travel) * 1000);
        return String.format("NO   (%.0f ms short)", (Travel - Arrive) * 1000);
    }

    /**
     * Bare movement aims; the left button orbits. addEventHandler, because setOnMouseMoved would
     * unhook CameraRig's orbit handler. MOUSE_MOVED stops while a button is down, so the brush
     * also listens to DRAGGED.
     */
    private void BindMouse(SubScene Viewport) {
        Viewport.addEventHandler(MouseEvent.MOUSE_MOVED, E -> Aim(Viewport, E));
        Viewport.addEventHandler(MouseEvent.MOUSE_DRAGGED, E -> { if (Brushing) Aim(Viewport, E); });
        Viewport.addEventHandler(MouseEvent.MOUSE_PRESSED, E -> {
            if (!E.isSecondaryButtonDown()) return;
            Brushing = true;
            BrushHoldZ = Session.Snapshot().PlayerBlade().Centre().Z();
            Aim(Viewport, E);
        });
        Viewport.addEventHandler(MouseEvent.MOUSE_RELEASED, E -> {
            if (!Brushing || E.isSecondaryButtonDown()) return;
            Brushing = false;
            Aim(Viewport, E);
        });
    }

    /** sceneToLocal, since events target the 3D nodes inside the SubScene. */
    private void Aim(SubScene Viewport, MouseEvent E) {
        Point2D P = Viewport.sceneToLocal(E.getSceneX(), E.getSceneY());
        // Only for a degenerate ray; used as an input it would be a loop with gain.
        Vec3 Fallback = PendingAim != null ? PendingAim : Session.Snapshot().PlayerBlade().Centre();

        CursorX = P.getX();
        CursorY = P.getY();
        RawAim = MouseAim.OnHittingPlane(Viewport, P.getX(), P.getY(), ReachEnvelope.HitY, Fallback);
        PendingAim = Brushing
                ? ReachEnvelope.ClampBrushed(RawAim, P.getY() / Math.max(1, Viewport.getHeight()), BrushHoldZ)
                : ReachEnvelope.Clamp(RawAim);
        Session.SetAim(PendingAim);
    }

    private void OnKey(KeyCode Code) {
        if (IsTopRowDigit(Code)) {
            Pick((Code.getChar().charAt(0) - '0' + 9) % 10);   // 1..9 then 0
            return;
        }
        switch (Code) {
            case N, RIGHT -> LaunchShot(NextShot(+1));
            case P, LEFT  -> LaunchShot(NextShot(-1));
            case R        -> LaunchShot(CurrentShot);
            case SPACE  -> Paused = !Paused;
            case PERIOD -> { Paused = true; PendingSingleSteps += 1; }
            case OPEN_BRACKET  -> TimeScale = Math.max(0.02, TimeScale / 1.6);
            case CLOSE_BRACKET -> TimeScale = Math.min(2.0, TimeScale * 1.6);
            case G -> { ShowGhost = !ShowGhost; RefreshGhost(); }
            case T -> { ShowTrail = !ShowTrail; FlightTrail.SetShown(ShowTrail); }
            case A -> Session.SetAutoReplay(!Session.Snapshot().AutoReplay());
            case B -> BallModel.SetMagnified(!BallModel.IsMagnified());
            case F -> Rig.ToggleRallyCam();
            case C -> Rig.Next();
            case V -> { ShotOverlay.SetShown(!ShotOverlay.IsShown()); RefreshShotReadout(); }
            case D -> ToggleControlDebug();
            case M -> ToggleDemo();
            case H -> { ShowHud = !ShowHud; HudLayer.SetShown(ShowHud); }
            case ESCAPE -> Platform.exit();
            default -> { }
        }
    }

    /** DIGIT0..DIGIT9 only: numpad digits never picked a feed. */
    private static boolean IsTopRowDigit(KeyCode Code) {
        return Code.name().startsWith("DIGIT");
    }

    private void ToggleControlDebug() {
        ShowControlDebug = !ShowControlDebug;
        HudLayer.SetControl(ShowControlDebug ? ControlReadout() : null);
    }

    private void ToggleDemo() {
        Session.SetDemoMode(!Session.Snapshot().DemoMode());
        HudLayer.SetFeed(Session.Snapshot().DemoMode() ? CurrentShot.Name() + "   [DEMO -- M to take over]"
                                       : CurrentShot.Name());
    }

    private void Pick(int Index) {
        if (Index < Feeds.All.size()) LaunchShot(Feeds.All.get(Index));
    }

    private Feed NextShot(int Delta) {
        return Feeds.Next(CurrentShot, Delta);
    }

    /** Start a rally with this feed, keeping the score, and clear what the last one drew. */
    private void LaunchShot(Feed Shot) {
        CurrentShot = Shot;
        Session.Launch(Shot);
        HudLayer.SetFeed(Shot.Name());
        HudLayer.SetScore(Session.Snapshot().Score());
        Rig.OnRallyHit(true);   // the feed stands in for the player's own shot

        Accumulator = 0;
        StepsSinceTrailPoint = 0;
        TrailPoints.clear();
        FlightTrail.Clear();
        Marks.Clear();
        BounceMarkPoints.clear();
        MarksChanged = false;

        // The no-spin ghost, predicted once at the trail's stride and length so they compare dot for dot.
        List<Vec3> GhostPath = FlightPredictor.Path(Shot.WithoutSpin(), TrailDots * TrailStride * Simulation.Step, TrailStride);
        Ghost.SetPath(GhostPath);
        RefreshGhost();
    }

    /** Offline capture: --shot="Topspin loop" --at=0.45 --view=SIDE --out=frame.png */
    private void ParseArgs() {
        for (String Arg : getParameters().getRaw()) {
            String[] Kv = Arg.split("=", 2);
            if (Kv.length == 2) ApplyArg(Kv[0], Kv[1]);
        }
        if (ScreenshotPath != null) {
            Session.SetAutoReplay(false);   // a replay would reset the clock before a late --at
            if (!RallyCamInCapture) Rig.StopRallyCam();
        }
    }

    private void ApplyArg(String Name, String Value) {
        switch (Name) {
            case "--shot" -> CurrentShot = Feeds.ByName(Value);
            case "--at"   -> ScreenshotAt = Double.parseDouble(Value);
            case "--out"  -> ScreenshotPath = Value;
            case "--view" -> Rig.Apply(CameraRig.View.FromArg(Value));
            case "--ball2x" -> BallModel.SetMagnified(Boolean.parseBoolean(Value));
            case "--controldebug" -> ShowControlDebug = Boolean.parseBoolean(Value);
            case "--demo" -> Session.SetDemoMode(Boolean.parseBoolean(Value));
            case "--rallycam" -> RallyCamInCapture = Boolean.parseBoolean(Value);
            default -> { }
        }
    }

    private void TakeScreenshot(Scene MainScene) {
        String Path = ScreenshotPath;
        ScreenshotPath = null;
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(MainScene.snapshot(null), null), "png", new File(Path));
            System.out.println("wrote " + Path + " at t=" + String.format("%.3f", Session.Snapshot().Time()));
        } catch (Exception E) {
            System.err.println("screenshot failed: " + E);
        }
        Platform.exit();
    }

    public static void main(String[] Args) { launch(Args); }
}
