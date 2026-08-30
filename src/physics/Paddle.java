package physics;

import static physics.Constants.*;

/**
 * A racket: a flat blade with a pose, driven from outside rather than integrated.
 *
 * The paddle is KINEMATIC. Nothing pushes it around -- a player's mouse or the opponent's
 * controller says where it should be, and it goes there. What makes the contact honest is
 * that its velocity is never invented: it is measured as the finite difference of its own
 * pose across one physics step, so the pace and spin it puts on the ball come from how it
 * actually moved. There is no line anywhere that says "a smash leaves at 25 m/s".
 *
 * Treating it as infinitely massive is a decision with a number behind it. A racket weighs
 * about 170 g, and for an impact 5 cm off its centre of mass the effective mass is
 *
 *     M_eff = 1 / (1/M + b^2/I_cm) ~ 0.13 kg
 *
 * against a 2.7 g ball, so the recoil correction to the outgoing speed is about 2%, and under
 * 5% even for a bad off-centre hit. The published racket restitution values were themselves
 * measured against rigid mountings, so they are already the right numbers for a racket treated
 * this way. Modelling the hand and forearm would add a great deal of machinery to chase a
 * couple of percent.
 */
public final class Paddle {

    /**
     * The collision shape: a disc-slab -- a flat cylinder of radius {@link Constants#BLADE_R}
     * and thickness {@link Constants#BLADE_THICK}, standing perpendicular to {@code normal}.
     *
     * A snapshot, not a live view of the paddle. The contact solver asks a collider the same
     * questions several times while resolving a step and must get consistent answers, so the
     * pose is frozen when the snapshot is taken.
     */
    public record Blade(Vec3 centre, Vec3 normal, Vec3 vel, Vec3 angVel) implements Collider {

        private static final double HALF_THICK = BLADE_THICK / 2;

        /**
         * Nearest point on the blade. This is the box clamp written in the blade's own frame:
         * clamp the in-plane offset to the rim, clamp the through-face offset to half the
         * thickness.
         */
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

        /** Centre inside the blade: leave through the nearer face. It is 15 mm thick and
         *  150 mm across, so the face is always the nearer way out in practice. */
        @Override public Vec3 escapeNormal(Vec3 p) {
            return p.minus(centre).dot(normal) >= 0 ? normal : normal.negate();
        }

        /**
         * Swept test, as a plane crossing plus a rim check.
         *
         * Exact for a thin blade and far cheaper than sweeping a cylinder. The caller has
         * already put the motion into the blade's own frame, so this is a static test.
         */
        @Override public double sweep(Vec3 p0, Vec3 p1) {
            double surface = HALF_THICK + BALL_R;
            double d0 = p0.minus(centre).dot(normal);
            double d1 = p1.minus(centre).dot(normal);

            double delta = d1 - d0;
            if (Math.abs(delta) < 1e-12) return -1;          // travelling parallel to the face

            // First moment it comes within one radius of the face it is approaching from.
            double target = d0 >= 0 ? surface : -surface;
            double t = (target - d0) / delta;
            if (t < 0 || t > 1) return -1;

            // ...and it only counts if that happens on the blade rather than past its edge.
            Vec3 at = Vec3.lerp(p0, p1, t);
            Vec3 d = at.minus(centre);
            Vec3 inPlane = d.minus(normal.scale(d.dot(normal)));
            double rim = BLADE_R + BALL_R;
            return inPlane.lengthSquared() > rim * rim ? -1 : t;
        }

        /** Velocity of the blade's material at a point: its own motion plus its rotation. */
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

    /**
     * Move the blade to a new pose over one step, deriving its velocity from the move.
     *
     * This is the whole reason a swing produces pace: nothing tells the contact solver how
     * hard the shot was, it measures it.
     *
     * @param dt the step the move happens over -- the PHYSICS step, never a frame time
     */
    public void moveTo(Vec3 newPos, Vec3 newNormal, double dt) {
        Vec3 n = newNormal.normalized();
        if (dt > 1e-12) {
            vel = newPos.minus(pos).scale(1.0 / dt);

            // Angular velocity of the face, from how far the normal swung. axis = n0 x n1,
            // angle ~ |n0 x n1| for the small rotations one step covers.
            Vec3 axis = normal.cross(n);
            double sin = axis.length();
            angVel = sin < 1e-9 ? Vec3.ZERO
                                : axis.scale(Math.asin(Math.min(1, sin)) / (sin * dt));
        }
        pos = newPos;
        normal = n;
    }

    /** Put the blade somewhere with no implied motion. Used when a rally is (re)started. */
    public void placeAt(Vec3 newPos, Vec3 newNormal) {
        pos = newPos;
        normal = newNormal.normalized();
        vel = Vec3.ZERO;
        angVel = Vec3.ZERO;
    }

    public Vec3 pos()    { return pos; }
    public Vec3 normal() { return normal; }
    public Vec3 vel()    { return vel; }
    public Vec3 angVel() { return angVel; }

    /** A frozen collision shape for the pose the blade is in right now. */
    public Blade collider() { return new Blade(pos, normal, vel, angVel); }
}
