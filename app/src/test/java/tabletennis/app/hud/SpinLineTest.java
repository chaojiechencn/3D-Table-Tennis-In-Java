package tabletennis.app.hud;

import org.junit.jupiter.api.Test;
import tabletennis.engine.math.Vec3;
import tabletennis.game.shot.ShotDecision;

import static tabletennis.testing.Claims.Check;

/** The spin line a player reads after each of their shots. */
final class SpinLineTest {

    @Test
    void SpinIsNamedTheWayAPlayerSaysIt() {
        String Loop = SpinLine.Format(Shot(34.4, 0.2, true));
        String Chop = SpinLine.Format(Shot(-21.6, -12.3, true));
        Check("topspin, backspin and the curve are named, and negligible spin is not",
              Loop.equals("your spin  topspin 34 rev/s   no sidespin")
              && Chop.equals("your spin  backspin 22 rev/s   sidespin 12 rev/s, curves right"),
              "'" + Loop + "' / '" + Chop + "'");
    }

    private static ShotDecision Shot(double Top, double Side, boolean FromRacket) {
        return new ShotDecision(Vec3.Zero, Vec3.Zero, Vec3.Zero, Vec3.Zero, Vec3.Zero, Vec3.Zero, Vec3.Zero,
                                Vec3.Zero, 0, Vec3.Zero, 0, true, null, Top, Side, FromRacket);
    }
}
