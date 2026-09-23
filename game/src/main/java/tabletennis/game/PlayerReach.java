package tabletennis.game;

import tabletennis.engine.BallState;
import tabletennis.engine.BallSpec;
import tabletennis.engine.RacketSpec;
import tabletennis.engine.Simulation;
import tabletennis.engine.TableSpec;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.FlightPredictor;


/**
 * Where the player's racket may be and how long it takes to get there. Cursor X maps to racket X,
 * cursor Y to racket Z (monotonically), and racket Y is fixed at {@link #HitY}: one horizontal
 * plane for the bat, full 3D for the ball.
 */
public final class PlayerReach {

    private PlayerReach() {}

    /** TUNED: peaks the worst feed's reachable window (302 ms). Never derived from the cursor. */
    public static final double HitY = 0.16;

    /** TUNED: how far up the table the blade may reach, about a metre short of the net. */
    public static final double ZNear = 0.30;

    /** TUNED: past this the ball has dropped out of the blade's capture band anyway. */
    public static final double ZFar = 2.40;

    /** A metre outside the table edge, so a ball run into the corner can be chased. */
    public static final double MaxX = TableSpec.HalfWidth + 1.0;

    /** TUNED: 1.90 m sits where returns arrive, minimising the worst-case dash. */
    public static final Vec3 Neutral = new Vec3(0, HitY, 1.90);

    /** Ball heights a blade at HitY can touch: its rim, plus the ball's own radius. */
    public static final double VerticalCapture = RacketSpec.BladeRadius + BallSpec.Radius;

    /** TUNED: a real brushing forehand's vertical travel; too small to become a reach axis. */
    public static final double BrushBand = 0.18;

    /** The legal hitting-plane point for a raw aim. The aim's Y is discarded, structurally. */
    public static Vec3 Clamp(Vec3 RawAim) {
        if (RawAim == null || !RawAim.IsFinite()) return Neutral;
        return new Vec3(ClampX(RawAim.X()), HitY, ClampZ(RawAim.Z()));
    }

    /**
     * The blade while the brush is held: height from the cursor (heightFrac 0 is the top of the
     * viewport), depth frozen at holdZ. Modal, so the cursor's Y still means one thing at a time.
     */
    public static Vec3 ClampBrushed(Vec3 RawAim, double HeightFrac, double HoldZ) {
        if (RawAim == null || !RawAim.IsFinite()) return Neutral;
        double Up = 1 - 2 * Clamp(HeightFrac, 0, 1);
        double Y = Math.max(RacketSpec.BladeRadius, HitY + Up * BrushBand);   // never through the table top
        return new Vec3(ClampX(RawAim.X()), Y, ClampZ(HoldZ));
    }

    /** For validation only; nothing on the control path may read the ball. */
    public static boolean CanTouch(Vec3 BallPos) {
        return BallPos.Z() >= ZNear && BallPos.Z() <= ZFar
            && Math.abs(BallPos.X()) <= MaxX
            && Math.abs(BallPos.Y() - HitY) <= VerticalCapture;
    }

    public static double TravelDistance(Vec3 From, Vec3 To) {
        return To.Minus(From).Length();
    }

    public static double TravelTime(Vec3 From, Vec3 To) {
        return TravelDistance(From, To) / Stroke.TrackSpeed;
    }

    /** Seconds until the ball reaches depthZ on a paddle-free flight, or NaN within 3 s. */
    public static double TimeToDepth(BallState Ball, double DepthZ) {
        final int Stride = 4;
        var Path = FlightPredictor.Path(Ball, 3.0, Stride);
        double Prev = Ball.Position().Z();
        for (int I = 0; I < Path.size(); I++) {
            double Z = Path.get(I).Z();
            boolean Crossed = (Prev < DepthZ && Z >= DepthZ) || (Prev > DepthZ && Z <= DepthZ);
            if (Crossed) {
                double F = (DepthZ - Prev) / (Z - Prev);
                return (I + F) * Stride * Simulation.Step;
            }
            Prev = Z;
        }
        return Double.NaN;
    }

    private static double ClampX(double X) { return Clamp(X, -MaxX, MaxX); }

    private static double ClampZ(double Z) { return Clamp(Z, ZNear, ZFar); }

    private static double Clamp(double V, double Lo, double Hi) {
        return V < Lo ? Lo : (V > Hi ? Hi : V);
    }
}
