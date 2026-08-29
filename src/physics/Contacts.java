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

    /** An axis-aligned collision volume in physics space. */
    public record Box(Vec3 min, Vec3 max) {
        public static Box centered(double cx, double cy, double cz,
                                   double sx, double sy, double sz) {
            return new Box(new Vec3(cx - sx / 2, cy - sy / 2, cz - sz / 2),
                           new Vec3(cx + sx / 2, cy + sy / 2, cz + sz / 2));
        }
        public Vec3 center() { return min.plus(max).scale(0.5); }
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
     * Resolve the motion from {@code prev} to {@code next} against one box.
     *
     * @return the corrected state and contact info, or {@code null} if there was no contact.
     */
    public static Hit resolve(BallState prev, BallState next, Box box, Material mat) {
        Vec3 p0 = prev.pos(), p1 = next.pos();
        Vec3 contactPos;

        // Case 1: the ball ends the step overlapping the box.
        if (closestPoint(p1, box).minus(p1).lengthSquared() < BALL_R * BALL_R) {
            contactPos = p1;
        } else {
            // Case 2: it passed clean through between steps. A 30 m/s smash covers 6.25 cm
            // per step, which is very nearly the 6.5 cm it takes to cross the table slab,
            // so this is not a theoretical concern: without the swept test, the hardest
            // shots in the game would occasionally fall straight through the table.
            double t = sweep(p0, p1, box);
            if (t < 0) return null;
            contactPos = Vec3.lerp(p0, p1, t);
        }

        Vec3 surface = closestPoint(contactPos, box);
        Vec3 offset = contactPos.minus(surface);
        Vec3 normal = offset.lengthSquared() < 1e-18
                    ? escapeNormal(contactPos, box)   // centre inside the box (thin net, deep hit)
                    : offset.normalized();

        return applyImpulse(next.withPos(contactPos), box, normal, mat);
    }

    /** The impulse itself: normal restitution, then grip-or-slide friction. */
    private static Hit applyImpulse(BallState s, Box box, Vec3 n, Material mat) {
        Vec3 v = s.vel(), w = s.spin();
        Vec3 contactPoint = s.pos().plusScaled(n, -BALL_R);

        double vn = v.dot(n);
        double impactSpeed = Math.abs(vn);

        // Already separating. We only got here through overlap, so push out and leave the
        // velocity alone: reflecting here would fling the ball out of a surface it is
        // already leaving, which looks like the ball being spat out of the table.
        if (vn > 0) {
            return new Hit(pushOut(s, box, n), contactPoint, n, 0, false);
        }

        boolean resting = impactSpeed < RESTING_SPEED;
        double e = resting ? 0.0 : mat.restitution();

        // Normal impulse magnitude (positive).
        double jn = -(1.0 + e) * vn * BALL_M;

        // Velocity of the material point of the ball that is touching the surface.
        Vec3 arm = n.scale(-BALL_R);                 // centre -> contact point
        Vec3 slip = v.plus(w.cross(arm)).tangentTo(n);

        // Tangential impulse that would exactly kill the slip (perfect grip):
        //
        //   dv_contact = J_t/m + (r^2/I) J_t = (1/m + 3/(2m)) J_t = (5/2m) J_t
        //
        // using I = (2/3)mr^2 for a HOLLOW shell. A solid sphere gives (7/2m) and a
        // coefficient of 2/7 below. The hollow ball grips about 40% harder, which is part
        // of why table tennis carries so much more spin than its scale suggests.
        Vec3 jtGrip = slip.scale(-(2.0 / 5.0) * BALL_M);

        double maxFriction = mat.friction() * jn;
        Vec3 jt = (jtGrip.length() <= maxFriction || slip.lengthSquared() < 1e-18)
                ? jtGrip                                       // inside the friction cone: bites
                : slip.normalized().scale(-maxFriction);       // Coulomb slide

        Vec3 impulse = n.scale(jn).plus(jt);

        Vec3 newVel = v.plusScaled(impulse, 1.0 / BALL_M);
        // Only the tangential part exerts torque: arm x (jn*n) is zero by construction.
        Vec3 newSpin = w.plusScaled(arm.cross(jt), 1.0 / BALL_I);

        if (resting) {
            // Rolling resistance, so a settled ball eventually stops instead of drifting.
            Vec3 tangential = newVel.tangentTo(n);
            double drop = ROLLING_MU * G * DT;
            newVel = tangential.length() > drop
                   ? newVel.minus(tangential.normalized().scale(drop))
                   : newVel.minus(tangential);
        }

        newVel = newVel.scale(mat.velDamping());
        newSpin = newSpin.scale(mat.spinDamping());

        BallState out = pushOut(s.withVel(newVel).withSpin(newSpin), box, n);
        return new Hit(out, contactPoint, n, impactSpeed, resting);
    }

    /** Move the ball back to just touching, along the contact normal. */
    private static BallState pushOut(BallState s, Box box, Vec3 n) {
        Vec3 surface = closestPoint(s.pos(), box);
        Vec3 offset = s.pos().minus(surface);
        double dist = offset.length();

        if (dist < 1e-9) {
            // Centre is inside the box: project out to the nearest face along n.
            return s.withPos(surface.plusScaled(n, BALL_R + SKIN));
        }
        if (dist >= BALL_R) return s;
        return s.withPos(surface.plusScaled(offset.scale(1.0 / dist), BALL_R + SKIN));
    }

    /** Nearest point on the box to p (equals p when p is inside). */
    private static Vec3 closestPoint(Vec3 p, Box b) {
        return new Vec3(clamp(p.x(), b.min().x(), b.max().x()),
                        clamp(p.y(), b.min().y(), b.max().y()),
                        clamp(p.z(), b.min().z(), b.max().z()));
    }

    /** For a centre inside the box: unit normal of the nearest face. */
    private static Vec3 escapeNormal(Vec3 p, Box b) {
        double best = p.x() - b.min().x();
        Vec3 n = new Vec3(-1, 0, 0);

        double d = b.max().x() - p.x();
        if (d < best) { best = d; n = new Vec3(1, 0, 0); }
        d = p.y() - b.min().y();
        if (d < best) { best = d; n = new Vec3(0, -1, 0); }
        d = b.max().y() - p.y();
        if (d < best) { best = d; n = new Vec3(0, 1, 0); }
        d = p.z() - b.min().z();
        if (d < best) { best = d; n = new Vec3(0, 0, -1); }
        d = b.max().z() - p.z();
        if (d < best) { n = new Vec3(0, 0, 1); }
        return n;
    }

    /**
     * Swept sphere vs box, via a ray against the box grown by the ball radius.
     *
     * Growing the box squares off its corners instead of rounding them, so a hit on the
     * exact corner of the table can register up to one radius early. The closest-point call
     * afterwards still produces the correct rounded normal, so the ball leaves at the right
     * angle; only the instant is slightly off, and only on true corner clips.
     *
     * @return time of impact in [0,1] along p0 -> p1, or -1 for no hit.
     */
    private static double sweep(Vec3 p0, Vec3 p1, Box box) {
        Vec3 d = p1.minus(p0);

        double[] o = { p0.x(), p0.y(), p0.z() };
        double[] dd = { d.x(), d.y(), d.z() };
        double[] lo = { box.min().x() - BALL_R, box.min().y() - BALL_R, box.min().z() - BALL_R };
        double[] hi = { box.max().x() + BALL_R, box.max().y() + BALL_R, box.max().z() + BALL_R };

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

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
