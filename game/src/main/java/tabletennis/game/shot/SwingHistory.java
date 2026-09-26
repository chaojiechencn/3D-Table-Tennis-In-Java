package tabletennis.game.shot;

import tabletennis.engine.Simulation;
import tabletennis.engine.math.Vec3;

/**
 * The player's blade positions over the last few physics steps, so a contact can read the swing
 * over a window rather than one step. The mouse reports more coarsely than the 480 Hz step, so a
 * single step's velocity is a sawtooth; the window average is the hand's actual motion. The
 * window is counted in whole steps, never seconds.
 */
public final class SwingHistory {

    private final Vec3[] Positions;
    private int Newest = -1;
    private int Recorded = 0;

    public SwingHistory(double WindowSeconds) {
        Positions = new Vec3[StepsIn(WindowSeconds) + 1];
    }

    /** The whole steps nearest to the window, at least one. */
    static int StepsIn(double WindowSeconds) {
        return Math.max(1, (int) Math.round(WindowSeconds / Simulation.Step));
    }

    /** Once per physics step, after the blade has moved. */
    public void Record(Vec3 BladePosition) {
        Newest = (Newest + 1) % Positions.length;
        Positions[Newest] = BladePosition;
        Recorded = Math.min(Recorded + 1, Positions.length);
    }

    /** The average velocity over the window, or over what has been recorded if that is shorter. */
    public Vec3 AverageVelocity() {
        if (Recorded < 2) return Vec3.Zero;
        int Oldest = (Newest - (Recorded - 1) + Positions.length) % Positions.length;
        double Seconds = (Recorded - 1) * Simulation.Step;
        return Positions[Newest].Minus(Positions[Oldest]).Scale(1.0 / Seconds);
    }
}
