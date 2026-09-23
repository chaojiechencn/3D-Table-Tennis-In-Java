package tabletennis.engine;

import tabletennis.engine.math.Quat;
import tabletennis.engine.math.Vec3;

/**
 * The ball at one instant. Spin is rad/s by the right-hand rule: for a ball travelling toward -Z,
 * topspin is about -X and backspin about +X. Orientation is visual only.
 */
public record BallState(Vec3 Position, Vec3 Velocity, Vec3 Spin, Quat Orientation) {

    public static BallState At(Vec3 Position, Vec3 Velocity, Vec3 Spin) {
        return new BallState(Position, Velocity, Spin, Quat.Identity);
    }

    public BallState WithPosition(Vec3 NewPosition) { return new BallState(NewPosition, Velocity, Spin, Orientation); }
    public BallState WithVelocity(Vec3 NewVelocity) { return new BallState(Position, NewVelocity, Spin, Orientation); }
    public BallState WithSpin(Vec3 NewSpin)         { return new BallState(Position, Velocity, NewSpin, Orientation); }

    public double Speed()    { return Velocity.Length(); }
    public double SpinRate() { return Spin.Length(); }

    public double SpinRevsPerSecond() { return SpinRate() / (2 * Math.PI); }

    public double KineticEnergy() {
        return 0.5 * BallSpec.Mass * Velocity.LengthSquared()
             + 0.5 * BallSpec.Inertia * Spin.LengthSquared();
    }

    public boolean IsFinite() {
        return Position.IsFinite() && Velocity.IsFinite() && Spin.IsFinite();
    }
}
