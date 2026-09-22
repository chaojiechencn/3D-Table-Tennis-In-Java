package tabletennis.engine;

/**
 * The ball at one instant. Spin is rad/s by the right-hand rule: for a ball travelling toward -Z,
 * topspin is about -X and backspin about +X. Orientation is visual only.
 */
public record BallState(Vec3 pos, Vec3 vel, Vec3 spin, Quat orient) {

    public static BallState at(Vec3 pos, Vec3 vel, Vec3 spin) {
        return new BallState(pos, vel, spin, Quat.IDENTITY);
    }

    public BallState withPos(Vec3 p)    { return new BallState(p, vel, spin, orient); }
    public BallState withVel(Vec3 v)    { return new BallState(pos, v, spin, orient); }
    public BallState withSpin(Vec3 s)   { return new BallState(pos, vel, s, orient); }

    public double speed()    { return vel.length(); }
    public double spinRate() { return spin.length(); }

    public double spinRevsPerSec() { return spinRate() / (2 * Math.PI); }

    public double kineticEnergy() {
        return 0.5 * Constants.BALL_M * vel.lengthSquared()
             + 0.5 * Constants.BALL_I * spin.lengthSquared();
    }

    public boolean isFinite() {
        return pos.isFinite() && vel.isFinite() && spin.isFinite();
    }
}
