package tabletennis.game.shot;

import tabletennis.engine.flight.SpinVector;
import tabletennis.engine.math.Vec3;

import static tabletennis.engine.math.Numeric.Clamp;

/** Topspin and sidespin in rev/s, relative to whichever way the shot finally heads. */
record SpinPlan(double Top, double Side) {

    /** A spin read back relative to the heading of Velocity: the inverse of VectorFor. */
    static SpinPlan Of(Vec3 Velocity, Vec3 Spin) {
        Vec3 Forward = new Vec3(Velocity.X(), 0, Velocity.Z()).Normalized();
        Vec3 TopAxis = Vec3.Up.Cross(Forward);
        return new SpinPlan(Spin.Dot(TopAxis) / (2 * Math.PI), Spin.Y() / (2 * Math.PI));
    }

    /** Topspin limited to [-TopLimit, TopLimit] and sidespin to [-SideLimit, SideLimit]. */
    SpinPlan CappedAt(double TopLimit, double SideLimit) {
        return new SpinPlan(Clamp(Top, -TopLimit, TopLimit), Clamp(Side, -SideLimit, SideLimit));
    }

    static SpinPlan Lerp(SpinPlan From, SpinPlan To, double Fraction) {
        return new SpinPlan(From.Top + (To.Top - From.Top) * Fraction, From.Side + (To.Side - From.Side) * Fraction);
    }

    /** As an angular velocity, for a ball leaving with Velocity. */
    Vec3 VectorFor(Vec3 Velocity) {
        return SpinVector.Of(new Vec3(Velocity.X(), 0, Velocity.Z()), Top, Side);
    }
}
