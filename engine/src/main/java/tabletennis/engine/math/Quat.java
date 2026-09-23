package tabletennis.engine.math;

/**
 * Unit quaternion carrying the ball's visual orientation only; a sphere flies the same however
 * it is turned. Chosen over Euler angles, which gimbal-lock on combined side- and topspin.
 */
public record Quat(double W, double X, double Y, double Z) {

    public static final Quat Identity = new Quat(1, 0, 0, 0);

    public static Quat FromAxisAngle(Vec3 Axis, double Angle) {
        Vec3 Unit = Axis.Normalized();
        if (Unit.LengthSquared() == 0) return Identity;
        double Half = Angle * 0.5, Sine = Math.sin(Half);
        return new Quat(Math.cos(Half), Unit.X() * Sine, Unit.Y() * Sine, Unit.Z() * Sine);
    }

    /** Hamilton product; A.Times(B) applies B first. */
    public Quat Times(Quat Other) {
        return new Quat(
            W * Other.W - X * Other.X - Y * Other.Y - Z * Other.Z,
            W * Other.X + X * Other.W + Y * Other.Z - Z * Other.Y,
            W * Other.Y - X * Other.Z + Y * Other.W + Z * Other.X,
            W * Other.Z + X * Other.Y - Y * Other.X + Z * Other.W);
    }

    public double Norm() { return Math.sqrt(W * W + X * X + Y * Y + Z * Z); }

    public Quat Normalized() {
        double Norm = Norm();
        if (Norm < 1e-12) return Identity;
        return new Quat(W / Norm, X / Norm, Y / Norm, Z / Norm);
    }

    /** In [0, pi]. */
    public double Angle() {
        return 2.0 * Math.acos(Math.min(1.0, Math.abs(W)));
    }

    /** Arbitrary unit vector when the angle is ~0. */
    public Vec3 Axis() {
        double Sine = Math.sqrt(1.0 - W * W);
        if (Sine < 1e-9) return Vec3.Up;
        Vec3 Axis = new Vec3(X / Sine, Y / Sine, Z / Sine);
        return W < 0 ? Axis.Negate() : Axis;   // the same branch Angle() takes
    }

    public static Quat Slerp(Quat From, Quat To, double Fraction) {
        Quat Target = To;
        double Cosine = From.W * Target.W + From.X * Target.X + From.Y * Target.Y + From.Z * Target.Z;
        if (Cosine < 0) {
            Target = new Quat(-Target.W, -Target.X, -Target.Y, -Target.Z);
            Cosine = -Cosine;
        }
        if (Cosine > 0.9995) {   // nearly parallel: lerp, or the sine below underflows
            return new Quat(From.W + (Target.W - From.W) * Fraction, From.X + (Target.X - From.X) * Fraction,
                            From.Y + (Target.Y - From.Y) * Fraction, From.Z + (Target.Z - From.Z) * Fraction)
                    .Normalized();
        }
        double Theta = Math.acos(Cosine), Sine = Math.sin(Theta);
        double FromWeight = Math.sin((1 - Fraction) * Theta) / Sine;
        double ToWeight = Math.sin(Fraction * Theta) / Sine;
        return new Quat(From.W * FromWeight + Target.W * ToWeight, From.X * FromWeight + Target.X * ToWeight,
                        From.Y * FromWeight + Target.Y * ToWeight, From.Z * FromWeight + Target.Z * ToWeight);
    }
}
