package tabletennis.app.scene;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.contact.BladeCollider;
import tabletennis.engine.math.Quat;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.FlightPredictor;
import tabletennis.game.GameSnapshot;
import tabletennis.game.StepResult;
import tabletennis.game.feed.Feed;
import tabletennis.game.rally.EventType;
import tabletennis.game.rally.RallyEvent;
import tabletennis.game.rally.Side;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Everything drawn in 3D: the hall, the ball and its shadow, both rackets, the flight trail and its
 * no-spin ghost, bounce marks and the shot overlay. It reads snapshots only, and renders between
 * the last two physics states, or 480 Hz physics against a 60 Hz display stutters.
 */
public final class TableScene {

    /** TUNED: whole physics steps per trail dot (4.2 ms), shared by the trail and the ghost. */
    private static final int TrailStride = 2;

    /** 1.25 s of flight at TrailStride: any shot end to end. */
    private static final int TrailDots = 300;

    private static final int BounceMarksKept = 24;

    private final BallView Ball = new BallView();
    private final Trail FlightTrail = new Trail(TrailDots, 0.0060, Color.web("#a8401a"), Color.web("#ffe08a"));
    private final Trail Ghost = new Trail(TrailDots, 0.0042, Color.web("#454b54"), Color.web("#9aa5b2"));
    private final BounceMarks Marks = new BounceMarks(BounceMarksKept);
    private final ShotOverlay Shots = new ShotOverlay();
    private final RacketView OpponentRacket = new RacketView();
    private final RacketView PlayerRacket = new RacketView();
    private final Group Root;

    private final Deque<Vec3> TrailPoints = new ArrayDeque<>();
    private int StepsSinceTrailPoint = 0;
    private boolean ShowTrail = true;
    private boolean ShowGhost = true;
    private boolean GhostDiffers;

    /** The blades at the start of the last step, so they interpolate with the same fraction as the ball. */
    private BladeCollider PlayerBladeBefore;
    private BladeCollider OpponentBladeBefore;

    public TableScene(GameSnapshot Start) {
        PlayerBladeBefore = Start.PlayerBlade();
        OpponentBladeBefore = Start.OpponentBlade();
        Root = new Group(ArenaView.Build(), Ball.ShadowNode(), Marks.Node(), Ghost.Node(), FlightTrail.Node(),
                         Shots.Node(), OpponentRacket.Node(), PlayerRacket.Node(), Ball.Node());
    }

    public Group Root() { return Root; }

    /** A new rally: clear what the last one drew, and predict the no-spin ghost to compare against. */
    public void OnLaunch(Feed Shot) {
        StepsSinceTrailPoint = 0;
        TrailPoints.clear();
        FlightTrail.Clear();
        Marks.Clear();
        // Predicted once at the trail's own stride and length, so the two compare dot for dot.
        Ghost.SetPath(FlightPredictor.Path(Shot.WithoutSpin(), TrailDots * TrailStride * Simulation.Step, TrailStride));
        GhostDiffers = Shot.Ball().SpinRate() > 1e-6;
        RefreshGhost();
    }

    public void BeforeStep(GameSnapshot Now) {
        PlayerBladeBefore = Now.PlayerBlade();
        OpponentBladeBefore = Now.OpponentBlade();
    }

    public void AfterStep(StepResult Result, GameSnapshot Now) {
        if (Result.Contact()) Shots.Show(Now.LastShot(), Result.HitBy() != Side.Player);
        for (RallyEvent Event : Result.Events()) {
            if (Event.Type() == EventType.TableBounce) Marks.Add(Event.Point());
        }
        SampleTrail(Now.Ball().Position());
    }

    /** Fraction is how far the display is between the previous physics state and the current one. */
    public void Render(GameSnapshot Now, double Fraction) {
        BallState From = Now.PreviousBall(), To = Now.Ball();
        Ball.Update(new BallState(
                Vec3.Lerp(From.Position(), To.Position(), Fraction),
                Vec3.Lerp(From.Velocity(), To.Velocity(), Fraction),
                Vec3.Lerp(From.Spin(), To.Spin(), Fraction),
                Quat.Slerp(From.Orientation(), To.Orientation(), Fraction)));
        PlayerRacket.Update(PlayerBladeBefore, Now.PlayerBlade(), Fraction);
        OpponentRacket.Update(OpponentBladeBefore, Now.OpponentBlade(), Fraction);
        if (ShowTrail) FlightTrail.SetPath(TrailPoints);
    }

    public void ToggleTrail() {
        ShowTrail = !ShowTrail;
        FlightTrail.SetShown(ShowTrail);
    }

    public void ToggleGhost() {
        ShowGhost = !ShowGhost;
        RefreshGhost();
    }

    /** Twice life size for projectors; the drawn ball only, never the physics radius. */
    public void SetBallMagnified(boolean On) { Ball.SetMagnified(On); }

    public boolean BallMagnified() { return Ball.IsMagnified(); }

    public void SetShotOverlayShown(boolean Shown) { Shots.SetShown(Shown); }

    public boolean ShotOverlayShown() { return Shots.IsShown(); }

    /** Counts whole physics steps, not seconds, so every caller rounds the same way. */
    private void SampleTrail(Vec3 BallPosition) {
        if (++StepsSinceTrailPoint < TrailStride) return;
        StepsSinceTrailPoint = 0;
        TrailPoints.addLast(BallPosition);
        while (TrailPoints.size() > TrailDots) TrailPoints.removeFirst();
    }

    /** A no-spin ghost of a no-spin feed is the flight itself, so it only shows when they differ. */
    private void RefreshGhost() {
        Ghost.SetShown(ShowGhost && GhostDiffers);
    }
}
