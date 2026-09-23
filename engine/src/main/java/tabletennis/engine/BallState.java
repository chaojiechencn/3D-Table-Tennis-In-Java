package tabletennis.engine;

/**
 * The ball at one instant. Spin is rad/s by the right-hand rule: for a ball travelling toward -Z,
 * topspin is about -X and backspin about +X. Orientation is visual only.
 */
public record BallState(Vec3 Pos, Vec3 Vel, Vec3 Spin, Quat Orient) {

    public static BallState At(Vec3 Pos, Vec3 Vel, Vec3 Spin) {
        return new BallState(Pos, Vel, Spin, Quat.Identity);
    }

    public BallState WithPos(Vec3 P)    { return new BallState(P, Vel, Spin, Orient); }
    public BallState WithVel(Vec3 V)    { return new BallState(Pos, V, Spin, Orient); }
    public BallState WithSpin(Vec3 S)   { return new BallState(Pos, Vel, S, Orient); }

    public double Speed()    { return Vel.Length(); }
    public double SpinRate() { return Spin.Length(); }

    public double SpinRevsPerSec() { return SpinRate() / (2 * Math.PI); }

    public double KineticEnergy() {
        return 0.5 * Constants.BallM * Vel.LengthSquared()
             + 0.5 * Constants.BallI * Spin.LengthSquared();
    }

    public boolean IsFinite() {
        return Pos.IsFinite() && Vel.IsFinite() && Spin.IsFinite();
    }
}
