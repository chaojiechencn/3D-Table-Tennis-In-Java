package tabletennis.game.match;

import tabletennis.game.rally.Side;

/** The score at one moment, for display. MatchWinner is null until the match is decided. */
public record ScoreSnapshot(int PlayerPoints, int OpponentPoints, int PlayerGames, int OpponentGames,
                            Side Server, boolean Deuce, Side MatchWinner) {}
