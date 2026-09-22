package physics;

import static physics.Constants.*;

/**
 * Solves the launch elevation that lands a ball on a chosen spot, by bisection: range is monotone
 * in elevation over the searched band, and bisection cannot blow up on a diving topspin shot.
 */
public final class Aim {

    private Aim() {}

    /** netClearance is negative into the net and NaN if the shot never reaches it. */
    public record Solution(BallState state, Vec3 landing, double netClearance,
                           double elevationDeg, boolean converged) {}

    private static final double MIN_ELEV = Math.toRadians(-35);
    private static final double MAX_ELEV = Math.toRadians(45);

    /** 28 halvings of the 80 degree bracket is 14 nm of landing error; each contact solves many. */
    private static final int ITERATIONS = 28;

    private static final int MAX_FLIGHT_STEPS = 480 * 6;

    /** Spin as a player describes it, in rev/s relative to the heading; negative top is backspin. */
    public static Vec3 spin(Vec3 headingHoriz, double topRevs, double sideRevs) {
        Vec3 f = new Vec3(headingHoriz.x(), 0, headingHoriz.z()).normalized();
        if (f.lengthSquared() == 0) return Vec3.ZERO;

        Vec3 topAxis = Vec3.UP.cross(f);
        return topAxis.scale(topRevs * 2 * Math.PI)
                      .plus(Vec3.UP.scale(sideRevs * 2 * Math.PI));
    }

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
        boolean tooFast = rangeAt(from, heading, speed, spinVec, lo) > range;
        if (tooFast) return finish(from, heading, speed, spinVec, lo, false);
        boolean tooSlow = rangeAt(from, heading, speed, spinVec, hi) < range;
        if (tooSlow) return finish(from, heading, speed, spinVec, hi, false);

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

    /** Where a shot first meets the table plane, on the table or not. Never ask a World this. */
    public static Vec3 landingPoint(BallState launch) {
        return fly(launch).landing();
    }

    private record Flight(Vec3 landing, double netClearance) {}

    /** Contact-free flight down to the descending crossing of the table plane. */
    private static Flight fly(BallState s) {
        double netClearance = Double.NaN;
        double prevZ = s.pos().z();

        for (int i = 0; i < MAX_FLIGHT_STEPS; i++) {
            BallState next = Integrator.step(s, DT);

            if (Double.isNaN(netClearance) && prevZ > 0 && next.pos().z() <= 0) {
                double t = prevZ / (prevZ - next.pos().z());
                double y = s.pos().y() + (next.pos().y() - s.pos().y()) * t;
                netClearance = y - BALL_R - NET_HEIGHT;
            }
            prevZ = next.pos().z();

            if (next.pos().y() <= BALL_R && next.vel().y() < 0) {
                double t = (s.pos().y() - BALL_R) / (s.pos().y() - next.pos().y());
                return new Flight(Vec3.lerp(s.pos(), next.pos(), Math.max(0, Math.min(1, t))),
                                  netClearance);
            }
            s = next;
        }
        return new Flight(s.pos(), netClearance);
    }
}
