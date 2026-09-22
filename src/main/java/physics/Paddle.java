package physics;

import static physics.Constants.*;

/**
 * A kinematic racket: posed from outside, its velocity measured by differencing its own pose over
 * a physics step. Treated as infinitely massive: M_eff ~ 0.13 kg against a 2.7 g ball is a ~2%
 * recoil, and published racket restitution was measured on rigid mounts anyway.
 */
public final class Paddle {

    /** A frozen disc-slab snapshot, so the solver gets consistent answers within a step. */
    public record Blade(Vec3 centre, Vec3 normal, Vec3 vel, Vec3 angVel) implements Collider {

        private static final double HALF_THICK = BLADE_THICK / 2;

        @Override public Vec3 closestPoint(Vec3 p) {
            Vec3 d = p.minus(centre);
            double along = d.dot(normal);
            Vec3 inPlane = d.minus(normal.scale(along));

            double r = inPlane.length();
            if (r > BLADE_R) inPlane = inPlane.scale(BLADE_R / r);

            double clamped = along < -HALF_THICK ? -HALF_THICK
                           : (along > HALF_THICK ? HALF_THICK : along);
            return centre.plus(inPlane).plusScaled(normal, clamped);
        }

        /** Through the nearer face, which for a 15 mm by 150 mm disc is always the way out. */
        @Override public Vec3 escapeNormal(Vec3 p) {
            return p.minus(centre).dot(normal) >= 0 ? normal : normal.negate();
        }

        /** A plane crossing plus a rim check: exact for a thin blade. */
        @Override public double sweep(Vec3 p0, Vec3 p1) {
            double surface = HALF_THICK + BALL_R;
            double d0 = p0.minus(centre).dot(normal);
            double d1 = p1.minus(centre).dot(normal);

            double delta = d1 - d0;
            if (Math.abs(delta) < 1e-12) return -1;          // parallel to the face

            double target = d0 >= 0 ? surface : -surface;
            double t = (target - d0) / delta;
            if (t < 0 || t > 1) return -1;

            Vec3 at = Vec3.lerp(p0, p1, t);
            Vec3 d = at.minus(centre);
            Vec3 inPlane = d.minus(normal.scale(d.dot(normal)));
            double rim = BLADE_R + BALL_R;
            return inPlane.lengthSquared() > rim * rim ? -1 : t;
        }

        @Override public Vec3 velocityAt(Vec3 point) {
            return vel.plus(angVel.cross(point.minus(centre)));
        }
    }

    private Vec3 pos;
    private Vec3 normal;
    private Vec3 vel = Vec3.ZERO;
    private Vec3 angVel = Vec3.ZERO;

    public Paddle(Vec3 pos, Vec3 normal) {
        this.pos = pos;
        this.normal = normal.normalized();
    }

    /** Pose the blade over one PHYSICS step, deriving its velocity from the move. */
    public void moveTo(Vec3 newPos, Vec3 newNormal, double dt) {
        Vec3 n = newNormal.normalized();
        if (dt > 1e-12) {
            vel = newPos.minus(pos).scale(1.0 / dt);
            Vec3 axis = normal.cross(n);
            double sin = axis.length();
            angVel = sin < 1e-9 ? Vec3.ZERO
                                : axis.scale(Math.asin(Math.min(1, sin)) / (sin * dt));
        }
        pos = newPos;
        normal = n;
    }

    /** Pose the blade with no implied motion. */
    public void placeAt(Vec3 newPos, Vec3 newNormal) {
        pos = newPos;
        normal = newNormal.normalized();
        vel = Vec3.ZERO;
        angVel = Vec3.ZERO;
    }

    public Vec3 pos()    { return pos; }
    public Vec3 normal() { return normal; }
    public Vec3 vel()    { return vel; }

    public Blade collider() { return new Blade(pos, normal, vel, angVel); }
}
