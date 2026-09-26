package tabletennis.app.hud;

import tabletennis.game.shot.ShotDecision;

/**
 * The spin on the player's last shot, in the words a player uses. Spin about +Y on a ball heading
 * down-table pushes it toward -X through Magnus (w x v), so positive sidespin curves left.
 */
public final class SpinLine {

    private SpinLine() {}

    /** Below this a spin is not worth naming. */
    private static final double Negligible = 1.0;

    public static String Format(ShotDecision Shot) {
        double Top = Shot.TopRevs(), Side = Shot.SideRevs();
        String Forward = Math.abs(Top) < Negligible ? "no topspin"
                       : String.format("%s %.0f rev/s", Top > 0 ? "topspin" : "backspin", Math.abs(Top));
        String Across = Math.abs(Side) < Negligible ? "no sidespin"
                      : String.format("sidespin %.0f rev/s, curves %s", Math.abs(Side), Side > 0 ? "left" : "right");
        return String.format("your spin  %s   %s%s", Forward, Across, Shot.RacketSpin() ? "" : "   (arcade)");
    }
}
