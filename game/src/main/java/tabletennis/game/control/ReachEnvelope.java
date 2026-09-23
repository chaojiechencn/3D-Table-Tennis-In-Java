package tabletennis.game.control;

import tabletennis.engine.BallSpec;
import tabletennis.engine.RacketSpec;
import tabletennis.engine.TableSpec;
import tabletennis.engine.math.Numeric;
import tabletennis.engine.math.Vec3;
import tabletennis.game.ai.HittingZone;

/**
 * Where the player's racket may be. Cursor X maps to racket X, cursor Y to racket Z
 * (monotonically), and racket Y is fixed at HitY: one horizontal plane for the bat, full 3D for
 * the ball. Holding the brush switches the cursor's Y to blade height instead, one meaning at a time.
 */
public final class ReachEnvelope {

    private ReachEnvelope() {}

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

    /** The envelope as a zone a prediction can search. */
    public static final HittingZone Zone = new HittingZone(HitY, ZNear, ZFar, VerticalCapture);

    /** The legal hitting-plane point for a raw aim. The aim's Y is discarded, structurally. */
    public static Vec3 Clamp(Vec3 RawAim) {
        if (RawAim == null || !RawAim.IsFinite()) return Neutral;
        return new Vec3(ClampX(RawAim.X()), HitY, ClampZ(RawAim.Z()));
    }

    /**
     * The blade while the brush is held: height from the cursor (HeightFraction 0 is the top of
     * the viewport), depth frozen at HoldZ, and never low enough to cut through the table top.
     */
    public static Vec3 ClampBrushed(Vec3 RawAim, double HeightFraction, double HoldZ) {
        if (RawAim == null || !RawAim.IsFinite()) return Neutral;
        double Up = 1 - 2 * Numeric.Clamp(HeightFraction, 0, 1);
        double Y = Math.max(RacketSpec.BladeRadius, HitY + Up * BrushBand);
        return new Vec3(ClampX(RawAim.X()), Y, ClampZ(HoldZ));
    }

    private static double ClampX(double X) { return Numeric.Clamp(X, -MaxX, MaxX); }

    private static double ClampZ(double Z) { return Numeric.Clamp(Z, ZNear, ZFar); }
}
