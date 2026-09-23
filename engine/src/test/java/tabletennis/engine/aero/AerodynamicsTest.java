package tabletennis.engine.aero;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;
import tabletennis.engine.Environment;
import tabletennis.engine.Simulation;
import tabletennis.engine.flight.Integrator;
import tabletennis.engine.math.Vec3;

import static tabletennis.engine.aero.AeroData.ConstantDragCoefficient;
import static tabletennis.engine.aero.Aerodynamics.DragLiftFactor;
import static tabletennis.testing.Claims.Check;

/** Drag, lift and spin decay against published measurements and closed-form solutions. */
final class AerodynamicsTest {

    private static final double G = Environment.Gravity;

    /** Not a check: the derived constants, printed so they can be eyeballed against sources. */
    @BeforeAll
    static void ReportDerivedConstants() {
        System.out.printf("  ball        r=%.3f m  m=%.4f kg  I=%.3e kg m^2 (hollow shell, 2/3 m r^2)%n",
                BallSpec.Radius, BallSpec.Mass, BallSpec.Inertia);
        System.out.printf("  aero        0.5*rho*A/m = %.4f 1/m%n", DragLiftFactor);
        System.out.printf("  C_d(no spin)  2.5 m/s %.2f   7.5 %.2f   12.5 %.2f   17.5 %.2f%n",
                Aerodynamics.MeasuredDragCoefficient(2.5, 0), Aerodynamics.MeasuredDragCoefficient(7.5, 0),
                Aerodynamics.MeasuredDragCoefficient(12.5, 0), Aerodynamics.MeasuredDragCoefficient(17.5, 0));
        System.out.printf("  drag at 10 m/s = %.2f m/s^2  (gravity is %.2f - air dominates)%n%n",
                Aerodynamics.Drag(new Vec3(0, 0, -10), Vec3.Zero, DragModel.Measured).Length(), G);
    }

    /** Deliberately flown against a CONSTANT drag coefficient: only that has a closed form. */
    @Test
    void TerminalVelocityMatchesClosedForm() {
        DragModel ConstantDrag = DragModel.Constant(ConstantDragCoefficient);
        double Analytic = Math.sqrt(G / (DragLiftFactor * ConstantDragCoefficient));

        BallState Ball = BallState.At(new Vec3(0, 500, 0), Vec3.Zero, Vec3.Zero);
        for (int Step = 0; Step < Simulation.StepsPerSecond * 30; Step++) {
            Ball = Integrator.Step(Ball, Simulation.Step, ConstantDrag);
        }
        double Simulated = -Ball.Velocity().Y();

        Check("terminal velocity matches closed form",
              Math.abs(Simulated - Analytic) < 0.01,
              String.format("sim %.3f m/s vs analytic %.3f m/s", Simulated, Analytic));

        Check("terminal velocity is in the published 9.0-9.6 m/s range",
              Simulated > 9.0 && Simulated < 9.6,
              String.format("%.2f m/s", Simulated));
    }

    /** Against the values the law was built from, and the band every table-tennis study reports. */
    @Test
    void MeasuredDragMatchesPublishedValues() {
        double[][] Published = { {2.5, 0.55}, {7.5, 0.49}, {12.5, 0.47}, {17.5, 0.47} };
        boolean AllMatch = true;
        StringBuilder Got = new StringBuilder();
        for (double[] Row : Published) {
            double Cd = Aerodynamics.MeasuredDragCoefficient(Row[0], 0);
            AllMatch &= Math.abs(Cd - Row[1]) < 1e-9;
            Got.append(String.format("%.1f:%.2f ", Row[0], Cd));
        }
        Check("C_d reproduces the measured table at zero spin", AllMatch, Got.toString().trim());

        double Lowest = 1, Highest = 0;
        for (double Speed = 2; Speed <= 35; Speed += 0.5) {
            double Cd = Aerodynamics.MeasuredDragCoefficient(Speed, 0);
            Lowest = Math.min(Lowest, Cd);
            Highest = Math.max(Highest, Cd);
        }
        Check("C_d stays inside the published 0.45-0.55 band over the whole playing range",
              Lowest >= 0.45 && Highest <= 0.55, String.format("%.3f to %.3f over 2-35 m/s", Lowest, Highest));

        Check("the old flat C_d = 0.40 was below every published value (why this changed)",
              ConstantDragCoefficient < Lowest, String.format("0.40 vs a measured minimum of %.2f", Lowest));

        double FixedPoint = 9.0;
        for (int Iteration = 0; Iteration < 200; Iteration++) {
            FixedPoint = Math.sqrt(G / (DragLiftFactor * Aerodynamics.MeasuredDragCoefficient(FixedPoint, 0)));
        }
        BallState Ball = BallState.At(new Vec3(0, 500, 0), Vec3.Zero, Vec3.Zero);
        for (int Step = 0; Step < Simulation.StepsPerSecond * 30; Step++) Ball = Integrator.Step(Ball, Simulation.Step);
        double Simulated = -Ball.Velocity().Y();

        Check("terminal velocity under the measured drag law matches its own fixed point",
              Math.abs(Simulated - FixedPoint) < 0.01,
              String.format("sim %.3f m/s vs fixed point %.3f m/s", Simulated, FixedPoint));

        // Two published claims genuinely conflict here, and the check says so rather than pick one.
        Check("the measured law puts terminal velocity just below the often-quoted 9.0-9.6 band",
              Simulated > 8.0 && Simulated < 9.0,
              String.format("%.2f m/s; the 9.0-9.6 figure implies C_d = 0.40, so the two "
                          + "published claims cannot be reconciled", Simulated));
    }

    /** Lift, converted out of the volume-based published fit into the area-based C_L used here. */
    @Test
    void MeasuredLiftMatchesPublishedValues() {
        // (speed, spin rad/s, expected C_L), from converting the fitted C_M by C_L = (8/3) C_M S.
        double[][] Cases = { {7.5, 100, 0.206}, {13.5, 300, 0.255}, {17.0, 200, 0.177}, {17.0, 650, 0.152} };
        boolean AllMatch = true;
        StringBuilder Got = new StringBuilder();
        for (double[] Case : Cases) {
            double Cl = Aerodynamics.LiftCoefficient(new Vec3(0, 0, -Case[0]), new Vec3(-Case[1], 0, 0));
            AllMatch &= Math.abs(Cl - Case[2]) < 0.02;
            Got.append(String.format("%.0f/%.0f:%.3f ", Case[0], Case[1], Cl));
        }
        Check("C_L matches the measured Magnus fit once converted to the area convention",
              AllMatch, Got.toString().trim());

        double Lowest = 9, Highest = 0;
        for (double Ratio = 0.25; Ratio <= 1.4; Ratio += 0.05) {
            double Cl = LiftAt(Ratio);
            Lowest = Math.min(Lowest, Cl);
            Highest = Math.max(Highest, Cl);
        }
        Check("C_L stays in the measured 0.15-0.40 band over the meat of the range (S = 0.25-1.4)",
              Lowest >= 0.15 && Highest <= 0.40, String.format("%.3f to %.3f", Lowest, Highest));

        Check("no spin means no lift",
              LiftAt(0.0) == 0 && LiftAt(0.01) < 0.02,
              String.format("C_L = %.4f at S=0, %.4f at S=0.01", LiftAt(0.0), LiftAt(0.01)));

        double WorstRatio = 0.8;
        double Measured = LiftAt(WorstRatio), Old = WorstRatio / (2 * WorstRatio + 1);
        Check("the old model overstated lift by nearly 2x through the middle of normal play",
              Old > Measured * 1.6,
              String.format("at S=%.1f: measured %.3f vs old %.3f, a factor of %.2f",
                            WorstRatio, Measured, Old, Old / Measured));
    }

    /** The lift crisis: C_L FALLS as spin increases through S ~ 0.5 to 0.8. */
    @Test
    void LiftHasACrisisTheOldModelCouldNotShow() {
        double Peak = LiftAt(0.50), Trough = LiftAt(0.80), Recovery = LiftAt(1.10);

        Check("C_L falls away between S = 0.5 and S = 0.8 (the measured lift crisis)",
              Trough < Peak - 0.03,
              String.format("%.3f at S=0.5 -> %.3f at S=0.8", Peak, Trough));

        Check("C_L recovers again above the crisis",
              Recovery > Trough + 0.03,
              String.format("%.3f at S=0.8 -> %.3f at S=1.1", Trough, Recovery));

        double OldPeak = 0.50 / (2 * 0.50 + 1), OldTrough = 0.80 / (2 * 0.80 + 1);
        Check("the old S/(2S+1) model could not have shown this dip at all",
              OldTrough > OldPeak,
              String.format("old model RISES %.3f -> %.3f across the same range", OldPeak, OldTrough));
    }

    /** Spin decay depends on airspeed, not only on how fast the ball is spinning. */
    @Test
    void SpinDecayScalesWithAirspeed() {
        Vec3 Spin = new Vec3(-600, 0, 0);
        double Slow = Aerodynamics.SpinDecay(Spin, new Vec3(0, 0, -5)).Length();
        double Fast = Aerodynamics.SpinDecay(Spin, new Vec3(0, 0, -25)).Length();

        Check("a fast ball sheds spin faster than a slow one at the same spin rate",
              Fast > Slow * 4.9 && Fast < Slow * 5.1,
              String.format("%.1f rad/s^2 at 5 m/s vs %.1f at 25 m/s, for 5x the airspeed", Slow, Fast));

        // Unchanged where it was tuned: a fix to the SHAPE of the law, not to how much spin a rally loses.
        double AtTypical = Aerodynamics.SpinDecay(Spin, new Vec3(0, 0, -12)).Length() / Spin.Length();
        Check("at a typical 12 m/s rally speed it still decays at the tuned 5%/s",
              Math.abs(AtTypical - 0.05) < 0.002, String.format("%.4f /s", AtTypical));
    }

    /** C_L at a given spin ratio, at a fixed mid-rally speed. */
    private static double LiftAt(double SpinRatio) {
        return Aerodynamics.LiftCoefficient(new Vec3(0, 0, -13.5),
                                            new Vec3(-SpinRatio * 13.5 / BallSpec.Radius, 0, 0));
    }
}
