package tabletennis.engine;

/** The one fixed step every simulation in the game advances by. Physics never sees a frame time. */
public final class Simulation {

    private Simulation() {}

    public static final int StepsPerSecond = 480;

    /** 1/480 s: a 30 m/s smash moves 6 cm per step and cannot tunnel the 2.5 cm table slab. */
    public static final double Step = 1.0 / StepsPerSecond;
}
