package tabletennis.engine;

import tabletennis.game.Shots;

import java.util.ArrayList;
import java.util.List;

import static tabletennis.engine.Constants.*;

/** Headless validation of the physics against numbers that did not come from this program. */
public final class SelfTest {

    private static final List<String> Failures = new ArrayList<>();
    private static int Checks = 0;

    public static void main(String[] Args) {
        System.out.println("Mr. Pong - physics validation");
        System.out.println("=".repeat(74));

        ReportedConstants();
        TerminalVelocityMatchesClosedForm();
        MeasuredDragMatchesPublishedValues();
        MeasuredLiftMatchesPublishedValues();
        LiftHasACrisisTheOldModelCouldNotShow();
        SpinDecayScalesWithAirspeed();
        VerticalDropMatchesAnalyticSolution();
        IttfDropTest();
        Rk4IsFourthOrder();
        MagnusCurvesTheRightWay();
        SidespinDeflectsTheRightWay();
        BounceNeverAddsEnergy();
        TopspinKicksForwardBackspinChecks();
        NetKillsTheBall();
        EveryAimedShotIsLegal();
        OutOfBoundsIsDetected();
        LongRunStaysStable();
        NoTunnellingAtSmashSpeed();
        PaddleImpartsItsOwnVelocity();
        SwingSpeedSplitsIntoPaceAndSpin();
        BrushingContactGeneratesTopspin();
        PaddleReversesIncomingBackspin();
        PaddleContactAddsNoFreeEnergy();
        NoTunnellingThroughASwungPaddle();

        System.out.println("=".repeat(74));
        if (Failures.isEmpty()) {
            System.out.printf("ALL %d CHECKS PASSED%n", Checks);
        } else {
            System.out.printf("%d of %d CHECKS FAILED:%n", Failures.size(), Checks);
            Failures.forEach(F -> System.out.println("  - " + F));
            System.exit(1);
        }
    }

    /** Not a test: print the derived constants so they can be eyeballed against sources. */
    private static void ReportedConstants() {
        System.out.printf("  ball        r=%.3f m  m=%.4f kg  I=%.3e kg m^2 (hollow shell, 2/3 m r^2)%n",
                BallR, BallM, BallI);
        System.out.printf("  aero        0.5*rho*A/m = %.4f 1/m%n", HalfRhoAOverM);
        System.out.printf("  C_d(no spin)  2.5 m/s %.2f   7.5 %.2f   12.5 %.2f   17.5 %.2f%n",
                Aero.MeasuredDragCoefficient(2.5, 0), Aero.MeasuredDragCoefficient(7.5, 0),
                Aero.MeasuredDragCoefficient(12.5, 0), Aero.MeasuredDragCoefficient(17.5, 0));
        System.out.printf("  drag at 10 m/s = %.2f m/s^2  (gravity is %.2f - air dominates)%n%n",
                Aero.Drag(new Vec3(0, 0, -10)).Length(), G);
    }

    /** Terminal velocity. */
    private static void TerminalVelocityMatchesClosedForm() {
        // Deliberately flown against a CONSTANT drag coefficient.
        Aero.DragModel ConstantCd = Aero.DragModel.Constant(CDrag);
        double Analytic = Math.sqrt(G / (HalfRhoAOverM * CDrag));

        BallState S = BallState.At(new Vec3(0, 500, 0), Vec3.Zero, Vec3.Zero);
        for (int I = 0; I < 480 * 30; I++) S = Integrator.Step(S, Dt, ConstantCd);
        double Simulated = -S.Vel().Y();

        Check("terminal velocity matches closed form",
              Math.abs(Simulated - Analytic) < 0.01,
              String.format("sim %.3f m/s vs analytic %.3f m/s", Simulated, Analytic));

        Check("terminal velocity is in the published 9.0-9.6 m/s range",
              Simulated > 9.0 && Simulated < 9.6,
              String.format("%.2f m/s", Simulated));
    }

    /**
     * Free fall with drag has a closed form, v(t) = -vt tanh(g t / vt) and
     * y(t) = y0 - (vt^2 / g) ln cosh(g t / vt), which tests integrator and drag together.
     */
    private static void VerticalDropMatchesAnalyticSolution() {
        // Constant C_d, for the same reason as above: the tanh / ln cosh solution does not exist
        // for a varying coefficient.
        Aero.DragModel ConstantCd = Aero.DragModel.Constant(CDrag);
        double Vt = Math.sqrt(G / (HalfRhoAOverM * CDrag));
        double Y0 = 100.0, T = 3.0;

        BallState S = BallState.At(new Vec3(0, Y0, 0), Vec3.Zero, Vec3.Zero);
        for (int I = 0; I < (int) Math.round(T / Dt); I++) S = Integrator.Step(S, Dt, ConstantCd);

        double YExact = Y0 - (Vt * Vt / G) * Math.log(Math.cosh(G * T / Vt));
        double VExact = -Vt * Math.tanh(G * T / Vt);

        Check("drop position matches analytic solution to 1 mm over 3 s",
              Math.abs(S.Pos().Y() - YExact) < 1e-3,
              String.format("sim %.6f m vs exact %.6f m", S.Pos().Y(), YExact));

        Check("drop velocity matches analytic solution to 1 mm/s over 3 s",
              Math.abs(S.Vel().Y() - VExact) < 1e-3,
              String.format("sim %.6f m/s vs exact %.6f m/s", S.Vel().Y(), VExact));
    }


    /**
     * The measured drag law, against the values it was built from and against the band every
     * table-tennis-specific study reports.
     */
    private static void MeasuredDragMatchesPublishedValues() {
        double[][] Published = { {2.5, 0.55}, {7.5, 0.49}, {12.5, 0.47}, {17.5, 0.47} };
        boolean AllMatch = true;
        StringBuilder Got = new StringBuilder();
        for (double[] P : Published) {
            double Cd = Aero.MeasuredDragCoefficient(P[0], 0);
            AllMatch &= Math.abs(Cd - P[1]) < 1e-9;
            Got.append(String.format("%.1f:%.2f ", P[0], Cd));
        }
        Check("C_d reproduces the measured table at zero spin", AllMatch, Got.toString().trim());

        double Lo = 1, Hi = 0;
        for (double V = 2; V <= 35; V += 0.5) {
            double Cd = Aero.MeasuredDragCoefficient(V, 0);
            Lo = Math.min(Lo, Cd);
            Hi = Math.max(Hi, Cd);
        }
        Check("C_d stays inside the published 0.45-0.55 band over the whole playing range",
              Lo >= 0.45 && Hi <= 0.55, String.format("%.3f to %.3f over 2-35 m/s", Lo, Hi));

        Check("the old flat C_d = 0.40 was below every published value (why this changed)",
              CDrag < Lo, String.format("0.40 vs a measured minimum of %.2f", Lo));

        // Terminal velocity under the measured law.
        double Vt = 9.0;
        for (int I = 0; I < 200; I++) {
            Vt = Math.sqrt(G / (HalfRhoAOverM * Aero.MeasuredDragCoefficient(Vt, 0)));
        }
        BallState S = BallState.At(new Vec3(0, 500, 0), Vec3.Zero, Vec3.Zero);
        for (int I = 0; I < 480 * 30; I++) S = Integrator.Step(S, Dt);
        double Simulated = -S.Vel().Y();

        Check("terminal velocity under the measured drag law matches its own fixed point",
              Math.abs(Simulated - Vt) < 0.01,
              String.format("sim %.3f m/s vs fixed point %.3f m/s", Simulated, Vt));

        // Two published claims genuinely conflict here, and the code should say so rather than
        // quietly pick one.
        Check("the measured law puts terminal velocity just below the often-quoted 9.0-9.6 band",
              Simulated > 8.0 && Simulated < 9.0,
              String.format("%.2f m/s; the 9.0-9.6 figure implies C_d = 0.40, so the two "
                          + "published claims cannot be reconciled", Simulated));
    }

    /** Lift, converted out of the volume-based published fit into this project's area-based C_L. */
    private static void MeasuredLiftMatchesPublishedValues() {
        // (speed, spin rad/s, expected C_L), from converting the fitted C_M by C_L = (8/3)C_M*S.
        double[][] Cases = { {7.5, 100, 0.206}, {13.5, 300, 0.255},
                             {17.0, 200, 0.177}, {17.0, 650, 0.152} };
        boolean Ok = true;
        StringBuilder Got = new StringBuilder();
        for (double[] C : Cases) {
            double Cl = Aero.LiftCoefficient(new Vec3(0, 0, -C[0]), new Vec3(-C[1], 0, 0));
            Ok &= Math.abs(Cl - C[2]) < 0.02;
            Got.append(String.format("%.0f/%.0f:%.3f ", C[0], C[1], Cl));
        }
        Check("C_L matches the measured Magnus fit once converted to the area convention",
              Ok, Got.toString().trim());

        // The band applies over the MEAT of the spin range.
        double Lo = 9, Hi = 0;
        for (double Sp = 0.25; Sp <= 1.4; Sp += 0.05) {
            double Cl = ClAt(Sp);
            Lo = Math.min(Lo, Cl);
            Hi = Math.max(Hi, Cl);
        }
        Check("C_L stays in the measured 0.15-0.40 band over the meat of the range (S = 0.25-1.4)",
              Lo >= 0.15 && Hi <= 0.40, String.format("%.3f to %.3f", Lo, Hi));

        Check("no spin means no lift",
              ClAt(0.0) == 0 && ClAt(0.01) < 0.02,
              String.format("C_L = %.4f at S=0, %.4f at S=0.01", ClAt(0.0), ClAt(0.01)));

        // The point of the whole change, stated as a claim and measured where it actually bites.
        double WorstS = 0.8;
        double Measured = ClAt(WorstS), Old = WorstS / (2 * WorstS + 1);
        Check("the old model overstated lift by nearly 2x through the middle of normal play",
              Old > Measured * 1.6,
              String.format("at S=%.1f: measured %.3f vs old %.3f, a factor of %.2f",
                            WorstS, Measured, Old, Old / Measured));
    }

    /** The lift crisis: C_L FALLS as spin increases through S ~ 0.5 to 0.8. */
    private static void LiftHasACrisisTheOldModelCouldNotShow() {
        double Peak = ClAt(0.50), Trough = ClAt(0.80), Recovery = ClAt(1.10);

        Check("C_L falls away between S = 0.5 and S = 0.8 (the measured lift crisis)",
              Trough < Peak - 0.03,
              String.format("%.3f at S=0.5 -> %.3f at S=0.8", Peak, Trough));

        Check("C_L recovers again above the crisis",
              Recovery > Trough + 0.03,
              String.format("%.3f at S=0.8 -> %.3f at S=1.1", Trough, Recovery));

        double OldPeak = 0.50 / (2 * 0.50 + 1), OldTrough = 0.80 / (2 * 0.80 + 1);
        Check("the old S/(2S+1) model could not have shown this dip at all",
              OldTrough > OldPeak,
              String.format("old model RISES %.3f -> %.3f across the same range",
                            OldPeak, OldTrough));
    }

    /** C_L at a given spin ratio, at a fixed mid-rally speed. */
    private static double ClAt(double SpinRatio) {
        return Aero.LiftCoefficient(new Vec3(0, 0, -13.5),
                                    new Vec3(-SpinRatio * 13.5 / BallR, 0, 0));
    }

    /**
     * Spin decay depends on how fast the ball is moving through the air, not only on how fast
     * it is spinning.
     */
    private static void SpinDecayScalesWithAirspeed() {
        Vec3 Spin = new Vec3(-600, 0, 0);
        double Slow = Aero.SpinDecay(Spin, new Vec3(0, 0, -5)).Length();
        double Fast = Aero.SpinDecay(Spin, new Vec3(0, 0, -25)).Length();

        Check("a fast ball sheds spin faster than a slow one at the same spin rate",
              Fast > Slow * 4.9 && Fast < Slow * 5.1,
              String.format("%.1f rad/s^2 at 5 m/s vs %.1f at 25 m/s, for 5x the airspeed",
                            Slow, Fast));

        // The magnitude is unchanged where it was originally tuned, so this is a fix to the SHAPE
        // of the law, not a silent change to how much spin a rally actually loses.
        double AtTypical = Aero.SpinDecay(Spin, new Vec3(0, 0, -12)).Length() / Spin.Length();
        Check("at a typical 12 m/s rally speed it still decays at the tuned 5%/s",
              Math.abs(AtTypical - 0.05) < 0.002, String.format("%.4f /s", AtTypical));
    }

    /** ITTF bounce test. */
    private static void IttfDropTest() {
        World W = new World();
        W.Launch(BallState.At(new Vec3(0, 0.305 + BallR, -0.7), Vec3.Zero, Vec3.Zero));

        boolean Bounced = false;
        double Peak = 0;
        for (int I = 0; I < 480 * 3; I++) {
            W.Step();
            if (W.TableBounces() > 0) {
                if (!Bounced) { Bounced = true; Peak = 0; }
                Peak = Math.max(Peak, W.State().Pos().Y() - BallR);
                if (W.TableBounces() > 1) break;
            }
        }

        // Report the restitution that was ACTUALLY used, not the intercept of the fit.
        double DropSpeed = Math.sqrt(2 * G * 0.305);
        double EUsed = TableMat.RestitutionAt(DropSpeed);

        Check("ball bounced off the table at all", Bounced, "");
        Check("ITTF drop test: 30.5 cm gives a 24-26 cm rebound",
              Peak >= 0.24 && Peak <= 0.26,
              String.format("rebound %.1f cm, e = %.3f at the %.2f m/s impact",
                            Peak * 100, EUsed, DropSpeed));

        // And show why the restitution is not the textbook sqrt(25/30.5) = 0.905: that value
        // ignores air resistance, and once drag is included it undershoots the ITTF band.
        double Naive = Math.sqrt(0.25 / 0.305);
        double NaiveRebound = ReboundWithRestitution(Naive);
        Check("the drag-free estimate of e would MISS the ITTF band (this is why e is higher)",
              NaiveRebound < 0.24,
              String.format("e = %.3f gives only %.1f cm", Naive, NaiveRebound * 100));
    }

    /** Rebound height from the ITTF drop, for an arbitrary restitution. */
    private static double ReboundWithRestitution(double E) {
        BallState S = BallState.At(new Vec3(0, 0.305 + BallR, -0.7), Vec3.Zero, Vec3.Zero);
        while (S.Pos().Y() > BallR) S = Integrator.Step(S, Dt);
        S = S.WithVel(new Vec3(0, -S.Vel().Y() * E, 0)).WithPos(new Vec3(0, BallR, -0.7));

        double Peak = 0;
        while (S.Vel().Y() > 0) {
            S = Integrator.Step(S, Dt);
            Peak = Math.max(Peak, S.Pos().Y() - BallR);
        }
        return Peak;
    }

    /** RK4 is fourth order, so quartering the step should cut the error by about 256x. */
    private static void Rk4IsFourthOrder() {
        BallState Start = Shots.ByName("Topspin loop").State();   // drag and Magnus both active
        double T = 0.4;

        Vec3 Coarse = IntegrateFor(Start, T, Dt);
        Vec3 Medium = IntegrateFor(Start, T, Dt / 4);
        Vec3 Fine   = IntegrateFor(Start, T, Dt / 16);

        double ErrCoarse = Coarse.Minus(Fine).Length();
        double ErrMedium = Medium.Minus(Fine).Length();
        double Ratio = ErrMedium < 1e-15 ? Double.POSITIVE_INFINITY : ErrCoarse / ErrMedium;

        Check("halving-the-step error ratio indicates 4th order (expect >= 100x per 4x)",
              Ratio > 100,
              String.format("error shrank %.0fx for a 4x smaller step", Ratio));

        Check("one physics step at DT is already accurate to under 0.1 mm over 0.4 s",
              ErrCoarse < 1e-4,
              String.format("%.3e m", ErrCoarse));
    }

    private static Vec3 IntegrateFor(BallState S, double Seconds, double Dt) {
        int Steps = (int) Math.round(Seconds / Dt);
        for (int I = 0; I < Steps; I++) S = Integrator.Step(S, Dt);
        return S.Pos();
    }

    /** The headline claim of the checkpoint: spin curves the ball. */
    private static void MagnusCurvesTheRightWay() {
        // Identical launch, three spins.
        Vec3 Pos = new Vec3(0, 0.30, 1.50), Vel = new Vec3(0, 0.4, -9.0);
        double Heavy = 90 * 2 * Math.PI;

        double Flat = FirstLandingZ(BallState.At(Pos, Vel, Vec3.Zero));
        double Top  = FirstLandingZ(BallState.At(Pos, Vel, new Vec3(-Heavy, 0, 0)));
        double Back = FirstLandingZ(BallState.At(Pos, Vel, new Vec3(Heavy, 0, 0)));

        // Travelling toward -Z, so "shorter" means a LARGER (less negative) landing z.
        Check("topspin lands shorter than no spin",
              Top > Flat + 0.05,
              String.format("topspin z=%.3f vs flat z=%.3f (%.0f cm shorter)",
                            Top, Flat, (Top - Flat) * 100));

        Check("backspin carries further than no spin",
              Back < Flat - 0.05,
              String.format("backspin z=%.3f vs flat z=%.3f (%.0f cm longer)",
                            Back, Flat, (Flat - Back) * 100));

        Check("the topspin/backspin spread is large enough to see on screen",
              (Top - Back) > 0.30,
              String.format("%.0f cm apart", (Top - Back) * 100));
    }

    /** Sidespin about +Y must push the ball toward -X. Verified against the cross product. */
    private static void SidespinDeflectsTheRightWay() {
        Vec3 Pos = new Vec3(0, 0.30, 1.50), Vel = new Vec3(0, 0.4, -9.0);
        double Spin = 90 * 2 * Math.PI;   // measured at the table plane, as above

        double Left  = FirstLandingX(BallState.At(Pos, Vel, new Vec3(0, Spin, 0)));
        double Right = FirstLandingX(BallState.At(Pos, Vel, new Vec3(0, -Spin, 0)));

        Check("sidespin about +Y deflects toward -X",
              Left < -0.02, String.format("landed x=%.3f m", Left));
        Check("sidespin about -Y deflects toward +X",
              Right > 0.02, String.format("landed x=%.3f m", Right));
        Check("the two sidespins are mirror images",
              Math.abs(Left + Right) < 1e-6,
              String.format("%.4f vs %.4f", Left, Right));
    }

    /** No contact may ever add kinetic energy. This is what stops a simulation exploding. */
    private static void BounceNeverAddsEnergy() {
        World W = new World();
        W.Launch(Shots.ByName("Topspin loop").State());

        double Worst = 0;
        double Prev = W.State().KineticEnergy();
        for (int I = 0; I < 480 * 20; I++) {
            W.Step();
            double Now = W.State().KineticEnergy();
            // Gravity legitimately adds KE during free fall, so only judge the steps where a
            // contact happened: those are the ones with a sudden jump.
            double Gain = Now - Prev;
            double GravityBudget = BallM * G * Math.abs(W.State().Vel().Y()) * Dt * 1.5 + 1e-9;
            if (Gain > GravityBudget) Worst = Math.max(Worst, Gain - GravityBudget);
            Prev = Now;
        }
        Check("no contact adds kinetic energy over a 20 s rally",
              Worst < 1e-6, String.format("worst unexplained gain %.3e J", Worst));
    }

    /**
     * The spin coupling, stated as a falsifiable claim: a topspin ball must leave the bounce
     * FASTER along its direction of travel than it arrived, and a heavy backspin ball must
     * leave slower, with its spin knocked down or reversed.
     */
    private static void TopspinKicksForwardBackspinChecks() {
        double Spin = 110 * 2 * Math.PI;
        Vec3 Pos = new Vec3(0, 0.25, 0.5), Vel = new Vec3(0, -3.0, -10);

        double[] Top  = BounceChange(BallState.At(Pos, Vel, new Vec3(-Spin, 0, 0)));
        double[] Back = BounceChange(BallState.At(Pos, Vel, new Vec3(Spin, 0, 0)));

        Check("topspin gains forward speed off the bounce",
              Top[0] > 0.2, String.format("forward speed %+.2f m/s", Top[0]));
        Check("backspin loses forward speed off the bounce",
              Back[0] < -0.2, String.format("forward speed %+.2f m/s", Back[0]));
        Check("the bounce reduces backspin (friction fights it)",
              Back[1] < -1.0, String.format("spin change %+.0f rad/s", Back[1]));
        Check("topspin and backspin behave oppositely off the same table",
              Top[0] * Back[0] < 0, "");
    }

    /** @return {change in forward speed, change in x-spin} across the first table bounce. */
    private static double[] BounceChange(BallState Start) {
        World W = new World();
        W.Launch(Start);
        BallState Before = Start;
        for (int I = 0; I < 480 * 3; I++) {
            BallState Prev = W.State();
            W.Step();
            if (W.TableBounces() > 0) { Before = Prev; break; }
        }
        for (int I = 0; I < 6; I++) W.Step();      // let it clear the surface
        BallState After = W.State();
        return new double[] { -After.Vel().Z() - (-Before.Vel().Z()),
                              After.Spin().X() - Before.Spin().X() };
    }

    /** A ball driven into the net must not bounce back off it like a wall. */
    private static void NetKillsTheBall() {
        World W = new World();
        W.Launch(Shots.ByName("Into the net").State());

        boolean HitNet = false;
        double SpeedAfter = 0;
        for (int I = 0; I < 480 * 3; I++) {
            W.Step();
            if (!HitNet && W.Events().stream().anyMatch(E -> E.Type() == World.EventType.Net)) {
                HitNet = true;
                SpeedAfter = W.State().Speed();
            }
        }
        Check("the net shot actually reaches the net", HitNet, "");
        Check("the net kills most of the speed",
              HitNet && SpeedAfter < 4.0, String.format("%.2f m/s leaving the net", SpeedAfter));
        Check("the ball ends up on the near side of the net",
              W.State().Pos().Z() > -0.05,
              String.format("final z=%.3f m", W.State().Pos().Z()));
    }

    /**
     * Every aimed preset must actually be playable: the solver converged, it clears the net,
     * and it lands inside the lines.
     */
    private static void EveryAimedShotIsLegal() {
        for (Shots Shot : Shots.All) {
            if (Shot.AimSolution() == null) continue;
            Aim.Solution Sol = Shot.AimSolution();

            Check("aim solver converged: " + Shot.Name(), Sol.Converged(),
                  String.format("elevation %+.1f deg", Sol.ElevationDeg()));

            Vec3 Land = Sol.Landing();
            Check("first bounce is inside the lines: " + Shot.Name(),
                  Math.abs(Land.X()) < TableWidth / 2 && Math.abs(Land.Z()) < TableLength / 2,
                  String.format("x=%+.2f z=%+.2f", Land.X(), Land.Z()));

            if (Shot.IsServe()) {
                // A serve clears the cord on its SECOND flight, so it has to be simulated all the
                // way through rather than asked of the launch solution.
                ServeIsLegal(Shot);
            } else {
                Check("clears the net: " + Shot.Name(), Sol.NetClearance() > 0.01,
                      String.format("%+.1f cm over the cord", Sol.NetClearance() * 100));
            }
        }
    }

    /**
     * A legal serve: bounce on the server's own half, over the net without touching it, then
     * down on the receiver's half.
     */
    private static void ServeIsLegal(Shots Shot) {
        World W = new World();
        W.Launch(Shot.State());

        boolean TouchedNet = false;
        double NearZ = Double.NaN, FarZ = Double.NaN;
        World.Event Seen = null;

        for (int I = 0; I < 480 * 4; I++) {
            W.Step();
            World.Event E = W.LastEvent();
            if (E == Seen || E == null) continue;
            Seen = E;
            if (E.Type() == World.EventType.Net) TouchedNet = true;
            if (E.Type() == World.EventType.TableBounce) {
                if (E.At().Z() > 0 && Double.isNaN(NearZ)) NearZ = E.At().Z();
                if (E.At().Z() < 0 && Double.isNaN(FarZ)) FarZ = E.At().Z();
            }
        }

        Check("serve bounces on its own half first: " + Shot.Name(),
              !Double.isNaN(NearZ), String.format("near bounce at z=%+.2f", NearZ));
        // The detail is printed on pass as well as on fail, so it has to describe what was actually
        // measured -- a PASS reading "(it clipped the cord)" says the opposite of the result it is
        // attached to.
        Check("serve clears the net without touching it: " + Shot.Name(),
              !TouchedNet, TouchedNet ? "it clipped the cord" : "no net contact");
        Check("serve lands on the receiver's half: " + Shot.Name(),
              !Double.isNaN(FarZ) && Math.abs(FarZ) < TableLength / 2,
              String.format("far bounce at z=%+.2f", FarZ));
    }

    /** Out-of-bounds detection, the other half of "hits the table or goes out". */
    private static void OutOfBoundsIsDetected() {
        World W = new World();
        // Fired well wide of the side edge.
        W.Launch(BallState.At(new Vec3(0, 0.35, 1.5), new Vec3(-6, 1.0, -9), Vec3.Zero));

        boolean Out = false;
        for (int I = 0; I < 480 * 4; I++) {
            W.Step();
            if (W.Events().stream().anyMatch(E -> E.Type() == World.EventType.OutOfBounds)) {
                Out = true;
                break;
            }
        }
        Check("a ball missing the table wide is reported out of bounds", Out, "");

        // The negative case matters as much as the positive one: an out detector that fires on
        // everything would pass the check above.
        for (Shots Shot : Shots.All) {
            if (Shot.AimSolution() == null) continue;
            Vec3 Landing = Aim.LandingPoint(Shot.State());
            Check("aimed shot lands in: " + Shot.Name(), !LandsOut(Shot.State()),
                  String.format("landed at x=%+.2f z=%+.2f", Landing.X(), Landing.Z()));
        }
    }

    private static boolean LandsOut(BallState S) {
        World W = new World();
        W.Launch(S);
        for (int I = 0; I < 480 * 2; I++) {
            W.Step();
            if (W.TableBounces() > 0) break;
        }
        return W.Events().stream().anyMatch(E -> E.Type() == World.EventType.OutOfBounds);
    }

    /** Ten minutes of simulated time with no NaNs, no runaway, no drift. */
    private static void LongRunStaysStable() {
        World W = new World();
        W.Launch(Shots.ByName("Topspin loop").State());

        int Steps = (int) (600 / Dt);
        for (int I = 0; I < Steps; I++) {
            W.Step();
            if (I % (480 * 12) == 0 && I > 0) W.Launch(Shots.ByIndex(I / (480 * 12)).State());
        }

        BallState S = W.State();
        Check("10 simulated minutes leave the state finite", S.IsFinite(), S.Pos().toString());
        // 40 m, not 25.
        Check("the ball has not escaped the room",
              S.Pos().Length() < 40, String.format("|pos| = %.2f m", S.Pos().Length()));
        Check("the ball is not moving impossibly fast",
              S.Speed() < 60, String.format("%.2f m/s", S.Speed()));
        Check("the orientation quaternion is still a unit quaternion",
              Math.abs(QuatNorm(S.Orient()) - 1) < 1e-9,
              String.format("|q| = %.15f", QuatNorm(S.Orient())));
    }

    /** The hardest shot in the game must not fall through the table. */
    private static void NoTunnellingAtSmashSpeed() {
        int Tested = 0, Caught = 0;
        for (double Speed = 20; Speed <= 60; Speed += 2.5) {
            Tested++;
            World W = new World();
            W.Launch(BallState.At(new Vec3(0, 0.30, -0.5), new Vec3(0, -Speed, 0), Vec3.Zero));
            for (int I = 0; I < 480; I++) {
                W.Step();
                if (W.TableBounces() > 0) { Caught++; break; }
                if (W.State().Pos().Y() < -0.3) break;   // it went through
            }
        }
        Check("no tunnelling straight down from 20 to 60 m/s (swept collision working)",
              Caught == Tested, Caught + " of " + Tested + " speeds bounced");

        // Sanity: at this step size a naive overlap-only test WOULD miss the fast ones, so the
        // check above is not passing for trivial reasons.
        double PerStep = 60 * Dt;
        Check("the fast cases really do outrun a static overlap test",
              PerStep > TableThick + 2 * BallR,
              String.format("%.1f cm per step vs a %.1f cm crossing",
                            PerStep * 100, (TableThick + 2 * BallR) * 100));
    }

    private static double QuatNorm(Quat Q) {
        return Math.sqrt(Q.W() * Q.W() + Q.X() * Q.X() + Q.Y() * Q.Y() + Q.Z() * Q.Z());
    }

    /** Where the shot first meets the plane of the table top, on or off the table. */
    private static double FirstLandingZ(BallState S) { return Aim.LandingPoint(S).Z(); }
    private static double FirstLandingX(BallState S) { return Aim.LandingPoint(S).X(); }


    // Everything below would have FAILED before the contact solver was moved into the surface's
    // frame of reference.

    /** A blade with a closed face, as used for a topspin stroke. */
    private static Vec3 ClosedFace(double Tilt) {
        return new Vec3(0, -Tilt, -1).Normalized();
    }

    /**
     * Strike a ball with a blade that moves through {@code swing} over one physics step,
     * finishing at {@code endPos}.
     */
    private static BallState PaddleStrike(BallState Ball, Vec3 EndPos, Vec3 Swing,
                                          Vec3 Normal, Constants.Material Mat) {
        Paddle Racket = new Paddle(EndPos.Minus(Swing.Scale(Dt)), Normal);
        Racket.MoveTo(EndPos, Normal, Dt);
        Paddle.Blade BladeShape = Racket.Collider();

        Contacts.Contact C = Contacts.Detect(Ball, Ball, BladeShape);
        if (C == null) return null;
        return Contacts.Respond(Ball, BladeShape, C, Mat).State();
    }

    /**
     * A blade swung into a STATIONARY ball must send it away at (1 + e) times the blade's own
     * speed.
     */
    private static void PaddleImpartsItsOwnVelocity() {
        double Swing = 10.0;
        Vec3 N = new Vec3(0, 0, -1);
        BallState Ball = BallState.At(new Vec3(0, 0.30, 0), Vec3.Zero, Vec3.Zero);

        BallState After = PaddleStrike(Ball, new Vec3(0, 0.30, 0.025),
                                       new Vec3(0, 0, -Swing), N, RacketMat);
        Check("a swung blade actually hits a stationary ball", After != null, "");
        if (After == null) return;

        double E = RacketMat.RestitutionAt(Swing);
        double Expected = (1 + E) * Swing;
        Check("a stationary ball leaves at (1+e) times the blade speed",
              Math.abs(-After.Vel().Z() - Expected) < 0.05,
              String.format("%.2f m/s from a %.0f m/s swing, expected %.2f at e = %.3f",
                            -After.Vel().Z(), Swing, Expected, E));

    }

    /** The pace-versus-spin trade-off, against measured players. */
    private static void SwingSpeedSplitsIntoPaceAndSpin() {
        double Swing = 17.8;
        BallState Arriving = BallState.At(new Vec3(0, 0.30, 0), new Vec3(0, 0, 10), Vec3.Zero);

        BallState Loop = Brush(Arriving, Swing, 30);   // 30 degrees up: a looping stroke
        BallState Drive = Brush(Arriving, Swing, 0);   // straight through: a drive

        Check("a looping brush at a measured swing speed gives the measured loop ball speed",
              Loop != null && Loop.Vel().Length() > 18 && Loop.Vel().Length() < 25,
              Loop == null ? "no contact"
                    : String.format("%.1f m/s against a measured forehand loop of ~21 m/s",
                                    Loop.Vel().Length()));

        double LoopRevs = Loop == null ? 0 : -Loop.Spin().X() / (2 * Math.PI);
        Check("and it carries the spin a real loop carries",
              LoopRevs > 88 && LoopRevs < 150,
              String.format("%.0f rev/s against a measured 117 +/- 29", LoopRevs));

        double DriveRevs = Drive == null ? 0 : -Drive.Spin().X() / (2 * Math.PI);
        Check("the same swing driven flat trades that spin for pace",
              Drive != null && Drive.Vel().Length() > Loop.Vel().Length() + 4
                            && DriveRevs < LoopRevs - 30,
              Drive == null ? "no contact"
                    : String.format("flat: %.1f m/s / %.0f rev/s   vs   loop: %.1f m/s / %.0f rev/s",
                                    Drive.Vel().Length(), DriveRevs,
                                    Loop.Vel().Length(), LoopRevs));
    }

    /** A brushing stroke of a given speed, angled {@code upDeg} above the horizontal. */
    private static BallState Brush(BallState Ball, double Speed, double UpDeg) {
        double A = Math.toRadians(UpDeg);
        Vec3 Swing = new Vec3(0, Speed * Math.sin(A), -Speed * Math.cos(A));
        return PaddleStrike(Ball, new Vec3(0, 0.245, 0.0263), Swing, ClosedFace(0.45),
                            RacketMat);
    }

    /** Brushing UP the back of the ball must generate topspin, at a rate a real player reaches. */
    private static void BrushingContactGeneratesTopspin() {
        BallState Ball = BallState.At(new Vec3(0, 0.30, 0), new Vec3(0, 0, 4), Vec3.Zero);

        BallState After = PaddleStrike(Ball, new Vec3(0, 0.245, 0.0263),
                                       new Vec3(0, 12, -9), ClosedFace(0.45), RacketMat);
        Check("an upward brush makes contact", After != null, "");
        if (After == null) return;

        // Ball now heading toward -Z, so topspin is rotation about -X (BallState's convention).
        double TopRevs = -After.Spin().X() / (2 * Math.PI);
        Check("brushing up the back of the ball generates TOPSPIN, not backspin",
              TopRevs > 0, String.format("%+.0f rev/s", TopRevs));

        Check("the spin generated is in the range a real player produces",
              TopRevs > 15 && TopRevs < 150,
              String.format("%.0f rev/s; skilled topspin forehands measure 117 +/- 29 rev/s "
                          + "and the peer-reviewed ceiling is 150", TopRevs));

        Check("the brush also sends the ball back down the table",
              After.Vel().Z() < 0, String.format("%.1f m/s in Z", After.Vel().Z()));
    }

    /** Heavy BACKSPIN into a brushing blade must come back as TOPSPIN. */
    private static void PaddleReversesIncomingBackspin() {
        // A ball arriving with heavy backspin.
        Vec3 Backspin = new Vec3(-90 * 2 * Math.PI, 0, 0);
        BallState Chop = BallState.At(new Vec3(0, 0.30, 0), new Vec3(0, 0, 5), Backspin);

        Vec3 Face = ClosedFace(0.45);
        Vec3 End = new Vec3(0, 0.245, 0.0263);
        Vec3 Swing = new Vec3(0, 14, -10);

        BallState Rubber = PaddleStrike(Chop, End, Swing, Face, RacketMat);
        Check("the blade reaches the chopped ball", Rubber != null, "");
        if (Rubber == null) return;

        // Measured about the axis of the OUTGOING ball, which now travels toward -Z: positive means
        // topspin.
        double InRevs = Chop.Spin().X() / (2 * Math.PI);
        double OutRevs = -Rubber.Spin().X() / (2 * Math.PI);
        Check("heavy backspin comes off an inverted rubber as topspin (spin REVERSAL)",
              OutRevs > 0,
              String.format("%.0f rev/s of backspin in -> %+.0f rev/s of topspin out",
                            Math.abs(InRevs), OutRevs));

        // Same stroke, rigid surface. It can strip spin, but it cannot reverse it.
        Constants.Material NoSpringback = Constants.Material.Rigid(
                RacketMat.RestitutionAt(0), RacketMat.Friction(), 1.0, 1.0);
        BallState Rigid = PaddleStrike(Chop, End, Swing, Face, NoSpringback);
        double RigidRevs = Rigid == null ? 0 : -Rigid.Spin().X() / (2 * Math.PI);
        Check("a grip-only surface generates strictly less spin (this is why rubber needs e_t)",
              RigidRevs < OutRevs,
              String.format("e_t=0 gives %+.0f rev/s where rubber gives %+.0f",
                            RigidRevs, OutRevs));
    }

    /**
     * A paddle is allowed to add energy -- that is what a swing is for -- but only as much as
     * the swing could actually have done.
     */
    private static void PaddleContactAddsNoFreeEnergy() {
        Vec3 N = new Vec3(0, 0, -1);
        BallState Incoming = BallState.At(new Vec3(0, 0.30, 0), new Vec3(0, 0, 8),
                                          new Vec3(-300, 0, 0));

        // A blade held perfectly still is just a wall.
        BallState Off = PaddleStrike(Incoming, new Vec3(0, 0.30, 0.025), Vec3.Zero, N,
                                     RacketMat);
        Check("a ball into a STATIONARY blade never gains energy",
              Off != null && Off.KineticEnergy() <= Incoming.KineticEnergy() + 1e-12,
              Off == null ? "no contact" : String.format("%.6f J -> %.6f J",
                            Incoming.KineticEnergy(), Off.KineticEnergy()));

        // A swung blade may add energy, but not more than (1+e)*u + |v_in| allows.
        double Swing = 15.0, Arriving = 6.0;
        BallState Ball = BallState.At(new Vec3(0, 0.30, 0), new Vec3(0, 0, Arriving), Vec3.Zero);
        BallState Hit = PaddleStrike(Ball, new Vec3(0, 0.30, 0.025),
                                     new Vec3(0, 0, -Swing), N, RacketMat);
        double Limit = (1 + RacketMat.RestitutionAt(Swing + Arriving)) * Swing + Arriving;
        Check("a swung blade cannot send the ball faster than its own swing allows",
              Hit != null && Hit.Vel().Length() <= Limit + 1e-9,
              Hit == null ? "no contact" : String.format("%.2f m/s against a limit of %.2f",
                            Hit.Vel().Length(), Limit));
    }

    /**
     * The paddle equivalent of the table tunnelling check, and a harder problem than the table
     * was.
     */
    private static void NoTunnellingThroughASwungPaddle() {
        int Caught = 0, Tried = 0;
        double BladeSpeed = 20.0;

        for (double Speed = 20; Speed <= 60.01; Speed += 2.5) {
            Tried++;
            BallState Ball = BallState.At(new Vec3(0, 0.30, 0), new Vec3(0, 0, Speed), Vec3.Zero);
            BallState Flown = Integrator.Step(Ball, Dt);

            // Put the blade half way along the RELATIVE sweep, so the crossing is mid-step at every
            // speed rather than only at the one the geometry happened to suit.
            double Meet = (Speed * Dt - BladeSpeed * Dt) / 2;
            Paddle Racket = new Paddle(new Vec3(0, 0.30, Meet + BladeSpeed * Dt), new Vec3(0, 0, -1));
            Racket.MoveTo(new Vec3(0, 0.30, Meet), new Vec3(0, 0, -1), Dt);

            if (Contacts.Detect(Ball, Flown, Racket.Collider()) != null) Caught++;
        }
        Check("no tunnelling through a swung paddle from 20 to 60 m/s",
              Caught == Tried,
              String.format("%d of %d speeds caught, closing at up to %.0f m/s",
                            Caught, Tried, 60 + BladeSpeed));
    }

    private static void Check(String What, boolean Ok, String Detail) {
        Checks++;
        System.out.printf("  [%s] %s%s%n", Ok ? "PASS" : "FAIL", What,
                          Detail.isEmpty() ? "" : "  (" + Detail + ")");
        if (!Ok) Failures.add(What + (Detail.isEmpty() ? "" : " -> " + Detail));
    }
}
