package tabletennis.engine;

/**
 * The regulation table. [ITTF] Laws of Table Tennis 2.01. The physics origin is the centre of
 * the playing surface, so the far end is at z = -HalfLength and the floor at y = -Height.
 */
public final class TableSpec {

    private TableSpec() {}

    /** 2.74 m. [ITTF] */
    public static final double Length = 2.74;

    /** 1.525 m. [ITTF] */
    public static final double Width = 1.525;

    /** 0.76 m. [ITTF] */
    public static final double Height = 0.76;

    /** Collision thickness of the top. */
    public static final double TopThickness = 0.025;

    public static final double HalfLength = Length / 2;
    public static final double HalfWidth = Width / 2;
}
