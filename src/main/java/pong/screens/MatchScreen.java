package pong.screens;

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

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static pong.config.Physical.DT;
import static pong.config.Physical.MAX_FRAME;
import pong._debug.ControlOverlay;
import pong._debug.ShotDebug;
import pong.core.math.Quat;
import pong.core.math.Vec3;
import pong.game_objects.ball.Ball;
import pong.game_objects.ball.view.BallView;
import pong.game_objects.ball.view.Trail;
import pong.game_objects.racket.Racket;
import pong.game_objects.racket.view.RacketView;
import pong.game_world.World;
import pong.game_world.view.BounceMarks;
import pong.game_world.view.CourtView;
import pong.helpers.MouseAim;
import pong.helpers.Xform;
import pong.systems.aim.Shots;
import pong.systems.control.PlayerReach;
import pong.systems.control.Stroke;
import pong.systems.opponent.DemoPlayer;
import pong.systems.opponent.Follower;
import pong.systems.opponent.Opponent;
import pong.systems.connectors.RallyRules;
import pong.systems.shotmaking.ShotAssist;
import pong.ui.Hud;

/**
 * Mr. Pong - a 3D table tennis game. Entry point, fixed-timestep loop, input, wiring and capture
 * mode; see docs/GAMEPLAY.md for what it plays like and docs/DESIGN.md for the architecture.
 *
 * A racket at each end (near follows the mouse, far is the AI); the player's CONTROL is two
 * horizontal dimensions while the ball's flight stays three ({@link pong.PlayerReach}); the shot
 * is entirely mouse motion, constrained to a playable target by {@link pong.ShotAssist} on both
 * rackets; the ITTF one-bounce rule is enforced by handing {@link World} a null racket until the
 * ball has bounced on that side; a rally-cam cuts between two fixed views on who last hit; `V`
 * shows what {@code ShotAssist} did and `D` shows the control/reachability overlay -- see their
 * readout methods below for why each exists. `S` skips {@code ShotAssist} altogether, on both
 * rackets, so every contact keeps the raw impulse-solver bounce instead of an authored shot.
 *
 * The physics lives in {@code physics} and the game logic in {@code play}; neither imports
 * JavaFX, so both run headlessly under {@code pong._tests.PhysicsTest} and {@code pong._tests.RallyTest}.
 */
public class MatchScreen extends Application {

    // ------------------------------------------------------------------ simulation

    private final World world = new World();

    /** The one-bounce rule, the point-ending rules and the score. See {@link RallyRules} --
     *  headless, so both validation suites drive the same rules this screen does. */
    private final RallyRules rules = new RallyRules(world);

    // Opens on a gentle no-spin corner-to-corner serve. Every hit after that -- both rackets --
    // goes through ShotAssist, which keeps the ball in a playable area, so the rally holds.
    private Shots currentShot = Shots.byName("Serve");

    /**
     * The two rackets.
     *
     * Both are KINEMATIC: nothing pushes them around, and neither one tells the ball anything.
     * A racket is moved to a pose each step and Racket works out the velocity it must have had
     * to get there, which is the velocity the contact solver strikes the ball with. That is
     * why they are advanced per PHYSICS STEP below and never per frame -- driven at the frame
     * rate, a slow machine would swing the same stroke harder.
     */
    private final Racket playerRacket =
            new Racket(PlayerReach.NEUTRAL, new Vec3(0, 0, -1));
    private final Racket aiRacket =
            new Racket(new Vec3(0, 0.20, Follower.PLANE_Z), new Vec3(0, 0, 1));

    private final Stroke stroke = new Stroke(PlayerReach.NEUTRAL);

    /** The demo hand, and whether it is driving. `M` toggles it. Supplies the CURSOR exactly as
     *  the mouse does (same Stroke, PlayerReach, TRACK_SPEED); replaces the player rather than
     *  assisting one -- see {@link DemoPlayer}. */
    private final DemoPlayer demo = new DemoPlayer();
    private boolean demoMode = false;

    /** Capture mode pins a fixed camera; this opts back into the rally-cam so an offline
     *  frame can show the swing. Only meaningful alongside --out. */
    private boolean keepRallyCam = false;
    private final Opponent opponent = new Follower();

    /** Turns every racket contact into a playable shot -- see ShotAssist. */
    private final ShotAssist shotAssist = new ShotAssist();

    /** `S` toggles this: when true, ShotAssist is skipped entirely and a contact keeps the raw
     *  impulse-solver bounce -- the real simulation with no authored aim, on either racket. Off
     *  by default; see docs/GAMEPLAY.md for what that trade costs (11 of 75 sweep contacts land
     *  at all without the assist). */
    private boolean rawPhysics = false;

    /**
     * Leftover time not yet consumed by a whole physics step.
     * The whole reason the loop is built this way (Gaffer On Games, "Fix Your Timestep!") is
     * that the contract asks for a game loop where "the physics runs the same on a fast or
     * slow computer". Stepping by the frame time would make the bounce height depend on the
     * frame rate, which is the classic way a physics demo becomes unreproducible.
     */
    private double accumulator = 0;
    private long lastNanos = 0;

    // Runs the whole simulation well below real time. Table tennis at 1:1 is a blur on a first
    // play -- the ball crosses the table in a third of a second -- so the game opens in slow
    // motion that leaves time to actually move to the ball. [ and ] still walk it up and down
    // (up to 2x), and it changes nothing in physics/: fewer fixed steps run per second, every
    // one of them identical to a full-speed step.
    private double timeScale = 0.45;
    private boolean paused = false;
    private int singleSteps = 0;

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
    private final ShotDebug shotDebug = new ShotDebug();   // V toggles it

    // Red rubber on the -normal side of both rackets. The player's blade faces down the table,
    // so its red side looks back at the camera; the opponent's faces the other way, so we see
    // its black side. The two never read as the same object from the player's viewpoint.
    private final RacketView playerView = new RacketView(false);
    private final RacketView aiView = new RacketView(false);

    /**
     * The racket poses at the START of the last physics step.
     *
     * Held so the rackets can be interpolated across a frame with the same alpha as the ball,
     * for the same reason the ball needs it: at 480 Hz against a 60 Hz display the two rates
     * never line up, and a blade snapped to the raw state stutters exactly when it is moving
     * fastest and being watched hardest. Racket.Blade is already precisely a frozen pose.
     */
    private Racket.Blade prevPlayerPose = playerRacket.collider();
    private Racket.Blade prevAiPose = aiRacket.collider();

    /**
     * Where the cursor last pointed on the hitting plane, or null before the mouse has moved
     * over the window.
     *
     * Sampled in the event handler and CONSUMED in advanceOne(). Never used straight from the
     * handler: mouse events arrive once a frame and the stroke is advanced once a step, so
     * moving the blade from the handler would be driving the physics at the frame rate.
     */
    private Vec3 pendingAim = null;

    /**
     * The cursor, and where its ray landed BEFORE the envelope clamped it. Debug-only: the
     * control overlay needs to show what was asked for next to what was granted, because
     * "the blade is not where I pointed" and "the blade cannot go where I pointed" look
     * identical on screen and have completely different fixes.
     */
    private double cursorX = Double.NaN, cursorY = Double.NaN;
    private Vec3 rawAim = null;

    /**
     * The brush modifier: hold the right mouse button and the cursor's Y stops meaning depth
     * and means blade HEIGHT instead, so the bat can be carried up through the ball for topspin
     * or cut down under it for backspin.
     *
     * The depth the blade had when the button went down is held for as long as it is held. That
     * is what keeps this from being the Sep 4 bug again: the cursor's Y axis carries exactly one
     * meaning at a time, and the button picks which.
     */
    private boolean brushing = false;
    private double brushHoldZ = PlayerReach.NEUTRAL.z();

    /** D toggles the control/reachability overlay -- see {@link ControlOverlay}. */
    private boolean showControlDebug = false;

    private final Deque<Vec3> trailPoints = new ArrayDeque<>();
    private int stepsSinceTrailPoint = 0;

    /** How many bounce marks are currently drawn, so they are only rewritten when one lands. */
    private int shownMarks = 0;

    /**
     * Trail resolution, in whole PHYSICS STEPS per dot -- not seconds, so the live trail and the
     * ghost are sampled by the identical rule (a duration like the old 2.5 ms rounds to a
     * different step count on each path, so the two stop being comparable dot for dot). TUNED: 2
     * steps is 4.2 ms, ~6 cm between dots on a 15 m/s drive, still a continuous line.
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

    /** Shorter pause after a *decided* point (net, out, double bounce) before the next serve —
     *  the point is cut early rather than waiting for the ball to trickle to a stop. */
    private static final double POINT_END_DELAY = 0.9;

    // ------------------------------------------------------------------ screenshot mode

    private String screenshotPath = null;
    private double screenshotAt = 0;

    @Override
    public void start(Stage stage) {
        parseArgs();

        // Hand the world its rackets. World.predict deliberately never does this -- a
        // prediction that gets intercepted is a prediction of nothing.
        world.setRackets(playerRacket, aiRacket);

        Group world3d = new Group(
                CourtView.build(),
                ball.shadowNode(),
                marks.node(),
                ghost.node(),
                trail.node(),
                shotDebug.node(),
                aiView.node(),
                playerView.node(),
                ball.node(),
                lighting());

        Group root3d = new Group(world3d, rig.gimbal());

        SubScene sub = new SubScene(root3d, 1280, 780, true, SceneAntialiasing.BALANCED);
        sub.setFill(Color.web("#17232e"));
        sub.setCamera(rig.camera());
        rig.attachControls(sub);
        attachPaddleControls(sub);

        StackPane layers = new StackPane(sub, hud.node());
        Scene scene = new Scene(layers, 1280, 780, Color.web("#17232e"));

        // Keep the 3D viewport matched to the window instead of letterboxing it.
        sub.widthProperty().bind(scene.widthProperty());
        sub.heightProperty().bind(scene.heightProperty());

        scene.setOnKeyPressed(e -> onKey(e.getCode()));
//
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

                // Clamp before scaling: a stall (a breakpoint, a window drag) must not hand
                // the accumulator a second of work and send the loop into a spiral trying to
                // catch up, which would drop the frame rate further and never recover.
                frame = Math.min(frame, MAX_FRAME);

                stepPhysics(frame);
                render(frame);

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
        prevPlayerPose = playerRacket.collider();
        prevAiPose = aiRacket.collider();

        // The demo drives the cursor when it is on; otherwise the mouse does. Exactly one of
        // them is ever the source, which is what keeps this from becoming an aim assist.
        if (demoMode) stroke.aimAt(demo.cursorFor(world.state(), rules.playerMayHit(), DT));
        else if (pendingAim != null) stroke.aimAt(pendingAim);
        stroke.advance(playerRacket, DT);
        opponent.advance(world.state(), aiRacket, DT);

        // The one-bounce rule and the point-ending rules both live in RallyRules; gate() decides
        // which blade is in the collision set for this step and observe() reads the result.
        rules.gate(playerRacket, aiRacket);

        // The ball as it is just before this step -- ShotAssist wants the pre-contact velocity.
        Ball beforeStep = world.state();
        world.step();
        RallyRules.Step step = rules.observe();

        // A racket just hit it: run the raw bounce through the arcade assist so the shot stays
        // playable, and cut the rally-cam.
        if (step.racketHit()) {
            boolean playerHit = step.playerHit();
            Racket racket = playerHit ? playerRacket : aiRacket;

            if (!rawPhysics) {
                world.setState(shotAssist.assist(beforeStep, world.state(), racket, playerHit));
            }
            rig.onRallyHit(playerHit);

            if (!rawPhysics) {
                ShotAssist.Debug d = shotAssist.debug();
                shotDebug.set(d.contact(), d.racketVel(), d.incomingVel(), d.reflectDir(),
                              d.intendDir(), d.finalDir(), d.target(), d.landing(),
                              d.speed(), d.spin(), d.passes(), d.legal());
                shotDebug.setTargetArea(shotAssist.targetHalfWidth(), shotAssist.targetNearDepth(),
                                        shotAssist.targetFarDepth(), !playerHit);
                hud.setShot(shotDebug.isShown() ? shotDebug.readout() : null);
            } else {
                hud.setShot(shotDebug.isShown() ? "raw physics -- no shot assist" : null);
            }
        }

        // A decided point cuts early rather than waiting for the ball to trickle to a stop.
        if (step.pointDecided()) {
            hud.setScore(rules.score().line());
            if (autoReplay && Double.isNaN(replayAt)) replayAt = world.time() + POINT_END_DELAY;
        }

        // Fallback: the ball dropped near the FLOOR without a clean event, or died on the table.
        boolean gone = world.state().pos().y() < -0.60;
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

    private void render(double frameSeconds) {
        // Interpolate between the last two physics states. Without this the ball visibly
        // stutters whenever the frame rate is not an exact multiple of the physics rate,
        // which at 480 Hz against a 60 Hz display it never is.
        double alpha = paused ? 0 : Math.min(1, accumulator / DT);
        Ball a = world.previous(), b = world.state();

        Ball shown = new Ball(
                Vec3.lerp(a.pos(), b.pos(), alpha),
                Vec3.lerp(a.vel(), b.vel(), alpha),
                Vec3.lerp(a.spin(), b.spin(), alpha),
                Quat.slerp(a.orient(), b.orient(), alpha));

        ball.update(shown);

        // Ease the rally-cam toward whichever fixed view the last hit picked. Real frame time
        // -- it is a view, not pong.
        rig.updateRally(frameSeconds, world.state().pos());

        // The same alpha, so the blade and the ball never disagree about where they are at
        // the instant of contact -- which is the one frame anybody is looking closely at.
        drawPaddle(playerView, prevPlayerPose, playerRacket, alpha);
        drawPaddle(aiView, prevAiPose, aiRacket, alpha);

        // The deque is handed over as-is. Copying it built a fresh 300-element list every
        // frame to describe a path that only changes by one point every other physics step.
        if (showTrail) trail.setPath(trailPoints);

        // Marks only move when the ball lands, so rewriting all 24 discs on every frame was
        // work with nothing to show for it.
        if (shownMarks != world.tableBounces()) {
            shownMarks = world.tableBounces();
            marks.setMarks(world.bounceMarks());
        }

        // Per frame, not per step: it is a readout, and it flies a 3 s prediction to work out
        // the ball's arrival time. Once every 8 physics steps is already more often than a
        // human can read it.
        if (showControlDebug) hud.setControl(controlReadout());
    }

    /**
     * Draw a racket interpolated between its last two poses.
     *
     * The normal is lerped and renormalised rather than slerped. A blade turns by well under a
     * degree in one 1/480 s step, and over an angle that small the two agree to parts in a
     * million -- this is not the ball's orientation, which tumbles fast enough to need the
     * real thing.
     */
    private static void drawPaddle(RacketView view, Racket.Blade from, Racket to, double alpha) {
        view.update(Vec3.lerp(from.centre(), to.pos(), alpha),
                    Vec3.lerp(from.normal(), to.normal(), alpha).normalized());
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

    // ------------------------------------------------------------------ input

    /**
     * Mouse control of the player's racket: bare movement aims, and that is all there is -- no
     * buttons, since the left button belongs to camera orbit ({@link CameraRig}). Uses
     * addEventHandler rather than setOnMouseMoved, a single-slot property that would silently
     * unhook the camera orbit CameraRig already installed on this SubScene.
     */
    private void attachPaddleControls(SubScene sub) {
        sub.addEventHandler(MouseEvent.MOUSE_MOVED, e -> aim(sub, e));

        // MOUSE_MOVED stops firing the moment any button is down, so the brush needs DRAGGED as
        // well or the blade would freeze completely for as long as the modifier is held.
        sub.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> { if (brushing) aim(sub, e); });

        sub.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.isSecondaryButtonDown()) {
                brushing = true;
                brushHoldZ = playerRacket.pos().z();   // freeze the depth we are standing at
                aim(sub, e);
            }
        });
        sub.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (brushing && !e.isSecondaryButtonDown()) {
                brushing = false;
                aim(sub, e);                           // hand the axis straight back to depth
            }
        });
    }

    /**
     * Sample the cursor onto the hitting plane and park it in a field for advanceOne(). Uses
     * sceneToLocal rather than getX/getY, since this handler sits on the SubScene but events are
     * targeted at the 3D nodes inside it -- scene coordinates are the one frame both agree on.
     */
    private void aim(SubScene sub, MouseEvent e) {
        Point2D p = sub.sceneToLocal(e.getSceneX(), e.getSceneY());
        Vec3 fallback = pendingAim != null ? pendingAim : playerRacket.pos();

        // MouseAim answers pure geometry (where the ray meets the hitting plane); PlayerReach
        // then applies the envelope, discarding the ray's height for its own -- the fallback is
        // only for a degenerate ray, never an input, or this would be a loop with gain.
        cursorX = p.getX();
        cursorY = p.getY();
        rawAim = MouseAim.onHittingPlane(sub, p.getX(), p.getY(), PlayerReach.HIT_Y, fallback);

        // While the brush modifier is held the ray still supplies X, but the cursor's height on
        // screen supplies the blade's height and the depth is the one frozen at button-down.
        pendingAim = brushing
                ? PlayerReach.clampBrushed(rawAim, p.getY() / Math.max(1, sub.getHeight()), brushHoldZ)
                : PlayerReach.clamp(rawAim);
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
            case F -> rig.toggleRallyCam();
            case C -> rig.next();          // next() drops the rally-cam for a manual view
            case V -> {
                shotDebug.setShown(!shotDebug.isShown());
                hud.setShot(shotDebug.isShown() ? shotDebug.readout() : null);
            }
            case D -> {
                showControlDebug = !showControlDebug;
                hud.setControl(showControlDebug ? controlReadout() : null);
            }
            case M -> { demoMode = !demoMode; refreshFeedLabel(); }
            case S -> { rawPhysics = !rawPhysics; refreshFeedLabel(); }
            case H -> { showHud = !showHud; hud.setShown(showHud); }
            case ESCAPE -> Platform.exit();

            default -> { }
        }
    }

    /** The feed label plus whichever of DEMO / raw-physics mode tags apply -- both M and S flip
     *  independent flags but share the one line, so this is the single place that composes it. */
    /** What D prints -- the numbers this screen knows, formatted by {@link ControlOverlay}. */
    private String controlReadout() {
        return ControlOverlay.readout(world.state(), playerRacket.pos(),
                pendingAim != null ? pendingAim : playerRacket.pos(),
                rawAim, cursorX, cursorY);
    }

    private void refreshFeedLabel() {
        String label = currentShot.name();
        if (rawPhysics) label += "   [RAW PHYSICS -- S for assist]";
        if (demoMode)   label += "   [DEMO -- M to take over]";
        hud.setFeed(label);
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
     * Put a ball in play. Not a serve (that is still to build) -- a feed: the ball appears just
     * behind the near end as though the player had struck it, and the opponent answers it. What
     * stops it rebounding off the player's own bat on the first step is the one-bounce rule
     * ({@link RallyRules#serve} opens with nobody allowed to hit, so World gets a null player
     * racket), not the launch geometry -- that guarantee holds wherever the player is pointing.
     */
    private void launchShot(Shots shot) {
        currentShot = shot;
        rules.serve(shot.state());
        refreshFeedLabel();
        hud.setScore(rules.score().line());   // also puts 0-0 up for the opening serve

        // The feed stands in for the player's own serve, so the rally-cam opens zoomed IN; it
        // will cut OUT when the opponent returns it.
        rig.onRallyHit(true);

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
                // The same thing D does, from the command line -- so a capture can prove the
                // control overlay actually renders, rather than only that it compiles.
                case "--controldebug" -> showControlDebug = Boolean.parseBoolean(kv[1]);
                case "--demo" -> demoMode = Boolean.parseBoolean(kv[1]);
                case "--rallycam" -> keepRallyCam = Boolean.parseBoolean(kv[1]);
                case "--rawphysics" -> rawPhysics = Boolean.parseBoolean(kv[1]);
                default -> { }
            }
        }

        // A capture wants a fixed, predictable camera. --view already opts out of the
        // rally-cam; this covers the default (no --view) case so a screenshot is not framed
        // by whichever view the last hit had selected. --rallycam=true opts back IN, which is
        // the only way to see the rally-cam's swing without a human watching.
        if (screenshotPath != null && !keepRallyCam) rig.stopRallyCam();
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
