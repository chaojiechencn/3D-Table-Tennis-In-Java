package tabletennis.app;

import javafx.animation.AnimationTimer;
import javafx.scene.Scene;
import tabletennis.app.camera.CameraRig;
import tabletennis.app.hud.ControlReadout;
import tabletennis.app.hud.Hud;
import tabletennis.app.hud.ShotLine;
import tabletennis.app.input.MouseControl;
import tabletennis.app.scene.TableScene;
import tabletennis.engine.Simulation;
import tabletennis.game.GameSession;
import tabletennis.game.GameSnapshot;
import tabletennis.game.StepResult;
import tabletennis.game.feed.Feed;
import tabletennis.game.rally.Side;

/**
 * The frame loop: pays wall-clock time out as whole physics steps, feeds each step's result to the
 * scene, camera and HUD, and renders between the last two states. The session never sees a frame
 * time. A replay is launched at the loop boundary, so the frame that launches it drops the rest
 * of its owed time.
 */
final class GameLoop {

    private final GameSession Session;
    private final TableScene World;
    private final CameraRig Rig;
    private final Hud Overlay;
    private final MouseControl Mouse;
    private final FixedStepClock Clock = new FixedStepClock();

    private Feed Current;
    private boolean ControlReadoutShown;
    private String ShotReadout = "";   // the last contact's numbers; nothing until the first contact
    private long LastPulseNanos;

    GameLoop(GameSession Session, TableScene World, CameraRig Rig, Hud Overlay, MouseControl Mouse) {
        this.Session = Session;
        this.World = World;
        this.Rig = Rig;
        this.Overlay = Overlay;
        this.Mouse = Mouse;
    }

    FixedStepClock Clock() { return Clock; }

    Feed Current() { return Current; }

    /** Start a rally with this feed, keeping the score, and clear what the last one drew. */
    void Launch(Feed Shot) {
        Current = Shot;
        Session.Launch(Shot);
        Overlay.SetFeed(Shot.Name());
        Overlay.SetScore(Session.Snapshot().Score());
        Rig.OnRallyHit(true);   // the feed stands in for the player's own shot
        Clock.Reset();
        World.OnLaunch(Shot);
    }

    void ToggleDemo() {
        Session.SetDemoMode(!Session.Snapshot().DemoMode());
        boolean Demo = Session.Snapshot().DemoMode();
        Overlay.SetFeed(Demo ? Current.Name() + "   [DEMO -- M to take over]" : Current.Name());
    }

    void ToggleShotOverlay() {
        World.SetShotOverlayShown(!World.ShotOverlayShown());
        RefreshShotReadout();
    }

    void ToggleControlReadout() {
        ControlReadoutShown = !ControlReadoutShown;
        Overlay.SetControl(ControlReadoutShown ? ControlReadoutText(Session.Snapshot()) : null);
    }

    void ShowControlReadout() { ControlReadoutShown = true; }

    /** Normal play: every pulse steps what the clock owes, then draws. */
    void Start() {
        new AnimationTimer() {
            @Override public void handle(long Now) {
                if (LastPulseNanos == 0) { LastPulseNanos = Now; return; }   // the first pulse has no duration
                double FrameSeconds = (Now - LastPulseNanos) / 1e9;
                LastPulseNanos = Now;
                Frame(FrameSeconds);
            }
        }.start();
    }

    /** Offline capture: whole steps to the capture time, a few pulses to settle, then one PNG. */
    void Capture(Scene Window, String Path, double AtSeconds) {
        long Steps = Math.round(AtSeconds / Simulation.Step);
        for (long Step = 0; Step < Steps; Step++) Advance();
        new AnimationTimer() {
            private int Pulses = 0;

            @Override public void handle(long Now) {
                Render(0);
                if (++Pulses >= FrameCapture.PulsesBeforeCapture) {
                    stop();   // exit is asynchronous; one image only
                    FrameCapture.WriteAndExit(Window, Path, Session.Snapshot().Time());
                }
            }
        }.start();
    }

    private void Frame(double FrameSeconds) {
        Clock.BeginFrame(FrameSeconds);
        while (Clock.TakeStep()) {
            Advance();
            if (!Clock.Paused() && Session.ReplayDue()) {
                Launch(Current);
                break;
            }
        }
        Render(Math.min(FrameSeconds, FixedStepClock.MaxFrameSeconds));
    }

    private void Advance() {
        World.BeforeStep(Session.Snapshot());
        StepResult Result = Session.Step();
        GameSnapshot Now = Session.Snapshot();
        World.AfterStep(Result, Now);
        if (Result.Contact()) {
            Rig.OnRallyHit(Result.HitBy() == Side.Player);
            ShotReadout = ShotLine.Format(Now.LastShot());
            RefreshShotReadout();
        }
        if (Result.PointAwarded()) Overlay.SetScore(Now.Score());
    }

    private void Render(double FrameSeconds) {
        GameSnapshot Now = Session.Snapshot();
        World.Render(Now, Clock.Fraction());
        Rig.UpdateRally(FrameSeconds, Now.Ball().Position());
        if (ControlReadoutShown) Overlay.SetControl(ControlReadoutText(Now));
    }

    private void RefreshShotReadout() {
        Overlay.SetShot(World.ShotOverlayShown() ? ShotReadout : null);
    }

    private String ControlReadoutText(GameSnapshot Now) {
        return ControlReadout.Format(Mouse.Cursor(), Now.PlayerBlade().Centre(), Now.Ball());
    }
}
