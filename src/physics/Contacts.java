package physics;

import physics.Constants.Material;

import static physics.Constants.*;

/**
 * ONE collision solver, used for the table, the net and the floor.
 *
 * Everything the ball can hit is an axis-aligned box, and everything it can hit responds the
 * same way: a normal impulse with restitution, plus a tangential impulse that either grips
 * or slides. The three surfaces differ only by their {@link Material}. Resisting the urge to
 * write a bespoke "bounceOffTable" and a separate "hitNet" is what keeps the spin coupling
 * consistent, because the net has to steal spin by the same rules the table uses to make it.
 *
 * The spin coupling is the interesting half. A topspin ball arrives with its contact patch
 * already moving backwards relative to the table, so friction there ADDS forward speed and
 * the ball kicks low and long. A backspin ball arrives with the patch moving forwards, so
 * friction subtracts, and the ball checks up short, sometimes reversing its own spin. None
 * of that is scripted; it falls out of the impulse below.
 */
public final class Contacts {

    private Contacts() {}

    /** An axis-aligned collision volume in physics space. The table, the net and the floor. */
    public record Box(Vec3 min, Vec3 max) implements Collider {
        public static Box centered(double cx, double cy, double cz,
                                   double sx, double sy, double sz) {
            return new Box(new Vec3(cx - sx / 2, cy - sy / 2, cz - sz / 2),
                           new Vec3(cx + sx / 2, cy + sy / 2, cz + sz / 2));
        }
        public Vec3 center() { return min.plus(max).scale(0.5); }

        /** Nearest point on the box to p (equals p when p is inside). */
        @Override public Vec3 closestPoint(Vec3 p) {
            return new Vec3(clamp(p.x(), min.x(), max.x()),
                            clamp(p.y(), min.y(), max.y()),
                            clamp(p.z(), min.z(), max.z()));
        }

        /** For a centre inside the box: unit normal of the nearest face. */
        @Override public Vec3 escapeNormal(Vec3 p) {
            double best = p.x() - min.x();
            Vec3 n = new Vec3(-1, 0, 0);

            double d = max.x() - p.x();
            if (d < best) { best = d; n = new Vec3(1, 0, 0); }
            d = p.y() - min.y();
            if (d < best) { best = d; n = new Vec3(0, -1, 0); }
            d = max.y() - p.y();
            if (d < best) { best = d; n = new Vec3(0, 1, 0); }
            d = p.z() - min.z();
            if (d < best) { best = d; n = new Vec3(0, 0, -1); }
            d = max.z() - p.z();
            if (d < best) { n = new Vec3(0, 0, 1); }
            return n;
        }

        /**
         * Swept sphere vs box, via a ray against the box grown by the ball radius.
         *
         * Growing the box squares off its corners instead of rounding them, so a hit on the
         * exact corner of the table can register up to one radius early. The closest-point
         * call afterwards still produces the correct rounded normal, so the ball leaves at
         * the right angle; only the instant is slightly off, and only on true corner clips.
         *
         * @return time of impact in [0,1] along p0 -> p1, or -1 for no hit.
         */
        @Override public double sweep(Vec3 p0, Vec3 p1) {
            Vec3 d = p1.minus(p0);

            double[] o = { p0.x(), p0.y(), p0.z() };
            double[] dd = { d.x(), d.y(), d.z() };
            double[] lo = { min.x() - BALL_R, min.y() - BALL_R, min.z() - BALL_R };
            double[] hi = { max.x() + BALL_R, max.y() + BALL_R, max.z() + BALL_R };

            double tEnter = 0.0, tExit = 1.0;

            for (int i = 0; i < 3; i++) {
                if (Math.abs(dd[i]) < 1e-12) {
                    if (o[i] < lo[i] || o[i] > hi[i]) return -1;      // parallel and outside
                    continue;
                }
                double t1 = (lo[i] - o[i]) / dd[i];
                double t2 = (hi[i] - o[i]) / dd[i];
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tEnter = Math.max(tEnter, t1);
                tExit = Math.min(tExit, t2);
                if (tEnter > tExit) return -1;
            }
            return tEnter;
        }

        /** The table is bolted to the floor. */
        @Override public Vec3 velocityAt(Vec3 point) { return Vec3.ZERO; }
    }

    /** What happened, so World can report it and the renderer can mark the bounce. */
    public record Hit(BallState state, Vec3 point, Vec3 normal,
                      double impactSpeed, boolean resting) {}

    /**
     * Below this normal speed we stop applying restitution. Without it, a ball settling on
     * the table enters an infinite sequence of ever smaller bounces, burns steps, and jitters
     * visibly. 0.15 m/s is under 1 mm of bounce height, so nothing real is lost.
     */
    private static final double RESTING_SPEED = 0.15;

    /** Rolling resistance, applied only while the ball is resting in contact. Without a sink
     *  here, a ball that comes to rest on the table rolls off the end forever. */
    private static final double ROLLING_MU = 0.02;

    /** Gap left between ball and surface after a contact, so the next step starts clean. */
    private static final double SKIN = 1e-4;

    /**
     * Where and when the ball touches a surface, before anything is done about it.
     *
     * Detection is separated from response so the caller can look at EVERY surface first and
     * then resolve only the earliest contact. With three static surfaces a fixed priority
     * order was good enough; with a paddle in the way it is not, because "the first one in
     * the list" and "the one it actually hit first" stop being the same thing.
     *
     * @param toi   fraction of the step at which contact happens, 1.0 for an end-of-step overlap
     * @param swept true if this was found by the swept test, i.e. the ball would otherwise
     *              have passed clean through and there is still step left to fly afterwards
     */
    public record Contact(double toi, Vec3 point, Vec3 normal, boolean swept) {}

    /**
     * Find the contact, if any, for the motion from {@code prev} to {@code next}.
     *
     * @return the contact, or {@code null} if the ball never touched this surface.
     */
    public static Contact detect(BallState prev, BallState next, Collider surface) {
        Vec3 p0 = prev.pos(), p1 = next.pos();

        // Case 1: the ball ends the step overlapping the surface.
        if (surface.closestPoint(p1).minus(p1).lengthSquared() < BALL_R * BALL_R) {
            return new Contact(1.0, p1, normalAt(surface, p1), false);
        }

        // Case 2: it passed clean through between steps. A 30 m/s smash covers 6.25 cm per
        // step, which is very nearly the 6.5 cm it takes to cross the table slab, so this is
        // not a theoretical concern: without the swept test, the hardest shots in the game
        // would occasionally fall straight through the table.
        //
        // The sweep is done in the SURFACE's frame. The collider is already at its
        // end-of-step pose, so the ball's start position has to be carried into that frame:
        // the surface was one step behind, and relative to it the ball started at p0 + u*DT.
        // For the table, the net and the floor u is zero and this is exactly the old test.
        Vec3 u = surface.velocityAt(p1);
        Vec3 q0 = p0.plusScaled(u, DT);

        double t = surface.sweep(q0, p1);
        if (t < 0) return null;

        Vec3 at = Vec3.lerp(q0, p1, t);
        return new Contact(t, at, normalAt(surface, at), true);
    }

    /** Apply the impulse for a contact already found by {@link #detect}. */
    public static Hit respond(BallState next, Collider surface, Contact contact, Material mat) {
        return applyImpulse(next.withPos(contact.point()), surface, contact.normal(), mat);
    }

    /** Outward unit normal at a point, falling back to the nearest face when the centre is
     *  inside the volume (the thin net, hit deep). */
    private static Vec3 normalAt(Collider surface, Vec3 p) {
        Vec3 offset = p.minus(surface.closestPoint(p));
        return offset.lengthSquared() < 1e-18 ? surface.escapeNormal(p) : offset.normalized();
    }

    /** The impulse itself: normal restitution, then grip-or-slide friction. */
    private static Hit applyImpulse(BallState s, Collider box, Vec3 n, Material mat) {
        Vec3 v = s.vel(), w = s.spin();
        Vec3 contactPoint = s.pos().plusScaled(n, -BALL_R);

        // Everything here is measured RELATIVE TO THE SURFACE. For the table, the net and the
        // floor u is zero and every line below is what it always was. For a paddle it is the
        // whole of the physics: a blade swung at 15 m/s into a ball drifting at 2 m/s is a
        // 17 m/s impact, and a blade brushing tangentially past a ball is what puts spin on
        // it. Written in absolute velocity, as this was, a paddle catching up to a receding
        // ball reads as "already separating" and does nothing at all.
        Vec3 u = box.velocityAt(contactPoint);

        double vn = v.minus(u).dot(n);
        double impactSpeed = Math.abs(vn);

        // Already separating. We only got here through overlap, so push out and leave the
        // velocity alone: reflecting here would fling the ball out of a surface it is
        // already leaving, which looks like the ball being spat out of the table.
        if (vn > 0) {
            return new Hit(pushOut(s, box, n), contactPoint, n, 0, false);
        }

        boolean resting = impactSpeed < RESTING_SPEED;
        double e = resting ? 0.0 : mat.restitutionAt(impactSpeed);

        // Normal impulse magnitude (positive).
        double jn = -(1.0 + e) * vn * BALL_M;

        // Slip: how fast the ball's contact patch is sliding ACROSS the surface. Subtracting
        // the surface's own velocity is what makes a brushing paddle stroke generate spin --
        // against a static world this term can only ever take spin off, never put it on.
        Vec3 arm = n.scale(-BALL_R);                 // centre -> contact point
        Vec3 slip = v.plus(w.cross(arm)).minus(u).tangentTo(n);

        // Tangential impulse. To exactly kill the slip (perfect grip):
        //
        //   dv_contact = J_t/m + (r^2/I) J_t = (1/m + 3/(2m)) J_t = (5/2m) J_t
        //
        // using I = (2/3)mr^2 for a HOLLOW shell. A solid sphere gives (7/2m) and a
        // coefficient of 2/7 below. The hollow ball grips about 40% harder, which is part
        // of why table tennis carries so much more spin than its scale suggests.
        //
        // The (1 + e_t) factor generalises that to a surface with tangential springback.
        // e_t = 0 is perfect grip and reduces this to exactly the line it replaced, which is
        // why the table, the net and the floor behave identically to before. Rubber has
        // e_t ~ 0.8: it does not merely stop the contact patch, it throws it back the other
        // way, and THAT is what turns an incoming backspin ball into an outgoing topspin one.
        // No amount of tuning a grip-or-slide model can produce that -- the best it can do is
        // remove spin, never reverse it.
        double et = mat.tangentialRestitutionAt(slip.length());
        Vec3 jtGrip = slip.scale(-(2.0 / 5.0) * (1.0 + et) * BALL_M);

        double maxFriction = mat.friction() * jn;
        Vec3 jt = (jtGrip.length() <= maxFriction || slip.lengthSquared() < 1e-18)
                ? jtGrip                                       // inside the friction cone: bites
                : slip.normalized().scale(-maxFriction);       // Coulomb slide

        Vec3 impulse = n.scale(jn).plus(jt);

        Vec3 newVel = v.plusScaled(impulse, 1.0 / BALL_M);
        // Only the tangential part exerts torque: arm x (jn*n) is zero by construction.
        Vec3 newSpin = w.plusScaled(arm.cross(jt), 1.0 / BALL_I);

        // Rolling resistance, so a settled ball eventually stops instead of drifting.
        //
        // Only for a ball at rest on a STATIC, upward-facing surface. On a swinging paddle it
        // is meaningless -- there is no rolling, the contact lasts under two milliseconds, and
        // it would quietly steal pace from every stroke. It is also the one place the solver
        // reaches for the global DT, which is another reason to keep it where it belongs.
        if (resting && u.lengthSquared() < 1e-18 && n.y() > 0.5) {
            Vec3 tangential = newVel.tangentTo(n);
            double drop = ROLLING_MU * G * DT;
            newVel = tangential.length() > drop
                   ? newVel.minus(tangential.normalized().scale(drop))
                   : newVel.minus(tangential);
        }

        // Damp the spin component about the contact normal -- the corkscrew, which the
        // tangential impulse has almost no purchase on because it acts in the contact plane.
        // Scoped to that one component on purpose: rubber's measured e_s = 0.805 applied to
        // the whole spin vector would delete a fifth of the topspin the stroke just made.
        if (mat.drillSpinDamping() != 1.0) {
            Vec3 drill = newSpin.projectOnto(n);
            newSpin = newSpin.minus(drill).plusScaled(drill, mat.drillSpinDamping());
        }

        newVel = newVel.scale(mat.velDamping());
        newSpin = newSpin.scale(mat.spinDamping());

        BallState out = pushOut(s.withVel(newVel).withSpin(newSpin), box, n);
        return new Hit(out, contactPoint, n, impactSpeed, resting);
    }

    /** Move the ball back to just touching, along the contact normal. */
    private static BallState pushOut(BallState s, Collider box, Vec3 n) {
        Vec3 surface = box.closestPoint(s.pos());
        Vec3 offset = s.pos().minus(surface);
        double dist = offset.length();

        if (dist < 1e-9) {
            // Centre is inside the box: project out to the nearest face along n.
            return s.withPos(surface.plusScaled(n, BALL_R + SKIN));
        }
        if (dist >= BALL_R) return s;
        return s.withPos(surface.plusScaled(offset.scale(1.0 / dist), BALL_R + SKIN));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
