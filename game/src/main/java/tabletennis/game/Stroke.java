package tabletennis.game;

import tabletennis.engine.world.Racket;
import tabletennis.engine.math.Vec3;

/**
 * The player's paddle: follows the cursor and nothing else. {@code advance} takes no BallState, so
 * the ball can never steer the blade. Advanced once per physics step, never per frame.
 */
public final class Stroke {

    /**
     * A sampling guard, not a feel knob: without it one step could cover a whole frame of mouse
     * travel. TUNED: crosses the table, yet stays under a real 17.8 m/s swing.
     */
    public static final double TrackSpeed = 13.0;

    /** TUNED: a full drive closes the face to the tilt of a real ~120 rev/s loop. */
    private static final double FaceClose = 0.55;

    /** Below this blade speed the motion has no meaningful direction. */
    private static final double StrokeEps = 0.20;

    /** TUNED: reaches a new face angle in ~1/8 s; a snapped face reads as enormous spin. */
    private static final double FaceTau = 0.04;

    /** TUNED: longer than a slow mouse's event gap, so "no event yet" is not "stopped". */
    private static final double AimStillDelay = 0.05;

    private static final double AimMoveEps = 1e-3;

    /** Square to the table, facing the opponent. */
    public static final Vec3 Square = new Vec3(0, 0, -1);

    private Vec3 Target;
    private Vec3 StrokeDir = Square;
    private double IdleTime = 0;

    public Stroke(Vec3 RestingAt) {
        this.Target = RestingAt;
    }

    public void AimAt(Vec3 Point) {
        if (Point.Minus(Target).Length() > AimMoveEps) IdleTime = 0;
        Target = Point;
    }

    /** Carry the blade toward the cursor at TrackSpeed and lean its face the way it travels. */
    public void Advance(Racket Blade, double Dt) {
        Vec3 From = Blade.Position();
        IdleTime += Dt;

        Vec3 Pos = From.MovedToward(Target, TrackSpeed * Dt);
        Vec3 Moved = Pos.Minus(From);
        if (Moved.Length() > StrokeEps * Dt) {
            StrokeDir = Moved.Normalized();
        } else if (IdleTime > AimStillDelay) {
            StrokeDir = Vec3.Lerp(StrokeDir, Square, 1 - Math.exp(-Dt / FaceTau)).Normalized();
        }
        // Otherwise the hand moved recently: still mid-stroke, so hold the lean.

        Blade.MoveTo(Pos, FaceToward(Blade.Normal(), Dt), Dt);
    }

    /** Sideways lean follows the swipe; driving forward (-Z) closes the face for topspin. */
    private Vec3 FaceToward(Vec3 CurrentNormal, double Dt) {
        Vec3 Desired = new Vec3(StrokeDir.X() * 0.5, StrokeDir.Z() * FaceClose, -1).Normalized();
        double K = 1 - Math.exp(-Dt / FaceTau);
        return Vec3.Lerp(CurrentNormal, Desired, K).Normalized();
    }
}
