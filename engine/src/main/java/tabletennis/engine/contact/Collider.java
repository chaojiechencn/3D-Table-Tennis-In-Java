package tabletennis.engine.contact;

import tabletennis.engine.math.Vec3;

/**
 * A shape the ball can hit: the four questions the single impulse solver asks of any surface.
 * Sealed so a new shape fails an exhaustive switch rather than falling into a wrong default.
 */
public sealed interface Collider permits BoxCollider, BladeCollider {

    /** Nearest surface point to Point; Point itself when it is inside. */
    Vec3 ClosestPoint(Vec3 Point);

    /** Outward normal for a ball centre inside the volume, where ClosestPoint has no direction. */
    Vec3 EscapeNormal(Vec3 Point);

    /** Fraction along From -> To (motion relative to this surface) of first touch, or -1. */
    double Sweep(Vec3 From, Vec3 To);

    /** Velocity of the surface material at the point, rotation included; Zero if static. */
    Vec3 VelocityAt(Vec3 Point);
}
