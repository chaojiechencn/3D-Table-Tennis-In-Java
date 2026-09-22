package physics;

import java.util.ArrayList;
import java.util.List;

import static physics.Constants.*;

/** Headless validation of the physics against numbers that did not come from this program. */
public final class SelfTest {

    private static final List<String> failures = new ArrayList<>();
    private static int checks = 0;

    public static void main(String[] args) {
        System.out.println("Mr. Pong - physics validation");
        System.out.println("=".repeat(74));

        reportedConstants();
        terminalVelocityMatchesClosedForm();
        measuredDragMatchesPublishedValues();
        measuredLiftMatchesPublishedValues();
        liftHasACrisisTheOldModelCouldNotShow();
        spinDecayScalesWithAirspeed();
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
        paddleImpartsItsOwnVelocity();
        swingSpeedSplitsIntoPaceAndSpin();
        brushingContactGeneratesTopspin();
        paddleReversesIncomingBackspin();
        paddleContactAddsNoFreeEnergy();
        noTunnellingThroughASwungPaddle();

        System.out.println("=".repeat(74));
        if (failures.isEmpty()) {
            System.out.printf("ALL %d CHECKS PASSED%n", checks);
        } else {
            System.out.printf("%d of %d CHECKS FAILED:%n", failures.size(), checks);
            failures.forEach(f -> System.out.println("  - " + f));
            System.exit(1);
        }
    }

    /** Not a test: print the derived constants so they can be eyeballed against sources. */
    private static void reportedConstants() {
        System.out.printf("  ball        r=%.3f m  m=%.4f kg  I=%.3e kg m^2 (hollow shell, 2/3 m r^2)%n",
                BALL_R, BALL_M, BALL_I);
        System.out.printf("  aero        0.5*rho*A/m = %.4f 1/m%n", HALF_RHO_A_OVER_M);
        System.out.printf("  C_d(no spin)  2.5 m/s %.2f   7.5 %.2f   12.5 %.2f   17.5 %.2f%n",
                Aero.measuredDragCoefficient(2.5, 0), Aero.measuredDragCoefficient(7.5, 0),
                Aero.measuredDragCoefficient(12.5, 0), Aero.measuredDragCoefficient(17.5, 0));
        System.out.printf("  drag at 10 m/s = %.2f m/s^2  (gravity is %.2f - air dominates)%n%n",
                Aero.drag(new Vec3(0, 0, -10)).length(), G);
    }

    /** Terminal velocity. */
    private static void terminalVelocityMatchesClosedForm() {
        // Deliberately flown against a CONSTANT drag coefficient.
        Aero.DragModel constantCd = Aero.DragModel.constant(C_DRAG);
        double analytic = Math.sqrt(G / (HALF_RHO_A_OVER_M * C_DRAG));

        BallState s = BallState.at(new Vec3(0, 500, 0), Vec3.ZERO, Vec3.ZERO);
        for (int i = 0; i < 480 * 30; i++) s = Integrator.step(s, DT, constantCd);
        double simulated = -s.vel().y();

        check("terminal velocity matches closed form",
              Math.abs(simulated - analytic) < 0.01,
              String.format("sim %.3f m/s vs analytic %.3f m/s", simulated, analytic));

        check("terminal velocity is in the published 9.0-9.6 m/s range",
              simulated > 9.0 && simulated < 9.6,
              String.format("%.2f m/s", simulated));
    }

    /**
     * Free fall with drag has a closed form, v(t) = -vt tanh(g t / vt) and
     * y(t) = y0 - (vt^2 / g) ln cosh(g t / vt), which tests integrator and drag together.
     */
    private static void verticalDropMatchesAnalyticSolution() {
        // Constant C_d, for the same reason as above: the tanh / ln cosh solution does not exist
        // for a varying coefficient.
        Aero.DragModel constantCd = Aero.DragModel.constant(C_DRAG);
        double vt = Math.sqrt(G / (HALF_RHO_A_OVER_M * C_DRAG));
        double y0 = 100.0, t = 3.0;

        BallState s = BallState.at(new Vec3(0, y0, 0), Vec3.ZERO, Vec3.ZERO);
        for (int i = 0; i < (int) Math.round(t / DT); i++) s = Integrator.step(s, DT, constantCd);

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
     * The measured drag law, against the values it was built from and against the band every
     * table-tennis-specific study reports.
     */
    private static void measuredDragMatchesPublishedValues() {
        double[][] published = { {2.5, 0.55}, {7.5, 0.49}, {12.5, 0.47}, {17.5, 0.47} };
        boolean allMatch = true;
        StringBuilder got = new StringBuilder();
        for (double[] p : published) {
            double cd = Aero.measuredDragCoefficient(p[0], 0);
            allMatch &= Math.abs(cd - p[1]) < 1e-9;
            got.append(String.format("%.1f:%.2f ", p[0], cd));
        }
        check("C_d reproduces the measured table at zero spin", allMatch, got.toString().trim());

        double lo = 1, hi = 0;
        for (double v = 2; v <= 35; v += 0.5) {
            double cd = Aero.measuredDragCoefficient(v, 0);
            lo = Math.min(lo, cd);
            hi = Math.max(hi, cd);
        }
        check("C_d stays inside the published 0.45-0.55 band over the whole playing range",
              lo >= 0.45 && hi <= 0.55, String.format("%.3f to %.3f over 2-35 m/s", lo, hi));

        check("the old flat C_d = 0.40 was below every published value (why this changed)",
              C_DRAG < lo, String.format("0.40 vs a measured minimum of %.2f", lo));

        // Terminal velocity under the measured law.
        double vt = 9.0;
        for (int i = 0; i < 200; i++) {
            vt = Math.sqrt(G / (HALF_RHO_A_OVER_M * Aero.measuredDragCoefficient(vt, 0)));
        }
        BallState s = BallState.at(new Vec3(0, 500, 0), Vec3.ZERO, Vec3.ZERO);
        for (int i = 0; i < 480 * 30; i++) s = Integrator.step(s, DT);
        double simulated = -s.vel().y();

        check("terminal velocity under the measured drag law matches its own fixed point",
              Math.abs(simulated - vt) < 0.01,
              String.format("sim %.3f m/s vs fixed point %.3f m/s", simulated, vt));

        // Two published claims genuinely conflict here, and the code should say so rather than
        // quietly pick one.
        check("the measured law puts terminal velocity just below the often-quoted 9.0-9.6 band",
              simulated > 8.0 && simulated < 9.0,
              String.format("%.2f m/s; the 9.0-9.6 figure implies C_d = 0.40, so the two "
                          + "published claims cannot be reconciled", simulated));
    }

    /** Lift, converted out of the volume-based published fit into this project's area-based C_L. */
    private static void measuredLiftMatchesPublishedValues() {
        // (speed, spin rad/s, expected C_L), from converting the fitted C_M by C_L = (8/3)C_M*S.
        double[][] cases = { {7.5, 100, 0.206}, {13.5, 300, 0.255},
                             {17.0, 200, 0.177}, {17.0, 650, 0.152} };
        boolean ok = true;
        StringBuilder got = new StringBuilder();
        for (double[] c : cases) {
            double cl = Aero.liftCoefficient(new Vec3(0, 0, -c[0]), new Vec3(-c[1], 0, 0));
            ok &= Math.abs(cl - c[2]) < 0.02;
            got.append(String.format("%.0f/%.0f:%.3f ", c[0], c[1], cl));
        }
        check("C_L matches the measured Magnus fit once converted to the area convention",
              ok, got.toString().trim());

        // The band applies over the MEAT of the spin range.
        double lo = 9, hi = 0;
        for (double sp = 0.25; sp <= 1.4; sp += 0.05) {
            double cl = clAt(sp);
            lo = Math.min(lo, cl);
            hi = Math.max(hi, cl);
        }
        check("C_L stays in the measured 0.15-0.40 band over the meat of the range (S = 0.25-1.4)",
              lo >= 0.15 && hi <= 0.40, String.format("%.3f to %.3f", lo, hi));

        check("no spin means no lift",
              clAt(0.0) == 0 && clAt(0.01) < 0.02,
              String.format("C_L = %.4f at S=0, %.4f at S=0.01", clAt(0.0), clAt(0.01)));

        // The point of the whole change, stated as a claim and measured where it actually bites.
        double worstS = 0.8;
        double measured = clAt(worstS), old = worstS / (2 * worstS + 1);
        check("the old model overstated lift by nearly 2x through the middle of normal play",
              old > measured * 1.6,
              String.format("at S=%.1f: measured %.3f vs old %.3f, a factor of %.2f",
                            worstS, measured, old, old / measured));
    }

    /** The lift crisis: C_L FALLS as spin increases through S ~ 0.5 to 0.8. */
    private static void liftHasACrisisTheOldModelCouldNotShow() {
        double peak = clAt(0.50), trough = clAt(0.80), recovery = clAt(1.10);

        check("C_L falls away between S = 0.5 and S = 0.8 (the measured lift crisis)",
              trough < peak - 0.03,
              String.format("%.3f at S=0.5 -> %.3f at S=0.8", peak, trough));

        check("C_L recovers again above the crisis",
              recovery > trough + 0.03,
              String.format("%.3f at S=0.8 -> %.3f at S=1.1", trough, recovery));

        double oldPeak = 0.50 / (2 * 0.50 + 1), oldTrough = 0.80 / (2 * 0.80 + 1);
        check("the old S/(2S+1) model could not have shown this dip at all",
              oldTrough > oldPeak,
              String.format("old model RISES %.3f -> %.3f across the same range",
                            oldPeak, oldTrough));
    }

    /** C_L at a given spin ratio, at a fixed mid-rally speed. */
    private static double clAt(double spinRatio) {
        return Aero.liftCoefficient(new Vec3(0, 0, -13.5),
                                    new Vec3(-spinRatio * 13.5 / BALL_R, 0, 0));
    }

    /**
     * Spin decay depends on how fast the ball is moving through the air, not only on how fast
     * it is spinning.
     */
    private static void spinDecayScalesWithAirspeed() {
        Vec3 spin = new Vec3(-600, 0, 0);
        double slow = Aero.spinDecay(spin, new Vec3(0, 0, -5)).length();
        double fast = Aero.spinDecay(spin, new Vec3(0, 0, -25)).length();

        check("a fast ball sheds spin faster than a slow one at the same spin rate",
              fast > slow * 4.9 && fast < slow * 5.1,
              String.format("%.1f rad/s^2 at 5 m/s vs %.1f at 25 m/s, for 5x the airspeed",
                            slow, fast));

        // The magnitude is unchanged where it was originally tuned, so this is a fix to the SHAPE
        // of the law, not a silent change to how much spin a rally actually loses.
        double atTypical = Aero.spinDecay(spin, new Vec3(0, 0, -12)).length() / spin.length();
        check("at a typical 12 m/s rally speed it still decays at the tuned 5%/s",
              Math.abs(atTypical - 0.05) < 0.002, String.format("%.4f /s", atTypical));
    }

    /** ITTF bounce test. */
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

        // Report the restitution that was ACTUALLY used, not the intercept of the fit.
        double dropSpeed = Math.sqrt(2 * G * 0.305);
        double eUsed = TABLE_MAT.restitutionAt(dropSpeed);

        check("ball bounced off the table at all", bounced, "");
        check("ITTF drop test: 30.5 cm gives a 24-26 cm rebound",
              peak >= 0.24 && peak <= 0.26,
              String.format("rebound %.1f cm, e = %.3f at the %.2f m/s impact",
                            peak * 100, eUsed, dropSpeed));

        // And show why the restitution is not the textbook sqrt(25/30.5) = 0.905: that value
        // ignores air resistance, and once drag is included it undershoots the ITTF band.
        double naive = Math.sqrt(0.25 / 0.305);
        double naiveRebound = reboundWithRestitution(naive);
        check("the drag-free estimate of e would MISS the ITTF band (this is why e is higher)",
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

    /** RK4 is fourth order, so quartering the step should cut the error by about 256x. */
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

    /** The headline claim of the checkpoint: spin curves the ball. */
    private static void magnusCurvesTheRightWay() {
        // Identical launch, three spins.
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
     * leave slower, with its spin knocked down or reversed.
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
     * and it lands inside the lines.
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
                // A serve clears the cord on its SECOND flight, so it has to be simulated all the
                // way through rather than asked of the launch solution.
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
        // The detail is printed on pass as well as on fail, so it has to describe what was actually
        // measured -- a PASS reading "(it clipped the cord)" says the opposite of the result it is
        // attached to.
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

        // The negative case matters as much as the positive one: an out detector that fires on
        // everything would pass the check above.
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

    /** Ten minutes of simulated time with no NaNs, no runaway, no drift. */
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
        // 40 m, not 25.
        check("the ball has not escaped the room",
              s.pos().length() < 40, String.format("|pos| = %.2f m", s.pos().length()));
        check("the ball is not moving impossibly fast",
              s.speed() < 60, String.format("%.2f m/s", s.speed()));
        check("the orientation quaternion is still a unit quaternion",
              Math.abs(quatNorm(s.orient()) - 1) < 1e-9,
              String.format("|q| = %.15f", quatNorm(s.orient())));
    }

    /** The hardest shot in the game must not fall through the table. */
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

        // Sanity: at this step size a naive overlap-only test WOULD miss the fast ones, so the
        // check above is not passing for trivial reasons.
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


    // Everything below would have FAILED before the contact solver was moved into the surface's
    // frame of reference.

    /** A blade with a closed face, as used for a topspin stroke. */
    private static Vec3 closedFace(double tilt) {
        return new Vec3(0, -tilt, -1).normalized();
    }

    /**
     * Strike a ball with a blade that moves through {@code swing} over one physics step,
     * finishing at {@code endPos}.
     */
    private static BallState paddleStrike(BallState ball, Vec3 endPos, Vec3 swing,
                                          Vec3 normal, Constants.Material mat) {
        Paddle paddle = new Paddle(endPos.minus(swing.scale(DT)), normal);
        paddle.moveTo(endPos, normal, DT);
        Paddle.Blade blade = paddle.collider();

        Contacts.Contact c = Contacts.detect(ball, ball, blade);
        if (c == null) return null;
        return Contacts.respond(ball, blade, c, mat).state();
    }

    /**
     * A blade swung into a STATIONARY ball must send it away at (1 + e) times the blade's own
     * speed.
     */
    private static void paddleImpartsItsOwnVelocity() {
        double swing = 10.0;
        Vec3 n = new Vec3(0, 0, -1);
        BallState ball = BallState.at(new Vec3(0, 0.30, 0), Vec3.ZERO, Vec3.ZERO);

        BallState after = paddleStrike(ball, new Vec3(0, 0.30, 0.025),
                                       new Vec3(0, 0, -swing), n, RACKET_MAT);
        check("a swung blade actually hits a stationary ball", after != null, "");
        if (after == null) return;

        double e = RACKET_MAT.restitutionAt(swing);
        double expected = (1 + e) * swing;
        check("a stationary ball leaves at (1+e) times the blade speed",
              Math.abs(-after.vel().z() - expected) < 0.05,
              String.format("%.2f m/s from a %.0f m/s swing, expected %.2f at e = %.3f",
                            -after.vel().z(), swing, expected, e));

    }

    /** The pace-versus-spin trade-off, against measured players. */
    private static void swingSpeedSplitsIntoPaceAndSpin() {
        double swing = 17.8;
        BallState arriving = BallState.at(new Vec3(0, 0.30, 0), new Vec3(0, 0, 10), Vec3.ZERO);

        BallState loop = brush(arriving, swing, 30);   // 30 degrees up: a looping stroke
        BallState drive = brush(arriving, swing, 0);   // straight through: a drive

        check("a looping brush at a measured swing speed gives the measured loop ball speed",
              loop != null && loop.vel().length() > 18 && loop.vel().length() < 25,
              loop == null ? "no contact"
                    : String.format("%.1f m/s against a measured forehand loop of ~21 m/s",
                                    loop.vel().length()));

        double loopRevs = loop == null ? 0 : -loop.spin().x() / (2 * Math.PI);
        check("and it carries the spin a real loop carries",
              loopRevs > 88 && loopRevs < 150,
              String.format("%.0f rev/s against a measured 117 +/- 29", loopRevs));

        double driveRevs = drive == null ? 0 : -drive.spin().x() / (2 * Math.PI);
        check("the same swing driven flat trades that spin for pace",
              drive != null && drive.vel().length() > loop.vel().length() + 4
                            && driveRevs < loopRevs - 30,
              drive == null ? "no contact"
                    : String.format("flat: %.1f m/s / %.0f rev/s   vs   loop: %.1f m/s / %.0f rev/s",
                                    drive.vel().length(), driveRevs,
                                    loop.vel().length(), loopRevs));
    }

    /** A brushing stroke of a given speed, angled {@code upDeg} above the horizontal. */
    private static BallState brush(BallState ball, double speed, double upDeg) {
        double a = Math.toRadians(upDeg);
        Vec3 swing = new Vec3(0, speed * Math.sin(a), -speed * Math.cos(a));
        return paddleStrike(ball, new Vec3(0, 0.245, 0.0263), swing, closedFace(0.45),
                            RACKET_MAT);
    }

    /** Brushing UP the back of the ball must generate topspin, at a rate a real player reaches. */
    private static void brushingContactGeneratesTopspin() {
        BallState ball = BallState.at(new Vec3(0, 0.30, 0), new Vec3(0, 0, 4), Vec3.ZERO);

        BallState after = paddleStrike(ball, new Vec3(0, 0.245, 0.0263),
                                       new Vec3(0, 12, -9), closedFace(0.45), RACKET_MAT);
        check("an upward brush makes contact", after != null, "");
        if (after == null) return;

        // Ball now heading toward -Z, so topspin is rotation about -X (BallState's convention).
        double topRevs = -after.spin().x() / (2 * Math.PI);
        check("brushing up the back of the ball generates TOPSPIN, not backspin",
              topRevs > 0, String.format("%+.0f rev/s", topRevs));

        check("the spin generated is in the range a real player produces",
              topRevs > 15 && topRevs < 150,
              String.format("%.0f rev/s; skilled topspin forehands measure 117 +/- 29 rev/s "
                          + "and the peer-reviewed ceiling is 150", topRevs));

        check("the brush also sends the ball back down the table",
              after.vel().z() < 0, String.format("%.1f m/s in Z", after.vel().z()));
    }

    /** Heavy BACKSPIN into a brushing blade must come back as TOPSPIN. */
    private static void paddleReversesIncomingBackspin() {
        // A ball arriving with heavy backspin.
        Vec3 backspin = new Vec3(-90 * 2 * Math.PI, 0, 0);
        BallState chop = BallState.at(new Vec3(0, 0.30, 0), new Vec3(0, 0, 5), backspin);

        Vec3 face = closedFace(0.45);
        Vec3 end = new Vec3(0, 0.245, 0.0263);
        Vec3 swing = new Vec3(0, 14, -10);

        BallState rubber = paddleStrike(chop, end, swing, face, RACKET_MAT);
        check("the blade reaches the chopped ball", rubber != null, "");
        if (rubber == null) return;

        // Measured about the axis of the OUTGOING ball, which now travels toward -Z: positive means
        // topspin.
        double inRevs = chop.spin().x() / (2 * Math.PI);
        double outRevs = -rubber.spin().x() / (2 * Math.PI);
        check("heavy backspin comes off an inverted rubber as topspin (spin REVERSAL)",
              outRevs > 0,
              String.format("%.0f rev/s of backspin in -> %+.0f rev/s of topspin out",
                            Math.abs(inRevs), outRevs));

        // Same stroke, rigid surface. It can strip spin, but it cannot reverse it.
        Constants.Material noSpringback = Constants.Material.rigid(
                RACKET_MAT.restitutionAt(0), RACKET_MAT.friction(), 1.0, 1.0);
        BallState rigid = paddleStrike(chop, end, swing, face, noSpringback);
        double rigidRevs = rigid == null ? 0 : -rigid.spin().x() / (2 * Math.PI);
        check("a grip-only surface generates strictly less spin (this is why rubber needs e_t)",
              rigidRevs < outRevs,
              String.format("e_t=0 gives %+.0f rev/s where rubber gives %+.0f",
                            rigidRevs, outRevs));
    }

    /**
     * A paddle is allowed to add energy -- that is what a swing is for -- but only as much as
     * the swing could actually have done.
     */
    private static void paddleContactAddsNoFreeEnergy() {
        Vec3 n = new Vec3(0, 0, -1);
        BallState incoming = BallState.at(new Vec3(0, 0.30, 0), new Vec3(0, 0, 8),
                                          new Vec3(-300, 0, 0));

        // A blade held perfectly still is just a wall.
        BallState off = paddleStrike(incoming, new Vec3(0, 0.30, 0.025), Vec3.ZERO, n,
                                     RACKET_MAT);
        check("a ball into a STATIONARY blade never gains energy",
              off != null && off.kineticEnergy() <= incoming.kineticEnergy() + 1e-12,
              off == null ? "no contact" : String.format("%.6f J -> %.6f J",
                            incoming.kineticEnergy(), off.kineticEnergy()));

        // A swung blade may add energy, but not more than (1+e)*u + |v_in| allows.
        double swing = 15.0, arriving = 6.0;
        BallState ball = BallState.at(new Vec3(0, 0.30, 0), new Vec3(0, 0, arriving), Vec3.ZERO);
        BallState hit = paddleStrike(ball, new Vec3(0, 0.30, 0.025),
                                     new Vec3(0, 0, -swing), n, RACKET_MAT);
        double limit = (1 + RACKET_MAT.restitutionAt(swing + arriving)) * swing + arriving;
        check("a swung blade cannot send the ball faster than its own swing allows",
              hit != null && hit.vel().length() <= limit + 1e-9,
              hit == null ? "no contact" : String.format("%.2f m/s against a limit of %.2f",
                            hit.vel().length(), limit));
    }

    /**
     * The paddle equivalent of the table tunnelling check, and a harder problem than the table
     * was.
     */
    private static void noTunnellingThroughASwungPaddle() {
        int caught = 0, tried = 0;
        double blade = 20.0;

        for (double speed = 20; speed <= 60.01; speed += 2.5) {
            tried++;
            BallState ball = BallState.at(new Vec3(0, 0.30, 0), new Vec3(0, 0, speed), Vec3.ZERO);
            BallState flown = Integrator.step(ball, DT);

            // Put the blade half way along the RELATIVE sweep, so the crossing is mid-step at every
            // speed rather than only at the one the geometry happened to suit.
            double meet = (speed * DT - blade * DT) / 2;
            Paddle paddle = new Paddle(new Vec3(0, 0.30, meet + blade * DT), new Vec3(0, 0, -1));
            paddle.moveTo(new Vec3(0, 0.30, meet), new Vec3(0, 0, -1), DT);

            if (Contacts.detect(ball, flown, paddle.collider()) != null) caught++;
        }
        check("no tunnelling through a swung paddle from 20 to 60 m/s",
              caught == tried,
              String.format("%d of %d speeds caught, closing at up to %.0f m/s",
                            caught, tried, 60 + blade));
    }

    private static void check(String what, boolean ok, String detail) {
        checks++;
        System.out.printf("  [%s] %s%s%n", ok ? "PASS" : "FAIL", what,
                          detail.isEmpty() ? "" : "  (" + detail + ")");
        if (!ok) failures.add(what + (detail.isEmpty() ? "" : " -> " + detail));
    }
}
