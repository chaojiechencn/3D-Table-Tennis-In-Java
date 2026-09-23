package tabletennis.engine.flight;

import org.junit.jupiter.api.Test;
import tabletennis.engine.BallState;
import tabletennis.engine.math.Vec3;

import static tabletennis.testing.Claims.Check;

/** The headline claim of the physics: spin curves the ball, and in the right direction. */
final class MagnusFlightTest {

    private static final Vec3 LaunchPoint = new Vec3(0, 0.30, 1.50);
    private static final Vec3 LaunchVelocity = new Vec3(0, 0.4, -9.0);

    /** Identical launch, three spins. */
    @Test
    void MagnusCurvesTheRightWay() {
        double Heavy = 90 * 2 * Math.PI;

        double Flat = LandingZ(BallState.At(LaunchPoint, LaunchVelocity, Vec3.Zero));
        double Top = LandingZ(BallState.At(LaunchPoint, LaunchVelocity, new Vec3(-Heavy, 0, 0)));
        double Back = LandingZ(BallState.At(LaunchPoint, LaunchVelocity, new Vec3(Heavy, 0, 0)));

        // Travelling toward -Z, so "shorter" means a LARGER (less negative) landing z.
        Check("topspin lands shorter than no spin",
              Top > Flat + 0.05,
              String.format("topspin z=%.3f vs flat z=%.3f (%.0f cm shorter)", Top, Flat, (Top - Flat) * 100));

        Check("backspin carries further than no spin",
              Back < Flat - 0.05,
              String.format("backspin z=%.3f vs flat z=%.3f (%.0f cm longer)", Back, Flat, (Flat - Back) * 100));

        Check("the topspin/backspin spread is large enough to see on screen",
              (Top - Back) > 0.30,
              String.format("%.0f cm apart", (Top - Back) * 100));
    }

    /** Sidespin about +Y must push the ball toward -X: verified against the cross product. */
    @Test
    void SidespinDeflectsTheRightWay() {
        double Spin = 90 * 2 * Math.PI;

        double Left = LandingX(BallState.At(LaunchPoint, LaunchVelocity, new Vec3(0, Spin, 0)));
        double Right = LandingX(BallState.At(LaunchPoint, LaunchVelocity, new Vec3(0, -Spin, 0)));

        Check("sidespin about +Y deflects toward -X", Left < -0.02, String.format("landed x=%.3f m", Left));
        Check("sidespin about -Y deflects toward +X", Right > 0.02, String.format("landed x=%.3f m", Right));
        Check("the two sidespins are mirror images",
              Math.abs(Left + Right) < 1e-6,
              String.format("%.4f vs %.4f", Left, Right));
    }

    private static double LandingZ(BallState Launch) { return TrialFlight.LandingPoint(Launch).Z(); }
    private static double LandingX(BallState Launch) { return TrialFlight.LandingPoint(Launch).X(); }
}
