package tabletennis.game;

import tabletennis.engine.BallState;
import tabletennis.engine.Paddle;
import tabletennis.engine.Vec3;

import static tabletennis.engine.Constants.*;

/**
 * An unbeatable opponent that moves to where the ball IS and swings when it arrives. It cannot be
 * made beatable by slowing it down; that needs prediction ({@link tabletennis.engine.World#Predict}).
 */
public final class Follower implements Opponent {

    /** A wall, not a player: far beyond a human's 17.8 m/s swing. */
    private static final double MaxSpeed = 25.0;

    /** Just beyond the opponent's end of the table. */
    public static final double PlaneZ = -(TableLength / 2 + 0.12);

    /** TUNED: without a ceiling the blade rides a rising ball up, hitting it every step. */
    private static final double MaxReachY = 0.55;

    public static final Vec3 Ready = new Vec3(0, 0.20, PlaneZ);
    public static final Vec3 Square = new Vec3(0, 0, 1);

    /** Steps in over the table only for a LOW ball this close; always stepping in costs 7 of 10 feeds. */
    private static final double ReachFwd = 0.55;
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
    public void Advance(BallState Ball, Paddle Blade, double Dt) {
        Vec3 B = Ball.Pos();
        boolean Incoming = B.Z() < 0 && Ball.Vel().Z() < 0;

        if (Incoming && B.Z() - PlaneZ < SwingRange && SwingElapsed < 0) {
            SwingFrom = Reachable(B);
            SwingElapsed = 0;
        }

        Vec3 Want;
        if (SwingElapsed >= 0) Want = NextSwingPoint(Dt);
        else if (Incoming) Want = Reachable(B);
        else Want = Ready;   // following a departing ball would chase it into the roof

        Blade.MoveTo(LimitStep(Blade.Pos(), Want, MaxSpeed * Dt), Face, Dt);
    }

    /** A half-sine stroke profile: an instant start would read as an impossible velocity. */
    private Vec3 NextSwingPoint(double Dt) {
        SwingElapsed += Dt;
        double T = Math.min(1, SwingElapsed / SwingTime);
        double S = (1 - Math.cos(Math.PI * T)) / 2;
        double Travel = SwingTime * S * 2 / Math.PI;
        if (T >= 1) SwingElapsed = -1;
        return new Vec3(SwingFrom.X(),
                        SwingFrom.Y() + SwingLift * Travel,
                        SwingFrom.Z() + SwingSpeed * Travel);
    }

    private static Vec3 LimitStep(Vec3 From, Vec3 To, double MaxStep) {
        Vec3 Step = To.Minus(From);
        return Step.Length() > MaxStep ? From.PlusScaled(Step.Normalized(), MaxStep) : To;
    }

    /** The ball's position, clamped to where the blade can be: above the table, below the roof. */
    private static Vec3 Reachable(Vec3 B) {
        double ToPlane = B.Z() - PlaneZ;
        boolean StepIn = B.Y() < StepInHeight && ToPlane > 0 && ToPlane < ReachFwd;
        return new Vec3(Clamp(B.X(), -TableWidth, TableWidth),
                        Clamp(B.Y(), 0.04, MaxReachY),
                        StepIn ? B.Z() : PlaneZ);
    }

    private static double Clamp(double V, double Lo, double Hi) {
        return V < Lo ? Lo : (V > Hi ? Hi : V);
    }
}
