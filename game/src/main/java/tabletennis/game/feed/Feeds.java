package tabletennis.game.feed;

import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;
import tabletennis.engine.flight.LaunchSolver;
import tabletennis.engine.flight.SpinVector;
import tabletennis.engine.math.Vec3;

import java.util.List;

/**
 * The named feeds, differing mainly in spin so the difference on screen is the spin. Each states
 * intent (speed, spin, target) and LaunchSolver solves the angle; speeds and spins are
 * match-realistic.
 */
public final class Feeds {

    private Feeds() {}

    // A flat 18 m/s ball from 30 cm cannot clear the net; topspin can be hit upward from there.
    private static final Vec3 LowLaunch = new Vec3(0, 0.30, 1.52);
    private static final Vec3 HighLaunch = new Vec3(0, 0.45, 1.52);

    public static final List<Feed> All = List.of(
        Aimed("Serve", "13 m/s, no spin, corner to corner - the default",
              new Vec3(-0.50, 0.45, 1.52), OnTable(0.50, -0.95), 13.0, 0, 0),

        Aimed("Flat drive", "18 m/s, no spin - the control case, drag only",
              HighLaunch, OnTable(0, -0.95), 18.0, 0, 0),

        Aimed("Topspin loop", "15 m/s, 110 rev/s topspin - Magnus drags it down",
              LowLaunch, OnTable(0, -1.05), 15.0, 110, 0),

        Aimed("Heavy backspin push", "7 m/s, 70 rev/s backspin - floats, then checks up",
              LowLaunch, OnTable(0, -0.80), 7.0, -70, 0),

        Aimed("Sidespin hook (left)", "12 m/s, 90 rev/s sidespin - bends across the table",
              LowLaunch, OnTable(0, -1.00), 12.0, 20, 90),

        Aimed("Sidespin hook (right)", "12 m/s, 90 rev/s the other way",
              LowLaunch, OnTable(0, -1.00), 12.0, 20, -90),

        Aimed("Smash", "30 m/s, 30 rev/s topspin - stress-tests the swept collision",
              new Vec3(0, 0.48, 1.45), OnTable(0, -0.90), 30.0, 30, 0),

        Aimed("Cross-court loop", "14 m/s, 100 rev/s topspin, aimed at the corner",
              new Vec3(-0.45, 0.30, 1.52), OnTable(0.55, -1.15), 14.0, 100, 35),

        Feed.Raw("Into the net", "8 m/s, low and flat - the net kills it dead",
                 BallState.At(new Vec3(0, 0.17, 0.95), new Vec3(0, -0.20, -8.0),
                              SpinVector.Of(new Vec3(0, 0, -1), 25, 0))),

        // Must land on the server's own half first and still clear the net afterwards.
        Aimed("Corkscrew serve", "4.5 m/s, 125 rev/s sidespin - own court, then over",
              new Vec3(0.15, 0.26, 1.60), OnTable(0.05, 0.80), 4.5, 40, 125),

        Feed.Raw("ITTF drop test", "released from 30.5 cm - should rebound to 24-26 cm",
                 BallState.At(new Vec3(0, 0.305 + BallSpec.Radius, -0.70), Vec3.Zero, Vec3.Zero)),

        Aimed("Backspin lob", "9 m/s, 60 rev/s backspin - the Magnus float",
              new Vec3(0, 0.35, 1.52), OnTable(0, -1.15), 9.0, -60, 0));

    /** The feed a game opens with. */
    public static Feed Default() { return ByName("Serve"); }

    public static Feed ByName(String Name) {
        for (Feed Each : All) if (Each.Name().equals(Name)) return Each;
        throw new IllegalArgumentException("no feed named " + Name);
    }

    /** Wraps around both ways, so any index picks a feed. */
    public static Feed ByIndex(int Index) {
        return All.get(Math.floorMod(Index, All.size()));
    }

    /** The feed Delta places after Current in the menu, wrapping around. */
    public static Feed Next(Feed Current, int Delta) {
        return ByIndex(Math.max(0, All.indexOf(Current)) + Delta);
    }

    private static Vec3 OnTable(double X, double Z) { return new Vec3(X, 0, Z); }

    private static Feed Aimed(String Name, String Detail, Vec3 From, Vec3 Target,
                              double Speed, double TopRevs, double SideRevs) {
        LaunchSolver.Solution Solution = LaunchSolver.AtTarget(From, Target, Speed, TopRevs, SideRevs);
        return new Feed(Name, Detail, Solution.State(), Solution);
    }
}
