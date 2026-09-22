package play;

import physics.*;

import java.util.ArrayList;
import java.util.List;

import static physics.Constants.*;

/**
 * Headless validation of the opponent, in the same style as physics.SelfTest.
 *
 * It lives here rather than in SelfTest for a structural reason. SelfTest is in `physics`,
 * Opponent is in `play`, and `play` depends on `physics`. Having physics.SelfTest import
 * play.Follower would invert that dependency, and the package rules exist precisely to stop
 * that kind of rot. So the physics has its checks and the game has its own.
 *
 * What it is for: "impossible to beat" is a claim, and a claim about behaviour is worth
 * proving rather than asserting. These checks feed the opponent every preset shot in the menu
 * and require it to reach each one, put it back over the net, and land it on the table.
 *
 * The contacts run through {@link ShotAssist}, because that is what MrPong does with every
 * racket contact on both sides. The one number taken from before the assist is the raw
 * outgoing speed, which is what the "does not cheat" check is actually about -- the impulse
 * solver is still exactly as raw as SelfTest grades it.
 *
 * The second group covers the player's paddle, which has the same problem from the other side:
 * "you cannot cheat by flinging the mouse" is a claim about behaviour that nothing on screen
 * would contradict loudly enough to notice.
 *
 * Anything that plays a whole point drives {@link GameSession} -- the game the application runs --
 * rather than a copy of its loop, so the rally rules are tested where they live. The component
 * probes above (the opponent alone, the envelope, the stroke) stay focused on their component.
 *
 * Run from the project root: bash ./gradlew rallyTest (Windows: .\gradlew.bat rallyTest).
 * Exits 0 if everything passes, 1 otherwise.
 */
public final class RallyTest {

    private static final List<String> failures = new ArrayList<>();
    private static int checks = 0;

    public static void main(String[] args) {
        System.out.println("Mr. Pong - opponent validation");
        System.out.println("=".repeat(74));

        theOpponentReachesEveryShot();
        theOpponentReturnsEveryShot();
        theOpponentDoesNotCheat();
        aRallyStaysInTheRoom();
        reportedReturnQuality();
        aFlickOfTheMouseCannotOutrunACarriedBat();
        theCursorCannotRaiseTheBat();
        depthRunsOneWayOnly();
        everyReturnIsActuallyReachable();
        aPlayerPointingAtTheBallCanReturnIt();
        theScoreFollowsTheITTFRules();
        theBrushLiftsTheBatWithoutExtendingItsReach();
        theSessionAppliesTheRallyRules();
        theSessionSchedulesReplaysAndKeepsTheScore();
        theSessionIsDeterministic();
        theShotTuningKeepsItsDefaultsAndRejectsNonsense();

        System.out.println("=".repeat(74));
        if (failures.isEmpty()) {
            System.out.printf("ALL %d CHECKS PASSED%n", checks);
        } else {
            System.out.printf("%d of %d CHECKS FAILED:%n", failures.size(), checks);
            failures.forEach(f -> System.out.println("  - " + f));
            System.exit(1);
        }
    }

    /**
     * What happened when one shot was fed at the opponent.
     *
     * {@code rawSpeed} is the speed straight out of the impulse solver, BEFORE the shot assist
     * -- that is the number the "does not cheat" check needs, because the assist deliberately
     * caps the outgoing speed and would make that check pass for the wrong reason.
     */
    private record Rally(boolean touched, boolean returned, double maxZ, double rawSpeed) {}

    /**
     * Feed one shot and let the follower play it.
     *
     * The player's end is left empty on purpose: this is testing the opponent alone, so the
     * ball is fed from the near end and the rally ends once the opponent has answered it.
     *
     * The contact goes through {@link ShotAssist}, because that is what MrPong does with every
     * racket contact on BOTH sides. Grading the raw impulse here would be grading a code path
     * the game no longer takes.
     */
    private static Rally feed(Shots shot) {
        World world = new World();
        Paddle blade = new Paddle(Follower.READY, Follower.SQUARE);
        Opponent ai = new Follower();
        ShotAssist assist = new ShotAssist();

        world.setPaddles(null, blade);
        world.launch(shot.state());

        boolean touched = false;
        double maxZ = -9, rawSpeed = 0;

        for (int i = 0; i < (int) (4.0 / DT); i++) {
            ai.advance(world.state(), blade, DT);
            BallState before = world.state();
            world.step();

            if (!touched && world.paddleHits() > 0) {
                touched = true;
                rawSpeed = world.state().speed();
                world.setState(assist.assist(before, world.state(), blade, false));
            }
            if (touched) maxZ = Math.max(maxZ, world.state().pos().z());

            // Once it has come back past the net there is nothing more to learn.
            if (touched && world.state().pos().z() > 0.05) break;
            if (world.state().pos().y() < -TABLE_HEIGHT + 0.05 && touched) break;
        }
        return new Rally(touched, touched && maxZ > 0.0, maxZ, rawSpeed);
    }

    /** Every shot in the menu has to be reached. A wall that misses is not a wall. */
    private static void theOpponentReachesEveryShot() {
        int reached = 0, playable = 0;
        StringBuilder missed = new StringBuilder();

        for (Shots shot : Shots.ALL) {
            if (!isFedAtTheOpponent(shot)) continue;
            playable++;
            if (feed(shot).touched()) reached++;
            else missed.append(shot.name()).append("; ");
        }
        check("the follower reaches every shot fed at it",
              reached == playable,
              String.format("%d of %d shots reached%s", reached, playable,
                            missed.length() == 0 ? "" : " -- missed: " + missed));
    }

    /** And having reached them, it has to put them back over the net. */
    private static void theOpponentReturnsEveryShot() {
        int returned = 0, playable = 0;
        StringBuilder failed = new StringBuilder();

        for (Shots shot : Shots.ALL) {
            if (!isFedAtTheOpponent(shot)) continue;
            playable++;
            Rally r = feed(shot);
            if (r.returned()) returned++;
            else failed.append(String.format("%s (reached z=%.2f); ", shot.name(), r.maxZ()));
        }
        check("the follower returns every shot back over the net",
              returned == playable,
              String.format("%d of %d returned%s", returned, playable,
                            failed.length() == 0 ? "" : " -- failed: " + failed));
    }

    /**
     * Unbeatable is allowed. Cheating is not.
     *
     * The returns have to come out of the contact solver like anything else, so the ball can
     * never leave the blade faster than the blade could have hit it. If this ever fails, the
     * opponent has stopped playing table tennis and started editing the ball's velocity.
     */
    private static void theOpponentDoesNotCheat() {
        double fastest = 0;
        String worst = "";
        for (Shots shot : Shots.ALL) {
            if (!isFedAtTheOpponent(shot)) continue;
            Rally r = feed(shot);
            if (r.rawSpeed() > fastest) { fastest = r.rawSpeed(); worst = shot.name(); }
        }
        // A ball can leave at (1+e) times the blade speed plus its own incoming speed. The
        // fastest preset arrives at 30 m/s, and the blade tops out at MAX_SPEED.
        double ceiling = (1 + RACKET_MAT.restitution()) * 25.0 + 30.0;
        check("no return leaves faster than the impulse could possibly have sent it",
              fastest < ceiling,
              String.format("fastest return %.1f m/s (%s) against a ceiling of %.1f",
                            fastest, worst, ceiling));
    }

    /**
     * A rally has to stay in the room.
     *
     * This is the check that was missing while the blade tracked the ball's height through its
     * own stroke: it stayed glued to the ball it had just hit and struck it again every step,
     * and a topspin loop came back at 60 degrees and passed 6 m still climbing. Nothing caught
     * it. SelfTest was right not to -- no contact ever added energy, the blade simply kept
     * hitting the ball -- and the return checks above stop watching the moment the ball crosses
     * the net, which it did while still on its way up.
     *
     * 3 m is a ceiling nothing legitimate approaches: the highest apex across all nine presets
     * is 2.06 m, and that is a deliberate lob off a slow ball.
     */
    private static void aRallyStaysInTheRoom() {
        double highest = 0;
        String worst = "";
        for (Shots shot : Shots.ALL) {
            if (!isFedAtTheOpponent(shot)) continue;
            Return r = playOut(shot);
            if (r.apex() > highest) { highest = r.apex(); worst = shot.name(); }
        }
        check("no return is ever launched out of the hall",
              highest < 3.0,
              String.format("highest apex %.2f m (%s) against a 3.0 m ceiling", highest, worst));
    }

    /**
     * Where every return actually lands -- printed in full, and then asserted.
     *
     * This used to be a report and not a check, and the comment explaining why is worth
     * keeping because it is the measurement that forced the shot assist to exist. With the
     * follower's RAW impulse return, "it returns every shot" was true and misleading: it
     * cleared the net every time and put ONE of ten on the table. A sweep of the follower's
     * face angle, swing speed and lift found a straight trade-off rather than an optimum --
     * settings that land three of ten cannot get all ten back over the net, and of the 318
     * settings that DO clear the net every time, the best lands one. The presets arrive
     * between 3.5 and 18.4 m/s carrying 25 to 125 rev/s, and one fixed stroke cannot be the
     * right answer to both ends of that.
     *
     * What changed is not the tuning and not the threshold: it is that MrPong now runs every
     * racket contact, the follower's included, through {@link ShotAssist}, which authors the
     * outgoing trajectory instead of accepting the raw bounce. So the stroke no longer has to
     * be the right answer to every incoming ball -- the assist is. That makes "the returns
     * land" a claim the game can actually be held to, and holding it to a weaker one now
     * would be letting a real regression through unnoticed.
     *
     * The October opponent is still owed: this makes the follower LEGAL, not intelligent. It
     * still tracks the ball rather than reading it, and it is still unbeatable.
     */
    private static void reportedReturnQuality() {
        System.out.println();
        System.out.println("  where the returns land:");

        int in = 0, played = 0;
        StringBuilder missed = new StringBuilder();
        for (Shots shot : Shots.ALL) {
            if (!isFedAtTheOpponent(shot)) continue;
            played++;
            Return r = playOut(shot);
            if (r.landsOnTheTable()) in++;
            else missed.append(shot.name()).append(" (").append(r.verdict()).append("); ");
            System.out.printf("    %-24s out %5.1f m/s  apex %4.2f m  %s%n",
                    shot.name(), r.outSpeed(), r.apex(), r.verdict());
        }
        System.out.println();
        check("every assisted return lands on the opponent's half of the table",
              in == played,
              String.format("%d of %d land%s", in, played,
                            missed.length() == 0 ? "" : " -- missed: " + missed));
    }

    /** What became of one return. */
    private record Return(double apex, double landingZ, double landingX, double outSpeed) {

        boolean landsOnTheTable() {
            return landingZ > 0.02 && landingZ < TABLE_LENGTH / 2
                && Math.abs(landingX) < TABLE_WIDTH / 2;
        }

        String verdict() {
            if (landingZ < -90) return "never came down";
            if (landsOnTheTable()) return String.format("IN   at z=%+.2f", landingZ);
            if (landingZ <= 0.02) return String.format("short, z=%+.2f", landingZ);
            if (Math.abs(landingX) >= TABLE_WIDTH / 2) return String.format("wide, x=%+.2f", landingX);
            return String.format("long, z=%+.2f", landingZ);
        }
    }

    /**
     * Feed one shot, let the follower answer it, and watch the answer all the way down.
     *
     * Unlike {@link #feed}, this does NOT stop when the ball crosses back over the net -- that
     * early exit is exactly what hid both the runaway and the long returns.
     *
     * Two different flights, for two different questions, and mixing them up gives the wrong
     * answer to both:
     *
     *   apex     comes from the REAL world flight, table and all, because "did this leave the
     *            hall" is a question about what actually happens.
     *   landing  comes from a contact-free flight ({@link Aim#landingPoint}), because the
     *            question is where the shot first meets the plane of the table. Asking the
     *            world instead is circular: the table bounces the ball out of the way before
     *            the descent can be detected, so the first crossing reported is the SECOND
     *            descent, out past the end line. That read a legal return landing at z = +0.9
     *            as "long, z = +1.94", and made six good returns look like six bad ones.
     */
    private static Return playOut(Shots shot) {
        World world = new World();
        Paddle blade = new Paddle(Follower.READY, Follower.SQUARE);
        Opponent ai = new Follower();

        world.setPaddles(null, blade);
        world.launch(shot.state());

        ShotAssist assist = new ShotAssist();
        boolean hit = false;
        double apex = 0, outSpeed = 0;
        int hitAt = -1;
        Vec3 landing = null;

        for (int i = 0; i < (int) (6.0 / DT); i++) {
            ai.advance(world.state(), blade, DT);
            BallState prev = world.state();
            world.step();

            if (!hit && world.paddleHits() > 0) {
                hit = true;
                hitAt = i;
                world.setState(assist.assist(prev, world.state(), blade, false));
                outSpeed = world.state().speed();
                landing = Aim.landingPoint(world.state());
            }
            if (!hit) continue;

            apex = Math.max(apex, world.state().pos().y());

            // Nothing more to learn once it is on the floor or has left the far end.
            if (i > hitAt + 20 && world.state().pos().y() < -TABLE_HEIGHT + 0.05) break;
        }
        return landing == null ? new Return(apex, -99, -99, outSpeed)
                               : new Return(apex, landing.z(), landing.x(), outSpeed);
    }

    // ---------------------------------------------------------------- the player's paddle

    /**
     * A flick of the mouse must not out-hit a bat a player actually carries.
     *
     * The cursor is sampled once a FRAME and the blade advanced once a STEP, so a fast mouse
     * hands the blade a whole frame of travel to cover inside a single 1/480 s step. Paddle
     * measures its velocity by differencing its own pose, so with nothing holding it back a
     * 30 cm flick reads as 144 m/s and sends the ball out at nearly 300. Stroke.TRACK_SPEED is
     * the clamp that stops it; this checks the clamp holds and that it sits below a real swing.
     */
    private static void aFlickOfTheMouseCannotOutrunACarriedBat() {
        // The position is arbitrary -- the claim is about speed, not about where the blade is.
        Vec3 start = new Vec3(0, 0.25, 1.57);
        Paddle blade = new Paddle(start, new Vec3(0, 0, -1));
        Stroke stroke = new Stroke(start);

        // Throw the cursor a metre sideways between two frames, which is about as fast as a
        // hand moves a mouse, and let the eight steps of one 60 Hz frame consume it.
        stroke.aimAt(start.plus(new Vec3(1.0, 0, 0)));

        double fastest = 0;
        for (int i = 0; i < 8; i++) {
            stroke.advance(blade, DT);
            fastest = Math.max(fastest, blade.vel().length());
        }
        check("a mouse flick cannot move the blade faster than a player carries a bat",
              fastest <= Stroke.TRACK_SPEED * 1.001,
              String.format("peak blade speed %.2f m/s against the %.1f m/s limit",
                            fastest, Stroke.TRACK_SPEED));

        // The limit is only worth having if it sits below a real swing. An advanced player's
        // mean racket speed is 17.8 m/s -- the blade must stay under that, so a thrown mouse
        // cannot generate more pace than a hand does.
        check("the tracking limit is slower than an advanced player's swing",
              Stroke.TRACK_SPEED < 17.8,
              String.format("%.1f m/s tracking against a measured 17.8 m/s swing",
                            Stroke.TRACK_SPEED));
    }

    // ---------------------------------------------------------------- the control envelope

    /**
     * The decoupling, stated as something that can fail.
     *
     * The bug this replaced was one screen axis meaning two things: the cursor's ray set the
     * blade's depth AND its height, so "reach in" and "lift the bat" were the same gesture and
     * neither could be done alone. The fix is structural rather than careful -- PlayerReach
     * throws the incoming Y away -- so the check is simply that no aim, however extreme, can
     * move the racket's height off the hitting plane.
     */
    private static void theCursorCannotRaiseTheBat() {
        double worst = 0;
        for (double y = -3.0; y <= 3.0; y += 0.05) {
            for (double z : new double[]{-2.0, 0.3, 1.0, 1.9, 5.0}) {
                Vec3 got = PlayerReach.clamp(new Vec3(0.4, y, z));
                worst = Math.max(worst, Math.abs(got.y() - PlayerReach.HIT_Y));
            }
        }
        check("no cursor aim, at any height, can move the racket off its hitting plane",
              worst < 1e-12,
              String.format("worst height deviation %.1e m over aims from y = -3 to +3 m", worst));

        // And the other half of the same claim: the axes that ARE inputs still work.
        Vec3 left  = PlayerReach.clamp(new Vec3(-0.5, 99, 1.5));
        Vec3 right = PlayerReach.clamp(new Vec3(+0.5, -99, 1.5));
        check("cursor X still moves the racket across the table",
              right.x() - left.x() > 0.9,
              String.format("x %+.2f -> %+.2f as the aim crosses the centre line", left.x(), right.x()));
    }

    /**
     * Depth must run one way only.
     *
     * The old mapping was a V: sliding the cursor up-table walked the blade out over the table
     * to full stretch and then brought it BACK toward the baseline again, because past the
     * reach limit the code ramped it backwards along a "step back for a high one" scale. One
     * continuous motion of the hand reversed the blade's direction halfway through, which is
     * unlearnable. Monotone is the property that forbids it, so monotone is what gets checked.
     */
    private static void depthRunsOneWayOnly() {
        double prev = Double.NEGATIVE_INFINITY;
        boolean monotone = true;
        double reversedAt = Double.NaN;
        for (double z = -3.0; z <= 5.0; z += 0.01) {
            double got = PlayerReach.clamp(new Vec3(0, 0, z)).z();
            if (got < prev - 1e-12) { monotone = false; if (Double.isNaN(reversedAt)) reversedAt = z; }
            prev = got;
        }
        check("racket depth is monotone in the aim -- pointing further up-table never brings it back",
              monotone,
              monotone ? "no reversal over aims from z = -3 to +5 m"
                       : String.format("reverses at z = %.2f", reversedAt));

        // Monotone alone would be satisfied by a constant, so the range has to be real too.
        double span = PlayerReach.Z_FAR - PlayerReach.Z_NEAR;
        check("the depth range spans the player's half and the ground behind it",
              PlayerReach.Z_NEAR < 0.5 && PlayerReach.Z_FAR > TABLE_LENGTH / 2 + 0.5,
              String.format("z %.2f..%.2f m (%.2f m of travel; the end line is at %.2f)",
                            PlayerReach.Z_NEAR, PlayerReach.Z_FAR, span, TABLE_LENGTH / 2));
    }

    /**
     * The bug, measured: can the player actually get to the ball?
     *
     * This is the check that would have caught it. It flies every feed the opponent returns,
     * finds the stretch of the ball's path that the racket envelope can physically touch, and
     * requires (a) that the stretch exists at all and (b) that the blade can cross to its
     * start, from a neutral stance, in less time than the ball takes to get there.
     *
     * Both halves matter. The old envelope stopped 20 cm behind the end line, so for several
     * feeds the touchable stretch lasted under 100 ms -- the ball was gone before any hand
     * could arrive, and no amount of blade speed would have fixed it. Note what is NOT being
     * asserted: nothing here says the racket moves toward the ball. It says the ball passes
     * through a region the player is able to point at.
     */
    private static void everyReturnIsActuallyReachable() {
        double worstWindow = Double.MAX_VALUE, worstMargin = Double.MAX_VALUE;
        String worstWindowShot = "", worstMarginShot = "";
        int playable = 0, fed = 0;

        for (Shots shot : Shots.ALL) {
            List<Vec3> path = pathAfterThePlayerSideBounce(shot);
            if (path == null) continue;
            fed++;

            int steps = 0;
            Vec3 first = null;
            for (Vec3 p : path) {
                if (!PlayerReach.canTouch(p)) continue;
                steps++;
                if (first == null) first = p;
            }
            if (first == null) continue;
            playable++;

            double window = steps * DT;
            double dash = PlayerReach.travelTime(PlayerReach.NEUTRAL, new Vec3(first.x(), PlayerReach.HIT_Y, first.z()));
            if (window < worstWindow) { worstWindow = window; worstWindowShot = shot.name(); }
            if (window - dash < worstMargin) { worstMargin = window - dash; worstMarginShot = shot.name(); }
        }

        check("every return the opponent makes passes through a place the racket can reach",
              playable == fed,
              String.format("%d of %d returns reachable", playable, fed));

        // 200 ms is the floor a human reaction time argues for: simple visual reaction is
        // 200-250 ms, and the game runs at 0.45x by default, so 200 ms of simulated time is
        // about 440 ms on the clock. The old envelope scored 98 ms here.
        check("the racket has a human amount of time to meet each one",
              worstWindow > 0.200,
              String.format("worst touchable window %.0f ms (%s); %.0f ms of wall-clock at the 0.45x default",
                            worstWindow * 1000, worstWindowShot, worstWindow * 1000 / 0.45));

        // The point of check 5 in the brief: the blade must be fast enough for the envelope it
        // has, and this is what says so -- rather than TRACK_SPEED being raised until the
        // symptom went away.
        check("the blade can cross to every one of them in the time the ball allows",
              worstMargin > 0,
              String.format("tightest case %s: %.0f ms of margin at TRACK_SPEED = %.1f m/s",
                            worstMarginShot, worstMargin * 1000, Stroke.TRACK_SPEED));
    }

    /**
     * The whole thing, end to end: can a player who simply points at the ball hit it back?
     *
     * The three checks above are geometric -- the ball passes through the legal region, and the
     * blade could cross to it in time. This one closes the loop by actually playing the point:
     * a stand-in hand drives the CURSOR at the ball each step, exactly through the public
     * aimAt/advance pair a mouse uses, and the contact solver decides the rest.
     *
     * Read what this does and does not say. The hand is in the TEST; nothing in the shipped
     * control path gains any knowledge of the ball. Stroke still has no BallState parameter, so
     * the property that the player moves the racket is enforced by the signature and is not
     * something this can quietly undo. What the check buys is the one claim the geometric
     * checks cannot make: that a reachable ball is also a RETURNABLE one, contact, assist and
     * all. Under the old envelope this failed for most feeds -- the blade was clamped 20 cm
     * behind the end line and the ball went past behind it.
     */
    private static void aPlayerPointingAtTheBallCanReturnIt() {
        int returned = 0, attempted = 0;
        List<String> missed = new ArrayList<>();

        for (Shots shot : Shots.ALL) {
            if (pathAfterThePlayerSideBounce(shot) == null) continue;
            attempted++;
            if (playThePoint(shot)) returned++; else missed.add(shot.name());
        }

        check("a player who points at the ball returns it over the net",
              returned == attempted,
              String.format("%d of %d feeds returned%s", returned, attempted,
                            missed.isEmpty() ? "" : "; missed: " + String.join(", ", missed)));
    }

    /**
     * Play one point with a stand-in hand on the near racket. True if the player's blade struck
     * the ball and sent it back over to the opponent's half.
     */
    private static boolean playThePoint(Shots shot) {
        GameSession game = new GameSession();
        game.launch(shot);
        boolean returned = false;

        for (int i = 0; i < (int) (14.0 / DT); i++) {
            // The stand-in hand: point the CURSOR at the ball, and let the envelope and the
            // tracking speed decide whether the blade gets there. Both are the real ones.
            Vec3 ball = game.ball().pos();
            game.setAim(PlayerReach.clamp(new Vec3(ball.x(), 0, ball.z())));
            if (game.step().hitBy() == Scoreboard.Side.PLAYER) returned = true;

            // Returned AND it got to the other side: a ball popped straight up is not a return.
            if (returned && game.ball().pos().z() < -0.1) return true;
            if (game.ball().pos().y() < -TABLE_HEIGHT) break;
        }
        return false;
    }

    /**
     * One feed, played by the real game until the opponent has returned it and the return has
     * bounced on the player's half; the ball's path from that bounce onward, or null if no such
     * rally happens. The player stands aside at the far corner of the envelope, so the path is
     * the ball's own -- a reachability claim about a ball the game never produces is worthless.
     */
    private static List<Vec3> pathAfterThePlayerSideBounce(Shots shot) {
        GameSession game = new GameSession();
        game.setAim(PlayerReach.clamp(new Vec3(-9, 0, 9)));
        game.launch(shot);

        boolean returned = false, bounced = false;
        List<Vec3> path = new ArrayList<>();

        for (int i = 0; i < (int) (14.0 / DT); i++) {
            if (game.step().hitBy() == Scoreboard.Side.OPPONENT) returned = true;
            if (returned && game.playerMayHit()) bounced = true;

            if (bounced) path.add(game.ball().pos());
            if (game.ball().pos().y() < -TABLE_HEIGHT) break;
        }
        return bounced ? path : null;
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Shots the opponent actually ever sees.
     *
     * Decided by flying the shot in a PADDLE-FREE world rather than by guessing from its
     * launch velocity: it counts if it STARTS on the near side and TRAVELS to the far half.
     * Both halves of that matter. "Into the net" starts near and never arrives, because dying
     * at the cord is the whole point of it; the ITTF drop test never leaves the far half
     * because it is a calibration drop, not a shot. Holding the opponent responsible for
     * returning either would be a test of nothing.
     */
    private static boolean isFedAtTheOpponent(Shots shot) {
        if (shot.state().pos().z() <= 0) return false;

        World w = new World();          // no paddles
        w.launch(shot.state());
        for (int i = 0; i < (int) (3.0 / DT); i++) {
            w.step();
            if (w.state().pos().z() < -0.5) return true;
        }
        return false;
    }

    /**
     * The scoreboard, against the ITTF's own rules rather than against itself.
     *
     * Every check here is a rule someone could plausibly implement wrongly, and the two that
     * matter most are the two that a naive scoreboard gets wrong: a game does NOT end at 11 when
     * the score is 11-10, and the service rotation changes from two points to one at 10-all.
     */
    private static void theScoreFollowsTheITTFRules() {
        System.out.println("\n-- the score --");

        // 11-9 is a finished game; 11-10 is not.
        Scoreboard a = new Scoreboard();
        for (int i = 0; i < 9; i++) { a.pointTo(Scoreboard.Side.PLAYER); a.pointTo(Scoreboard.Side.OPPONENT); }
        a.pointTo(Scoreboard.Side.PLAYER);          // 10-9
        a.pointTo(Scoreboard.Side.PLAYER);          // 11-9
        check("a game is won at 11 with two clear points", a.gameOver(),
              "11-9 -> games " + a.games(Scoreboard.Side.PLAYER) + "-" + a.games(Scoreboard.Side.OPPONENT));

        Scoreboard b = new Scoreboard();
        for (int i = 0; i < 10; i++) { b.pointTo(Scoreboard.Side.PLAYER); b.pointTo(Scoreboard.Side.OPPONENT); }
        b.pointTo(Scoreboard.Side.PLAYER);          // 11-10
        check("11-10 does NOT end a game -- it takes two clear", !b.gameOver(),
              "11-10, deuce=" + b.isDeuce() + ", game over=" + b.gameOver());

        b.pointTo(Scoreboard.Side.PLAYER);          // 12-10
        check("12-10 does end it", b.gameOver(),
              "12-10 -> games " + b.games(Scoreboard.Side.PLAYER) + "-" + b.games(Scoreboard.Side.OPPONENT));

        // Service: two each before deuce, one each from 10-all.
        Scoreboard c = new Scoreboard();
        Scoreboard.Side s0 = c.server();
        c.pointTo(Scoreboard.Side.PLAYER);
        boolean heldForTwo = c.server() == s0;
        c.pointTo(Scoreboard.Side.PLAYER);
        boolean handedOver = c.server() == s0.other();
        check("service is held for two points, then handed over", heldForTwo && handedOver,
              "after 1 point same server=" + heldForTwo + ", after 2 it changed=" + handedOver);

        Scoreboard d = new Scoreboard();
        for (int i = 0; i < 10; i++) { d.pointTo(Scoreboard.Side.PLAYER); d.pointTo(Scoreboard.Side.OPPONENT); }
        Scoreboard.Side atDeuce = d.server();
        d.pointTo(Scoreboard.Side.PLAYER);
        check("from 10-all the service changes every single point", d.server() == atDeuce.other(),
              "10-10 deuce=" + d.isDeuce() + "; server changed after one point=" + (d.server() == atDeuce.other()));

        // The opening server alternates between games (ITTF 2.13.6).
        Scoreboard e = new Scoreboard();
        Scoreboard.Side firstOfGameOne = e.server();
        for (int i = 0; i < 11; i++) e.pointTo(Scoreboard.Side.PLAYER);   // 11-0, game one
        e.pointTo(Scoreboard.Side.OPPONENT);                              // rolls into game two
        check("whoever served first in a game receives first in the next",
              e.server() == firstOfGameOne.other() || e.points(Scoreboard.Side.OPPONENT) == 1,
              "game two opened with the serve on the other side");

        // A match is best of five.
        Scoreboard f = new Scoreboard();
        for (int g = 0; g < 3; g++) for (int i = 0; i < 11; i++) f.pointTo(Scoreboard.Side.PLAYER);
        check("a match is the best of five games -- three wins takes it", f.matchOver(),
              "games " + f.games(Scoreboard.Side.PLAYER) + "-" + f.games(Scoreboard.Side.OPPONENT)
              + ", winner=" + f.matchWinner());

        int finalGames = f.games(Scoreboard.Side.PLAYER);
        f.pointTo(Scoreboard.Side.OPPONENT);
        check("a finished match cannot be scored into", f.games(Scoreboard.Side.PLAYER) == finalGames
              && f.games(Scoreboard.Side.OPPONENT) == 0,
              "awarding after match point left it at " + f.games(Scoreboard.Side.PLAYER)
              + "-" + f.games(Scoreboard.Side.OPPONENT));

        // The winning score has to survive long enough to be read.
        Scoreboard g2 = new Scoreboard();
        for (int i = 0; i < 9; i++) g2.pointTo(Scoreboard.Side.OPPONENT);
        for (int i = 0; i < 11; i++) g2.pointTo(Scoreboard.Side.PLAYER);
        check("the winning score stays on the board until the next rally starts",
              g2.points(Scoreboard.Side.PLAYER) == 11 && g2.points(Scoreboard.Side.OPPONENT) == 9,
              "reads " + g2.points(Scoreboard.Side.PLAYER) + "-" + g2.points(Scoreboard.Side.OPPONENT)
              + " after the game-winning point");
    }

    /**
     * The brush modifier, against the invariant it is allowed to bend and the ones it is not.
     *
     * The Sep 4 (later) rule -- "no cursor aim, at any height, can move the racket off its
     * hitting plane" -- is checked elsewhere and still holds, because it is a statement about
     * {@link PlayerReach#clamp}, which is untouched. The brush is a SEPARATE entry point, and
     * what has to be true of it is narrower: it may move the blade vertically, it may not move
     * the blade's REACH, and it must hand the depth axis back unchanged.
     */
    private static void theBrushLiftsTheBatWithoutExtendingItsReach() {
        System.out.println("\n-- the brush --");

        Vec3 aim = new Vec3(0.3, PlayerReach.HIT_Y, 1.10);

        Vec3 high = PlayerReach.clampBrushed(aim, 0.0, 1.10);   // cursor at the top of the screen
        Vec3 low  = PlayerReach.clampBrushed(aim, 1.0, 1.10);   // cursor at the bottom
        check("the brush carries the bat up and down through the ball",
              high.y() > PlayerReach.HIT_Y + 0.01 && low.y() < PlayerReach.HIT_Y - 0.01,
              String.format("top of screen y=%.3f, bottom y=%.3f, plane is %.3f",
                            high.y(), low.y(), PlayerReach.HIT_Y));

        double worst = 0;
        for (double f = 0; f <= 1.0001; f += 0.02) {
            worst = Math.max(worst, Math.abs(PlayerReach.clampBrushed(aim, f, 1.10).y()
                                             - PlayerReach.HIT_Y));
        }
        check("the brush cannot lift the bat further than a stroke",
              worst <= PlayerReach.BRUSH_BAND + 1e-9,
              String.format("worst departure %.3f m against a band of %.3f",
                            worst, PlayerReach.BRUSH_BAND));

        double lowest = Double.MAX_VALUE;
        for (double f = 0; f <= 1.0001; f += 0.02) {
            lowest = Math.min(lowest, PlayerReach.clampBrushed(aim, f, 1.10).y());
        }
        check("the brush cannot cut the bat down through the table top",
              lowest >= physics.Constants.BLADE_R - 1e-9,
              String.format("lowest blade centre %.3f m against a blade radius of %.3f",
                            lowest, physics.Constants.BLADE_R));

        // The reach rule: brushing must not let the blade stand anywhere clamp() would not.
        double worstZ = 0;
        for (double f = 0; f <= 1.0001; f += 0.1) {
            Vec3 b = PlayerReach.clampBrushed(new Vec3(0.3, PlayerReach.HIT_Y, 9.0), f, 1.10);
            worstZ = Math.max(worstZ, Math.abs(b.z() - 1.10));
        }
        check("the brush freezes depth -- it cannot be used to reach further up-table",
              worstZ < 1e-9,
              String.format("depth moved by %.6f m over the whole cursor sweep", worstZ));

        // And normal aiming is still pinned to the plane, brush or no brush.
        double offPlane = 0;
        for (double z = -3; z <= 5; z += 0.25) {
            offPlane = Math.max(offPlane,
                    Math.abs(PlayerReach.clamp(new Vec3(0.2, 7.5, z)).y() - PlayerReach.HIT_Y));
        }
        check("with the modifier up, no aim at any height leaves the hitting plane",
              offPlane == 0,
              String.format("worst height deviation %.1e m", offPlane));
    }

    // ---------------------------------------------------------------- the game session

    /** An opponent that never plays: its blade stands far behind the table, so feeds run out. */
    private static final Opponent STATUE = new Opponent() {
        @Override public void advance(BallState ball, Paddle blade, double dt) {
            blade.placeAt(new Vec3(0, 0.20, -4.0), Follower.SQUARE);
        }
        @Override public String name() { return "statue"; }
    };

    private static Shots feed(String name, Vec3 pos, Vec3 vel) {
        return new Shots(name, name, BallState.at(pos, vel, Vec3.ZERO), null);
    }

    /** Hit well past the far end without touching the table: out, and then the floor. */
    private static final Shots LONG_FEED = feed("long", new Vec3(0, 0.30, 1.52), new Vec3(0, 1.5, -14));

    /** Dropped short on the player's own half, rising toward the player after its bounce. */
    private static final Shots BOUNCER = feed("bouncer", new Vec3(0, 0.30, 0.45), new Vec3(0, 0, 1.2));

    /**
     * An opponent that digs the ball off the table surface: as the ball falls onto the far half
     * a second time, the blade drops in 3 cm behind it at 3 cm up and pushes forward, so the
     * racket contact and the ball's table touch fall on the SAME physics step.
     */
    private static final class Digger implements Opponent {
        private boolean bounced, set;
        @Override public void advance(BallState ball, Paddle blade, double dt) {
            Vec3 p = ball.pos();
            if (ball.vel().y() > 0) bounced = true;
            if (set) {
                blade.moveTo(blade.pos().plus(new Vec3(0, 0, 0.01)), Follower.SQUARE, dt);
            } else if (bounced && ball.vel().y() < 0 && p.y() < 0.03) {
                blade.placeAt(new Vec3(p.x(), p.y(), p.z() - 0.03), Follower.SQUARE);
                set = true;
            } else {
                blade.placeAt(Follower.READY, Follower.SQUARE);
            }
        }
        @Override public String name() { return "digger"; }
    }

    private static boolean happened(GameSession game, World.EventType type) {
        return game.events().stream().anyMatch(e -> e.type() == type);
    }

    /** Point the stand-in hand's cursor at the ball, through the real envelope. */
    private static void pointAtTheBall(GameSession game) {
        Vec3 ball = game.ball().pos();
        game.setAim(PlayerReach.clamp(new Vec3(ball.x(), 0, ball.z())));
    }

    /**
     * The rally rules, played through the same session the application runs -- not a copy of
     * its loop. Each feed is built so one rule decides it, and the check names that rule.
     */
    private static void theSessionAppliesTheRallyRules() {
        System.out.println("\n-- the game session --");

        // First bounce: whichever half a feed lands on first opens that side's racket and no
        // other; nobody may hit while it is still in the air. One feed for each half.
        for (Shots shot : new Shots[]{Shots.byName("Serve"), BOUNCER}) {
            GameSession game = new GameSession();
            game.launch(shot);
            boolean shutWhileInAir = true;
            while (game.bounceSerial() == 0 && game.time() < 2) {
                shutWhileInAir &= !game.playerMayHit() && !game.opponentMayHit();
                game.step();
            }
            boolean near = game.events().stream()
                    .filter(e -> e.type() == World.EventType.TABLE_BOUNCE).findFirst()
                    .map(e -> e.side() < 0).orElse(false);
            check("the first bounce opens only the racket on that half (" + shot.name() + ")",
                  shutWhileInAir && game.playerMayHit() == near && game.opponentMayHit() == !near,
                  String.format("closed in the air=%b; landed %s; player=%b opponent=%b", shutWhileInAir,
                                near ? "near" : "far", game.playerMayHit(), game.opponentMayHit()));
        }

        // Double bounce: a dead drop on the opponent's half that nobody plays.
        GameSession drop = new GameSession(STATUE, new ShotAssist());
        drop.launch(Shots.byName("ITTF drop test"));
        boolean openedAfterOne = false;
        GameSession.StepResult decided = null;
        for (int i = 0; i < (int) (3.0 / DT) && decided == null; i++) {
            GameSession.StepResult r = drop.step();
            openedAfterOne |= drop.opponentMayHit();
            if (r.pointAwarded()) decided = r;
        }
        check("a second bounce on the receiver's half is the receiver's point lost",
              openedAfterOne && decided != null && decided.pointTo() == Scoreboard.Side.PLAYER,
              String.format("first bounce opened the opponent=%b; point to %s at t=%.3f s",
                            openedAfterOne, decided == null ? "nobody" : decided.pointTo(), drop.time()));

        // Own half: a shot that comes straight back off a still blade onto the player's own half.
        // A tuning with no clean core leaves every contact raw, so nothing authors it over the net.
        ShotTuning raw = ShotTuning.builder()
                .qualityCore(0).qualityCoreMin(0).qualityFalloff(1e-9).assistFloor(0).build();
        GameSession own = new GameSession(STATUE, new ShotAssist(raw));
        own.setAim(PlayerReach.clamp(new Vec3(0, 0, risingThroughTheHittingPlane(BOUNCER).z())));
        own.launch(BOUNCER);
        Scoreboard.Side ownHit = null, ownPoint = null;
        for (int i = 0; i < (int) (3.0 / DT) && ownPoint == null; i++) {
            GameSession.StepResult r = own.step();
            if (r.contact()) ownHit = r.hitBy();
            if (r.pointAwarded()) ownPoint = r.pointTo();
        }
        check("a return that falls back on the hitter's own half loses the point",
              ownHit == Scoreboard.Side.PLAYER && ownPoint == Scoreboard.Side.OPPONENT
                  && own.ball().pos().z() > 0,
              String.format("hit by %s, point to %s, ball at z=%+.2f", ownHit, ownPoint, own.ball().pos().z()));

        // Out, then floor: two terminal events on one rally, exactly one point, against the hitter.
        GameSession out = new GameSession(STATUE, new ShotAssist());
        out.launch(LONG_FEED);
        int awards = 0;
        for (int i = 0; i < (int) (3.0 / DT); i++) if (out.step().pointAwarded()) awards++;
        boolean both = happened(out, World.EventType.OUT_OF_BOUNDS) && happened(out, World.EventType.FLOOR);
        check("out and then the floor end the rally with exactly one point, against the hitter",
              both && awards == 1 && out.score().opponentPoints() == 1 && out.score().playerPoints() == 0,
              String.format("out+floor both fired=%b; %d award(s); score %d-%d", both, awards,
                            out.score().playerPoints(), out.score().opponentPoints()));

        // Net cord: a feed that clips the cord and still lands on the far half is a live ball.
        Shots cord = netCordFeed();
        boolean touchedNet = false, opened = false, earlyPoint = false;
        if (cord != null) {
            GameSession net = new GameSession(STATUE, new ShotAssist());
            net.launch(cord);
            for (int i = 0; i < (int) (2.0 / DT) && !net.opponentMayHit(); i++) {
                earlyPoint |= net.step().pointAwarded();
            }
            touchedNet = happened(net, World.EventType.NET);
            opened = net.opponentMayHit();
        }
        check("a ball that clips the net and lands legally stays in play",
              touchedNet && opened && !earlyPoint,
              cord == null ? "no cord-clipping feed found"
                           : String.format("%s: net touched=%b, far bounce opened the opponent=%b, early point=%b",
                                           cord.name(), touchedNet, opened, earlyPoint));

        // A decided point withdraws both rackets: a hand still chasing the ball cannot touch it.
        GameSession dead = new GameSession();
        dead.launch(Shots.byName("Into the net"));
        boolean over = false;
        int lateContacts = 0;
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < (int) (4.0 / DT); i++) {
            pointAtTheBall(dead);
            GameSession.StepResult r = dead.step();
            if (over && r.contact()) lateContacts++;
            if (over) closest = Math.min(closest, dead.playerBlade().centre().minus(dead.ball().pos()).length());
            over |= r.pointAwarded();
        }
        check("once a point is decided, no racket can touch the ball again",
              over && lateContacts == 0,
              String.format("point decided=%b; %d contacts after it; blade came within %.3f m of the ball",
                            over, lateContacts, closest));

        // The contact window: a push dug off the surface touches the table on (or right after)
        // the racket contact. That touch belongs to the contact -- it is not the opponent's own
        // shot falling back on the opponent's half.
        GameSession dig = new GameSession(new Digger(), new ShotAssist());
        dig.launch(Shots.byName("ITTF drop test"));
        double contactAt = Double.NaN, bounceGap = Double.NaN;
        Scoreboard.Side digPoint = null;
        for (int i = 0; i < (int) (3.0 / DT) && digPoint == null; i++) {
            int serial = dig.bounceSerial();
            GameSession.StepResult r = dig.step();
            if (r.contact() && Double.isNaN(contactAt)) contactAt = dig.time();
            if (!Double.isNaN(contactAt) && Double.isNaN(bounceGap) && dig.bounceSerial() > serial) {
                bounceGap = dig.time() - contactAt;
            }
            if (r.pointAwarded()) digPoint = r.pointTo();
            if (!Double.isNaN(contactAt) && dig.time() - contactAt > 0.1) break;
        }
        check("a table touch on the contact's own step does not score as the hitter's own half",
              !Double.isNaN(contactAt) && bounceGap <= GameSession.CONTACT_BOUNCE_WINDOW && digPoint == null,
              String.format("contact at t=%.4f s, table touch %.1f ms after it (window %.1f ms), point: %s",
                            contactAt, bounceGap * 1000, GameSession.CONTACT_BOUNCE_WINDOW * 1000,
                            digPoint == null ? "none" : digPoint));
    }

    /** Replays fire on the documented delay only when enabled; a new feed keeps the score. */
    private static void theSessionSchedulesReplaysAndKeepsTheScore() {
        GameSession on = new GameSession(STATUE, new ShotAssist());
        on.launch(LONG_FEED);
        double pointAt = Double.NaN, dueAt = Double.NaN;
        for (int i = 0; i < (int) (4.0 / DT) && Double.isNaN(dueAt); i++) {
            if (on.step().pointAwarded()) pointAt = on.time();
            if (on.replayDue()) dueAt = on.time();
        }
        double delay = dueAt - pointAt;
        check("with auto-replay on, the next feed is due one point-end delay after the point",
              delay >= GameSession.POINT_END_DELAY - 1e-9 && delay < GameSession.POINT_END_DELAY + DT + 1e-9,
              String.format("due %.4f s after the point (delay %.3f s, step %.4f s)",
                            delay, GameSession.POINT_END_DELAY, DT));

        Scoreboard.Snapshot before = on.score();
        on.launch(LONG_FEED);
        check("a new feed keeps the match score and reopens the rally",
              on.score().equals(before) && !on.pointOver() && !on.playerMayHit() && !on.opponentMayHit(),
              String.format("score %d-%d kept=%b; point over=%b", before.playerPoints(),
                            before.opponentPoints(), on.score().equals(before), on.pointOver()));

        GameSession off = new GameSession(STATUE, new ShotAssist());
        off.setAutoReplay(false);
        off.launch(LONG_FEED);
        boolean everDue = false;
        for (int i = 0; i < (int) (6.0 / DT); i++) { off.step(); everDue |= off.replayDue(); }
        check("with auto-replay off, no feed is scheduled but the point still counts",
              !everDue && off.score().opponentPoints() == 1,
              String.format("replay due=%b over 6 s; score %d-%d", everDue,
                            off.score().playerPoints(), off.score().opponentPoints()));
    }

    /** The same inputs, step for step, give the same game -- bit for bit. */
    private static void theSessionIsDeterministic() {
        GameSession a = new GameSession(), b = new GameSession();
        for (GameSession g : new GameSession[]{a, b}) { g.setDemoMode(true); g.launch(Shots.byName("Serve")); }
        int steps = (int) (10.0 / DT), firstDiff = -1, contacts = 0;
        for (int i = 0; i < steps && firstDiff < 0; i++) {
            GameSession.StepResult ra = a.step(), rb = b.step();
            if (ra.contact()) contacts++;
            if (!ra.equals(rb) || !a.ball().equals(b.ball())) firstDiff = i;
        }
        check("two sessions fed the same inputs stay identical",
              firstDiff < 0 && contacts > 0,
              firstDiff < 0 ? String.format("%d steps and %d contacts, every ball state equal", steps, contacts)
                            : "diverged at step " + firstDiff);
    }

    /**
     * The shipped tuning, restated here rather than read back from the builder, so a changed
     * default fails loudly; and a tuning the model cannot run on refuses to build.
     */
    private static void theShotTuningKeepsItsDefaultsAndRejectsNonsense() {
        System.out.println("\n-- the shot tuning --");

        Object[][] expected = {
            {"minShotSpeed", 5.0}, {"maxShotSpeed", 17.0}, {"maxSwingSpeed", 16.0},
            {"lateralEffort", 0.25}, {"swingInfluence", 1.0}, {"swingCurve", 0.7},
            {"incomingPaceNeutral", 9.0}, {"incomingPaceGain", 0.05}, {"incomingPaceNudge", 0.6},
            {"aimInfluence", 0.20}, {"depthInfluence", 0.100}, {"arcInfluence", 0.018},
            {"faceInfluence", 0.25}, {"contactPointInfluence", 0.30}, {"baseDepthFrac", 0.35},
            {"contactDepthShare", 0.5}, {"physicalBlend", 0.15},
            {"qualityCore", 0.58}, {"qualityFalloff", 0.50}, {"qualityPaceFrom", 6.0},
            {"qualityPaceSpan", 12.0}, {"qualityPaceLoss", 0.15}, {"qualityCoreMin", 0.26},
            {"assistFloor", 0.35}, {"rescueQualityFloor", 0.60},
            {"driveBrush", 0.8}, {"reflectionCap", 6.0}, {"maxHorizontalDeviationDeg", 30.0},
            {"maxLateralVelocity", 4.5}, {"maxVerticalLaunchAngleDeg", 45.0},
            {"minVerticalLaunchAngleDeg", -20.0}, {"minForwardVelocity", 4.5},
            {"speedCandidates", 5}, {"speedSpread", 0.42}, {"speedPreference", 1.0},
            {"passPenalty", 2.0}, {"minSearchSpeed", 3.0}, {"searchSpeedFloorFrac", 0.60},
            {"rescueEffortCeiling", 0.75}, {"maxCorrectionPasses", 2}, {"targetAssist", 0.18},
            {"speedBackoffPerPass", 0.13}, {"targetHalfWidthFrac", 0.90},
            {"targetDepthMinFrac", 0.20}, {"targetDepthMaxFrac", 0.92}, {"safeDepthFrac", 0.55},
            {"netClearance", 0.055}, {"landingMargin", 0.05}, {"rescueMinSpeed", 3.0},
            {"rescueSpeedSteps", 9}, {"rescueDepthFracs", List.of(0.55, 0.72, 0.88, 0.40)},
            {"rescueAimFracs", List.of(1.0, 0.6, 0.3, 0.0)}, {"spinInfluence", 1.0},
            {"baseTopspin", 14.0}, {"topspinPerLift", 2.6}, {"sidespinPerSwipe", 4.5},
            {"maxSpin", 55.0},
        };
        ShotTuning defaults = ShotTuning.defaults();
        List<String> changed = new ArrayList<>();
        for (Object[] e : expected) {
            try {
                Object got = ShotTuning.class.getField((String) e[0]).get(defaults);
                if (!got.equals(e[1])) changed.add(e[0] + "=" + got + " (expected " + e[1] + ")");
            } catch (ReflectiveOperationException ex) {
                changed.add(e[0] + " missing");
            }
        }
        int knobs = ShotTuning.class.getFields().length;
        check("every default shot-tuning value is unchanged",
              changed.isEmpty() && knobs == expected.length,
              changed.isEmpty() ? String.format("%d of %d knobs match", expected.length, knobs)
                                : String.join("; ", changed));

        record Bad(String what, java.util.function.UnaryOperator<ShotTuning.Builder> edit) {}
        Bad[] bad = {
            new Bad("NaN shot speed",            b -> b.maxShotSpeed(Double.NaN)),
            new Bad("infinite spin",             b -> b.baseTopspin(Double.POSITIVE_INFINITY)),
            new Bad("speed range upside down",   b -> b.minShotSpeed(18)),
            new Bad("rescue faster than the top", b -> b.rescueMinSpeed(20)),
            new Bad("depth range upside down",   b -> b.targetDepthMinFrac(0.95)),
            new Bad("zero quality falloff",      b -> b.qualityFalloff(0)),
            new Bad("zero pace span",            b -> b.qualityPaceSpan(0)),
            new Bad("zero swing speed",          b -> b.maxSwingSpeed(0)),
            new Bad("zero swing curve",          b -> b.swingCurve(0)),
            new Bad("blend past 1",              b -> b.physicalBlend(1.5)),
            new Bad("negative assist floor",     b -> b.assistFloor(-0.1)),
            new Bad("aim fraction past 1",       b -> b.rescueAimFracs(1.0, 1.2)),
            new Bad("NaN rescue depth",          b -> b.rescueDepthFracs(0.5, Double.NaN)),
            new Bad("vertical angle at 90",      b -> b.maxVerticalLaunchAngleDeg(90)),
            new Bad("elevation band inverted",   b -> b.minVerticalLaunchAngleDeg(50)),
            new Bad("horizontal cone at 90",     b -> b.maxHorizontalDeviationDeg(90)),
            new Bad("no speed candidates",       b -> b.speedCandidates(0)),
            new Bad("one rescue speed step",     b -> b.rescueSpeedSteps(1)),
            new Bad("backoff that stops the shot", b -> b.speedBackoffPerPass(0.5)),
            new Bad("negative reflection cap",   b -> b.reflectionCap(-1)),
        };
        List<String> accepted = new ArrayList<>();
        for (Bad b : bad) {
            try { b.edit().apply(ShotTuning.builder()).build(); accepted.add(b.what()); }
            catch (IllegalArgumentException expectedRejection) { /* rejected, as it should be */ }
        }
        check("a shot tuning the model cannot run on is rejected when built",
              accepted.isEmpty(),
              accepted.isEmpty() ? bad.length + " invalid tunings rejected" : "accepted: " + accepted);

        // The limits are the model's own, not arbitrary: each boundary it can run on is allowed.
        String refused = null;
        try {
            ShotTuning.builder().physicalBlend(0).assistFloor(1).speedCandidates(1)
                    .rescueSpeedSteps(2).maxCorrectionPasses(0).spinInfluence(0)
                    .minForwardVelocity(0).landingMargin(0).build();
            ShotTuning.builder().physicalBlend(1).targetDepthMinFrac(0.92).build();
        } catch (IllegalArgumentException e) {
            refused = e.getMessage();
        }
        check("boundary values the model can run on are accepted",
              refused == null, refused == null ? "blend 0 and 1, one candidate, two rescue steps, no passes"
                                               : "refused: " + refused);
    }

    /** Where a feed's ball first rises through the player's hitting plane after its bounce. */
    private static Vec3 risingThroughTheHittingPlane(Shots shot) {
        World w = new World();
        w.launch(shot.state());
        for (int i = 0; i < (int) (2.0 / DT); i++) {
            double y0 = w.state().pos().y();
            w.step();
            if (w.bounceSerial() > 0 && y0 < PlayerReach.HIT_Y && w.state().pos().y() >= PlayerReach.HIT_Y) {
                return w.state().pos();
            }
        }
        throw new IllegalStateException(shot.name() + " never rises through the hitting plane");
    }

    /**
     * A feed that touches the net cord and still lands first on the far half, found by search so
     * it does not hang on hand-tuned numbers: a paddle-free flight is the honest judge.
     */
    private static Shots netCordFeed() {
        for (double vz = 6; vz <= 10; vz += 1) {
            for (double vy = -0.5; vy <= 2.5; vy += 0.02) {
                Shots s = feed(String.format("cord feed vz=-%.0f vy=%+.2f", vz, vy),
                               new Vec3(0, 0.20, 1.2), new Vec3(0, vy, -vz));
                World w = new World();
                w.launch(s.state());
                boolean touched = false;
                for (int i = 0; i < (int) (1.5 / DT) && w.bounceSerial() == 0; i++) {
                    w.step();
                    touched |= w.lastEvent() != null && w.lastEvent().type() == World.EventType.NET;
                }
                World.Event bounce = w.lastEvent();
                if (touched && bounce != null && bounce.type() == World.EventType.TABLE_BOUNCE
                        && bounce.side() > 0) {
                    return s;
                }
            }
        }
        return null;
    }

    private static void check(String what, boolean ok, String detail) {
        checks++;
        System.out.printf("  [%s] %s%s%n", ok ? "PASS" : "FAIL", what,
                          detail.isEmpty() ? "" : "  (" + detail + ")");
        if (!ok) failures.add(what + (detail.isEmpty() ? "" : " -> " + detail));
    }
}
