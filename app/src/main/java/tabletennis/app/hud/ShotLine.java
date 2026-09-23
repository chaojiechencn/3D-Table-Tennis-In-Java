package tabletennis.app.hud;

import tabletennis.engine.math.Vec3;
import tabletennis.game.shot.ShotDecision;

/** The numbers from the last shot decision that the V overlay does not draw as arrows. */
public final class ShotLine {

    private ShotLine() {}

    public static String Format(ShotDecision Shot) {
        double Rev = 2 * Math.PI;
        Vec3 Spin = Shot.Spin();
        return String.format("shot  %.1f m/s   spin (%+.0f %+.0f %+.0f) rev/s   passes %d   %s",
                             Shot.Speed(), Spin.X() / Rev, Spin.Y() / Rev, Spin.Z() / Rev, Shot.Passes(),
                             Shot.Legal() ? "LEGAL" : "fallback");
    }
}
