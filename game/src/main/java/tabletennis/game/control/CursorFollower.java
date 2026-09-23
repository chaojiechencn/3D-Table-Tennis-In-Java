package tabletennis.game.control;

import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.Racket;

/**
 * The player's blade: carried toward the cursor and nothing else. Advance takes no ball, so the
 * ball can never steer the blade. Advanced once per physics step, never per frame.
 */
public final class CursorFollower {

    /**
     * A sampling guard, not a feel knob: without it one step could cover a whole frame of mouse
     * travel. TUNED: crosses the table, yet stays under a real 17.8 m/s swing.
     */
    public static final double TrackSpeed = 13.0;

    /** Square to the table, facing the opponent. */
    public static final Vec3 Square = new Vec3(0, 0, -1);

    /** TUNED: a full drive closes the face to the tilt of a real ~120 rev/s loop. */
    private static final double FaceClose = 0.55;

    /** How far a sideways swipe leans the face. */
    private static final double FaceLean = 0.5;

    /** Below this blade speed the motion has no meaningful direction. */
    private static final double StillSpeed = 0.20;

    /** TUNED: reaches a new face angle in ~1/8 s; a snapped face reads as enormous spin. */
    private static final double FaceTau = 0.04;

    /** TUNED: longer than a slow mouse's event gap, so "no event yet" is not "stopped". */
    private static final double AimStillDelay = 0.05;

    private static final double AimMovedBy = 1e-3;

    private Vec3 Target;
    private Vec3 StrokeDirection = Square;
    private double IdleTime = 0;

    public CursorFollower(Vec3 RestingAt) {
        this.Target = RestingAt;
    }

    public void AimAt(Vec3 Point) {
        if (Point.Minus(Target).Length() > AimMovedBy) IdleTime = 0;
        Target = Point;
    }

    /** Carry the blade toward the cursor at TrackSpeed and lean its face the way it travels. */
    public void Advance(Racket Blade, double Seconds) {
        Vec3 From = Blade.Position();
        IdleTime += Seconds;

        Vec3 To = From.MovedToward(Target, TrackSpeed * Seconds);
        Vec3 Moved = To.Minus(From);
        if (Moved.Length() > StillSpeed * Seconds) {
            StrokeDirection = Moved.Normalized();
        } else if (IdleTime > AimStillDelay) {
            StrokeDirection = Vec3.Lerp(StrokeDirection, Square, 1 - Math.exp(-Seconds / FaceTau)).Normalized();
        }
        // Otherwise the hand moved recently: still mid-stroke, so hold the lean.

        Blade.MoveTo(To, FaceToward(Blade.Normal(), Seconds), Seconds);
    }

    /** Sideways lean follows the swipe; driving forward (-Z) closes the face for topspin. */
    private Vec3 FaceToward(Vec3 CurrentNormal, double Seconds) {
        Vec3 Desired = new Vec3(StrokeDirection.X() * FaceLean, StrokeDirection.Z() * FaceClose, -1).Normalized();
        double Easing = 1 - Math.exp(-Seconds / FaceTau);
        return Vec3.Lerp(CurrentNormal, Desired, Easing).Normalized();
    }
}
