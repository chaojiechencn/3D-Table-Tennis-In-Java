package physics;

import java.util.ArrayList;
import java.util.List;

import static physics.Constants.*;

/**
 * Headless validation of the physics against numbers that did not come from this program.
 *
 * The contract commits to using "research papers on table tennis ball trajectories, so I
 * have real numbers to check my simulation against instead of guessing". This is where that
 * promise is kept. Every check below compares the simulation either to a closed-form
 * solution of the same equations, or to a published/ITTF measurement.
 *
 * A demo that merely looks right is not evidence. This is.
 *
 * Run: java -cp out/production/T1-WTT-Project-CS-IS physics.SelfTest
 * Exits 0 if everything passes, 1 otherwise.
 */
public final class SelfTest {

    private static final List<String> failures = new ArrayList<>();
    private static int checks = 0;

    public static void main(String[] args) {
        System.out.println("Mr. Pong - physics validation");
        System.out.println("=".repeat(74));

        reportedConstants();
        terminalVelocityMatchesClosedForm();
        verticalDropMatchesAnalyticSolution();
        ittfDropTest();
        rk4IsFourthOrder();
        magnusCurvesTheRightWay();
        sidespinDeflectsTheRightWay();
        bounceNeverAddsEnergy();
        topspinKicksForwardBackspinChecks();
        netKillsTheBall();
        everyAimedShotIsLegal();
        outOfBoundsIsDetected();
        longRunStaysStable();
        noTunnellingAtSmashSpeed();

        System.out.println("=".repeat(74));
        if (failures.isEmpty()) {
            System.out.printf("ALL %d CHECKS PASSED%n", checks);
        } else {
            System.out.printf("%d of %d CHECKS FAILED:%n", failures.size(), checks);
            failures.forEach(f -> System.out.println("  - " + f));
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ checks

    /** Not a test: print the derived constants so they can be eyeballed against sources. */
    private static void reportedConstants() {
        System.out.printf("  ball        r=%.3f m  m=%.4f kg  I=%.3e kg m^2 (hollow shell, 2/3 m r^2)%n",
                BALL_R, BALL_M, BALL_I);
        System.out.printf("  aero        0.5*rho*A/m = %.4f 1/m   C_d = %.2f%n",
                HALF_RHO_A_OVER_M, C_DRAG);
        System.out.printf("  drag at 10 m/s = %.2f m/s^2  (gravity is %.2f - air dominates)%n%n",
                Aero.drag(new Vec3(0, 0, -10)).length(), G);
    }

    /**
     * Terminal velocity. Closed form: v = sqrt(g / (0.5*rho*A*C_d/m)).
     * Published figures for a 40 mm table tennis ball put this at roughly 9-9.6 m/s, which
     * is the number that makes the sport possible: it is why a ball hit at 25 m/s slows so
     * dramatically over the length of the table.
     */
    private static void terminalVelocityMatchesClosedForm() {
        double analytic = Math.sqrt(G / (HALF_RHO_A_OVER_M * C_DRAG));

        BallState s = BallState.at(new Vec3(0, 500, 0), Vec3.ZERO, Vec3.ZERO);
        for (int i = 0; i < 480 * 30; i++) s = Integrator.step(s, DT);
        double simulated = -s.vel().y();

        check("terminal velocity matches closed form",
              Math.abs(simulated - analytic) < 0.01,
              String.format("sim %.3f m/s vs analytic %.3f m/s", simulated, analytic));

        check("terminal velocity is in the published 9.0-9.6 m/s range",
              simulated > 9.0 && simulated < 9.6,
              String.format("%.2f m/s", simulated));
    }

    /**
     * Free fall WITH drag has a closed-form solution:
     *     v(t) = -vt * tanh(g t / vt)
     *     y(t) = y0 - (vt^2/g) * ln(cosh(g t / vt))
     * Comparing against it tests the integrator and the drag model together, over a regime
     * where drag is doing most of the work. Nothing here is self-referential.
     */
    private static void verticalDropMatchesAnalyticSolution() {
        double vt = Math.sqrt(G / (HALF_RHO_A_OVER_M * C_DRAG));
        double y0 = 100.0, t = 3.0;

        BallState s = BallState.at(new Vec3(0, y0, 0), Vec3.ZERO, Vec3.ZERO);
        for (int i = 0; i < (int) Math.round(t / DT); i++) s = Integrator.step(s, DT);

        double yExact = y0 - (vt * vt / G) * Math.log(Math.cosh(G * t / vt));
        double vExact = -vt * Math.tanh(G * t / vt);

        check("drop position matches analytic solution to 1 mm over 3 s",
              Math.abs(s.pos().y() - yExact) < 1e-3,
              String.format("sim %.6f m vs exact %.6f m", s.pos().y(), yExact));

        check("drop velocity matches analytic solution to 1 mm/s over 3 s",
              Math.abs(s.vel().y() - vExact) < 1e-3,
              String.format("sim %.6f m/s vs exact %.6f m/s", s.vel().y(), vExact));
    }

    /**
     * ITTF bounce test. The Laws require a ball dropped from 30.5 cm to rebound 24-26 cm.
     * That test uses a steel block rather than a table, but the ball-table restitution
     * measured in the literature (0.89-0.93) brackets the same result, so a table bounce
     * landing inside the ITTF band is the right sanity anchor for TABLE_MAT.
     */
    private static void ittfDropTest() {
        World w = new World();
        w.launch(BallState.at(new Vec3(0, 0.305 + BALL_R, -0.7), Vec3.ZERO, Vec3.ZERO));

        boolean bounced = false;
        double peak = 0;
        for (int i = 0; i < 480 * 3; i++) {
            w.step();
            if (w.tableBounces() > 0) {
                if (!bounced) { bounced = true; peak = 0; }
                peak = Math.max(peak, w.state().pos().y() - BALL_R);
                if (w.tableBounces() > 1) break;
            }
        }

        check("ball bounced off the table at all", bounced, "");
        check("ITTF drop test: 30.5 cm gives a 24-26 cm rebound",
              peak >= 0.24 && peak <= 0.26,
              String.format("rebound %.1f cm with e = %.3f", peak * 100, TABLE_MAT.restitution()));

        // And show why the restitution is not the textbook sqrt(25/30.5) = 0.905: that value
        // ignores air resistance, and once drag is included it undershoots the ITTF band.
        double naive = Math.sqrt(0.25 / 0.305);
        double naiveRebound = reboundWithRestitution(naive);
        check("the drag-free estimate of e would MISS the ITTF band (this is why e = 0.92)",
              naiveRebound < 0.24,
              String.format("e = %.3f gives only %.1f cm", naive, naiveRebound * 100));
    }

    /** Rebound height from the ITTF drop, for an arbitrary restitution. */
    private static double reboundWithRestitution(double e) {
        BallState s = BallState.at(new Vec3(0, 0.305 + BALL_R, -0.7), Vec3.ZERO, Vec3.ZERO);
        while (s.pos().y() > BALL_R) s = Integrator.step(s, DT);
        s = s.withVel(new Vec3(0, -s.vel().y() * e, 0)).withPos(new Vec3(0, BALL_R, -0.7));

        double peak = 0;
        while (s.vel().y() > 0) {
            s = Integrator.step(s, DT);
            peak = Math.max(peak, s.pos().y() - BALL_R);
        }
        return peak;
    }

    /**
     * RK4 is fourth order, so quartering the step should cut the error by about 256x.
     * If someone quietly replaces the integrator with Euler, this is what catches it.
     */
    private static void rk4IsFourthOrder() {
        BallState start = Shots.byName("Topspin loop").state();   // drag and Magnus both active
        double t = 0.4;

        Vec3 coarse = integrateFor(start, t, DT);
        Vec3 medium = integrateFor(start, t, DT / 4);
        Vec3 fine   = integrateFor(start, t, DT / 16);

        double errCoarse = coarse.minus(fine).length();
        double errMedium = medium.minus(fine).length();
        double ratio = errMedium < 1e-15 ? Double.POSITIVE_INFINITY : errCoarse / errMedium;

        check("halving-the-step error ratio indicates 4th order (expect >= 100x per 4x)",
              ratio > 100,
              String.format("error shrank %.0fx for a 4x smaller step", ratio));

        check("one physics step at DT is already accurate to under 0.1 mm over 0.4 s",
              errCoarse < 1e-4,
              String.format("%.3e m", errCoarse));
    }

    private static Vec3 integrateFor(BallState s, double seconds, double dt) {
        int steps = (int) Math.round(seconds / dt);
        for (int i = 0; i < steps; i++) s = Integrator.step(s, dt);
        return s.pos();
    }

    /**
     * The headline claim of the checkpoint: spin curves the ball. Topspin must land the ball
     * SHORTER than the identical shot with no spin, backspin must carry it LONGER.
     */
    private static void magnusCurvesTheRightWay() {
        // Identical launch, three spins. Measured at the plane of the table rather than at a
        // legal bounce, so a backspin ball that floats past the end still yields a number --
        // that overshoot IS the effect being measured.
        Vec3 pos = new Vec3(0, 0.30, 1.50), vel = new Vec3(0, 0.4, -9.0);
        double heavy = 90 * 2 * Math.PI;

        double flat = firstLandingZ(BallState.at(pos, vel, Vec3.ZERO));
        double top  = firstLandingZ(BallState.at(pos, vel, new Vec3(-heavy, 0, 0)));
        double back = firstLandingZ(BallState.at(pos, vel, new Vec3(heavy, 0, 0)));

        // Travelling toward -Z, so "shorter" means a LARGER (less negative) landing z.
        check("topspin lands shorter than no spin",
              top > flat + 0.05,
              String.format("topspin z=%.3f vs flat z=%.3f (%.0f cm shorter)",
                            top, flat, (top - flat) * 100));

        check("backspin carries further than no spin",
              back < flat - 0.05,
              String.format("backspin z=%.3f vs flat z=%.3f (%.0f cm longer)",
                            back, flat, (flat - back) * 100));

        check("the topspin/backspin spread is large enough to see on screen",
              (top - back) > 0.30,
              String.format("%.0f cm apart", (top - back) * 100));
    }

    /** Sidespin about +Y must push the ball toward -X. Verified against the cross product. */
    private static void sidespinDeflectsTheRightWay() {
        Vec3 pos = new Vec3(0, 0.30, 1.50), vel = new Vec3(0, 0.4, -9.0);
        double spin = 90 * 2 * Math.PI;   // measured at the table plane, as above

        double left  = firstLandingX(BallState.at(pos, vel, new Vec3(0, spin, 0)));
        double right = firstLandingX(BallState.at(pos, vel, new Vec3(0, -spin, 0)));

        check("sidespin about +Y deflects toward -X",
              left < -0.02, String.format("landed x=%.3f m", left));
        check("sidespin about -Y deflects toward +X",
              right > 0.02, String.format("landed x=%.3f m", right));
        check("the two sidespins are mirror images",
              Math.abs(left + right) < 1e-6,
              String.format("%.4f vs %.4f", left, right));
    }

    /** No contact may ever add kinetic energy. This is what stops a simulation exploding. */
    private static void bounceNeverAddsEnergy() {
        World w = new World();
        w.launch(Shots.byName("Topspin loop").state());

        double worst = 0;
        double prev = w.state().kineticEnergy();
        for (int i = 0; i < 480 * 20; i++) {
            w.step();
            double now = w.state().kineticEnergy();
            // Gravity legitimately adds KE during free fall, so only judge the steps where a
            // contact happened: those are the ones with a sudden jump.
            double gain = now - prev;
            double gravityBudget = BALL_M * G * Math.abs(w.state().vel().y()) * DT * 1.5 + 1e-9;
            if (gain > gravityBudget) worst = Math.max(worst, gain - gravityBudget);
            prev = now;
        }
        check("no contact adds kinetic energy over a 20 s rally",
              worst < 1e-6, String.format("worst unexplained gain %.3e J", worst));
    }

    /**
     * The spin coupling, stated as a falsifiable claim: a topspin ball must leave the bounce
     * FASTER along its direction of travel than it arrived, and a heavy backspin ball must
     * leave slower, with its spin knocked down or reversed. This is the whole reason the
     * bounce uses a friction impulse instead of just flipping v.y.
     */
    private static void topspinKicksForwardBackspinChecks() {
        double spin = 110 * 2 * Math.PI;
        Vec3 pos = new Vec3(0, 0.25, 0.5), vel = new Vec3(0, -3.0, -10);

        double[] top  = bounceChange(BallState.at(pos, vel, new Vec3(-spin, 0, 0)));
        double[] back = bounceChange(BallState.at(pos, vel, new Vec3(spin, 0, 0)));

        check("topspin gains forward speed off the bounce",
              top[0] > 0.2, String.format("forward speed %+.2f m/s", top[0]));
        check("backspin loses forward speed off the bounce",
              back[0] < -0.2, String.format("forward speed %+.2f m/s", back[0]));
        check("the bounce reduces backspin (friction fights it)",
              back[1] < -1.0, String.format("spin change %+.0f rad/s", back[1]));
        check("topspin and backspin behave oppositely off the same table",
              top[0] * back[0] < 0, "");
    }

    /** @return {change in forward speed, change in x-spin} across the first table bounce. */
    private static double[] bounceChange(BallState start) {
        World w = new World();
        w.launch(start);
        BallState before = start;
        for (int i = 0; i < 480 * 3; i++) {
            BallState prev = w.state();
            w.step();
            if (w.tableBounces() > 0) { before = prev; break; }
        }
        for (int i = 0; i < 6; i++) w.step();      // let it clear the surface
        BallState after = w.state();
        return new double[] { -after.vel().z() - (-before.vel().z()),
                              after.spin().x() - before.spin().x() };
    }

    /** A ball driven into the net must not bounce back off it like a wall. */
    private static void netKillsTheBall() {
        World w = new World();
        w.launch(Shots.byName("Into the net").state());

        boolean hitNet = false;
        double speedAfter = 0;
        for (int i = 0; i < 480 * 3; i++) {
            w.step();
            if (!hitNet && w.events().stream().anyMatch(e -> e.type() == World.EventType.NET)) {
                hitNet = true;
                speedAfter = w.state().speed();
            }
        }
        check("the net shot actually reaches the net", hitNet, "");
        check("the net kills most of the speed",
              hitNet && speedAfter < 4.0, String.format("%.2f m/s leaving the net", speedAfter));
        check("the ball ends up on the near side of the net",
              w.state().pos().z() > -0.05,
              String.format("final z=%.3f m", w.state().pos().z()));
    }

    /**
     * Every aimed preset must actually be playable: the solver converged, it clears the net,
     * and it lands inside the lines. Without this the demo menu can rot silently -- change a
     * drag coefficient and a shot that used to clear the net by a centimetre now clips it.
     */
    private static void everyAimedShotIsLegal() {
        for (Shots shot : Shots.ALL) {
            if (shot.solution() == null) continue;
            Aim.Solution sol = shot.solution();

            check("aim solver converged: " + shot.name(), sol.converged(),
                  String.format("elevation %+.1f deg", sol.elevationDeg()));

            Vec3 land = sol.landing();
            check("first bounce is inside the lines: " + shot.name(),
                  Math.abs(land.x()) < TABLE_WIDTH / 2 && Math.abs(land.z()) < TABLE_LENGTH / 2,
                  String.format("x=%+.2f z=%+.2f", land.x(), land.z()));

            if (shot.isServe()) {
                // A serve clears the cord on its SECOND flight, so it has to be simulated all
                // the way through rather than asked of the launch solution.
                serveIsLegal(shot);
            } else {
                check("clears the net: " + shot.name(), sol.netClearance() > 0.01,
                      String.format("%+.1f cm over the cord", sol.netClearance() * 100));
            }
        }
    }

    /**
     * A legal serve: bounce on the server's own half, over the net without touching it, then
     * down on the receiver's half.
     */
    private static void serveIsLegal(Shots shot) {
        World w = new World();
        w.launch(shot.state());

        boolean touchedNet = false;
        double nearZ = Double.NaN, farZ = Double.NaN;
        World.Event seen = null;

        for (int i = 0; i < 480 * 4; i++) {
            w.step();
            World.Event e = w.lastEvent();
            if (e == seen || e == null) continue;
            seen = e;
            if (e.type() == World.EventType.NET) touchedNet = true;
            if (e.type() == World.EventType.TABLE_BOUNCE) {
                if (e.at().z() > 0 && Double.isNaN(nearZ)) nearZ = e.at().z();
                if (e.at().z() < 0 && Double.isNaN(farZ)) farZ = e.at().z();
            }
        }

        check("serve bounces on its own half first: " + shot.name(),
              !Double.isNaN(nearZ), String.format("near bounce at z=%+.2f", nearZ));
        // The detail is printed on pass as well as on fail, so it has to describe what was
        // actually measured -- a PASS reading "(it clipped the cord)" says the opposite of
        // the result it is attached to.
        check("serve clears the net without touching it: " + shot.name(),
              !touchedNet, touchedNet ? "it clipped the cord" : "no net contact");
        check("serve lands on the receiver's half: " + shot.name(),
              !Double.isNaN(farZ) && Math.abs(farZ) < TABLE_LENGTH / 2,
              String.format("far bounce at z=%+.2f", farZ));
    }

    /** Out-of-bounds detection, the other half of "hits the table or goes out". */
    private static void outOfBoundsIsDetected() {
        World w = new World();
        // Fired well wide of the side edge.
        w.launch(BallState.at(new Vec3(0, 0.35, 1.5), new Vec3(-6, 1.0, -9), Vec3.ZERO));

        boolean out = false;
        for (int i = 0; i < 480 * 4; i++) {
            w.step();
            if (w.events().stream().anyMatch(e -> e.type() == World.EventType.OUT_OF_BOUNDS)) {
                out = true;
                break;
            }
        }
        check("a ball missing the table wide is reported out of bounds", out, "");

        // The negative case matters as much as the positive one: an out detector that fires
        // on everything would pass the check above.
        for (Shots shot : Shots.ALL) {
            if (shot.solution() == null) continue;
            Vec3 landing = Aim.landingPoint(shot.state());
            check("aimed shot lands in: " + shot.name(), !landsOut(shot.state()),
                  String.format("landed at x=%+.2f z=%+.2f", landing.x(), landing.z()));
        }
    }

    private static boolean landsOut(BallState s) {
        World w = new World();
        w.launch(s);
        for (int i = 0; i < 480 * 2; i++) {
            w.step();
            if (w.tableBounces() > 0) break;
        }
        return w.events().stream().anyMatch(e -> e.type() == World.EventType.OUT_OF_BOUNDS);
    }

    /**
     * Ten minutes of simulated time with no NaNs, no runaway, no drift. The contract lists
     * "how to keep the simulation from breaking after running a while" as something to learn,
     * so it gets an explicit check rather than a hope.
     */
    private static void longRunStaysStable() {
        World w = new World();
        w.launch(Shots.byName("Topspin loop").state());

        int steps = (int) (600 / DT);
        for (int i = 0; i < steps; i++) {
            w.step();
            if (i % (480 * 12) == 0 && i > 0) w.launch(Shots.byIndex(i / (480 * 12)).state());
        }

        BallState s = w.state();
        check("10 simulated minutes leave the state finite", s.isFinite(), s.pos().toString());
        check("the ball has not escaped the room",
              s.pos().length() < 25, String.format("|pos| = %.2f m", s.pos().length()));
        check("the ball is not moving impossibly fast",
              s.speed() < 60, String.format("%.2f m/s", s.speed()));
        check("the orientation quaternion is still a unit quaternion",
              Math.abs(quatNorm(s.orient()) - 1) < 1e-9,
              String.format("|q| = %.15f", quatNorm(s.orient())));
    }

    /**
     * The hardest shot in the game must not fall through the table.
     *
     * Fired straight DOWN at the surface, deliberately. An angled smash is the wrong test:
     * a 45 m/s ball aimed along the table simply flies off the far end before it descends,
     * so it never bounces and the test would "fail" while the collision code was fine. Firing
     * vertically isolates the thing actually under test, which is whether a ball that moves
     * further in one step than the slab is thick still gets caught.
     */
    private static void noTunnellingAtSmashSpeed() {
        int tested = 0, caught = 0;
        for (double speed = 20; speed <= 60; speed += 2.5) {
            tested++;
            World w = new World();
            w.launch(BallState.at(new Vec3(0, 0.30, -0.5), new Vec3(0, -speed, 0), Vec3.ZERO));
            for (int i = 0; i < 480; i++) {
                w.step();
                if (w.tableBounces() > 0) { caught++; break; }
                if (w.state().pos().y() < -0.3) break;   // it went through
            }
        }
        check("no tunnelling straight down from 20 to 60 m/s (swept collision working)",
              caught == tested, caught + " of " + tested + " speeds bounced");

        // Sanity: at this step size a naive overlap-only test WOULD miss the fast ones, so
        // the check above is not passing for trivial reasons.
        double perStep = 60 * DT;
        check("the fast cases really do outrun a static overlap test",
              perStep > TABLE_THICK + 2 * BALL_R,
              String.format("%.1f cm per step vs a %.1f cm crossing",
                            perStep * 100, (TABLE_THICK + 2 * BALL_R) * 100));
    }

    private static double quatNorm(Quat q) {
        return Math.sqrt(q.w() * q.w() + q.x() * q.x() + q.y() * q.y() + q.z() * q.z());
    }

    /** Where the shot first meets the plane of the table top, on or off the table. */
    private static double firstLandingZ(BallState s) { return Aim.landingPoint(s).z(); }
    private static double firstLandingX(BallState s) { return Aim.landingPoint(s).x(); }

    // ------------------------------------------------------------------ harness

    private static void check(String what, boolean ok, String detail) {
        checks++;
        System.out.printf("  [%s] %s%s%n", ok ? "PASS" : "FAIL", what,
                          detail.isEmpty() ? "" : "  (" + detail + ")");
        if (!ok) failures.add(what + (detail.isEmpty() ? "" : " -> " + detail));
    }
}
