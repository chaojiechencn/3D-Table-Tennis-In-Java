package physics;

/** Immutable vector in right-handed physics space: metres, +Y up, +Z toward the player. */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 UP   = new Vec3(0, 1, 0);

    public Vec3 plus(Vec3 o)        { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 minus(Vec3 o)       { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 scale(double s)     { return new Vec3(x * s, y * s, z * s); }
    public Vec3 negate()            { return new Vec3(-x, -y, -z); }

    public Vec3 plusScaled(Vec3 o, double s) {
        return new Vec3(x + o.x * s, y + o.y * s, z + o.z * s);
    }

    public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }

    /** Right-handed; Magnus and the contact impulses depend on this sign. */
    public Vec3 cross(Vec3 o) {
        return new Vec3(y * o.z - z * o.y,
                        z * o.x - x * o.z,
                        x * o.y - y * o.x);
    }

    public double lengthSquared() { return x * x + y * y + z * z; }
    public double length()        { return Math.sqrt(lengthSquared()); }

    /** Unit vector, or ZERO when degenerate. */
    public Vec3 normalized() {
        double len = length();
        return len < 1e-12 ? ZERO : scale(1.0 / len);
    }

    public Vec3 projectOnto(Vec3 unitN) { return unitN.scale(dot(unitN)); }

    public Vec3 tangentTo(Vec3 unitN) { return minus(projectOnto(unitN)); }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t,
                        a.y + (b.y - a.y) * t,
                        a.z + (b.z - a.z) * t);
    }

    @Override public String toString() {
        return String.format("(%.3f, %.3f, %.3f)", x, y, z);
    }
}
