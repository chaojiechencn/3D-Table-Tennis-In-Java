package pong.game_objects.ball;

import pong.config.Physical;
import pong.core.math.Quat;
import pong.core.math.Vec3;

/**
 * A complete snapshot of the ball at one instant, in physics space.
 *
 * Immutable on purpose. The fixed-timestep loop keeps the previous and current states side
 * by side so the renderer can interpolate between them; if states were mutable that would
 * silently become "the same state twice" and every frame would judder.
 *
 * @param pos     centre of the ball, metres
 * @param vel     velocity, m/s
 * @param spin    angular velocity, rad/s, right-hand rule. For a ball travelling toward
 *                the far end (-Z), TOPSPIN is spin about -X (the top of the ball moves the
 *                way the ball is going) and BACKSPIN is spin about +X. Sidespin is about Y.
 * @param orient  visual orientation only; never influences the flight
 */
public record Ball(Vec3 pos, Vec3 vel, Vec3 spin, Quat orient) {

    public static Ball at(Vec3 pos, Vec3 vel, Vec3 spin) {
        return new Ball(pos, vel, spin, Quat.IDENTITY);
    }

    public Ball withPos(Vec3 p)    { return new Ball(p, vel, spin, orient); }
    public Ball withVel(Vec3 v)    { return new Ball(pos, v, spin, orient); }
    public Ball withSpin(Vec3 s)   { return new Ball(pos, vel, s, orient); }

    public double speed()    { return vel.length(); }
    public double spinRate() { return spin.length(); }

    /** Spin in revolutions per second — the unit table tennis is actually discussed in.
     *  A heavy loop is 100-150 rev/s; a serve can exceed 150. */
    public double spinRevsPerSec() { return spinRate() / (2 * Math.PI); }

    /** Kinetic energy, translational + rotational. Used by PhysicsTest to prove no contact
     *  ever adds energy to the ball. */
    public double kineticEnergy() {
        return 0.5 * Physical.BALL_M * vel.lengthSquared()
             + 0.5 * Physical.BALL_I * spin.lengthSquared();
    }

    public boolean isFinite() {
        return pos.isFinite() && vel.isFinite() && spin.isFinite();
    }
}
