import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.scene.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import physics.*;
import play.Follower;
import play.Opponent;
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
 * Mr. Pong - a 3D table tennis game.
 *
 * Checkpoint 1 (27 August) was the physics on its own: a ball flying with spin, curving in the
 * air, bouncing off the table and dying in the net. That still runs, and the ghost trail and
 * camera presets that made the curve visible are still here.
 *
 * This is checkpoint 2, the playable half. What it adds:
 *
 *   - a racket on the near end that follows the mouse, and one at the far end played by the AI
 *   - a charge-and-release stroke: hold the right button to wind up, drag to choose the
 *     direction you will swing through, let go to hit
 *   - spin that comes out of the CONTACT rather than out of a table of shot types. Nothing
 *     here tells the ball how fast to leave or how much spin to carry; the solver measures the
 *     blade's own velocity and the tilt of its face, and the shot falls out of that
 *
 * Still missing, and deliberately next rather than now: serving and scoring.
 *
 * The physics lives in the physics package and the game logic in play, and neither imports
 * JavaFX -- so both can be checked headlessly, and neither can accidentally start depending on
 * the frame rate. Run physics.SelfTest and play.RallyTest for that.
 */
public class MrPong extends Application {

    // ------------------------------------------------------------------ simulation

    private final World world = new World();
    private Shots currentShot = Shots.byName("Topspin loop");

    /**
     * The two rackets.
     *
     * Both are KINEMATIC: nothing pushes them around, and neither one tells the ball anything.
     * A racket is moved to a pose each step and Paddle works out the velocity it must have had
     * to get there, which is the velocity the contact solver strikes the ball with. That is
     * why they are advanced per PHYSICS STEP below and never per frame -- driven at the frame
     * rate, a slow machine would swing the same stroke harder.
     */
    private final Paddle playerPaddle =
            new Paddle(new Vec3(0, 0.25, MouseAim.PLAYER_PLANE_Z), new Vec3(0, 0, -1));
    private final Paddle aiPaddle =
            new Paddle(new Vec3(0, 0.20, Follower.PLANE_Z), new Vec3(0, 0, 1));

    private final Stroke stroke = new Stroke(new Vec3(0, 0.25, MouseAim.PLAYER_PLANE_Z));
    private final Opponent opponent = new Follower();

    /**
     * Leftover time not yet consumed by a whole physics step.
     * The whole reason the loop is built this way (Gaffer On Games, "Fix Your Timestep!") is
     * that the contract asks for a game loop where "the physics runs the same on a fast or
     * slow computer". Stepping by the frame time would make the bounce height depend on the
     * frame rate, which is the classic way a physics demo becomes unreproducible.
     */
    private double accumulator = 0;
    private long lastNanos = 0;

    private double timeScale = 1.0;
    private boolean paused = false;
    private int singleSteps = 0;
    private double fps = 0;

    // ------------------------------------------------------------------ view

    private final BallView ball = new BallView();
    // Sized and coloured to read against a dark table from across a room. The live trail
    // warms toward the ball so the direction of travel is obvious in a still frame; the ghost
    // stays deliberately colourless so it never competes with the real one.
    private final Trail trail = new Trail(TRAIL_DOTS, 0.0060,
            Color.web("#a8401a"), Color.web("#ffe08a"));
    private final Trail ghost = new Trail(TRAIL_DOTS, 0.0042,
            Color.web("#454b54"), Color.web("#9aa5b2"));
    private final BounceMarks marks = new BounceMarks(24);
    private final CameraRig rig = new CameraRig();
    private final Hud hud = new Hud();

    // Red rubber on the -normal side of both rackets. The player's blade faces down the table,
    // so its red side looks back at the camera; the opponent's faces the other way, so we see
    // its black side. The two never read as the same object from the player's viewpoint.
    private final PaddleView playerView = new PaddleView(false);
    private final PaddleView aiView = new PaddleView(false);

    /**
     * The racket poses at the START of the last physics step.
     *
     * Held so the rackets can be interpolated across a frame with the same alpha as the ball,
     * for the same reason the ball needs it: at 480 Hz against a 60 Hz display the two rates
     * never line up, and a blade snapped to the raw state stutters exactly when it is moving
     * fastest and being watched hardest. Paddle.Blade is already precisely a frozen pose.
     */
    private Paddle.Blade prevPlayerPose = playerPaddle.collider();
    private Paddle.Blade prevAiPose = aiPaddle.collider();

    /**
     * Where the cursor last pointed on the hitting plane, or null before the mouse has moved
     * over the window.
     *
     * Sampled in the event handler and CONSUMED in advanceOne(). Never used straight from the
     * handler: mouse events arrive once a frame and the stroke is advanced once a step, so
     * moving the blade from the handler would be driving the physics at the frame rate.
     */
    private Vec3 pendingAim = null;

    private final Deque<Vec3> trailPoints = new ArrayDeque<>();
    private int stepsSinceTrailPoint = 0;

    /** How many bounce marks are currently drawn, so they are only rewritten when one lands. */
    private int shownMarks = 0;

    /**
     * Trail resolution, in whole PHYSICS STEPS per dot.
     *
     * Counted in steps rather than in seconds so that the live trail and the ghost are
     * sampled by the identical rule. Expressed as a duration it could not be: the old 2.5 ms
     * is 1.2 steps at DT = 1/480 s, and the two paths rounded that fraction in OPPOSITE
     * directions -- the live trail accumulated up to 2 steps, the ghost rounded down to 1.
     * The ghost came out twice as dense as the shot it exists to be compared against, and
     * 360 points long against a 300-dot trail, so it silently dropped the first 0.125 s of
     * its own flight and no longer started where the ball started. Comparing two paths drawn
     * to two different rules is exactly the thing this demo must not do.
     *
     * 2 steps is 4.2 ms, about 6 cm between dots on a 15 m/s drive, which still reads as a
     * continuous line. Being in simulated steps (not frames) is also what makes slow motion
     * show the same curve rather than a denser one.
     */
    private static final int TRAIL_STRIDE = 2;

    /** 300 dots at TRAIL_STRIDE is 1.25 s of flight, which covers any shot end to end. */
    private static final int TRAIL_DOTS = 300;

    private boolean showGhost = true;
    private boolean showTrail = true;
    private boolean showHud = true;
    private boolean autoReplay = true;

    /**
     * When the current rally should restart, or NaN while it is still live.
     *
     * Without this the demo dies quietly: the interesting second is over, the ball rolls away
     * across the floor, and what is left on screen is an empty table. Since this is meant to
     * be left running in front of someone, the feed loops.
     */
    private double replayAt = Double.NaN;

    /** How long to keep watching after the ball has dropped past the table. */
    private static final double REPLAY_DELAY = 1.8;

    // ------------------------------------------------------------------ screenshot mode

    private String screenshotPath = null;
    private double screenshotAt = 0;

    @Override
    public void start(Stage stage) {
        parseArgs();

        // Hand the world its rackets. World.predict deliberately never does this -- a
        // prediction that gets intercepted is a prediction of nothing.
        world.setPaddles(playerPaddle, aiPaddle);

        Group world3d = new Group(
                Court.build(),
                marks.node(),
                ghost.node(),
                trail.node(),
                aiView.node(),
                playerView.node(),
                ball.node(),
                lighting());

        Group root3d = new Group(world3d, rig.gimbal());

        SubScene sub = new SubScene(root3d, 1280, 780, true, SceneAntialiasing.BALANCED);
        sub.setFill(Color.web("#0b0e13"));
        sub.setCamera(rig.camera());
        rig.attachControls(sub);
        attachStrokeControls(sub);

        StackPane layers = new StackPane(sub, hud.node());
        Scene scene = new Scene(layers, 1280, 780, Color.web("#0b0e13"));

        // Keep the 3D viewport matched to the window instead of letterboxing it.
        sub.widthProperty().bind(scene.widthProperty());
        sub.heightProperty().bind(scene.heightProperty());

        scene.setOnKeyPressed(e -> onKey(e.getCode()));

        stage.setScene(scene);
        stage.setTitle("Mr. Pong");
        stage.show();
        sub.requestFocus();

        launchShot(currentShot);
        startLoop(scene);
    }

    // ------------------------------------------------------------------ the loop

    private void startLoop(Scene scene) {
        new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNanos == 0) { lastNanos = now; return; }   // first frame has no dt

                double frame = (now - lastNanos) / 1e9;
                lastNanos = now;
                fps = fps == 0 ? 1 / frame : fps * 0.9 + (1 / frame) * 0.1;

                // Clamp before scaling: a stall (a breakpoint, a window drag) must not hand
                // the accumulator a second of work and send the loop into a spiral trying to
                // catch up, which would drop the frame rate further and never recover.
                frame = Math.min(frame, MAX_FRAME);

                stepPhysics(frame);
                render();

                if (screenshotPath != null && world.time() >= screenshotAt) {
                    takeScreenshot(scene);
                }
            }
        }.start();
    }

    private void stepPhysics(double frameSeconds) {
        if (paused) {
            // Single-stepping still goes through the same fixed step, so a frame examined
            // while paused is identical to the one that would have been produced live.
            while (singleSteps > 0) {
                advanceOne();
                singleSteps--;
            }
            return;
        }

        int steps = 0;
        accumulator += frameSeconds * timeScale;
        while (accumulator >= DT) {
            advanceOne();
            accumulator -= DT;
            if (++steps > 4000) { accumulator = 0; break; }        // hard safety stop

            if (!Double.isNaN(replayAt) && world.time() >= replayAt) {
                launchShot(currentShot);
                break;                  // launchShot resets the accumulator; stop stepping
            }
        }
    }

    /**
     * One physics step: move both rackets, then let the world resolve what that did.
     *
     * The order is the point. Both blades are posed for the step BEFORE the step runs, so the
     * velocity the contact solver sees is the one the blade actually had while the ball was
     * arriving. Posing them afterwards would hit the ball with the previous step's swing.
     */
    private void advanceOne() {
        // Freeze the poses the rackets are about to leave, for render() to interpolate from.
        prevPlayerPose = playerPaddle.collider();
        prevAiPose = aiPaddle.collider();

        if (pendingAim != null) stroke.aimAt(pendingAim);
        stroke.advance(playerPaddle, DT);
        opponent.advance(world.state(), aiPaddle, DT);

        world.step();

        // The rally is over in one of two ways: the ball has dropped past the table and is
        // never coming back, or it has died ON the table -- which is what the net shot and the
        // ITTF drop test both do, and checking only for the first left those two sitting
        // motionless forever.
        boolean gone = world.state().pos().y() < -0.25;
        boolean stopped = world.time() > 1.5 && world.state().speed() < 0.25;
        if (autoReplay && Double.isNaN(replayAt) && (gone || stopped)) {
            replayAt = world.time() + REPLAY_DELAY;
        }

        if (++stepsSinceTrailPoint >= TRAIL_STRIDE) {
            stepsSinceTrailPoint = 0;
            trailPoints.addLast(world.state().pos());
            while (trailPoints.size() > TRAIL_DOTS) trailPoints.removeFirst();
        }
    }

    // ------------------------------------------------------------------ rendering

    private void render() {
        // Interpolate between the last two physics states. Without this the ball visibly
        // stutters whenever the frame rate is not an exact multiple of the physics rate,
        // which at 480 Hz against a 60 Hz display it never is.
        double alpha = paused ? 0 : Math.min(1, accumulator / DT);
        BallState a = world.previous(), b = world.state();

        BallState shown = new BallState(
                Vec3.lerp(a.pos(), b.pos(), alpha),
                Vec3.lerp(a.vel(), b.vel(), alpha),
                Vec3.lerp(a.spin(), b.spin(), alpha),
                Quat.slerp(a.orient(), b.orient(), alpha));

        ball.update(shown);

        // The same alpha, so the blade and the ball never disagree about where they are at
        // the instant of contact -- which is the one frame anybody is looking closely at.
        drawPaddle(playerView, prevPlayerPose, playerPaddle, alpha);
        drawPaddle(aiView, prevAiPose, aiPaddle, alpha);

        // The deque is handed over as-is. Copying it built a fresh 300-element list every
        // frame to describe a path that only changes by one point every other physics step.
        if (showTrail) trail.setPath(trailPoints);

        // Marks only move when the ball lands, so rewriting all 24 discs on every frame was
        // work with nothing to show for it.
        if (shownMarks != world.tableBounces()) {
            shownMarks = world.tableBounces();
            marks.setMarks(world.bounceMarks());
        }

        hud.setCharge(stroke.charge(), stroke.phase() == Stroke.Phase.CHARGING);
    }

    /**
     * Draw a racket interpolated between its last two poses.
     *
     * The normal is lerped and renormalised rather than slerped. A blade turns by well under a
     * degree in one 1/480 s step, and over an angle that small the two agree to parts in a
     * million -- this is not the ball's orientation, which tumbles fast enough to need the
     * real thing.
     */
    private static void drawPaddle(PaddleView view, Paddle.Blade from, Paddle to, double alpha) {
        view.update(Vec3.lerp(from.centre(), to.pos(), alpha),
                    Vec3.lerp(from.normal(), to.normal(), alpha).normalized());
    }

    private Group lighting() {
        // Two lights over the table plus a soft ambient. A single light leaves the underside
        // of the ball fully black, which reads as a hole punched in the table.
        PointLight key = new PointLight(Color.web("#fff3e0"));
        Xform.place(key, 0.6, 2.2, 1.2);

        PointLight fill = new PointLight(Color.web("#9fc4ff").deriveColor(0, 1, 0.55, 1));
        Xform.place(fill, -1.2, 1.8, -1.6);

        return new Group(key, fill, new AmbientLight(Color.gray(0.32)));
    }

    // ------------------------------------------------------------------ input

    /**
     * Mouse control of the player's racket.
     *
     * Three gestures on three separate signals, which is exactly why CameraRig had to be
     * narrowed to the left button: bare movement aims, the right button charges and swings,
     * and the left button orbits the camera. MOUSE_MOVED was entirely unused before this.
     *
     * addEventHandler throughout. setOnMouseMoved and friends are single-slot properties, so
     * assigning one here would silently unhook the camera orbit that CameraRig just installed
     * on this same SubScene.
     */
    private void attachStrokeControls(SubScene sub) {
        sub.addEventHandler(MouseEvent.MOUSE_MOVED, e -> aim(sub, e));

        sub.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.SECONDARY) return;
            aim(sub, e);            // press where the cursor IS, not where it last moved
            stroke.press();
        });

        // Dragging with the right button down IS the backswing. It keeps aiming, and Stroke
        // reads the drag since the press as the direction the swing will travel through.
        sub.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (e.isSecondaryButtonDown()) aim(sub, e);
        });

        sub.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) stroke.release();
        });
    }

    /**
     * Sample the cursor onto the hitting plane and park it in a field for advanceOne().
     *
     * sceneToLocal, not getX/getY. This handler sits on the SubScene, but the events are
     * targeted at the 3D nodes inside it and carry coordinates belonging to whatever was
     * picked. Scene coordinates are the one frame both ends agree on.
     */
    private void aim(SubScene sub, MouseEvent e) {
        Point2D p = sub.sceneToLocal(e.getSceneX(), e.getSceneY());
        Vec3 fallback = pendingAim != null ? pendingAim : playerPaddle.pos();
        pendingAim = MouseAim.onPlayerPlane(sub, p.getX(), p.getY(), fallback);
    }

    private void onKey(KeyCode code) {
        switch (code) {
            // Written out rather than doing arithmetic on the enum ordinals: KeyCode happens
            // to lay DIGIT0..DIGIT9 out contiguously today, but nothing promises that.
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
            case PERIOD -> { paused = true; singleSteps += 1; }
            case OPEN_BRACKET  -> timeScale = Math.max(0.02, timeScale / 1.6);
            case CLOSE_BRACKET -> timeScale = Math.min(2.0, timeScale * 1.6);

            case G -> {
                showGhost = !showGhost;
                ghost.setShown(showGhost && currentShot.state().spinRate() > 1e-6);
            }
            case T -> { showTrail = !showTrail; trail.setShown(showTrail); }
            case A -> { autoReplay = !autoReplay; replayAt = Double.NaN; }
            case B -> ball.setMagnified(!ball.isMagnified());
            case C -> rig.next();
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

    /**
     * Put a ball in play.
     *
     * Not a serve -- serving is the next piece of work. This is a feed: the ball appears just
     * behind the near end travelling down the table, as though the player had struck it, and
     * the opponent answers it. The player's racket is left exactly where it is, because the
     * hitting plane sits BEHIND every feed's launch point (see MouseAim.PLAYER_PLANE_Z) and
     * the ball therefore flies away from the blade rather than into it.
     */
    private void launchShot(Shots shot) {
        currentShot = shot;
        world.launch(shot.state());
        hud.setFeed(shot.name());

        accumulator = 0;
        replayAt = Double.NaN;
        stepsSinceTrailPoint = 0;
        shownMarks = 0;
        trailPoints.clear();
        trail.clear();
        marks.clear();

        // The comparison ghost: identical launch, spin deleted. Predicted once, up front,
        // because it never changes and re-simulating it every frame would be pure waste.
        // Sampled at the SAME stride as the live trail and run for exactly as long as the
        // trail can hold, so the two paths are comparable dot for dot and both start at the
        // moment of launch.
        List<Vec3> ghostPath = World.predict(shot.withoutSpin(),
                TRAIL_DOTS * TRAIL_STRIDE * DT, TRAIL_STRIDE);
        ghost.setPath(ghostPath);
        ghost.setShown(showGhost && shot.state().spinRate() > 1e-6);
    }

    // ------------------------------------------------------------------ screenshot mode

    /**
     * Offline capture, used to check the rendering without a human watching:
     *   java MrPong --shot="Topspin loop" --at=0.45 --view=SIDE --out=frame.png
     */
    private void parseArgs() {
        // Capture mode has to disable the replay loop, or a requested --at beyond the loop
        // point would never be reached and the process would hang forever.
        for (String arg : getParameters().getRaw()) {
            if (arg.startsWith("--out=")) autoReplay = false;
        }
        for (String arg : getParameters().getRaw()) {
            String[] kv = arg.split("=", 2);
            if (kv.length != 2) continue;
            switch (kv[0]) {
                case "--shot" -> currentShot = Shots.byName(kv[1]);
                case "--at"   -> screenshotAt = Double.parseDouble(kv[1]);
                case "--out"  -> screenshotPath = kv[1];
                case "--view" -> rig.apply(CameraRig.View.valueOf(kv[1]));
                case "--ball2x" -> ball.setMagnified(Boolean.parseBoolean(kv[1]));
                default -> { }
            }
        }
    }

    private void takeScreenshot(Scene scene) {
        String path = screenshotPath;
        screenshotPath = null;                 // once only
        try {
            WritableImage img = scene.snapshot(null);
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", new File(path));
            System.out.println("wrote " + path + " at t=" + String.format("%.3f", world.time()));
        } catch (Exception e) {
            System.err.println("screenshot failed: " + e);
        }
        Platform.exit();
    }

    public static void main(String[] args) { launch(args); }
}
