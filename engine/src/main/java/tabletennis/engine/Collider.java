package tabletennis.engine;

/**
 * A shape the ball can hit: the four questions the single impulse solver asks of any surface.
 * Sealed so a new shape fails an exhaustive switch rather than falling into a wrong default.
 */
public sealed interface Collider permits Contacts.Box, Paddle.Blade {

    /** Nearest surface point to p; p itself when p is inside. */
    Vec3 closestPoint(Vec3 p);

    /** Outward normal for a ball centre inside the volume, where closestPoint has no direction. */
    Vec3 escapeNormal(Vec3 p);

    /** Fraction along p0 -> p1 (motion relative to this surface) of first touch, or -1. */
    double sweep(Vec3 p0, Vec3 p1);

    /** Velocity of the surface material at the point, rotation included; ZERO if static. */
    Vec3 velocityAt(Vec3 point);
}
