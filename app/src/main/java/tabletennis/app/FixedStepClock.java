package tabletennis.app;

import tabletennis.engine.Simulation;

/**
 * Turns wall-clock frames into whole physics steps (Gaffer On Games, "Fix Your Timestep!"): time
 * accumulates, scaled for slow motion, and is paid out one Simulation.Step at a time; the
 * remainder becomes the render interpolation fraction. Pause freezes it except for queued single
 * steps, which go through the same fixed step so a paused frame matches the one produced live.
 */
final class FixedStepClock {

    /** Longest frame honoured; beyond it time is dropped, so a stall cannot spiral. */
    static final double MaxFrameSeconds = 0.25;

    /** Opens in slow motion: at 1:1 a first rally is a blur. */
    static final double DefaultTimeScale = 0.45;
    static final double MinTimeScale = 0.02, MaxTimeScale = 2.0;
    private static final double TimeScaleStep = 1.6;

    private double Accumulator;
    private double TimeScale = DefaultTimeScale;
    private boolean Paused;
    private int QueuedSingleSteps;

    /** Clamped before scaling, so a stall cannot hand the accumulator more than a bounded frame. */
    void BeginFrame(double WallSeconds) {
        if (!Paused) Accumulator += Math.min(WallSeconds, MaxFrameSeconds) * TimeScale;
    }

    /** True while a whole step is owed; each true pays one step out. */
    boolean TakeStep() {
        if (Paused) {
            if (QueuedSingleSteps == 0) return false;
            QueuedSingleSteps--;
            return true;
        }
        if (Accumulator < Simulation.Step) return false;
        Accumulator -= Simulation.Step;
        return true;
    }

    /** How far the display is between the last two physics states. */
    double Fraction() {
        return Paused ? 0 : Math.min(1, Accumulator / Simulation.Step);
    }

    /** A new rally owes nothing from the old one. */
    void Reset() { Accumulator = 0; }

    void TogglePause() { Paused = !Paused; }

    /** Pauses, and advances exactly one step on the next frame. */
    void QueueSingleStep() {
        Paused = true;
        QueuedSingleSteps++;
    }

    void Slower() { TimeScale = Math.max(MinTimeScale, TimeScale / TimeScaleStep); }

    void Faster() { TimeScale = Math.min(MaxTimeScale, TimeScale * TimeScaleStep); }

    boolean Paused() { return Paused; }

    double TimeScale() { return TimeScale; }
}
