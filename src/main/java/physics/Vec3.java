package physics;

/**
 * Immutable 3D vector in RIGHT-HANDED physics space (metres, +Y up, +Z toward the camera).
 *
 * A record, so equality and toString come free and the JIT scalarises the short-lived ones
 * inside the integrator. Do not add mutating methods: the RK4 integrator relies on being
 * able to hold onto old states without defensive copying.
 */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 UP   = new Vec3(0, 1, 0);

    public Vec3 plus(Vec3 o)        { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 minus(Vec3 o)       { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 scale(double s)     { return new Vec3(x * s, y * s, z * s); }
    public Vec3 negate()            { return new Vec3(-x, -y, -z); }

    /** this + o*s, the fused form the integrator leans on. */
    public Vec3 plusScaled(Vec3 o, double s) {
        return new Vec3(x + o.x * s, y + o.y * s, z + o.z * s);
    }

    public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }

    /** Right-handed cross product. Magnus and the contact impulses both depend on this sign. */
    public Vec3 cross(Vec3 o) {
        return new Vec3(y * o.z - z * o.y,
                        z * o.x - x * o.z,
                        x * o.y - y * o.x);
    }

    public double lengthSquared() { return x * x + y * y + z * z; }
    public double length()        { return Math.sqrt(lengthSquared()); }

    /** Unit vector, or ZERO if this is (near) degenerate — callers must handle ZERO. */
    public Vec3 normalized() {
        double len = length();
        return len < 1e-12 ? ZERO : scale(1.0 / len);
    }

    /** Component of this parallel to unit vector n. */
    public Vec3 projectOnto(Vec3 unitN) { return unitN.scale(dot(unitN)); }

    /** Component of this perpendicular to unit vector n. */
    public Vec3 tangentTo(Vec3 unitN) { return minus(projectOnto(unitN)); }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    /** Linear interpolation, used only by the renderer to smooth between physics steps. */
    public static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t,
                        a.y + (b.y - a.y) * t,
                        a.z + (b.z - a.z) * t);
    }

    @Override public String toString() {
        return String.format("(%.3f, %.3f, %.3f)", x, y, z);
    }
}
