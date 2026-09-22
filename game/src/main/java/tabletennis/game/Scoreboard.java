package tabletennis.game;

/**
 * The match score by ITTF Laws 2.11 and 2.13: games to 11 won by two with no ceiling, service
 * every two points and every point from 10-all, best of 5. The server is DERIVED from the score,
 * never stored, so it cannot drift from the points.
 */
public final class Scoreboard {

    public enum Side {
        PLAYER, OPPONENT;

        public Side other() { return this == PLAYER ? OPPONENT : PLAYER; }
    }

    /** ITTF 2.11.1 */
    public static final int POINTS_TO_WIN_GAME = 11;
    public static final int WIN_BY = 2;
    /** ITTF 2.13.3 */
    public static final int SERVE_ROTATION = 2;
    public static final int DEUCE_FROM = 10;

    /** Read-only, for display; matchWinner is null until the match is decided. */
    public record Snapshot(int playerPoints, int opponentPoints, int playerGames, int opponentGames,
                           Side server, boolean deuce, Side matchWinner) {}

    private final int gamesToWinMatch;
    private int playerPoints, opponentPoints;
    private int playerGames, opponentGames;

    /** ITTF 2.13.6: whoever served first in a game receives first in the next. */
    private Side openingServer = Side.PLAYER;

    public Scoreboard() { this(3); }

    public Scoreboard(int gamesToWinMatch) {
        if (gamesToWinMatch < 1) throw new IllegalArgumentException("a match needs at least one game");
        this.gamesToWinMatch = gamesToWinMatch;
    }

    /**
     * Award a point. A finished game is cleared on the NEXT point, so its winning score stays on
     * screen until play resumes; awarding into a finished match is absorbed, not an error.
     */
    public void pointTo(Side winner) {
        if (matchOver()) return;
        if (gameOver()) startNextGame();

        if (winner == Side.PLAYER) playerPoints++; else opponentPoints++;
        if (gameOver()) {
            if (winner == Side.PLAYER) playerGames++; else opponentGames++;
        }
    }

    private void startNextGame() {
        playerPoints = 0;
        opponentPoints = 0;
        openingServer = openingServer.other();
    }

    public int points(Side s) { return s == Side.PLAYER ? playerPoints : opponentPoints; }

    public int games(Side s) { return s == Side.PLAYER ? playerGames : opponentGames; }

    public boolean isDeuce() {
        return playerPoints >= DEUCE_FROM && opponentPoints >= DEUCE_FROM;
    }

    public boolean gameOver() {
        int hi = Math.max(playerPoints, opponentPoints);
        int lo = Math.min(playerPoints, opponentPoints);
        return hi >= POINTS_TO_WIN_GAME && hi - lo >= WIN_BY;
    }

    public boolean matchOver() { return matchWinner() != null; }

    public Side matchWinner() {
        if (playerGames >= gamesToWinMatch) return Side.PLAYER;
        if (opponentGames >= gamesToWinMatch) return Side.OPPONENT;
        return null;
    }

    /** Completed service turns: two points each before 10-all, one each after. */
    public Side server() {
        int played = playerPoints + opponentPoints;
        int beforeDeuce = 2 * DEUCE_FROM;
        int turns = isDeuce() ? beforeDeuce / SERVE_ROTATION + (played - beforeDeuce)
                              : played / SERVE_ROTATION;
        return turns % 2 == 0 ? openingServer : openingServer.other();
    }

    public Snapshot snapshot() {
        return new Snapshot(playerPoints, opponentPoints, playerGames, opponentGames,
                            server(), isDeuce(), matchWinner());
    }
}
