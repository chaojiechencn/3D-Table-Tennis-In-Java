package tabletennis.game.ai;

import tabletennis.engine.BallState;
import tabletennis.engine.TableSpec;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.Racket;

import static tabletennis.engine.math.Numeric.Clamp;

/**
 * An unbeatable opponent that moves to where the ball IS and swings when it arrives. It cannot be
 * made beatable by slowing it down, which only makes it erratic; difficulty needs prediction.
 */
public final class BallFollower implements Opponent {

    /** A wall, not a player: far beyond a human's 17.8 m/s swing. */
    private static final double MaxSpeed = 25.0;

    /** Just beyond the opponent's end of the table. */
    public static final double PlaneZ = -(TableSpec.HalfLength + 0.12);

    /** TUNED: without a ceiling the blade rides a rising ball up, hitting it every step. */
    private static final double MaxReachY = 0.55;

    private static final double MinReachY = 0.04;

    public static final Vec3 Ready = new Vec3(0, 0.20, PlaneZ);

    /** Square to the table, facing the player. */
    public static final Vec3 Square = new Vec3(0, 0, 1);

    /** Steps in over the table only for a LOW ball this close; always stepping in costs 7 of 10 feeds. */
    private static final double ReachForward = 0.55;
    private static final double StepInHeight = 0.22;

    private static final double SwingRange = 0.30;
    private static final double SwingTime = 0.10;

    // TUNED by sweep: no fixed stroke returns every preset legally AND lands most of them, so the
    // net wins. A closed face plus lift brushes topspin that brings the return down.
    private static final double FaceClosed = 0.20;
    private static final double SwingSpeed = 8.5;
    private static final double SwingLift = 5.0;

    private static final Vec3 Face = new Vec3(0, -FaceClosed, 1).Normalized();

    private double SwingElapsed = -1;   // negative when not swinging

    /** Fixed when the stroke commits, or the blade follows the ball it just hit and hits it again. */
    private Vec3 SwingFrom = Ready;

    @Override
    public void Advance(BallState Ball, Racket Blade, double Seconds) {
        Vec3 At = Ball.Position();
        boolean Incoming = At.Z() < 0 && Ball.Velocity().Z() < 0;

        if (Incoming && At.Z() - PlaneZ < SwingRange && SwingElapsed < 0) {
            SwingFrom = Reachable(At);
            SwingElapsed = 0;
        }

        Vec3 Want;
        if (SwingElapsed >= 0) Want = NextSwingPoint(Seconds);
        else if (Incoming) Want = Reachable(At);
        else Want = Ready;   // following a departing ball would chase it into the roof

        Blade.MoveTo(Blade.Position().MovedToward(Want, MaxSpeed * Seconds), Face, Seconds);
    }

    /** A half-sine stroke profile: an instant start would read as an impossible velocity. */
    private Vec3 NextSwingPoint(double Seconds) {
        SwingElapsed += Seconds;
        double Progress = Math.min(1, SwingElapsed / SwingTime);
        double Eased = (1 - Math.cos(Math.PI * Progress)) / 2;
        double Travel = SwingTime * Eased * 2 / Math.PI;
        if (Progress >= 1) SwingElapsed = -1;
        return new Vec3(SwingFrom.X(),
                        SwingFrom.Y() + SwingLift * Travel,
                        SwingFrom.Z() + SwingSpeed * Travel);
    }

    /** The ball's position, clamped to where the blade can be: above the table, below the roof. */
    private static Vec3 Reachable(Vec3 Ball) {
        double ToPlane = Ball.Z() - PlaneZ;
        boolean StepIn = Ball.Y() < StepInHeight && ToPlane > 0 && ToPlane < ReachForward;
        return new Vec3(Clamp(Ball.X(), -TableSpec.Width, TableSpec.Width),
                        Clamp(Ball.Y(), MinReachY, MaxReachY),
                        StepIn ? Ball.Z() : PlaneZ);
    }
}
