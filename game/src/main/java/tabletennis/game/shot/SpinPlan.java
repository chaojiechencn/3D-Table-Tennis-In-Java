package tabletennis.game.shot;

import tabletennis.engine.flight.SpinVector;
import tabletennis.engine.math.Vec3;

/** Topspin and sidespin in rev/s, relative to whichever way the shot finally heads. */
record SpinPlan(double Top, double Side) {

    /** As an angular velocity, for a ball leaving with Velocity. */
    Vec3 VectorFor(Vec3 Velocity) {
        return SpinVector.Of(new Vec3(Velocity.X(), 0, Velocity.Z()), Top, Side);
    }
}
