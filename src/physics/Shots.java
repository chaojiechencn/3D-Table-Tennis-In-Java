package physics;

import static physics.Constants.*;

/**
 * Named launch presets for the physics demo.
 *
 * The checkpoint asks to show the ball "flying with spin, curving in the air, and bouncing
 * right off the table and net". A single shot cannot show that; a menu of shots that differ
 * mainly in their spin can, because then the difference on screen is unambiguously the spin
 * doing it.
 *
 * Each shot states WHAT IT IS TRYING TO DO - a speed, a spin, and a spot on the table - and
 * {@link Aim} solves for the launch angle. Hard-coding velocities was tried first and was
 * hopeless: with drag this strong, the angle that lands a 15 m/s ball is nowhere near the
 * one that lands an 8 m/s ball, so every preset had to be re-guessed by hand and most of
 * them sailed off the end anyway.
 *
 * Speeds and spins are match-realistic rather than convenient. A loop drive really does
 * leave the bat around 15 m/s carrying 100+ revolutions per second, and a serve really can
 * carry more spin than a smash carries speed.
 */
public record Shots(String name, String detail, BallState state, Aim.Solution solution) {

    /**
     * Contact points, just behind the near edge.
     *
     * There are two heights, and the difference is not cosmetic. A flat ball struck at 18 m/s
     * from 30 cm does not clear the net -- the solver returns a trajectory 1.5 cm too low -- so
     * a flat drive has to be taken from above 35 cm. A topspin loop at the same speed can be
     * struck from 30 cm and still land, because Magnus lets it be hit UPWARD and still come
     * down in time. That is the actual reason topspin dominates the sport, and the two heights
     * below are the demo showing it rather than asserting it.
     */
    private static final Vec3 FROM = new Vec3(0, 0.30, 1.52);
    private static final Vec3 FROM_HIGH = new Vec3(0, 0.45, 1.52);

    /** A spot on the far court, well inside the lines. */
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

        // Deliberately aimed SHORT of the net so it never gets there: the point of this one
        // is the net contact, not the landing spot, so it is specified directly.
        raw("Into the net", "8 m/s, low and flat - the net kills it dead",
            BallState.at(new Vec3(0, 0.17, 0.95), new Vec3(0, -0.20, -8.0),
                         Aim.spin(new Vec3(0, 0, -1), 25, 0))),

        // A serve must bounce on the server's own half FIRST, so it is aimed at the near
        // court. It also has to survive that bounce with enough left to clear the net, which
        // rules out most of the parameter space: aimed steeply at mid-court it lands dead and
        // never gets over. Struck slowly from behind the end line, it works.
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

    /** Look a shot up by name. Used by SelfTest so that reordering the menu cannot silently
     *  point a check at a different shot than the one it was written for. */
    public static Shots byName(String name) {
        for (Shots s : ALL) if (s.name().equals(name)) return s;
        throw new IllegalArgumentException("no shot named " + name);
    }

    /** Spin in rev/s, the unit the sport actually uses. */
    public double spinRevs() { return state.spinRevsPerSec(); }

    /** The same shot with the spin removed, for the side-by-side comparison ghost. */
    public BallState withoutSpin() { return state.withSpin(Vec3.ZERO); }

    /**
     * A serve, meaning its first bounce is on the server's own half.
     *
     * Worth distinguishing because a serve is the one legal shot whose opening flight never
     * reaches the net at all, so "does it clear the cord" has to be asked of the flight AFTER
     * the first bounce instead.
     */
    public boolean isServe() { return solution != null && solution.landing().z() > 0; }

    /** Height above the net cord as it crosses, or NaN if it is not an aimed shot. */
    public double netClearance() { return solution == null ? Double.NaN : solution.netClearance(); }
}
