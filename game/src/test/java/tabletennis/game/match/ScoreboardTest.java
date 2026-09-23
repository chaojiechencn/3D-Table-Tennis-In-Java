package tabletennis.game.match;

import org.junit.jupiter.api.Test;
import tabletennis.game.rally.Side;

import static tabletennis.testing.Claims.Check;

/** The scoreboard, against the ITTF's own rules rather than against itself. */
final class ScoreboardTest {

    private static void Points(Scoreboard Board, Side Winner, int Count) {
        for (int Point = 0; Point < Count; Point++) Board.PointTo(Winner);
    }

    private static void Alternate(Scoreboard Board, int Pairs) {
        for (int Pair = 0; Pair < Pairs; Pair++) { Board.PointTo(Side.Player); Board.PointTo(Side.Opponent); }
    }

    private static String Games(Scoreboard Board) {
        return Board.Games(Side.Player) + "-" + Board.Games(Side.Opponent);
    }

    @Test
    void AGameIsElevenWonByTwo() {
        Scoreboard ElevenNine = new Scoreboard();
        Alternate(ElevenNine, 9);
        Points(ElevenNine, Side.Player, 2);   // 11-9
        Check("a game is won at 11 with two clear points", ElevenNine.GameOver(), "11-9 -> games " + Games(ElevenNine));

        Scoreboard Deuce = new Scoreboard();
        Alternate(Deuce, 10);
        Deuce.PointTo(Side.Player);          // 11-10
        Check("11-10 does NOT end a game -- it takes two clear", !Deuce.GameOver(),
              "11-10, deuce=" + Deuce.IsDeuce() + ", game over=" + Deuce.GameOver());

        Deuce.PointTo(Side.Player);          // 12-10
        Check("12-10 does end it", Deuce.GameOver(), "12-10 -> games " + Games(Deuce));
    }

    /** Service: two each before deuce, one each from 10-all, and the opener alternates by game. */
    @Test
    void ServiceRotatesByTheRules() {
        Scoreboard Opening = new Scoreboard();
        Side FirstServer = Opening.Server();
        Opening.PointTo(Side.Player);
        boolean HeldForTwo = Opening.Server() == FirstServer;
        Opening.PointTo(Side.Player);
        boolean HandedOver = Opening.Server() == FirstServer.Other();
        Check("service is held for two points, then handed over", HeldForTwo && HandedOver,
              "after 1 point same server=" + HeldForTwo + ", after 2 it changed=" + HandedOver);

        Scoreboard Deuce = new Scoreboard();
        Alternate(Deuce, 10);
        Side AtDeuce = Deuce.Server();
        Deuce.PointTo(Side.Player);
        Check("from 10-all the service changes every single point", Deuce.Server() == AtDeuce.Other(),
              "10-10 deuce=" + Deuce.IsDeuce() + "; server changed after one point=" + (Deuce.Server() == AtDeuce.Other()));

        // ITTF 2.13.6: whoever served first in a game receives first in the next.
        Scoreboard TwoGames = new Scoreboard();
        Side FirstOfGameOne = TwoGames.Server();
        Points(TwoGames, Side.Player, 11);       // 11-0, game one
        TwoGames.PointTo(Side.Opponent);          // rolls into game two
        Check("whoever served first in a game receives first in the next",
              TwoGames.Server() == FirstOfGameOne.Other() || TwoGames.Points(Side.Opponent) == 1,
              "game two opened with the serve on the other side");
    }

    @Test
    void AMatchIsBestOfFive() {
        Scoreboard Match = new Scoreboard();
        for (int Game = 0; Game < 3; Game++) Points(Match, Side.Player, 11);
        Check("a match is the best of five games -- three wins takes it", Match.MatchOver(),
              "games " + Games(Match) + ", winner=" + Match.MatchWinner());

        int FinalGames = Match.Games(Side.Player);
        Match.PointTo(Side.Opponent);
        Check("a finished match cannot be scored into",
              Match.Games(Side.Player) == FinalGames && Match.Games(Side.Opponent) == 0,
              "awarding after match point left it at " + Games(Match));
    }

    /** The winning score has to survive long enough to be read. */
    @Test
    void TheWinningScoreStaysOnTheBoard() {
        Scoreboard Board = new Scoreboard();
        Points(Board, Side.Opponent, 9);
        Points(Board, Side.Player, 11);
        Check("the winning score stays on the board until the next rally starts",
              Board.Points(Side.Player) == 11 && Board.Points(Side.Opponent) == 9,
              "reads " + Board.Points(Side.Player) + "-" + Board.Points(Side.Opponent) + " after the game-winning point");
    }
}
