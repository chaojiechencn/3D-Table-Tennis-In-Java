package play;

/**
 * The score, kept by the ITTF's rules rather than by a counter that goes up.
 *
 * This is plain Java with no view and no `World`, for the same reason the rest of `play/` is:
 * scoring is a rule system, rule systems are exactly what a headless test can pin down, and a
 * scoreboard that can only be checked by playing a match is a scoreboard that does not get
 * checked. Every number below is a rule, and every rule has a check in {@link RallyTest}.
 *
 * The rules implemented, from the ITTF Handbook (Laws 2.11 "A Game" and 2.13 "The Order of
 * Serving, Receiving and Ends"):
 *
 * <ul>
 *   <li><b>A game is to 11</b>, and must be won by <b>two clear points</b>. 11-9 ends it; 11-10
 *       does not.</li>
 *   <li><b>At 10-all the game does not end at 11.</b> Play continues until someone leads by two,
 *       so 13-11 and 24-22 are both legal finishes. This is why the target is expressed as
 *       "two clear" and not as a number to reach -- there is no ceiling.</li>
 *   <li><b>Service alternates every two points</b> -- and every <b>one</b> point once the score
 *       reaches 10-all. That switch is the part everyone gets wrong, and it is why the server is
 *       computed from the score below rather than tracked as a flag that gets toggled.</li>
 *   <li><b>A match is the best of an odd number of games</b>, best of 5 by default (first to 3).</li>
 *   <li><b>Ends change after every game</b>, and again in the deciding game as soon as one player
 *       reaches 5. Exposed as {@link #endsChangeAt} for the renderer; the rule does not affect
 *       the score, only which way round the table is drawn.</li>
 * </ul>
 *
 * <b>The server is derived, never stored.</b> A toggled "whose serve" flag is the classic way to
 * get this wrong: it drifts the first time a point is awarded twice, or not at all, and the
 * symptom (the wrong player serving, six points later) points nowhere near the cause. Deriving it
 * from the score means the serve cannot disagree with the scoreboard even if a point is replayed.
 */
public final class Scoreboard {

    /** Which side of the table a point, a game or a serve belongs to. */
    public enum Side {
        PLAYER, OPPONENT;

        /** The other one. */
        public Side other() { return this == PLAYER ? OPPONENT : PLAYER; }
    }

    /** ITTF 2.11.1: a game is won by the first player to reach 11... */
    public static final int POINTS_TO_WIN_GAME = 11;

    /** ...2.11.1 again: ...unless both reach 10, when it takes a two-point lead. */
    public static final int WIN_BY = 2;

    /** ITTF 2.13.3: service changes every 2 points. */
    public static final int SERVE_ROTATION = 2;

    /** ITTF 2.13.3: from 10-all, service changes every point instead. */
    public static final int DEUCE_FROM = 10;

    /** ITTF 2.13.4: in the deciding game, ends change when a score of 5 is first reached. */
    public static final int DECIDER_ENDS_CHANGE_AT = 5;

    private final int gamesToWinMatch;

    private int playerPoints, opponentPoints;
    private int playerGames, opponentGames;

    /**
     * Who served the first point of the CURRENT game.
     *
     * ITTF 2.13.6: the player who served first in a game receives first in the next. Holding the
     * game's opening server (rather than the match's) is what makes {@link #server()} a pure
     * function of the current game's score.
     */
    private Side openingServer = Side.PLAYER;

    /** Best of 5: first to 3 games. */
    public Scoreboard() { this(3); }

    /**
     * @param gamesToWinMatch games needed to take the match -- 3 for best of 5, 4 for best of 7.
     */
    public Scoreboard(int gamesToWinMatch) {
        if (gamesToWinMatch < 1) throw new IllegalArgumentException("a match needs at least one game");
        this.gamesToWinMatch = gamesToWinMatch;
    }

    // ------------------------------------------------------------------ awarding a point

    /**
     * Award the point, and close the game or the match if that point finished one.
     *
     * Awarding into a finished match is a no-op rather than an exception: the rally loop can only
     * find out that the ball is dead by watching events, and it is better for a late duplicate to
     * be absorbed here than for the caller to carry a "have I already scored this" flag that can
     * itself fall out of step. The caller still latches the point -- this is the second line of
     * defence, not the first.
     */
    public void pointTo(Side winner) {
        if (matchOver()) return;

        // The previous game is cleared HERE, on the next point, rather than the instant it was
        // won. Resetting eagerly meant the winning score never reached the screen: the 11th
        // point landed and the HUD went straight to 0-0, so the player never saw 11-9. Rolling
        // over lazily leaves the finished game up until the next rally actually starts.
        if (gameOver()) startNextGame();

        if (winner == Side.PLAYER) playerPoints++; else opponentPoints++;

        // Exactly once: after the roll-over above, gameOver() is false again, so a game can
        // never be counted twice however many points are awarded afterwards.
        if (gameOver()) {
            if (winner == Side.PLAYER) playerGames++; else opponentGames++;
        }
    }

    /** Reset the point score and hand the opening serve to the other player (ITTF 2.13.6). */
    private void startNextGame() {
        playerPoints = 0;
        opponentPoints = 0;
        openingServer = openingServer.other();
    }

    // ------------------------------------------------------------------ reading the state

    public int points(Side s) { return s == Side.PLAYER ? playerPoints : opponentPoints; }

    public int games(Side s) { return s == Side.PLAYER ? playerGames : opponentGames; }

    /** True once both sides have reached 10, when every point is a possible game point. */
    public boolean isDeuce() {
        return playerPoints >= DEUCE_FROM && opponentPoints >= DEUCE_FROM;
    }

    /**
     * Has the CURRENT game been won?
     *
     * Reached 11 AND two clear. Both halves matter: the first alone ends a 11-10 game that is
     * still alive, and the second alone would end a 2-0 game.
     */
    public boolean gameOver() {
        int hi = Math.max(playerPoints, opponentPoints);
        int lo = Math.min(playerPoints, opponentPoints);
        return hi >= POINTS_TO_WIN_GAME && hi - lo >= WIN_BY;
    }

    public boolean matchOver() {
        return playerGames >= gamesToWinMatch || opponentGames >= gamesToWinMatch;
    }

    /** Who has won the match, or null while it is still being played. */
    public Side matchWinner() {
        if (playerGames >= gamesToWinMatch) return Side.PLAYER;
        if (opponentGames >= gamesToWinMatch) return Side.OPPONENT;
        return null;
    }

    /** True in the deciding game -- both sides one game short of the match. */
    public boolean isDecidingGame() {
        return playerGames == gamesToWinMatch - 1 && opponentGames == gamesToWinMatch - 1;
    }

    /**
     * Should the players have changed ends by now?
     *
     * After every game, and in the deciding game the moment either side first reaches 5. Purely a
     * presentation question -- the renderer may honour it or ignore it -- so it reports the rule
     * rather than acting on it.
     */
    public boolean endsChangeAt() {
        return isDecidingGame()
            && (playerPoints == DECIDER_ENDS_CHANGE_AT || opponentPoints == DECIDER_ENDS_CHANGE_AT);
    }

    /**
     * Whose serve it is, derived from the score.
     *
     * Count how many service turns have been completed. Before 10-all a turn is two points; from
     * 10-all it is one. The twenty points played before deuce are therefore ten turns however the
     * game got there, and every point after that adds one more -- which is exactly the ITTF rule,
     * written as arithmetic instead of as a flag someone has to remember to flip.
     */
    public Side server() {
        int played = playerPoints + opponentPoints;
        int turns;
        if (isDeuce()) {
            int beforeDeuce = 2 * DEUCE_FROM;
            turns = beforeDeuce / SERVE_ROTATION + (played - beforeDeuce);
        } else {
            turns = played / SERVE_ROTATION;
        }
        return (turns % 2 == 0) ? openingServer : openingServer.other();
    }

    /** Start the whole match again. */
    public void reset() {
        playerPoints = opponentPoints = 0;
        playerGames = opponentGames = 0;
        openingServer = Side.PLAYER;
    }

    // ------------------------------------------------------------------ display

    /**
     * One line for the HUD: games, the running point score, and who is serving.
     *
     * The serving dot goes next to the server's own score because that is where a player looks
     * for it, and "deuce" is spelled out because at 10-all the rule the player needs to know has
     * changed and the numbers alone do not say so.
     */
    public String line() {
        if (matchOver()) {
            return (matchWinner() == Side.PLAYER ? "YOU WIN" : "OPPONENT WINS")
                 + "   games " + playerGames + " - " + opponentGames;
        }
        String youDot = server() == Side.PLAYER ? "* " : "  ";
        String oppDot = server() == Side.OPPONENT ? " *" : "  ";
        return "games " + playerGames + " - " + opponentGames
             + "    " + youDot + playerPoints + " - " + opponentPoints + oppDot
             + (isDeuce() ? "   deuce" : "");
    }

    @Override public String toString() { return line(); }
}
