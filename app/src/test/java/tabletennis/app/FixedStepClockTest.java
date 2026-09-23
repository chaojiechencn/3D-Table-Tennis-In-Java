package tabletennis.app;

import org.junit.jupiter.api.Test;
import tabletennis.engine.Simulation;

import static tabletennis.testing.Claims.Check;

/** The frame-to-step accounting, which decides how the game runs the same on fast and slow machines. */
final class FixedStepClockTest {

    private static int StepsOwed(FixedStepClock Clock) {
        int Steps = 0;
        while (Clock.TakeStep()) Steps++;
        return Steps;
    }

    @Test
    void AFramePaysOutWholeStepsAndKeepsTheRemainder() {
        FixedStepClock Clock = new FixedStepClock();
        Clock.BeginFrame(1.0 / 60);   // 7.5 ms of game time at the default 0.45x
        int Steps = StepsOwed(Clock);
        double Expected = (1.0 / 60) * FixedStepClock.DefaultTimeScale / Simulation.Step;
        Check("a frame pays out only whole physics steps and keeps the rest for interpolation",
              Steps == (int) Expected && Math.abs(Clock.Fraction() - (Expected - Steps)) < 1e-9,
              String.format("%d steps, fraction %.3f, for %.3f steps owed", Steps, Clock.Fraction(), Expected));
    }

    @Test
    void AStallIsClamped() {
        FixedStepClock Clock = new FixedStepClock();
        Clock.BeginFrame(5.0);
        int Steps = StepsOwed(Clock);
        double Owed = FixedStepClock.MaxFrameSeconds * FixedStepClock.DefaultTimeScale / Simulation.Step;
        // Within one step of the clamped frame: repeated subtraction rounds either way at the boundary.
        Check("a five-second stall pays out no more than one clamped frame of steps",
              Steps <= Math.ceil(Owed) && Steps >= Math.floor(Owed) - 1,
              String.format("%d steps for %.2f steps' worth of clamped frame", Steps, Owed));
    }

    @Test
    void PauseFreezesTimeExceptForQueuedSingleSteps() {
        FixedStepClock Clock = new FixedStepClock();
        Clock.TogglePause();
        Clock.BeginFrame(1.0);
        int WhilePaused = StepsOwed(Clock);
        Clock.QueueSingleStep();
        Clock.QueueSingleStep();
        int Stepped = StepsOwed(Clock);
        Check("paused, a frame advances nothing but the queued single steps",
              WhilePaused == 0 && Stepped == 2 && Clock.Fraction() == 0,
              String.format("%d steps paused, %d after queueing two, fraction %.1f", WhilePaused, Stepped, Clock.Fraction()));
    }

    @Test
    void TimeScaleStaysInItsBounds() {
        FixedStepClock Clock = new FixedStepClock();
        for (int Press = 0; Press < 20; Press++) Clock.Slower();
        double Slowest = Clock.TimeScale();
        for (int Press = 0; Press < 40; Press++) Clock.Faster();
        double Fastest = Clock.TimeScale();
        Check("slow motion and fast forward stop at their bounds",
              Slowest == FixedStepClock.MinTimeScale && Fastest == FixedStepClock.MaxTimeScale,
              String.format("slowest %.2fx, fastest %.2fx", Slowest, Fastest));
    }
}
