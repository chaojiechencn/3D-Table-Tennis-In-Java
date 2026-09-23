package tabletennis.engine;

/** The net assembly, standing across the table at z = 0. [ITTF] Laws of Table Tennis 2.02. */
public final class NetSpec {

    private NetSpec() {}

    /** 15.25 cm. [ITTF] */
    public static final double Height = 0.1525;

    /** 1.83 m, overhanging the table by 15.25 cm each side. [ITTF] */
    public static final double Width = 1.83;

    /** Real netting is ~1 mm; thicker so a fast ball cannot tunnel between steps. */
    public static final double Thickness = 0.006;
}
