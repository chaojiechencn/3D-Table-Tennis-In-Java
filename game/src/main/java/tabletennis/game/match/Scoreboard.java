package tabletennis.game.match;

import tabletennis.game.rally.Side;

/**
 * The match score by ITTF Laws 2.11 and 2.13: games to 11 won by two with no ceiling, service
 * every two points and every point from 10-all, best of 5. The server is DERIVED from the score,
 * never stored, so it cannot drift from the points.
 */
public final class Scoreboard {

    /** ITTF 2.11.1 */
    public static final int PointsToWinGame = 11;
    public static final int WinBy = 2;

    /** ITTF 2.13.3 */
    public static final int ServeRotation = 2;
    public static final int DeuceFrom = 10;

    /** Best of five: first to three games. */
    private static final int DefaultGamesToWinMatch = 3;

    private final int GamesToWinMatch;
    private int PlayerPoints, OpponentPoints;
    private int PlayerGames, OpponentGames;

    /** ITTF 2.13.6: whoever served first in a game receives first in the next. */
    private Side OpeningServer = Side.Player;

    public Scoreboard() { this(DefaultGamesToWinMatch); }

    public Scoreboard(int GamesToWinMatch) {
        if (GamesToWinMatch < 1) throw new IllegalArgumentException("a match needs at least one game");
        this.GamesToWinMatch = GamesToWinMatch;
    }

    /**
     * Award a point. A finished game is cleared on the NEXT point, so its winning score stays on
     * screen until play resumes; awarding into a finished match is absorbed, not an error.
     */
    public void PointTo(Side Winner) {
        if (MatchOver()) return;
        if (GameOver()) StartNextGame();

        if (Winner == Side.Player) PlayerPoints++; else OpponentPoints++;
        if (GameOver()) {
            if (Winner == Side.Player) PlayerGames++; else OpponentGames++;
        }
    }

    private void StartNextGame() {
        PlayerPoints = 0;
        OpponentPoints = 0;
        OpeningServer = OpeningServer.Other();
    }

    public int Points(Side Of) { return Of == Side.Player ? PlayerPoints : OpponentPoints; }

    public int Games(Side Of) { return Of == Side.Player ? PlayerGames : OpponentGames; }

    public boolean IsDeuce() {
        return PlayerPoints >= DeuceFrom && OpponentPoints >= DeuceFrom;
    }

    public boolean GameOver() {
        int Leader = Math.max(PlayerPoints, OpponentPoints);
        int Trailer = Math.min(PlayerPoints, OpponentPoints);
        return Leader >= PointsToWinGame && Leader - Trailer >= WinBy;
    }

    public boolean MatchOver() { return MatchWinner() != null; }

    /** Null until the match is decided. */
    public Side MatchWinner() {
        if (PlayerGames >= GamesToWinMatch) return Side.Player;
        if (OpponentGames >= GamesToWinMatch) return Side.Opponent;
        return null;
    }

    /** Completed service turns: two points each before 10-all, one each after. */
    public Side Server() {
        int Played = PlayerPoints + OpponentPoints;
        int BeforeDeuce = 2 * DeuceFrom;
        int Turns = IsDeuce() ? BeforeDeuce / ServeRotation + (Played - BeforeDeuce)
                              : Played / ServeRotation;
        return Turns % 2 == 0 ? OpeningServer : OpeningServer.Other();
    }

    public ScoreSnapshot Snapshot() {
        return new ScoreSnapshot(PlayerPoints, OpponentPoints, PlayerGames, OpponentGames,
                                 Server(), IsDeuce(), MatchWinner());
    }
}
