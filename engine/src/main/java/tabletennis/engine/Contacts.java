package tabletennis.engine;

import tabletennis.engine.Constants.Material;

import static tabletennis.engine.Constants.*;

/**
 * The ONE collision solver (AGENTS.md invariant 4): a normal impulse with restitution plus a
 * tangential impulse that grips, springs back or slides, differing per surface only by
 * {@link Material}. Spin coupling falls out of the impulse rather than being scripted.
 */
public final class Contacts {

    private Contacts() {}

    /** An axis-aligned volume: the table, the net and the floor. */
    public record Box(Vec3 min, Vec3 max) implements Collider {
        public static Box centered(double cx, double cy, double cz,
                                   double sx, double sy, double sz) {
            return new Box(new Vec3(cx - sx / 2, cy - sy / 2, cz - sz / 2),
                           new Vec3(cx + sx / 2, cy + sy / 2, cz + sz / 2));
        }

        @Override public Vec3 closestPoint(Vec3 p) {
            return new Vec3(clamp(p.x(), min.x(), max.x()),
                            clamp(p.y(), min.y(), max.y()),
                            clamp(p.z(), min.z(), max.z()));
        }

        /** Normal of the nearest face. */
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
         * A ray against the box grown by the ball radius. Square corners can register a corner
         * clip up to one radius early; the rounded normal from closestPoint is still correct.
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

        @Override public Vec3 velocityAt(Vec3 point) { return Vec3.ZERO; }
    }

    public record Hit(BallState state, Vec3 point, Vec3 normal,
                      double impactSpeed, boolean resting) {}

    /** Below this normal speed there is no bounce: under 1 mm of height, and it ends the jitter. */
    private static final double RESTING_SPEED = 0.15;

    /** Applied only while resting, so a settled ball stops instead of rolling forever. */
    private static final double ROLLING_MU = 0.02;

    /** Gap left after a contact so the next step starts clean. */
    private static final double SKIN = 1e-4;

    /**
     * A detected touch, before any response, so World can resolve the EARLIEST of all surfaces.
     * {@code toi} is the step fraction (1.0 for an end-of-step overlap); {@code swept} means the
     * ball would otherwise have passed through and still has step left to fly.
     */
    public record Contact(double toi, Vec3 point, Vec3 normal, boolean swept) {}

    /** The contact for the motion prev -> next, or null if the ball never touched the surface. */
    public static Contact detect(BallState prev, BallState next, Collider surface) {
        Vec3 p0 = prev.pos(), p1 = next.pos();

        if (surface.closestPoint(p1).minus(p1).lengthSquared() < BALL_R * BALL_R) {
            return new Contact(1.0, p1, normalAt(surface, p1), false);
        }

        // Swept in the SURFACE's frame (invariant 5): carry the start into the collider's
        // end-of-step pose. For every static surface u is zero and this is a plain sweep.
        Vec3 u = surface.velocityAt(p1);
        Vec3 q0 = p0.plusScaled(u, DT);

        double t = surface.sweep(q0, p1);
        if (t < 0) return null;

        Vec3 at = Vec3.lerp(q0, p1, t);
        return new Contact(t, at, normalAt(surface, at), true);
    }

    public static Hit respond(BallState next, Collider surface, Contact contact, Material mat) {
        return applyImpulse(next.withPos(contact.point()), surface, contact.normal(), mat);
    }

    private static Vec3 normalAt(Collider surface, Vec3 p) {
        Vec3 offset = p.minus(surface.closestPoint(p));
        return offset.lengthSquared() < 1e-18 ? surface.escapeNormal(p) : offset.normalized();
    }

    private static Hit applyImpulse(BallState s, Collider surface, Vec3 n, Material mat) {
        Vec3 v = s.vel(), w = s.spin();
        Vec3 contactPoint = s.pos().plusScaled(n, -BALL_R);
        // Relative to the surface (invariant 5): in absolute terms a blade catching a receding
        // ball would read as already separating.
        Vec3 u = surface.velocityAt(contactPoint);

        double vn = v.minus(u).dot(n);
        double impactSpeed = Math.abs(vn);
        if (vn > 0) {
            // Already leaving: reflecting would spit the ball out of the surface.
            return new Hit(pushOut(s, surface, n), contactPoint, n, 0, false);
        }

        boolean resting = impactSpeed < RESTING_SPEED;
        double e = resting ? 0.0 : mat.restitutionAt(impactSpeed);
        double jn = -(1.0 + e) * vn * BALL_M;

        Vec3 arm = n.scale(-BALL_R);
        Vec3 slip = v.plus(w.cross(arm)).minus(u).tangentTo(n);
        Vec3 jt = tangentialImpulse(slip, jn, mat);

        Vec3 newVel = v.plusScaled(n.scale(jn).plus(jt), 1.0 / BALL_M);
        Vec3 newSpin = w.plusScaled(arm.cross(jt), 1.0 / BALL_I);   // normal impulse has no torque

        boolean restingOnStaticFloor = resting && u.lengthSquared() < 1e-18 && n.y() > 0.5;
        if (restingOnStaticFloor) newVel = withRollingResistance(newVel, n);
        newSpin = dampDrillSpin(newSpin, n, mat);

        newVel = newVel.scale(mat.velDamping());
        newSpin = newSpin.scale(mat.spinDamping());

        BallState out = pushOut(s.withVel(newVel).withSpin(newSpin), surface, n);
        return new Hit(out, contactPoint, n, impactSpeed, resting);
    }

    /**
     * Killing the slip of a HOLLOW shell (I = (2/3)mr²) takes J = -(2/5) m slip; the (1 + e_t)
     * factor springs the patch back, which is what reverses spin off rubber. Past the friction
     * cone the patch slides instead.
     */
    private static Vec3 tangentialImpulse(Vec3 slip, double jn, Material mat) {
        double et = mat.tangentialRestitutionAt(slip.length());
        Vec3 grip = slip.scale(-(2.0 / 5.0) * (1.0 + et) * BALL_M);

        double maxFriction = mat.friction() * jn;
        boolean insideCone = grip.length() <= maxFriction || slip.lengthSquared() < 1e-18;
        return insideCone ? grip : slip.normalized().scale(-maxFriction);
    }

    /** Only on a static, upward-facing surface: on a swinging paddle it would steal pace. */
    private static Vec3 withRollingResistance(Vec3 vel, Vec3 n) {
        Vec3 tangential = vel.tangentTo(n);
        double drop = ROLLING_MU * G * DT;
        return tangential.length() > drop
             ? vel.minus(tangential.normalized().scale(drop))
             : vel.minus(tangential);
    }

    /** Damps only spin about the normal; applied to all of it, it would erase a stroke's topspin. */
    private static Vec3 dampDrillSpin(Vec3 spin, Vec3 n, Material mat) {
        if (mat.drillSpinDamping() == 1.0) return spin;
        Vec3 drill = spin.projectOnto(n);
        return spin.minus(drill).plusScaled(drill, mat.drillSpinDamping());
    }

    private static BallState pushOut(BallState s, Collider surface, Vec3 n) {
        Vec3 nearest = surface.closestPoint(s.pos());
        Vec3 offset = s.pos().minus(nearest);
        double dist = offset.length();

        if (dist < 1e-9) return s.withPos(nearest.plusScaled(n, BALL_R + SKIN));
        if (dist >= BALL_R) return s;
        return s.withPos(nearest.plusScaled(offset.scale(1.0 / dist), BALL_R + SKIN));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
