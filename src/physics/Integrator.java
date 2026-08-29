package physics;

import physics.Aero.Derivative;

/**
 * Classical Runge-Kutta 4 over the flight equations, at a fixed step.
 *
 * Why RK4 and not Euler: the acceleration depends on velocity (drag goes as |v|v) and on the
 * spin/speed ratio (Magnus), so the system is genuinely nonlinear and Euler bleeds energy in a
 * way that shows up as a ball that dies short. RK4 is 4th-order — halving the step cuts the
 * error 16x — which is why the simulation can be trusted against published trajectories
 * instead of merely looking plausible.
 *
 * Note this integrates FLIGHT ONLY. Contacts are impulsive: they are discontinuities, and
 * running RK4 across a discontinuity samples the derivative on both sides of a wall and
 * produces nonsense. World steps flight first, then resolves contacts separately.
 */
public final class Integrator {

    private Integrator() {}

    /** Advance one fixed step of {@code dt}. Orientation is carried along for the renderer. */
    public static BallState step(BallState s, double dt) {
        Vec3 p = s.pos(), v = s.vel(), w = s.spin();

        Derivative a = Aero.derivative(p, v, w);
        Derivative b = sample(p, v, w, a, dt * 0.5);
        Derivative c = sample(p, v, w, b, dt * 0.5);
        Derivative d = sample(p, v, w, c, dt);

        Vec3 dPos  = weighted(a.dPos(),  b.dPos(),  c.dPos(),  d.dPos());
        Vec3 dVel  = weighted(a.dVel(),  b.dVel(),  c.dVel(),  d.dVel());
        Vec3 dSpin = weighted(a.dSpin(), b.dSpin(), c.dSpin(), d.dSpin());

        Vec3 newPos  = p.plusScaled(dPos,  dt);
        Vec3 newVel  = v.plusScaled(dVel,  dt);
        Vec3 newSpin = w.plusScaled(dSpin, dt);

        return new BallState(newPos, newVel, newSpin, spinOrientation(s.orient(), w, newSpin, dt));
    }

    /** Evaluate the derivative at an offset along a previous derivative estimate. */
    private static Derivative sample(Vec3 p, Vec3 v, Vec3 w, Derivative d, double dt) {
        return Aero.derivative(p.plusScaled(d.dPos(), dt),
                               v.plusScaled(d.dVel(), dt),
                               w.plusScaled(d.dSpin(), dt));
    }

    /** The RK4 weighting: (a + 2b + 2c + d)/6. */
    private static Vec3 weighted(Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        return a.plus(b.scale(2)).plus(c.scale(2)).plus(d).scale(1.0 / 6.0);
    }

    /**
     * Advance the visual orientation by rotating about the mean spin axis.
     *
     * Kept out of RK4 deliberately: orientation has no effect on the physics, and a
     * quaternion cannot be RK4'd componentwise without leaving the unit sphere. Rotating by
     * the exact angle |omega|*dt about the averaged axis is both cheaper and, for a rigid
     * body, exact whenever the axis is not itself moving — which for a ball in flight it
     * essentially is not, since spin decay is parallel to spin.
     */
    private static Quat spinOrientation(Quat orient, Vec3 spinBefore, Vec3 spinAfter, double dt) {
        Vec3 mean = spinBefore.plus(spinAfter).scale(0.5);
        double rate = mean.length();
        if (rate < 1e-9) return orient;
        return Quat.fromAxisAngle(mean, rate * dt).times(orient).normalized();
    }
}
