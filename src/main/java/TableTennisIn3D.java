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
import physics.*;
import play.GameSession;
import play.PlayerReach;
import play.Scoreboard;
import play.ShotAssist;
import play.Stroke;
import render.*;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static physics.Constants.DT;
import static physics.Constants.MAX_FRAME;

/**
 * Mr. Pong, a 3D table tennis game. The JavaFX entry point and composition root: frame loop,
 * input, camera, views and capture mode around one {@link GameSession}, which decides the game.
 * The session only advances in whole fixed physics steps and never sees a frame time.
 */
public class TableTennisIn3D extends Application {

    private static final Color BACKGROUND = Color.web("#17232e");
    private static final int WINDOW_WIDTH = 1280, WINDOW_HEIGHT = 780;

    /** Stops a catch-up spiral after a stall; MAX_FRAME already bounds a normal frame. */
    private static final int MAX_STEPS_PER_FRAME = 4000;

    /** TUNED: whole physics steps per trail dot (4.2 ms), shared by trail and ghost. */
    private static final int TRAIL_STRIDE = 2;

    /** 1.25 s of flight at TRAIL_STRIDE: any shot end to end. */
    private static final int TRAIL_DOTS = 300;

    private final GameSession session = new GameSession();
    private Shots currentShot = Shots.byName("Serve");

    private double accumulator = 0;
    private long lastNanos = 0;
    /** Opens in slow motion: at 1:1 a first rally is a blur. */
    private double timeScale = 0.45;
    private boolean paused = false;
    private int pendingSingleSteps = 0;

    private final BallView ballView = new BallView();
    private final Trail trail = new Trail(TRAIL_DOTS, 0.0060, Color.web("#a8401a"), Color.web("#ffe08a"));
    private final Trail ghost = new Trail(TRAIL_DOTS, 0.0042, Color.web("#454b54"), Color.web("#9aa5b2"));
    private final BounceMarks bounceMarks = new BounceMarks(24);
    private final CameraRig rig = new CameraRig();
    private final Hud hud = new Hud();
    private final ShotDebug shotDebug = new ShotDebug();
    private final PaddleView playerView = new PaddleView(false);
    private final PaddleView opponentView = new PaddleView(false);

    /** Poses at the start of the last step, so blades interpolate with the same alpha as the ball. */
    private Paddle.Blade prevPlayerPose = session.playerBlade();
    private Paddle.Blade prevOpponentPose = session.opponentBlade();

    private final Deque<Vec3> trailPoints = new ArrayDeque<>();
    private int stepsSinceTrailPoint = 0;
    private int marksAtBounceSerial = 0;

    private boolean showGhost = true;
    private boolean showTrail = true;
    private boolean showHud = true;
    private boolean showControlDebug = false;

    /** The mapped cursor, consumed by the session once per physics step, never per frame. */
    private Vec3 pendingAim = null;
    private double cursorX = Double.NaN, cursorY = Double.NaN;
    private Vec3 rawAim = null;   // before clamping, so the D overlay can show a held blade

    /** While the right button is held the cursor's Y means height and depth is frozen. */
    private boolean brushing = false;
    private double brushHoldZ = PlayerReach.NEUTRAL.z();

    private String screenshotPath = null;
    private double screenshotAt = 0;
    private boolean rallyCamInCapture = false;

    @Override
    public void start(Stage stage) {
        parseArgs();

        SubScene viewport = buildViewport();
        bindMouse(viewport);

        StackPane layers = new StackPane(viewport, hud.node());
        Scene scene = new Scene(layers, WINDOW_WIDTH, WINDOW_HEIGHT, BACKGROUND);
        viewport.widthProperty().bind(scene.widthProperty());
        viewport.heightProperty().bind(scene.heightProperty());
        scene.setOnKeyPressed(e -> onKey(e.getCode()));

        stage.setScene(scene);
        stage.setTitle("Mr. Pong");
        stage.show();
        viewport.requestFocus();

        launchShot(currentShot);
        startLoop(scene);
    }

    private SubScene buildViewport() {
        Group world3d = new Group(
                Court.build(),
                ballView.shadowNode(),
                bounceMarks.node(),
                ghost.node(),
                trail.node(),
                shotDebug.node(),
                opponentView.node(),
                playerView.node(),
                ballView.node(),
                lighting());

        SubScene viewport = new SubScene(new Group(world3d, rig.gimbal()), WINDOW_WIDTH, WINDOW_HEIGHT,
                                         true, SceneAntialiasing.BALANCED);
        viewport.setFill(BACKGROUND);
        viewport.setCamera(rig.camera());
        rig.attachControls(viewport);
        return viewport;
    }

    /** TUNED sports-hall lighting: warm key, cool cross-fill, coloured ambient for the underside. */
    private Group lighting() {
        DirectionalLight key = new DirectionalLight(Color.web("#b9b1a4"));
        key.setDirection(Xform.toScene(new Vec3(0.35, -1, -0.28)).normalize());
        DirectionalLight fill = new DirectionalLight(Color.web("#435c78"));
        fill.setDirection(Xform.toScene(new Vec3(-0.8, -0.55, 0.4)).normalize());
        return new Group(key, fill, new AmbientLight(Color.web("#303944")));
    }

    private void startLoop(Scene scene) {
        new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNanos == 0) { lastNanos = now; return; }   // first frame has no dt
                // Clamped before scaling, so a stall cannot hand the accumulator a second of work.
                double frame = Math.min((now - lastNanos) / 1e9, MAX_FRAME);
                lastNanos = now;

                stepFrame(frame);
                render(frame);
                if (screenshotPath != null && session.time() >= screenshotAt) takeScreenshot(scene);
            }
        }.start();
    }

    private void stepFrame(double frameSeconds) {
        if (paused) {
            runSingleSteps();
            return;
        }
        int steps = 0;
        accumulator += frameSeconds * timeScale;
        while (accumulator >= DT) {
            advanceOne();
            accumulator -= DT;
            if (++steps > MAX_STEPS_PER_FRAME) { accumulator = 0; break; }
            if (session.replayDue()) {
                launchShot(currentShot);   // resets the accumulator
                break;
            }
        }
    }

    /** Through the same fixed step, so a paused frame matches the one produced live. */
    private void runSingleSteps() {
        while (pendingSingleSteps > 0) {
            advanceOne();
            pendingSingleSteps--;
        }
    }

    private void advanceOne() {
        prevPlayerPose = session.playerBlade();
        prevOpponentPose = session.opponentBlade();

        GameSession.StepResult result = session.step();
        if (result.contact()) showContact(result.hitBy());
        if (result.pointAwarded()) hud.setScore(session.score());
        sampleTrail();
    }

    private void sampleTrail() {
        if (++stepsSinceTrailPoint < TRAIL_STRIDE) return;
        stepsSinceTrailPoint = 0;
        trailPoints.addLast(session.ball().pos());
        while (trailPoints.size() > TRAIL_DOTS) trailPoints.removeFirst();
    }

    private void showContact(Scoreboard.Side hitBy) {
        boolean playerHit = hitBy == Scoreboard.Side.PLAYER;
        rig.onRallyHit(playerHit);

        ShotAssist.Debug d = session.lastShot();
        shotDebug.set(d.contact(), d.racketVel(), d.incomingVel(), d.reflectDir(),
                      d.intendDir(), d.finalDir(), d.target(), d.landing(),
                      d.speed(), d.spin(), d.passes(), d.legal());
        shotDebug.setTargetArea(session.targetHalfWidth(), session.targetNearDepth(),
                                session.targetFarDepth(), !playerHit);
        refreshShotReadout();
    }

    /** Interpolated between the last two physics states, or 480 Hz against 60 Hz stutters. */
    private void render(double frameSeconds) {
        double alpha = paused ? 0 : Math.min(1, accumulator / DT);
        BallState from = session.previousBall(), to = session.ball();
        ballView.update(new BallState(
                Vec3.lerp(from.pos(), to.pos(), alpha),
                Vec3.lerp(from.vel(), to.vel(), alpha),
                Vec3.lerp(from.spin(), to.spin(), alpha),
                Quat.slerp(from.orient(), to.orient(), alpha)));

        rig.updateRally(frameSeconds, to.pos());
        drawPaddle(playerView, prevPlayerPose, session.playerBlade(), alpha);
        drawPaddle(opponentView, prevOpponentPose, session.opponentBlade(), alpha);

        if (showTrail) trail.setPath(trailPoints);
        if (marksAtBounceSerial != session.bounceSerial()) {
            marksAtBounceSerial = session.bounceSerial();
            bounceMarks.setMarks(session.bounceMarks());
        }
        if (showControlDebug) hud.setControl(controlReadout());
    }

    /** The normal is lerped, not slerped: under a degree per step, the two agree to 1e-6. */
    private static void drawPaddle(PaddleView view, Paddle.Blade from, Paddle.Blade to, double alpha) {
        view.update(Vec3.lerp(from.centre(), to.centre(), alpha),
                    Vec3.lerp(from.normal(), to.normal(), alpha).normalized());
    }

    private void refreshShotReadout() {
        hud.setShot(shotDebug.isShown() ? shotDebug.readout() : null);
    }

    private void refreshGhost() {
        ghost.setShown(showGhost && currentShot.state().spinRate() > 1e-6);
    }

    /**
     * The D overlay: did the control mapping fail, or was the ball unplayable? Read-only and
     * downstream of the blade's target, so the ball never steers the control.
     */
    private String controlReadout() {
        BallState b = session.ball();
        Vec3 blade = session.playerBlade().centre();
        Vec3 target = pendingAim != null ? pendingAim : blade;

        double dist = PlayerReach.travelDistance(blade, target);
        double travel = PlayerReach.travelTime(blade, target);
        double arrive = PlayerReach.timeToDepth(b, target.z());
        boolean clamped = rawAim != null
                && (Math.abs(rawAim.x() - target.x()) > 1e-6 || Math.abs(rawAim.z() - target.z()) > 1e-6);

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
            Double.isNaN(cursorX) ? "(none yet)" : String.format("(%4.0f,%4.0f)", cursorX, cursorY),
            rawAim == null ? "" : String.format("   ray -> x %+.3f  z %+.3f", rawAim.x(), rawAim.z()),
            blade.x(), blade.y(), blade.z(),
            target.x(), target.y(), target.z(), clamped ? "   (clamped)" : "",
            -PlayerReach.MAX_X, PlayerReach.MAX_X, PlayerReach.HIT_Y, PlayerReach.Z_NEAR, PlayerReach.Z_FAR,
            dist, travel * 1000, Stroke.TRACK_SPEED,
            b.pos().x(), b.pos().y(), b.pos().z(),
            Double.isNaN(arrive) ? "  --  " : String.format("%.0f ms", arrive * 1000), target.z(),
            reachVerdict(travel, arrive));
    }

    private static String reachVerdict(double travel, double arrive) {
        if (Double.isNaN(arrive)) return "n/a  (ball not coming to this depth)";
        if (travel <= arrive) return String.format("YES  (%.0f ms to spare)", (arrive - travel) * 1000);
        return String.format("NO   (%.0f ms short)", (travel - arrive) * 1000);
    }

    /**
     * Bare movement aims; the left button orbits. addEventHandler, because setOnMouseMoved would
     * unhook CameraRig's orbit handler. MOUSE_MOVED stops while a button is down, so the brush
     * also listens to DRAGGED.
     */
    private void bindMouse(SubScene viewport) {
        viewport.addEventHandler(MouseEvent.MOUSE_MOVED, e -> aim(viewport, e));
        viewport.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> { if (brushing) aim(viewport, e); });
        viewport.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (!e.isSecondaryButtonDown()) return;
            brushing = true;
            brushHoldZ = session.playerBlade().centre().z();
            aim(viewport, e);
        });
        viewport.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (!brushing || e.isSecondaryButtonDown()) return;
            brushing = false;
            aim(viewport, e);
        });
    }

    /** sceneToLocal, since events target the 3D nodes inside the SubScene. */
    private void aim(SubScene viewport, MouseEvent e) {
        Point2D p = viewport.sceneToLocal(e.getSceneX(), e.getSceneY());
        // Only for a degenerate ray; used as an input it would be a loop with gain.
        Vec3 fallback = pendingAim != null ? pendingAim : session.playerBlade().centre();

        cursorX = p.getX();
        cursorY = p.getY();
        rawAim = MouseAim.onHittingPlane(viewport, p.getX(), p.getY(), PlayerReach.HIT_Y, fallback);
        pendingAim = brushing
                ? PlayerReach.clampBrushed(rawAim, p.getY() / Math.max(1, viewport.getHeight()), brushHoldZ)
                : PlayerReach.clamp(rawAim);
        session.setAim(pendingAim);
    }

    private void onKey(KeyCode code) {
        if (isTopRowDigit(code)) {
            pick((code.getChar().charAt(0) - '0' + 9) % 10);   // 1..9 then 0
            return;
        }
        switch (code) {
            case N, RIGHT -> launchShot(nextShot(+1));
            case P, LEFT  -> launchShot(nextShot(-1));
            case R        -> launchShot(currentShot);
            case SPACE  -> paused = !paused;
            case PERIOD -> { paused = true; pendingSingleSteps += 1; }
            case OPEN_BRACKET  -> timeScale = Math.max(0.02, timeScale / 1.6);
            case CLOSE_BRACKET -> timeScale = Math.min(2.0, timeScale * 1.6);
            case G -> { showGhost = !showGhost; refreshGhost(); }
            case T -> { showTrail = !showTrail; trail.setShown(showTrail); }
            case A -> session.setAutoReplay(!session.autoReplay());
            case B -> ballView.setMagnified(!ballView.isMagnified());
            case F -> rig.toggleRallyCam();
            case C -> rig.next();
            case V -> { shotDebug.setShown(!shotDebug.isShown()); refreshShotReadout(); }
            case D -> toggleControlDebug();
            case M -> toggleDemo();
            case H -> { showHud = !showHud; hud.setShown(showHud); }
            case ESCAPE -> Platform.exit();
            default -> { }
        }
    }

    /** DIGIT0..DIGIT9 only: numpad digits never picked a feed. */
    private static boolean isTopRowDigit(KeyCode code) {
        return code.name().startsWith("DIGIT");
    }

    private void toggleControlDebug() {
        showControlDebug = !showControlDebug;
        hud.setControl(showControlDebug ? controlReadout() : null);
    }

    private void toggleDemo() {
        session.setDemoMode(!session.demoMode());
        hud.setFeed(session.demoMode() ? currentShot.name() + "   [DEMO -- M to take over]"
                                       : currentShot.name());
    }

    private void pick(int index) {
        if (index < Shots.ALL.length) launchShot(Shots.ALL[index]);
    }

    private Shots nextShot(int delta) {
        int i = 0;
        for (int k = 0; k < Shots.ALL.length; k++) {
            if (Shots.ALL[k] == currentShot) { i = k; break; }
        }
        return Shots.byIndex(i + delta);
    }

    /** Start a rally with this feed, keeping the score, and clear what the last one drew. */
    private void launchShot(Shots shot) {
        currentShot = shot;
        session.launch(shot);
        hud.setFeed(shot.name());
        hud.setScore(session.score());
        rig.onRallyHit(true);   // the feed stands in for the player's own shot

        accumulator = 0;
        stepsSinceTrailPoint = 0;
        trailPoints.clear();
        trail.clear();
        bounceMarks.clear();

        // The no-spin ghost, predicted once at the trail's stride and length so they compare dot for dot.
        List<Vec3> ghostPath = World.predict(shot.withoutSpin(), TRAIL_DOTS * TRAIL_STRIDE * DT, TRAIL_STRIDE);
        ghost.setPath(ghostPath);
        refreshGhost();
    }

    /** Offline capture: --shot="Topspin loop" --at=0.45 --view=SIDE --out=frame.png */
    private void parseArgs() {
        for (String arg : getParameters().getRaw()) {
            String[] kv = arg.split("=", 2);
            if (kv.length == 2) applyArg(kv[0], kv[1]);
        }
        if (screenshotPath != null) {
            session.setAutoReplay(false);   // a replay would reset the clock before a late --at
            if (!rallyCamInCapture) rig.stopRallyCam();
        }
    }

    private void applyArg(String name, String value) {
        switch (name) {
            case "--shot" -> currentShot = Shots.byName(value);
            case "--at"   -> screenshotAt = Double.parseDouble(value);
            case "--out"  -> screenshotPath = value;
            case "--view" -> rig.apply(CameraRig.View.fromArg(value));
            case "--ball2x" -> ballView.setMagnified(Boolean.parseBoolean(value));
            case "--controldebug" -> showControlDebug = Boolean.parseBoolean(value);
            case "--demo" -> session.setDemoMode(Boolean.parseBoolean(value));
            case "--rallycam" -> rallyCamInCapture = Boolean.parseBoolean(value);
            default -> { }
        }
    }

    private void takeScreenshot(Scene scene) {
        String path = screenshotPath;
        screenshotPath = null;
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(scene.snapshot(null), null), "png", new File(path));
            System.out.println("wrote " + path + " at t=" + String.format("%.3f", session.time()));
        } catch (Exception e) {
            System.err.println("screenshot failed: " + e);
        }
        Platform.exit();
    }

    public static void main(String[] args) { launch(args); }
}
