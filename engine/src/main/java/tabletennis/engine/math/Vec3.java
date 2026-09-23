package tabletennis.engine.math;

/** Immutable vector in right-handed physics space: metres, +Y up, +Z toward the near end. */
public record Vec3(double X, double Y, double Z) {

    public static final Vec3 Zero = new Vec3(0, 0, 0);
    public static final Vec3 Up = new Vec3(0, 1, 0);

    public Vec3 Plus(Vec3 Other)     { return new Vec3(X + Other.X, Y + Other.Y, Z + Other.Z); }
    public Vec3 Minus(Vec3 Other)    { return new Vec3(X - Other.X, Y - Other.Y, Z - Other.Z); }
    public Vec3 Scale(double Factor) { return new Vec3(X * Factor, Y * Factor, Z * Factor); }
    public Vec3 Negate()             { return new Vec3(-X, -Y, -Z); }

    public Vec3 PlusScaled(Vec3 Other, double Factor) {
        return new Vec3(X + Other.X * Factor, Y + Other.Y * Factor, Z + Other.Z * Factor);
    }

    public double Dot(Vec3 Other) { return X * Other.X + Y * Other.Y + Z * Other.Z; }

    /** Right-handed; Magnus and the contact impulses depend on this sign. */
    public Vec3 Cross(Vec3 Other) {
        return new Vec3(Y * Other.Z - Z * Other.Y,
                        Z * Other.X - X * Other.Z,
                        X * Other.Y - Y * Other.X);
    }

    public double LengthSquared() { return X * X + Y * Y + Z * Z; }
    public double Length()        { return Math.sqrt(LengthSquared()); }

    /** Unit vector, or Zero when degenerate. */
    public Vec3 Normalized() {
        double Length = Length();
        return Length < 1e-12 ? Zero : Scale(1.0 / Length);
    }

    public Vec3 ProjectOnto(Vec3 UnitNormal) { return UnitNormal.Scale(Dot(UnitNormal)); }

    public Vec3 TangentTo(Vec3 UnitNormal) { return Minus(ProjectOnto(UnitNormal)); }

    /** This point carried toward Target by at most MaxStep. */
    public Vec3 MovedToward(Vec3 Target, double MaxStep) {
        Vec3 Offset = Target.Minus(this);
        double Distance = Offset.Length();
        return Distance <= MaxStep ? Target : PlusScaled(Offset.Scale(1.0 / Distance), MaxStep);
    }

    public boolean IsFinite() {
        return Double.isFinite(X) && Double.isFinite(Y) && Double.isFinite(Z);
    }

    public static Vec3 Lerp(Vec3 From, Vec3 To, double Fraction) {
        return new Vec3(From.X + (To.X - From.X) * Fraction,
                        From.Y + (To.Y - From.Y) * Fraction,
                        From.Z + (To.Z - From.Z) * Fraction);
    }

    @Override public String toString() {
        return String.format("(%.3f, %.3f, %.3f)", X, Y, Z);
    }
}
