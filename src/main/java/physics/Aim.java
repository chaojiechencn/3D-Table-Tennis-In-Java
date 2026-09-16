package physics;

import static physics.Constants.*;

/**
 * Solves for the launch angle that puts the ball on a chosen spot of the table.
 *
 * The first attempt at the demo hard-coded a velocity for every shot, and almost every one
 * of them sailed off the far end. That is not a bug in the physics, it is the physics being
 * right: with drag this strong and Magnus this strong, the launch angle that lands a 15 m/s
 * ball on the table is nothing like the one that lands an 8 m/s ball, and eyeballing it does
 * not work. So the demo states its intent - "15 m/s, 110 rev/s topspin, landing here" - and
 * this class finds the angle.
 *
 * It also pays for itself twice. In September the AI has to answer the same question in the
 * other direction ("where should I stand, and what shot gets it back") and it can call
 * straight into this.
 *
 * Method: bisection on elevation. Horizontal range at the descending crossing of the table
 * plane increases monotonically with elevation over the searched band, so bisection is
 * both sufficient and immune to the derivative blow-ups that Newton hits when a heavy
 * topspin shot starts diving.
 */
public final class Aim {

    private Aim() {}

    /**
     * @param state          the launch state that achieves the target
     * @param landing        where it actually crosses the table plane on the way down
     * @param netClearance   height above the net cord as it passes z = 0; negative means it
     *                       hits the net, NaN means it never gets there
     * @param elevationDeg   the solved launch elevation
     * @param converged      false if the target is simply out of reach at this speed
     */
    public record Solution(BallState state, Vec3 landing, double netClearance,
                           double elevationDeg, boolean converged) {}

    private static final double MIN_ELEV = Math.toRadians(-35);
    private static final double MAX_ELEV = Math.toRadians(45);
    /**
     * Bisection halvings of the elevation bracket.
     *
     * The bracket is 80 degrees wide, so 28 halvings resolve the launch angle to 1.4 rad / 2^28
     * = 5e-9 rad -- five nanoradians, which over a 2.7 m shot is 14 nanometres of landing
     * position. The ball is 40 mm across.
     *
     * It was 60, which bisects to the last bit of a double and is free when this only ever ran
     * twelve times at startup to solve the shot presets. It is not free now: play/ShotAssist
     * calls this for every candidate shot, a dozen or more on the frame a racket contact lands
     * on, and each iteration flies a whole trajectory. Cutting the count less than halves the
     * accuracy of anything that matters and more than halves the cost of a contact.
     */
    private static final int ITERATIONS = 28;

    /**
     * Build a spin vector from spin expressed the way a player would describe it, relative
     * to where the ball is going.
     *
     * @param headingHoriz  horizontal direction of travel (need not be normalised)
     * @param topRevs       revolutions per second of topspin; negative for backspin
     * @param sideRevs      revolutions per second of sidespin about the vertical axis
     */
    public static Vec3 spin(Vec3 headingHoriz, double topRevs, double sideRevs) {
        Vec3 f = new Vec3(headingHoriz.x(), 0, headingHoriz.z()).normalized();
        if (f.lengthSquared() == 0) return Vec3.ZERO;

        // For a ball heading toward -Z this gives -X, which is topspin. Deriving the axis
        // rather than writing it down keeps shots aimed across the table honest too.
        Vec3 topAxis = Vec3.UP.cross(f);
        return topAxis.scale(topRevs * 2 * Math.PI)
                      .plus(Vec3.UP.scale(sideRevs * 2 * Math.PI));
    }

    /**
     * Find the launch state that sends the ball from {@code from} to {@code target} at the
     * given speed, with spin described relative to the direction of travel.
     */
    public static Solution atTarget(Vec3 from, Vec3 target, double speed,
                                    double topRevs, double sideRevs) {
        Vec3 flat = new Vec3(target.x() - from.x(), 0, target.z() - from.z());
        double range = flat.length();
        if (range < 1e-6) {
            return new Solution(BallState.at(from, Vec3.ZERO, Vec3.ZERO), from, Double.NaN, 0, false);
        }
        Vec3 heading = flat.scale(1.0 / range);
        Vec3 spinVec = spin(heading, topRevs, sideRevs);

        double lo = MIN_ELEV, hi = MAX_ELEV;
        if (rangeAt(from, heading, speed, spinVec, lo) > range) {
            // Even the flattest allowed shot overshoots: too fast for this target.
            return finish(from, heading, speed, spinVec, lo, false);
        }
        if (rangeAt(from, heading, speed, spinVec, hi) < range) {
            // Cannot reach even lobbed: too slow.
            return finish(from, heading, speed, spinVec, hi, false);
        }

        for (int i = 0; i < ITERATIONS; i++) {
            double mid = 0.5 * (lo + hi);
            if (rangeAt(from, heading, speed, spinVec, mid) < range) lo = mid; else hi = mid;
        }
        return finish(from, heading, speed, spinVec, 0.5 * (lo + hi), true);
    }

    private static Solution finish(Vec3 from, Vec3 heading, double speed, Vec3 spinVec,
                                   double elev, boolean converged) {
        BallState launch = BallState.at(from, velocity(heading, speed, elev), spinVec);
        Flight f = fly(launch);
        return new Solution(launch, f.landing, f.netClearance, Math.toDegrees(elev), converged);
    }

    private static Vec3 velocity(Vec3 heading, double speed, double elevation) {
        return heading.scale(speed * Math.cos(elevation))
                      .plus(Vec3.UP.scale(speed * Math.sin(elevation)));
    }

    private static double rangeAt(Vec3 from, Vec3 heading, double speed, Vec3 spinVec, double elev) {
        Flight f = fly(BallState.at(from, velocity(heading, speed, elev), spinVec));
        Vec3 d = f.landing.minus(from);
        return Math.sqrt(d.x() * d.x() + d.z() * d.z());
    }

    /**
     * Where a shot would first meet the plane of the table top, whether or not that spot is
     * actually on the table.
     *
     * Measuring "how far did it carry" through {@link World} instead would only report shots
     * that legally land, so a floaty backspin ball that sails past the end would come back as
     * "no result" -- exactly the case you most want to measure when comparing spins.
     */
    public static Vec3 landingPoint(BallState launch) {
        return fly(launch).landing();
    }

    private record Flight(Vec3 landing, double netClearance) {}

    /**
     * Free flight only, down to the descending crossing of the table plane. No contacts:
     * the point is to find out WHERE it would first touch, so bouncing would be circular.
     */
    private static Flight fly(BallState s) {
        double netClearance = Double.NaN;
        double prevZ = s.pos().z();

        for (int i = 0; i < 480 * 6; i++) {
            BallState next = Integrator.step(s, DT);

            // Sample the height as it crosses the plane of the net.
            if (Double.isNaN(netClearance) && prevZ > 0 && next.pos().z() <= 0) {
                double t = prevZ / (prevZ - next.pos().z());
                double y = s.pos().y() + (next.pos().y() - s.pos().y()) * t;
                netClearance = y - BALL_R - NET_HEIGHT;
            }
            prevZ = next.pos().z();

            if (next.pos().y() <= BALL_R && next.vel().y() < 0) {
                // Interpolate to the exact crossing so the solver is smooth.
                double t = (s.pos().y() - BALL_R) / (s.pos().y() - next.pos().y());
                return new Flight(Vec3.lerp(s.pos(), next.pos(), Math.max(0, Math.min(1, t))),
                                  netClearance);
            }
            s = next;
        }
        return new Flight(s.pos(), netClearance);
    }
}
