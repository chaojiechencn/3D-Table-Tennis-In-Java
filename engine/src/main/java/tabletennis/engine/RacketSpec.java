package tabletennis.engine;

/** The racket blade as the contact solver sees it: a disc slab. */
public final class RacketSpec {

    private RacketSpec() {}

    /** ITTF Law 2.4.1 sets no size; a 75 mm disc matches a typical 157 x 150 mm head. */
    public static final double BladeRadius = 0.075;

    /** Blade (~6 mm) plus two rubbers capped at 4.05 mm each by ITTF Law 2.4.3. */
    public static final double BladeThickness = 0.015;
}
