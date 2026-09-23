package tabletennis.engine.flight;

import tabletennis.engine.math.Vec3;

/** Spin as a player describes it: rev/s of topspin and sidespin relative to the ball's heading. */
public final class SpinVector {

    private SpinVector() {}

    /** Negative TopRevs is backspin; positive SideRevs spins about +Y. Zero for a vertical heading. */
    public static Vec3 Of(Vec3 Heading, double TopRevs, double SideRevs) {
        Vec3 Forward = new Vec3(Heading.X(), 0, Heading.Z()).Normalized();
        if (Forward.LengthSquared() == 0) return Vec3.Zero;

        Vec3 TopAxis = Vec3.Up.Cross(Forward);
        return TopAxis.Scale(TopRevs * 2 * Math.PI)
                      .Plus(Vec3.Up.Scale(SideRevs * 2 * Math.PI));
    }
}
