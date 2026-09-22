package physics;

import physics.Aero.Derivative;

/**
 * Classical RK4 over the flight equations at a fixed step. Flight only: contacts are
 * discontinuities, which World resolves separately after each step.
 */
public final class Integrator {

    private Integrator() {}

    public static BallState step(BallState s, double dt) {
        return step(s, dt, Aero.DEFAULT_DRAG);
    }

    /** Under a named drag law; SelfTest uses a constant coefficient to compare with the closed form. */
    public static BallState step(BallState s, double dt, Aero.DragModel drag) {
        Vec3 p = s.pos(), v = s.vel(), w = s.spin();

        Derivative a = Aero.derivative(p, v, w, drag);
        Derivative b = sample(p, v, w, a, dt * 0.5, drag);
        Derivative c = sample(p, v, w, b, dt * 0.5, drag);
        Derivative d = sample(p, v, w, c, dt, drag);

        Vec3 newPos  = p.plusScaled(weighted(a.dPos(),  b.dPos(),  c.dPos(),  d.dPos()),  dt);
        Vec3 newVel  = v.plusScaled(weighted(a.dVel(),  b.dVel(),  c.dVel(),  d.dVel()),  dt);
        Vec3 newSpin = w.plusScaled(weighted(a.dSpin(), b.dSpin(), c.dSpin(), d.dSpin()), dt);

        return new BallState(newPos, newVel, newSpin, spinOrientation(s.orient(), w, newSpin, dt));
    }

    private static Derivative sample(Vec3 p, Vec3 v, Vec3 w, Derivative d, double dt,
                                     Aero.DragModel drag) {
        return Aero.derivative(p.plusScaled(d.dPos(), dt),
                               v.plusScaled(d.dVel(), dt),
                               w.plusScaled(d.dSpin(), dt),
                               drag);
    }

    private static Vec3 weighted(Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        return a.plus(b.scale(2)).plus(c.scale(2)).plus(d).scale(1.0 / 6.0);
    }

    /** Rotates by |omega|*dt about the mean spin axis: exact while the axis is steady. */
    private static Quat spinOrientation(Quat orient, Vec3 spinBefore, Vec3 spinAfter, double dt) {
        Vec3 mean = spinBefore.plus(spinAfter).scale(0.5);
        double rate = mean.length();
        if (rate < 1e-9) return orient;
        return Quat.fromAxisAngle(mean, rate * dt).times(orient).normalized();
    }
}
