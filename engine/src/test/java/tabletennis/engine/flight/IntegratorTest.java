package tabletennis.engine.flight;

import org.junit.jupiter.api.Test;
import tabletennis.engine.BallState;
import tabletennis.engine.Environment;
import tabletennis.engine.Simulation;
import tabletennis.engine.aero.DragModel;
import tabletennis.engine.math.Vec3;

import static tabletennis.engine.aero.AeroData.ConstantDragCoefficient;
import static tabletennis.engine.aero.Aerodynamics.DragLiftFactor;
import static tabletennis.testing.Claims.Check;

/** RK4 against real analysis: an exact solution, and its own convergence order. */
final class IntegratorTest {

    /** The launch the Topspin loop feed solves for: drag and Magnus both active. */
    static BallState TopspinLoop() {
        return LaunchSolver.AtTarget(new Vec3(0, 0.30, 1.52), new Vec3(0, 0, -1.05), 15.0, 110, 0).State();
    }

    /**
     * Free fall with constant drag has a closed form, v(t) = -vt tanh(g t / vt) and
     * y(t) = y0 - (vt^2 / g) ln cosh(g t / vt), which tests integrator and drag together.
     */
    @Test
    void VerticalDropMatchesAnalyticSolution() {
        double G = Environment.Gravity;
        DragModel ConstantDrag = DragModel.Constant(ConstantDragCoefficient);
        double TerminalSpeed = Math.sqrt(G / (DragLiftFactor * ConstantDragCoefficient));
        double StartHeight = 100.0, Seconds = 3.0;

        BallState Ball = BallState.At(new Vec3(0, StartHeight, 0), Vec3.Zero, Vec3.Zero);
        for (int Step = 0; Step < (int) Math.round(Seconds / Simulation.Step); Step++) {
            Ball = Integrator.Step(Ball, Simulation.Step, ConstantDrag);
        }

        double ExactHeight = StartHeight - (TerminalSpeed * TerminalSpeed / G) * Math.log(Math.cosh(G * Seconds / TerminalSpeed));
        double ExactVelocity = -TerminalSpeed * Math.tanh(G * Seconds / TerminalSpeed);

        Check("drop position matches analytic solution to 1 mm over 3 s",
              Math.abs(Ball.Position().Y() - ExactHeight) < 1e-3,
              String.format("sim %.6f m vs exact %.6f m", Ball.Position().Y(), ExactHeight));

        Check("drop velocity matches analytic solution to 1 mm/s over 3 s",
              Math.abs(Ball.Velocity().Y() - ExactVelocity) < 1e-3,
              String.format("sim %.6f m/s vs exact %.6f m/s", Ball.Velocity().Y(), ExactVelocity));
    }

    /** Fourth order: quartering the step should cut the error by about 256x. */
    @Test
    void Rk4IsFourthOrder() {
        BallState Start = TopspinLoop();
        double Seconds = 0.4;

        Vec3 Coarse = PositionAfter(Start, Seconds, Simulation.Step);
        Vec3 Medium = PositionAfter(Start, Seconds, Simulation.Step / 4);
        Vec3 Fine = PositionAfter(Start, Seconds, Simulation.Step / 16);

        double CoarseError = Coarse.Minus(Fine).Length();
        double MediumError = Medium.Minus(Fine).Length();
        double Ratio = MediumError < 1e-15 ? Double.POSITIVE_INFINITY : CoarseError / MediumError;

        Check("halving-the-step error ratio indicates 4th order (expect >= 100x per 4x)",
              Ratio > 100,
              String.format("error shrank %.0fx for a 4x smaller step", Ratio));

        Check("one physics step at DT is already accurate to under 0.1 mm over 0.4 s",
              CoarseError < 1e-4,
              String.format("%.3e m", CoarseError));
    }

    private static Vec3 PositionAfter(BallState Ball, double Seconds, double StepSeconds) {
        int Steps = (int) Math.round(Seconds / StepSeconds);
        for (int Step = 0; Step < Steps; Step++) Ball = Integrator.Step(Ball, StepSeconds);
        return Ball.Position();
    }
}
