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
 * Run: java -cp out/production/3D-Table-Tennis-In-Java play.RallyTest
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
        Paddle blade = new Paddle(new Vec3(0, 0.20, Follower.PLANE_Z), new Vec3(0, 0, 1));
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
        Paddle blade = new Paddle(new Vec3(0, 0.20, Follower.PLANE_Z), new Vec3(0, 0, 1));
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
            double before = prev.pos().y();
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

        // A ball down the table heading away, so the reach and face logic have something sane
        // to work with; the claim here is about blade SPEED, which does not depend on it.
        BallState ball = BallState.at(new Vec3(0, 0.25, -0.5), new Vec3(0, 0, -8), Vec3.ZERO);

        double fastest = 0;
        for (int i = 0; i < 8; i++) {
            stroke.advance(blade, ball, DT);
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

    private static void check(String what, boolean ok, String detail) {
        checks++;
        System.out.printf("  [%s] %s%s%n", ok ? "PASS" : "FAIL", what,
                          detail.isEmpty() ? "" : "  (" + detail + ")");
        if (!ok) failures.add(what + (detail.isEmpty() ? "" : " -> " + detail));
    }
}
