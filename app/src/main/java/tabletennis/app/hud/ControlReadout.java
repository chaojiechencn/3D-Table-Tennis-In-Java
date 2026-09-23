package tabletennis.app.hud;

import tabletennis.engine.BallState;
import tabletennis.engine.math.Vec3;
import tabletennis.game.control.CursorFollower;
import tabletennis.game.control.ReachEnvelope;
import tabletennis.game.control.ReachTiming;

/**
 * The D overlay: did the control mapping fail, or was the ball unplayable? Those look identical on
 * screen and have opposite fixes. It reads the ball, so it runs downstream of the blade's target,
 * never on the path that sets it.
 */
public final class ControlReadout {

    private ControlReadout() {}

    /** Where the cursor was, where its ray met the hitting plane, and the target it became. */
    public record Cursor(double X, double Y, Vec3 RawAim, Vec3 Aim) {}

    public static String Format(Cursor Mouse, Vec3 Blade, BallState Ball) {
        Vec3 Target = Mouse.Aim() != null ? Mouse.Aim() : Blade;
        Vec3 Raw = Mouse.RawAim();

        double Distance = ReachTiming.TravelDistance(Blade, Target);
        double Travel = ReachTiming.TravelTime(Blade, Target);
        double Arrival = ReachTiming.TimeToDepth(Ball, Target.Z());
        boolean Clamped = Raw != null
                && (Math.abs(Raw.X() - Target.X()) > 1e-6 || Math.abs(Raw.Z() - Target.Z()) > 1e-6);
        Vec3 At = Ball.Position();

        return String.format("""
            CONTROL  [D]
              cursor     %s px%s
              racket     x %+.3f  y %+.3f  z %+.3f
              target     x %+.3f  y %+.3f  z %+.3f%s
              bounds     x [%+.2f, %+.2f]   y %.3f fixed   z [%.2f, %.2f]
              travel     %.3f m  ->  %.0f ms at %.1f m/s
              ball       x %+.3f  y %+.3f  z %+.3f
              arrival    %s  (to racket depth z %+.3f)
              reachable  %s""",
            Double.isNaN(Mouse.X()) ? "(none yet)" : String.format("(%4.0f,%4.0f)", Mouse.X(), Mouse.Y()),
            Raw == null ? "" : String.format("   ray -> x %+.3f  z %+.3f", Raw.X(), Raw.Z()),
            Blade.X(), Blade.Y(), Blade.Z(),
            Target.X(), Target.Y(), Target.Z(), Clamped ? "   (clamped)" : "",
            -ReachEnvelope.MaxX, ReachEnvelope.MaxX, ReachEnvelope.HitY, ReachEnvelope.ZNear, ReachEnvelope.ZFar,
            Distance, Travel * 1000, CursorFollower.TrackSpeed,
            At.X(), At.Y(), At.Z(),
            Double.isNaN(Arrival) ? "  --  " : String.format("%.0f ms", Arrival * 1000), Target.Z(),
            Verdict(Travel, Arrival));
    }

    private static String Verdict(double Travel, double Arrival) {
        if (Double.isNaN(Arrival)) return "n/a  (ball not coming to this depth)";
        if (Travel <= Arrival) return String.format("YES  (%.0f ms to spare)", (Arrival - Travel) * 1000);
        return String.format("NO   (%.0f ms short)", (Travel - Arrival) * 1000);
    }
}
