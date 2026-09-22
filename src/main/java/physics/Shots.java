package physics;

import static physics.Constants.*;

/**
 * Named launch presets that differ mainly in spin, so the difference on screen is the spin.
 * Each states intent (speed, spin, target) and {@link Aim} solves the angle; speeds and spins
 * are match-realistic.
 */
public record Shots(String name, String detail, BallState state, Aim.Solution solution) {

    // A flat 18 m/s ball from 30 cm cannot clear the net; topspin can be hit upward from there.
    private static final Vec3 FROM = new Vec3(0, 0.30, 1.52);
    private static final Vec3 FROM_HIGH = new Vec3(0, 0.45, 1.52);

    private static Vec3 target(double x, double z) { return new Vec3(x, 0, z); }

    private static Shots aimed(String name, String detail, Vec3 from, Vec3 target,
                               double speed, double topRevs, double sideRevs) {
        Aim.Solution sol = Aim.atTarget(from, target, speed, topRevs, sideRevs);
        return new Shots(name, detail, sol.state(), sol);
    }

    private static Shots raw(String name, String detail, BallState s) {
        return new Shots(name, detail, s, null);
    }

    public static final Shots[] ALL = {
        aimed("Serve", "13 m/s, no spin, corner to corner - the default",
              new Vec3(-0.50, 0.45, 1.52), target(0.50, -0.95), 13.0, 0, 0),

        aimed("Flat drive", "18 m/s, no spin - the control case, drag only",
              FROM_HIGH, target(0, -0.95), 18.0, 0, 0),

        aimed("Topspin loop", "15 m/s, 110 rev/s topspin - Magnus drags it down",
              FROM, target(0, -1.05), 15.0, 110, 0),

        aimed("Heavy backspin push", "7 m/s, 70 rev/s backspin - floats, then checks up",
              FROM, target(0, -0.80), 7.0, -70, 0),

        aimed("Sidespin hook (left)", "12 m/s, 90 rev/s sidespin - bends across the table",
              FROM, target(0, -1.00), 12.0, 20, 90),

        aimed("Sidespin hook (right)", "12 m/s, 90 rev/s the other way",
              FROM, target(0, -1.00), 12.0, 20, -90),

        aimed("Smash", "30 m/s, 30 rev/s topspin - stress-tests the swept collision",
              new Vec3(0, 0.48, 1.45), target(0, -0.90), 30.0, 30, 0),

        aimed("Cross-court loop", "14 m/s, 100 rev/s topspin, aimed at the corner",
              new Vec3(-0.45, 0.30, 1.52), target(0.55, -1.15), 14.0, 100, 35),

        raw("Into the net", "8 m/s, low and flat - the net kills it dead",
            BallState.at(new Vec3(0, 0.17, 0.95), new Vec3(0, -0.20, -8.0),
                         Aim.spin(new Vec3(0, 0, -1), 25, 0))),

        // Must land on the server's own half first and still clear the net afterwards.
        aimed("Corkscrew serve", "4.5 m/s, 125 rev/s sidespin - own court, then over",
              new Vec3(0.15, 0.26, 1.60), target(0.05, 0.80), 4.5, 40, 125),

        raw("ITTF drop test", "released from 30.5 cm - should rebound to 24-26 cm",
            BallState.at(new Vec3(0, 0.305 + BALL_R, -0.70), Vec3.ZERO, Vec3.ZERO)),

        aimed("Backspin lob", "9 m/s, 60 rev/s backspin - the Magnus float",
              new Vec3(0, 0.35, 1.52), target(0, -1.15), 9.0, -60, 0),
    };

    public static Shots byIndex(int i) {
        return ALL[Math.floorMod(i, ALL.length)];
    }

    public static Shots byName(String name) {
        for (Shots s : ALL) if (s.name().equals(name)) return s;
        throw new IllegalArgumentException("no shot named " + name);
    }

    public double spinRevs() { return state.spinRevsPerSec(); }

    public BallState withoutSpin() { return state.withSpin(Vec3.ZERO); }

    /** First bounce on the server's own half, so net clearance is asked after that bounce. */
    public boolean isServe() { return solution != null && solution.landing().z() > 0; }

    public double netClearance() { return solution == null ? Double.NaN : solution.netClearance(); }
}
