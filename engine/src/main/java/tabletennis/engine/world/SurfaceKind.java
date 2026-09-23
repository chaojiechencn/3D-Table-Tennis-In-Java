package tabletennis.engine.world;

/**
 * What a surface is, and how hard the ball must meet it for the contact to count as an event.
 * Slower contacts are real but not events: a dying ball touches the table dozens of times.
 */
public enum SurfaceKind {
    Net(0.05),
    Table(0.35),
    Floor(0.4),
    Blade(0.3);

    private final double NotableAbove;

    SurfaceKind(double NotableAbove) {
        this.NotableAbove = NotableAbove;
    }

    public boolean IsNotable(double ImpactSpeed) {
        return ImpactSpeed > NotableAbove;
    }
}
