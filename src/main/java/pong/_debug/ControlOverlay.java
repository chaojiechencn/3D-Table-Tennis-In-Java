package pong._debug;

import pong.core.math.Vec3;
import pong.game_objects.ball.Ball;
import pong.systems.control.PlayerReach;
import pong.systems.control.Stroke;

/**
 * The control/reachability readout, toggled with D.
 *
 * It answers one question: "was that the CONTROL MAPPING failing to express where the player
 * wanted the bat, or was the ball genuinely unplayable?" The two look identical on screen and
 * have opposite fixes, which is why the numbers are worth printing at all.
 *
 * Read-only and downstream of everything. The blade's target has already been computed and
 * handed to {@link Stroke} before this is called, so the ball position appearing here can never
 * steer the control path -- the ball is not an input to the player's racket, and this class
 * having no way to write anything is part of how that stays true.
 *
 * It lives under {@code _debug/} for the same reason that directory is spelled with a leading
 * underscore: nothing here is part of the game, and the game does not notice if it is deleted.
 */
public final class ControlOverlay {

    private ControlOverlay() {}

    /**
     * @param ball    where the ball is now
     * @param blade   where the player's racket is now
     * @param target  where the cursor is asking it to be, after the envelope clamped it
     * @param rawAim  where the cursor's ray landed BEFORE the clamp, or null before the mouse
     *                has moved. Shown next to the granted target because "the blade is not where
     *                I pointed" and "the blade cannot go where I pointed" look the same on screen.
     * @param cursorX cursor position in pixels, or NaN before the mouse has moved
     * @param cursorY cursor position in pixels
     */
    public static String readout(Ball ball, Vec3 blade, Vec3 target, Vec3 rawAim,
                                 double cursorX, double cursorY) {
        double dist = PlayerReach.travelDistance(blade, target);
        double travel = PlayerReach.travelTime(blade, target);
        double arrive = PlayerReach.timeToDepth(ball, target.z());

        // Clamped is not the same as unreachable: it is normal to point past the end of the legal
        // region and be held at its edge. Worth showing, because a blade that seems stuck is
        // usually a blade sitting on a clamp.
        boolean clamped = rawAim != null
                && (Math.abs(rawAim.x() - target.x()) > 1e-6
                 || Math.abs(rawAim.z() - target.z()) > 1e-6);

        String verdict;
        if (Double.isNaN(arrive)) verdict = "n/a  (ball not coming to this depth)";
        else if (travel <= arrive) verdict = String.format("YES  (%.0f ms to spare)", (arrive - travel) * 1000);
        else                       verdict = String.format("NO   (%.0f ms short)", (travel - arrive) * 1000);

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
            Double.isNaN(cursorX) ? "(none yet)" : String.format("(%4.0f,%4.0f)", cursorX, cursorY),
            rawAim == null ? "" : String.format("   ray -> x %+.3f  z %+.3f", rawAim.x(), rawAim.z()),
            blade.x(), blade.y(), blade.z(),
            target.x(), target.y(), target.z(), clamped ? "   (clamped)" : "",
            -PlayerReach.MAX_X, PlayerReach.MAX_X, PlayerReach.HIT_Y,
            PlayerReach.Z_NEAR, PlayerReach.Z_FAR,
            dist, travel * 1000, Stroke.TRACK_SPEED,
            ball.pos().x(), ball.pos().y(), ball.pos().z(),
            Double.isNaN(arrive) ? "  --  " : String.format("%.0f ms", arrive * 1000), target.z(),
            verdict);
    }
}
