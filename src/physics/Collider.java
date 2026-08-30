package physics;

/**
 * A shape the ball can hit.
 *
 * This exists so there can still be exactly ONE impulse solver once a paddle joins the table,
 * the net and the floor. {@link Contacts#applyImpulse} never needed to know it was talking to
 * a box -- it only ever asks a surface four questions, and those four questions are this
 * interface. Everything about restitution, friction and spin transfer stays in one place, and
 * a new shape only has to answer where its surface is.
 *
 * The fourth question is the one that matters for a paddle. A table does not move, so the
 * three original surfaces answer {@link Vec3#ZERO} and nothing changes for them. A paddle
 * does move, and the whole of its physics -- the pace it puts on the ball, the spin a brushing
 * stroke generates -- is the difference between the ball's velocity and the surface's.
 *
 * Sealed because the set of shapes is small and closed, and because an exhaustive switch is
 * a better error than a silently wrong default if a fifth one ever appears.
 */
public sealed interface Collider permits Contacts.Box, Paddle.Blade {

    /** Nearest point on the surface to p. Equals p when p is inside the volume. */
    Vec3 closestPoint(Vec3 p);

    /**
     * Unit outward normal to use when the ball's CENTRE is inside the volume, where the
     * closest point degenerates and cannot give a direction. The thin net is the case that
     * forced this to exist.
     */
    Vec3 escapeNormal(Vec3 p);

    /**
     * Swept test: the fraction along p0 -> p1 at which a ball of radius {@code BALL_R} first
     * touches this surface, or -1 if it never does.
     *
     * The caller passes the ball's motion RELATIVE to this surface, so a moving collider does
     * not need to do anything special here -- it is already in its own frame.
     */
    double sweep(Vec3 p0, Vec3 p1);

    /**
     * Velocity of the material point of the surface at {@code point}, in m/s.
     *
     * {@link Vec3#ZERO} for anything bolted to the floor. Note this is the velocity of the
     * SURFACE, including any contribution from its own rotation -- it is what the impulse
     * solver subtracts to get the relative approach and the relative slip.
     */
    Vec3 velocityAt(Vec3 point);
}
