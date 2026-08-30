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
 * and require it to return each one over the net.
 *
 * The second group covers the player's stroke, which has the same problem from the other side:
 * "charging harder hits harder" and "you cannot cheat by flicking the mouse" are both claims
 * about behaviour that nothing on screen would contradict loudly enough to notice.
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
        aFlickOfTheMouseCannotOutrunAStroke();
        chargingASwingMakesItFaster();

        System.out.println("=".repeat(74));
        if (failures.isEmpty()) {
            System.out.printf("ALL %d CHECKS PASSED%n", checks);
        } else {
            System.out.printf("%d of %d CHECKS FAILED:%n", failures.size(), checks);
            failures.forEach(f -> System.out.println("  - " + f));
            System.exit(1);
        }
    }

    /** What happened when one shot was fed at the opponent. */
    private record Rally(boolean touched, boolean returned, double maxZ, double outgoingSpeed) {}

    /**
     * Feed one shot and let the follower play it.
     *
     * The player's end is left empty on purpose: this is testing the opponent alone, so the
     * ball is fed from the near end and the rally ends once the opponent has answered it.
     */
    private static Rally feed(Shots shot) {
        World world = new World();
        Paddle blade = new Paddle(new Vec3(0, 0.20, Follower.PLANE_Z), new Vec3(0, 0, 1));
        Opponent ai = new Follower();

        world.setPaddles(null, blade);
        world.launch(shot.state());

        boolean touched = false;
        double maxZ = -9, outSpeed = 0;

        for (int i = 0; i < (int) (4.0 / DT); i++) {
            ai.advance(world.state(), blade, DT);
            world.step();

            if (!touched && world.paddleHits() > 0) {
                touched = true;
                outSpeed = world.state().speed();
            }
            if (touched) maxZ = Math.max(maxZ, world.state().pos().z());

            // Once it has come back past the net there is nothing more to learn.
            if (touched && world.state().pos().z() > 0.05) break;
            if (world.state().pos().y() < -TABLE_HEIGHT + 0.05 && touched) break;
        }
        return new Rally(touched, touched && maxZ > 0.0, maxZ, outSpeed);
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
            if (r.outgoingSpeed() > fastest) { fastest = r.outgoingSpeed(); worst = shot.name(); }
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
     * Not a check: print where every return actually lands.
     *
     * It is here because "the follower returns every shot" is true and misleading on its own.
     * It clears the net every time; it puts one of nine ON the table.
     *
     * That is not a tuning failure to be quietly improved away. A sweep of the follower's face
     * angle, swing speed and lift finds a straight trade-off: settings that land three of nine
     * cannot get all nine back over the net, and of the 318 settings that DO clear the net
     * every time, the best lands one. The presets arrive between 3.5 and 18.4 m/s carrying 25
     * to 125 rev/s, and a fixed stroke cannot be the right answer to both ends of that.
     *
     * Returning all nine legally means choosing the stroke from the ball, which means reading
     * it -- World.predict, and the October opponent. Printed rather than asserted so the number
     * stays in front of whoever runs this, instead of being rediscovered later.
     */
    private static void reportedReturnQuality() {
        System.out.println();
        System.out.println("  where the returns land (not a check -- see the October opponent):");

        int in = 0, played = 0;
        for (Shots shot : Shots.ALL) {
            if (!isFedAtTheOpponent(shot)) continue;
            played++;
            Return r = playOut(shot);
            boolean landed = r.landsOnTheTable();
            if (landed) in++;
            System.out.printf("    %-24s out %5.1f m/s  apex %4.2f m  %s%n",
                    shot.name(), r.outSpeed(), r.apex(), r.verdict());
        }
        System.out.printf("    -> %d of %d land on the table%n%n", in, played);
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
     */
    private static Return playOut(Shots shot) {
        World world = new World();
        Paddle blade = new Paddle(new Vec3(0, 0.20, Follower.PLANE_Z), new Vec3(0, 0, 1));
        Opponent ai = new Follower();

        world.setPaddles(null, blade);
        world.launch(shot.state());

        boolean hit = false;
        double apex = 0, outSpeed = 0;
        int hitAt = -1;

        for (int i = 0; i < (int) (6.0 / DT); i++) {
            ai.advance(world.state(), blade, DT);
            double before = world.state().pos().y();
            world.step();

            if (!hit && world.paddleHits() > 0) {
                hit = true;
                outSpeed = world.state().speed();
                hitAt = i;
            }
            if (!hit) continue;

            apex = Math.max(apex, world.state().pos().y());

            // The first descent back through the plane of the table top is where it landed --
            // whether or not there is any table underneath it at that point.
            Vec3 p = world.state().pos();
            if (i > hitAt + 20 && before > BALL_R + 0.001 && p.y() <= BALL_R + 0.001
                    && world.state().vel().y() < 0) {
                return new Return(apex, p.z(), p.x(), outSpeed);
            }
        }
        return new Return(apex, -99, -99, outSpeed);
    }

    // ---------------------------------------------------------------- the player's stroke

    /**
     * A flick of the mouse must not out-hit a stroke.
     *
     * The cursor is sampled once a FRAME and the blade advanced once a STEP, so a fast mouse
     * hands the blade a whole frame of travel to cover inside a single 1/480 s step. Paddle
     * measures its velocity by differencing its own pose, so with nothing holding it back a
     * 30 cm flick reads as 144 m/s. That is not just unphysical, it inverts the mechanic:
     * charging would be strictly worse than twitching.
     */
    private static void aFlickOfTheMouseCannotOutrunAStroke() {
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

        // The limit is only worth having if it sits below a real swing.
        check("the tracking limit is slower than an intermediate player's swing",
              Stroke.TRACK_SPEED < 12.4,
              String.format("%.1f m/s tracking against a measured 12.4 m/s swing",
                            Stroke.TRACK_SPEED));
    }

    /**
     * Charging has to be worth doing, and it has to top out somewhere real.
     *
     * The stroke's peak speed is pi*L/(2T) for its half-sine profile, and the charge sets L.
     * Fully wound up that is pi*1.00/(2*0.09) = 17.5 m/s against a measured mean racket speed
     * of 17.8 m/s for advanced players; released instantly it is pi*0.15/(2*0.09) = 2.6 m/s.
     *
     * Deliberately a check on BLADE speed, not ball speed. Nothing anywhere scales the ball by
     * the charge -- the charge only lengthens the swing, and the contact solver turns that into
     * pace and spin on its own. Checking the ball here would be checking two things at once.
     */
    private static void chargingASwingMakesItFaster() {
        double full = peakSwingSpeed(1.0);
        double flick = peakSwingSpeed(0.0);

        check("a fully charged swing reaches the speed an advanced player swings at",
              full > 16.0 && full < 19.0,
              String.format("%.1f m/s against a measured 17.8 m/s", full));

        check("a swing released with no charge is far slower than a charged one",
              flick < full / 3,
              String.format("%.1f m/s uncharged against %.1f m/s charged", flick, full));
    }

    /**
     * Wind up to {@code charge} of full, release, and report the fastest the blade ever moves.
     *
     * The whole swing is measured, not just its end, because the failure this is guarding
     * against is a spike on ONE step -- which is invisible in the final pose and lethal to any
     * ball nearby.
     */
    private static double peakSwingSpeed(double charge) {
        Vec3 start = new Vec3(0, 0.25, 1.57);
        Paddle blade = new Paddle(start, new Vec3(0, 0, -1));
        Stroke stroke = new Stroke(start);

        stroke.press();
        for (int i = 0; i < (int) Math.round(charge * 0.70 / DT); i++) stroke.advance(blade, DT);
        stroke.release();

        double fastest = 0;
        while (stroke.phase() == Stroke.Phase.SWINGING) {
            stroke.advance(blade, DT);
            fastest = Math.max(fastest, blade.vel().length());
        }
        return fastest;
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
