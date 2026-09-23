package tabletennis.game.control;

import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.FlightPredictor;

import java.util.List;

/**
 * Could the player have got there? For validation and the control overlay ONLY: these read the
 * ball, so nothing on the path that sets the blade's target may call them.
 */
public final class ReachTiming {

    private ReachTiming() {}

    private static final double PredictionSeconds = 3.0;
    private static final int PredictionStride = 4;

    public static boolean CanTouch(Vec3 BallPosition) {
        return BallPosition.Z() >= ReachEnvelope.ZNear && BallPosition.Z() <= ReachEnvelope.ZFar
            && Math.abs(BallPosition.X()) <= ReachEnvelope.MaxX
            && Math.abs(BallPosition.Y() - ReachEnvelope.HitY) <= ReachEnvelope.VerticalCapture;
    }

    public static double TravelDistance(Vec3 From, Vec3 To) {
        return To.Minus(From).Length();
    }

    public static double TravelTime(Vec3 From, Vec3 To) {
        return TravelDistance(From, To) / CursorFollower.TrackSpeed;
    }

    /** Seconds until the ball reaches DepthZ on a racket-free flight, or NaN if not within 3 s. */
    public static double TimeToDepth(BallState Ball, double DepthZ) {
        List<Vec3> Path = FlightPredictor.Path(Ball, PredictionSeconds, PredictionStride);
        double PreviousZ = Ball.Position().Z();
        for (int Index = 0; Index < Path.size(); Index++) {
            double Z = Path.get(Index).Z();
            boolean Crossed = (PreviousZ < DepthZ && Z >= DepthZ) || (PreviousZ > DepthZ && Z <= DepthZ);
            if (Crossed) {
                double Along = (DepthZ - PreviousZ) / (Z - PreviousZ);
                return (Index + Along) * PredictionStride * Simulation.Step;
            }
            PreviousZ = Z;
        }
        return Double.NaN;
    }
}
