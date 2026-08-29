import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import physics.*;
import render.*;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static physics.Constants.DT;
import static physics.Constants.MAX_FRAME;

/**
 * Mr. Pong - Checkpoint 1, the physics demo.
 *
 * Target for 27 August, from the project contract: "The ball flying with spin, curving in the
 * air, and bouncing right off the table and net. Not a game yet, just the physics running on
 * screen."
 *
 * So there is no paddle, no opponent and no score here on purpose. What there is:
 *
 *   - a ball with real drag and a real Magnus force, integrated with RK4 at a fixed step
 *   - bounces that couple spin and velocity through a friction impulse, so topspin kicks
 *     forward off the table and backspin checks up
 *   - a net that kills a ball instead of reflecting it
 *   - in/out detection against the actual ITTF table dimensions
 *   - a menu of shots that differ mainly in their spin, so the difference on screen is
 *     attributable to the spin and nothing else
 *   - a grey ghost trail: the SAME shot with the spin deleted, flown alongside, which turns
 *     "it curves" from a claim into a visible gap
 *
 * The physics itself lives in the physics package and does not import JavaFX, so it can be
 * checked headlessly. Run physics.SelfTest for that.
 */
public class MrPong extends Application {

    // ------------------------------------------------------------------ simulation

    private final World world = new World();
    private Shots currentShot = Shots.byName("Topspin loop");

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
    private int stepsLastFrame = 0;
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
     * be left running in front of someone, the shot loops.
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

        Group world3d = new Group(
                Court.build(),
                marks.node(),
                ghost.node(),
                trail.node(),
                ball.node(),
                lighting());

        Group root3d = new Group(world3d, rig.gimbal());

        SubScene sub = new SubScene(root3d, 1280, 780, true, SceneAntialiasing.BALANCED);
        sub.setFill(Color.web("#0b0e13"));
        sub.setCamera(rig.camera());
        rig.attachControls(sub);

        StackPane layers = new StackPane(sub, hud.node());
        Scene scene = new Scene(layers, 1280, 780, Color.web("#0b0e13"));

        // Keep the 3D viewport matched to the window instead of letterboxing it.
        sub.widthProperty().bind(scene.widthProperty());
        sub.heightProperty().bind(scene.heightProperty());

        scene.setOnKeyPressed(e -> onKey(e.getCode()));

        stage.setScene(scene);
        stage.setTitle("Mr. Pong - physics demo (checkpoint 1)");
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
        stepsLastFrame = 0;

        if (paused) {
            // Single-stepping still goes through the same fixed step, so a frame examined
            // while paused is identical to the one that would have been produced live.
            while (singleSteps > 0) {
                advanceOne();
                singleSteps--;
            }
            return;
        }

        accumulator += frameSeconds * timeScale;
        while (accumulator >= DT) {
            advanceOne();
            accumulator -= DT;
            if (++stepsLastFrame > 4000) { accumulator = 0; break; }   // hard safety stop

            if (!Double.isNaN(replayAt) && world.time() >= replayAt) {
                launchShot(currentShot);
                break;                  // launchShot resets the accumulator; stop stepping
            }
        }
    }

    private void advanceOne() {
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

        // The deque is handed over as-is. Copying it built a fresh 300-element list every
        // frame to describe a path that only changes by one point every other physics step.
        if (showTrail) trail.setPath(trailPoints);

        // Marks only move when the ball lands, so rewriting all 24 discs on every frame was
        // work with nothing to show for it.
        if (shownMarks != world.tableBounces()) {
            shownMarks = world.tableBounces();
            marks.setMarks(world.bounceMarks());
        }

        if (showHud) {
            hud.update(world, shown, currentShot, timeScale, paused, fps, stepsLastFrame,
                       ball.isMagnified(), rig.view().label(), showGhost);
        }
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

    // ------------------------------------------------------------------ shots

    private void launchShot(Shots shot) {
        currentShot = shot;
        world.launch(shot.state());

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

    // ------------------------------------------------------------------ input

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
