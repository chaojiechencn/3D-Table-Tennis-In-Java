package tabletennis.engine;

/** The regulation ball. [ITTF] Technical Leaflet T3 "The Ball" and Laws of Table Tennis 2.03. */
public final class BallSpec {

    private BallSpec() {}

    /** 40 mm diameter. [ITTF] */
    public static final double Radius = 0.020;

    /** 2.7 g. [ITTF] */
    public static final double Mass = 0.0027;

    /** Hollow shell, so (2/3)mr², not the solid sphere's (2/5)mr². [ITTF] */
    public static final double Inertia = (2.0 / 3.0) * Mass * Radius * Radius;

    public static final double CrossSection = Math.PI * Radius * Radius;
}
