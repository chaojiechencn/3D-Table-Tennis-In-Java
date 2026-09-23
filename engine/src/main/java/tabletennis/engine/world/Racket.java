package tabletennis.engine.world;

import tabletennis.engine.contact.BladeCollider;
import tabletennis.engine.math.Vec3;

/**
 * A kinematic racket: posed from outside, its velocity measured by differencing its own pose over
 * a physics step. Treated as infinitely massive: M_eff ~ 0.13 kg against a 2.7 g ball is a ~2%
 * recoil, and published racket restitution was measured on rigid mounts anyway.
 */
public final class Racket {

    private Vec3 Position;
    private Vec3 Normal;
    private Vec3 Velocity = Vec3.Zero;
    private Vec3 AngularVelocity = Vec3.Zero;

    public Racket(Vec3 Position, Vec3 Normal) {
        this.Position = Position;
        this.Normal = Normal.Normalized();
    }

    /** Pose the blade over one PHYSICS step, deriving its velocity from the move. */
    public void MoveTo(Vec3 NewPosition, Vec3 NewNormal, double Seconds) {
        Vec3 UnitNormal = NewNormal.Normalized();
        if (Seconds > 1e-12) {
            Velocity = NewPosition.Minus(Position).Scale(1.0 / Seconds);
            Vec3 Axis = Normal.Cross(UnitNormal);
            double Sine = Axis.Length();
            AngularVelocity = Sine < 1e-9 ? Vec3.Zero
                                          : Axis.Scale(Math.asin(Math.min(1, Sine)) / (Sine * Seconds));
        }
        Position = NewPosition;
        Normal = UnitNormal;
    }

    /** Pose the blade with no implied motion. */
    public void PlaceAt(Vec3 NewPosition, Vec3 NewNormal) {
        Position = NewPosition;
        Normal = NewNormal.Normalized();
        Velocity = Vec3.Zero;
        AngularVelocity = Vec3.Zero;
    }

    public Vec3 Position() { return Position; }
    public Vec3 Normal()   { return Normal; }
    public Vec3 Velocity() { return Velocity; }

    /** The blade as the solver sees it for one step. */
    public BladeCollider Blade() { return new BladeCollider(Position, Normal, Velocity, AngularVelocity); }
}
