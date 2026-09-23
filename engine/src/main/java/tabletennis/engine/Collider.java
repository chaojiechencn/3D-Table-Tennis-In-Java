package tabletennis.engine;

/**
 * A shape the ball can hit: the four questions the single impulse solver asks of any surface.
 * Sealed so a new shape fails an exhaustive switch rather than falling into a wrong default.
 */
public sealed interface Collider permits Contacts.Box, Paddle.Blade {

    /** Nearest surface point to p; p itself when p is inside. */
    Vec3 ClosestPoint(Vec3 P);

    /** Outward normal for a ball centre inside the volume, where closestPoint has no direction. */
    Vec3 EscapeNormal(Vec3 P);

    /** Fraction along p0 -> p1 (motion relative to this surface) of first touch, or -1. */
    double Sweep(Vec3 P0, Vec3 P1);

    /** Velocity of the surface material at the point, rotation included; ZERO if static. */
    Vec3 VelocityAt(Vec3 Point);
}
