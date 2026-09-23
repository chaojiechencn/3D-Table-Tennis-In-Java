package tabletennis.app.hud;

import org.junit.jupiter.api.Test;
import tabletennis.game.match.ScoreSnapshot;
import tabletennis.game.rally.Side;

import static tabletennis.testing.Claims.Check;

/** The score line a player reads between points. */
final class ScoreLineTest {

    @Test
    void TheServingDotSitsByTheServer() {
        String PlayerServes = ScoreLine.Format(new ScoreSnapshot(3, 2, 1, 0, Side.Player, false, null));
        String OpponentServes = ScoreLine.Format(new ScoreSnapshot(3, 2, 1, 0, Side.Opponent, false, null));
        Check("the serving dot sits beside the server's own score",
              PlayerServes.equals("games 1 - 0    * 3 - 2  ") && OpponentServes.equals("games 1 - 0      3 - 2 *"),
              "'" + PlayerServes + "' / '" + OpponentServes + "'");
    }

    @Test
    void DeuceAndTheWinnerAreSpelledOut() {
        String Deuce = ScoreLine.Format(new ScoreSnapshot(10, 10, 0, 0, Side.Player, true, null));
        String Won = ScoreLine.Format(new ScoreSnapshot(0, 0, 3, 1, Side.Player, false, Side.Player));
        Check("deuce is spelled out, and a finished match names its winner",
              Deuce.endsWith("   deuce") && Won.equals("YOU WIN   games 3 - 1"),
              "'" + Deuce + "' / '" + Won + "'");
    }
}
