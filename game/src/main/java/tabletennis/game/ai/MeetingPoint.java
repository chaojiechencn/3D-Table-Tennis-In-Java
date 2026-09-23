package tabletennis.game.ai;

import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.FlightPredictor;
import tabletennis.game.rally.Side;

import java.util.List;

/**
 * Where a ball heading for one end can be met: the point on its predicted flight, after its bounce
 * on that half, where it passes closest to the blade's plane. The first reachable point is always
 * a graze at the rim. This is the prediction the demo hand plays with and a predicting opponent is
 * owed; it reads the ball's future, so it must never be wired to the player's own blade.
 */
public final class MeetingPoint {

    private MeetingPoint() {}

    /** A point on the ball's predicted path, and how long until the ball is there. */
    public record Meeting(Vec3 Point, double Time) {}

    /**
     * Null when the ball is leaving that half or never passes through the zone. BouncedAlready
     * says whether the ball has bounced on the receiver's half, which a path starting past its
     * apex cannot show.
     */
    public static Meeting Find(BallState Ball, boolean BouncedAlready, Side Receiver, HittingZone Zone,
                               double LookaheadSeconds, int Stride) {
        double IntoHalf = Receiver == Side.Player ? 1 : -1;
        boolean GoingAway = Ball.Velocity().Z() * IntoHalf <= 0 && Ball.Position().Z() * IntoHalf > 0;
        if (GoingAway) return null;

        List<Vec3> Path = FlightPredictor.Path(Ball, LookaheadSeconds, Stride);
        boolean Bounced = BouncedAlready;
        double PreviousY = Ball.Position().Y();

        Meeting Best = null;
        double BestMiss = Double.MAX_VALUE;
        for (int Index = 0; Index < Path.size(); Index++) {
            Vec3 Point = Path.get(Index);
            double Depth = Point.Z() * IntoHalf;
            if (Depth > 0 && Point.Y() > PreviousY) Bounced = true;   // rising on this half: it bounced
            PreviousY = Point.Y();

            if (!Bounced || Depth <= 0) continue;
            if (Depth < Zone.NearDepth() || Depth > Zone.FarDepth()) continue;

            double Miss = Math.abs(Point.Y() - Zone.PlaneY());
            if (Miss > Zone.VerticalCapture()) continue;
            if (Miss < BestMiss) {
                BestMiss = Miss;
                Best = new Meeting(Point, Index * Stride * Simulation.Step);
            }
        }
        return Best;
    }
}
