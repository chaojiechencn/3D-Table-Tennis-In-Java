package tabletennis.game;

/** The two ends of the table. The player's end is +Z, the opponent's -Z. */
public enum Side {
    Player,
    Opponent;

    public Side Other() { return this == Player ? Opponent : Player; }

    /** Whose half a point on the table lies in. */
    public static Side HalfAt(double Z) { return Z < 0 ? Opponent : Player; }
}
