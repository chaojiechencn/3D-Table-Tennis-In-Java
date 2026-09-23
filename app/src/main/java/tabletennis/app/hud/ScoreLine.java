package tabletennis.app.hud;

import tabletennis.game.match.ScoreSnapshot;
import tabletennis.game.rally.Side;

/** The score as one line of text. */
public final class ScoreLine {

    private ScoreLine() {}

    /** The serving dot sits by the server's score; "deuce" is spelled out because the rule changes. */
    public static String Format(ScoreSnapshot Now) {
        String Games = "games " + Now.PlayerGames() + " - " + Now.OpponentGames();
        if (Now.MatchWinner() != null) {
            return (Now.MatchWinner() == Side.Player ? "YOU WIN" : "OPPONENT WINS") + "   " + Games;
        }
        String PlayerDot = Now.Server() == Side.Player ? "* " : "  ";
        String OpponentDot = Now.Server() == Side.Opponent ? " *" : "  ";
        return Games + "    " + PlayerDot + Now.PlayerPoints() + " - " + Now.OpponentPoints() + OpponentDot
             + (Now.Deuce() ? "   deuce" : "");
    }
}
