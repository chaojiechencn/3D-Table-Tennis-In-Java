import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.scene.*;
import javafx.scene.image.WritableImage;
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
 * Mr. Pong - a 3D table tennis game. The JavaFX entry point and composition root: it owns the
 * frame loop, input, camera, views and capture mode, and drives one {@link GameSession}, which
 * owns everything that decides the game. See docs/GAMEPLAY.md for how it plays and
 * docs/DESIGN.md for the architecture.
 *
 * Mouse events and frame times stay here. The session only ever advances in whole fixed physics
 * steps, so the game plays the same on a fast or a slow machine.
 */
public class Table_Tennis_In_3D extends Application {

    // ------------------------------------------------------------------ game

    private final GameSession session = new GameSession();
    // Opens on a gentle no-spin corner-to-corner serve; every hit after that goes through
    // ShotAssist on both rackets, which keeps the rally playable.
    private Shots currentShot = Shots.byName("Serve");

    // ------------------------------------------------------------------ frame loop

    /** Leftover time not yet consumed by a whole physics step ("Fix Your Timestep!"). */
    private double accumulator = 0;
    private long lastNanos = 0;

    /**
     * Simulated seconds per real second. Table tennis at 1:1 is a blur on a first play, so the
     * game opens in slow motion; [ and ] walk it up to 2x. Fewer fixed steps run per second --
     * every one is identical to a full-speed step.
     */
    private double timeScale = 0.45;
    private boolean paused = false;
    private int pendingSingleSteps = 0;

    /** Guards against a death spiral after a stall; MAX_FRAME already bounds a normal frame. */
    private static final int MAX_STEPS_PER_FRAME = 4000;

    // ------------------------------------------------------------------ views

    /**
     * Trail resolution, in whole PHYSICS STEPS per dot, so the live trail and the ghost sample by
     * the identical rule. TUNED: 2 steps is 4.2 ms, ~6 cm between dots on a 15 m/s drive.
     */
    private static final int TRAIL_STRIDE = 2;

    /** 300 dots at TRAIL_STRIDE is 1.25 s of flight, which covers any shot end to end. */
    private static final int TRAIL_DOTS = 300;

    private final BallView ballView = new BallView();
    // The live trail warms toward the ball so the direction of travel reads in a still frame;
    // the no-spin ghost stays colourless so it never competes with the real one.
    private final Trail trail = new Trail(TRAIL_DOTS, 0.0060,
            Color.web("#a8401a"), Color.web("#ffe08a"));
    private final Trail ghost = new Trail(TRAIL_DOTS, 0.0042,
            Color.web("#454b54"), Color.web("#9aa5b2"));
    private final BounceMarks bounceMarks = new BounceMarks(24);
    private final CameraRig rig = new CameraRig();
    private final Hud hud = new Hud();
    private final ShotDebug shotDebug = new ShotDebug();

    // Red rubber on the -normal side of both rackets: we see the player's red side and the
    // opponent's black side, so the two never read as the same object.
    private final PaddleView playerView = new PaddleView(false);
    private final PaddleView opponentView = new PaddleView(false);

    /**
     * The racket poses at the START of the last physics step, so the blades interpolate across a
     * frame with the same alpha as the ball -- at 480 Hz against 60 Hz the rates never line up,
     * and a blade snapped to the raw state stutters exactly when it is moving fastest.
     */
    private Paddle.Blade prevPlayerPose = session.playerBlade();
    private Paddle.Blade prevOpponentPose = session.opponentBlade();

    private final Deque<Vec3> trailPoints = new ArrayDeque<>();
    private int stepsSinceTrailPoint = 0;
    /** The bounce serial the drawn marks reflect, so they are only rewritten when one lands. */
    private int marksAtBounceSerial = 0;

    private boolean showGhost = true;
    private boolean showTrail = true;
    private boolean showHud = true;
    private boolean showControlDebug = false;   // D -- see controlReadout()

    // ------------------------------------------------------------------ input

    /**
     * Where the cursor last pointed on the hitting plane, or null before the mouse has moved.
     * Handed to the session, which consumes it once per physics step -- never moved straight
     * from the handler, which would drive the blade at the frame rate.
     */
    private Vec3 pendingAim = null;

    /** The cursor and where its ray landed BEFORE the envelope clamped it, for the D overlay:
     *  "not where I pointed" and "cannot go where I pointed" look identical on screen. */
    private double cursorX = Double.NaN, cursorY = Double.NaN;
    private Vec3 rawAim = null;

    /**
     * The brush modifier: while the right button is held, the cursor's Y means blade HEIGHT
     * instead of depth, and the depth at button-down is frozen. One meaning per axis at a time.
     */
    private boolean brushing = false;
    private double brushHoldZ = PlayerReach.NEUTRAL.z();

    // ------------------------------------------------------------------ capture mode

    private String screenshotPath = null;
    private double screenshotAt = 0;
    /** Capture pins a fixed camera; --rallycam=true opts back into the rally-cam's swing. */
    private boolean rallyCamInCapture = false;

    @Override
    public void start(Stage stage) {
        parseArgs();

        SubScene viewport = buildViewport();
        bindMouse(viewport);

        StackPane layers = new StackPane(viewport, hud.node());
        Scene scene = new Scene(layers, 1280, 780, Color.web("#17232e"));
        // Keep the 3D viewport matched to the window instead of letterboxing it.
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

    // ------------------------------------------------------------------ scene

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

        SubScene viewport = new SubScene(new Group(world3d, rig.gimbal()), 1280, 780, true,
                                         SceneAntialiasing.BALANCED);
        viewport.setFill(Color.web("#17232e"));
        viewport.setCamera(rig.camera());
        rig.attachControls(viewport);
        return viewport;
    }

    /** TUNED broad overhead lighting for a sports hall: directional sources keep incidence
     *  uniform along the table as the camera cuts, a warm key and cooler cross-fill model the
     *  room's ceiling and reflected light, and the coloured ambient lifts the ball's underside
     *  without washing out its spherical shading. */
    private Group lighting() {
        DirectionalLight key = new DirectionalLight(Color.web("#b9b1a4"));
        key.setDirection(Xform.toScene(new Vec3(0.35, -1, -0.28)).normalize());
        DirectionalLight fill = new DirectionalLight(Color.web("#435c78"));
        fill.setDirection(Xform.toScene(new Vec3(-0.8, -0.55, 0.4)).normalize());
        return new Group(key, fill, new AmbientLight(Color.web("#303944")));
    }

    // ------------------------------------------------------------------ frame loop

    private void startLoop(Scene scene) {
        new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNanos == 0) { lastNanos = now; return; }   // first frame has no dt

                // Clamp before scaling: a stall (a breakpoint, a window drag) must not hand the
                // accumulator a second of work and send the loop into a catch-up spiral.
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
            // Single steps go through the same fixed step, so a paused frame is identical to
            // the one that would have been produced live.
            while (pendingSingleSteps > 0) {
                advanceOne();
                pendingSingleSteps--;
            }
            return;
        }

        int steps = 0;
        accumulator += frameSeconds * timeScale;
        while (accumulator >= DT) {
            advanceOne();
            accumulator -= DT;
            if (++steps > MAX_STEPS_PER_FRAME) { accumulator = 0; break; }

            if (session.replayDue()) {
                launchShot(currentShot);
                break;                  // launchShot resets the accumulator; stop stepping
            }
        }
    }

    /** One physics step, and the presentation that follows from it. */
    private void advanceOne() {
        prevPlayerPose = session.playerBlade();
        prevOpponentPose = session.opponentBlade();

        GameSession.StepResult result = session.step();
        if (result.contact()) showContact(result.hitBy());
        if (result.pointAwarded()) hud.setScore(session.score());

        if (++stepsSinceTrailPoint >= TRAIL_STRIDE) {
            stepsSinceTrailPoint = 0;
            trailPoints.addLast(session.ball().pos());
            while (trailPoints.size() > TRAIL_DOTS) trailPoints.removeFirst();
        }
    }

    /** A racket contact: cut the rally-cam and refresh the shot-assist overlay. */
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

    // ------------------------------------------------------------------ rendering

    private void render(double frameSeconds) {
        // Interpolate between the last two physics states, or the ball stutters whenever the
        // frame rate is not a multiple of the physics rate -- which at 480 Hz it never is.
        double alpha = paused ? 0 : Math.min(1, accumulator / DT);
        BallState from = session.previousBall(), to = session.ball();
        ballView.update(new BallState(
                Vec3.lerp(from.pos(), to.pos(), alpha),
                Vec3.lerp(from.vel(), to.vel(), alpha),
                Vec3.lerp(from.spin(), to.spin(), alpha),
                Quat.slerp(from.orient(), to.orient(), alpha)));

        // Real frame time: the camera is a view, not physics.
        rig.updateRally(frameSeconds, to.pos());

        // The same alpha, so blade and ball never disagree at the instant of contact.
        drawPaddle(playerView, prevPlayerPose, session.playerBlade(), alpha);
        drawPaddle(opponentView, prevOpponentPose, session.opponentBlade(), alpha);

        if (showTrail) trail.setPath(trailPoints);

        if (marksAtBounceSerial != session.bounceSerial()) {
            marksAtBounceSerial = session.bounceSerial();
            bounceMarks.setMarks(session.bounceMarks());
        }

        // Per frame, not per step: it flies a 3 s prediction, and a human reads it far less often.
        if (showControlDebug) hud.setControl(controlReadout());
    }

    /**
     * Draw a racket between two poses. The normal is lerped and renormalised, not slerped: a
     * blade turns well under a degree per step, where the two agree to parts in a million.
     */
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
     * The control/reachability overlay (D): was it the CONTROL MAPPING failing to put the bat
     * where the player wanted, or was the ball genuinely unplayable? Read-only and downstream of
     * everything -- the blade's target is already chosen, so the ball never steers the control.
     */
    private String controlReadout() {
        BallState b = session.ball();
        Vec3 blade = session.playerBlade().centre();
        Vec3 target = pendingAim != null ? pendingAim : blade;

        double dist = PlayerReach.travelDistance(blade, target);
        double travel = PlayerReach.travelTime(blade, target);
        double arrive = PlayerReach.timeToDepth(b, target.z());

        // Clamped is not unreachable: pointing past the legal region holds the blade at its edge,
        // and a blade that seems stuck is usually one sitting on a clamp.
        boolean clamped = rawAim != null
                && (Math.abs(rawAim.x() - target.x()) > 1e-6 || Math.abs(rawAim.z() - target.z()) > 1e-6);

        String verdict;
        if (Double.isNaN(arrive))          verdict = "n/a  (ball not coming to this depth)";
        else if (travel <= arrive)         verdict = String.format("YES  (%.0f ms to spare)", (arrive - travel) * 1000);
        else                               verdict = String.format("NO   (%.0f ms short)", (travel - arrive) * 1000);

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
            verdict);
    }

    // ------------------------------------------------------------------ input

    /**
     * Bare mouse movement aims; the left button belongs to camera orbit ({@link CameraRig}).
     * addEventHandler, not setOnMouseMoved: that single-slot property would silently unhook the
     * orbit handler CameraRig already installed on this SubScene.
     */
    private void bindMouse(SubScene viewport) {
        viewport.addEventHandler(MouseEvent.MOUSE_MOVED, e -> aim(viewport, e));

        // MOUSE_MOVED stops firing while any button is down, so the brush needs DRAGGED too.
        viewport.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> { if (brushing) aim(viewport, e); });

        viewport.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.isSecondaryButtonDown()) {
                brushing = true;
                brushHoldZ = session.playerBlade().centre().z();   // freeze the current depth
                aim(viewport, e);
            }
        });
        viewport.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (brushing && !e.isSecondaryButtonDown()) {
                brushing = false;
                aim(viewport, e);                                    // hand the axis back to depth
            }
        });
    }

    /**
     * Map the cursor onto the hitting plane and hand it to the session. sceneToLocal, not
     * getX/getY: events target the 3D nodes inside the SubScene, and scene coordinates are the
     * one frame both agree on.
     */
    private void aim(SubScene viewport, MouseEvent e) {
        Point2D p = viewport.sceneToLocal(e.getSceneX(), e.getSceneY());
        // The fallback is only for a degenerate ray, never an input, or this would be a loop
        // with gain. PlayerReach then applies the envelope, discarding the ray's height.
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
        switch (code) {
            // Written out: nothing promises KeyCode keeps DIGIT0..DIGIT9 contiguous.
            case DIGIT1 -> pick(0);
            case DIGIT2 -> pick(1);
            case DIGIT3 -> pick(2);
            case DIGIT4 -> pick(3);
            case DIGIT5 -> pick(4);
            case DIGIT6 -> pick(5);
            case DIGIT7 -> pick(6);
            case DIGIT8 -> pick(7);
            case DIGIT9 -> pick(8);
            case DIGIT0 -> pick(9);

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
            case C -> rig.next();          // next() drops the rally-cam for a manual view
            case V -> { shotDebug.setShown(!shotDebug.isShown()); refreshShotReadout(); }
            case D -> {
                showControlDebug = !showControlDebug;
                hud.setControl(showControlDebug ? controlReadout() : null);
            }
            case M -> {
                session.setDemoMode(!session.demoMode());
                hud.setFeed(session.demoMode() ? currentShot.name() + "   [DEMO -- M to take over]"
                                               : currentShot.name());
            }
            case H -> { showHud = !showHud; hud.setShown(showHud); }
            case ESCAPE -> Platform.exit();

            default -> { }
        }
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

    // ------------------------------------------------------------------ feeds

    /** Start a rally with this feed, keeping the score, and reset what the views drew for the last. */
    private void launchShot(Shots shot) {
        currentShot = shot;
        session.launch(shot);
        hud.setFeed(shot.name());
        hud.setScore(session.score());      // also puts 0-0 on screen for the opening serve

        // The feed stands in for the player's own shot, so the rally-cam opens zoomed IN; it cuts
        // OUT when the opponent returns it.
        rig.onRallyHit(true);

        accumulator = 0;
        stepsSinceTrailPoint = 0;
        trailPoints.clear();
        trail.clear();
        bounceMarks.clear();

        // The comparison ghost: identical launch, spin deleted, predicted once up front. Sampled
        // at the trail's stride for exactly the trail's length, so the two compare dot for dot.
        List<Vec3> ghostPath = World.predict(shot.withoutSpin(),
                TRAIL_DOTS * TRAIL_STRIDE * DT, TRAIL_STRIDE);
        ghost.setPath(ghostPath);
        refreshGhost();
    }

    // ------------------------------------------------------------------ capture mode

    /**
     * Offline capture, to check the rendering without a human watching:
     *   --shot="Topspin loop" --at=0.45 --view=SIDE --out=frame.png
     */
    private void parseArgs() {
        for (String arg : getParameters().getRaw()) {
            String[] kv = arg.split("=", 2);
            if (kv.length != 2) continue;
            switch (kv[0]) {
                case "--shot" -> currentShot = Shots.byName(kv[1]);
                case "--at"   -> screenshotAt = Double.parseDouble(kv[1]);
                case "--out"  -> screenshotPath = kv[1];
                case "--view" -> rig.apply(CameraRig.View.valueOf(kv[1]));
                case "--ball2x" -> ballView.setMagnified(Boolean.parseBoolean(kv[1]));
                // What D does, from the command line, so a capture proves the overlay renders.
                case "--controldebug" -> showControlDebug = Boolean.parseBoolean(kv[1]);
                case "--demo" -> session.setDemoMode(Boolean.parseBoolean(kv[1]));
                case "--rallycam" -> rallyCamInCapture = Boolean.parseBoolean(kv[1]);
                default -> { }
            }
        }

        if (screenshotPath != null) {
            // A replay loop would reset the clock before a late --at was ever reached.
            session.setAutoReplay(false);
            // A capture wants a fixed, predictable camera unless it asked for the rally-cam.
            if (!rallyCamInCapture) rig.stopRallyCam();
        }
    }

    private void takeScreenshot(Scene scene) {
        String path = screenshotPath;
        screenshotPath = null;                 // once only
        try {
            WritableImage img = scene.snapshot(null);
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", new File(path));
            System.out.println("wrote " + path + " at t=" + String.format("%.3f", session.time()));
        } catch (Exception e) {
            System.err.println("screenshot failed: " + e);
        }
        Platform.exit();
    }

    public static void main(String[] args) { launch(args); }
}
