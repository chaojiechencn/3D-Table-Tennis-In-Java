package tabletennis.game.feed;

import tabletennis.engine.BallState;
import tabletennis.engine.flight.LaunchSolver;
import tabletennis.engine.math.Vec3;

/**
 * A ball put into play: its menu name, a one-line description, the launch, and the solution that
 * aimed it (null for a raw launch such as the drop test).
 */
public record Feed(String Name, String Detail, BallState Ball, LaunchSolver.Solution Solution) {

    /** A raw launch, not aimed at anything. */
    public static Feed Raw(String Name, String Detail, BallState Ball) {
        return new Feed(Name, Detail, Ball, null);
    }

    public BallState WithoutSpin() { return Ball.WithSpin(Vec3.Zero); }

    /** First bounce on the server's own half, so net clearance is asked after that bounce. */
    public boolean IsServe() { return Solution != null && Solution.Landing().Z() > 0; }
}
