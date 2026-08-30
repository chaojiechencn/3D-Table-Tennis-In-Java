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
        check("the follower returns every shot back over the net (this is what makes it unbeatable)",
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
