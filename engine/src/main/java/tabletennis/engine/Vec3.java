package tabletennis.engine;

/** Immutable vector in right-handed physics space: metres, +Y up, +Z toward the player. */
public record Vec3(double X, double Y, double Z) {

    public static final Vec3 Zero = new Vec3(0, 0, 0);
    public static final Vec3 Up   = new Vec3(0, 1, 0);

    public Vec3 Plus(Vec3 O)        { return new Vec3(X + O.X, Y + O.Y, Z + O.Z); }
    public Vec3 Minus(Vec3 O)       { return new Vec3(X - O.X, Y - O.Y, Z - O.Z); }
    public Vec3 Scale(double S)     { return new Vec3(X * S, Y * S, Z * S); }
    public Vec3 Negate()            { return new Vec3(-X, -Y, -Z); }

    public Vec3 PlusScaled(Vec3 O, double S) {
        return new Vec3(X + O.X * S, Y + O.Y * S, Z + O.Z * S);
    }

    public double Dot(Vec3 O) { return X * O.X + Y * O.Y + Z * O.Z; }

    /** Right-handed; Magnus and the contact impulses depend on this sign. */
    public Vec3 Cross(Vec3 O) {
        return new Vec3(Y * O.Z - Z * O.Y,
                        Z * O.X - X * O.Z,
                        X * O.Y - Y * O.X);
    }

    public double LengthSquared() { return X * X + Y * Y + Z * Z; }
    public double Length()        { return Math.sqrt(LengthSquared()); }

    /** Unit vector, or ZERO when degenerate. */
    public Vec3 Normalized() {
        double Len = Length();
        return Len < 1e-12 ? Zero : Scale(1.0 / Len);
    }

    public Vec3 ProjectOnto(Vec3 UnitN) { return UnitN.Scale(Dot(UnitN)); }

    public Vec3 TangentTo(Vec3 UnitN) { return Minus(ProjectOnto(UnitN)); }

    public boolean IsFinite() {
        return Double.isFinite(X) && Double.isFinite(Y) && Double.isFinite(Z);
    }

    public static Vec3 Lerp(Vec3 A, Vec3 B, double T) {
        return new Vec3(A.X + (B.X - A.X) * T,
                        A.Y + (B.Y - A.Y) * T,
                        A.Z + (B.Z - A.Z) * T);
    }

    @Override public String toString() {
        return String.format("(%.3f, %.3f, %.3f)", X, Y, Z);
    }
}
