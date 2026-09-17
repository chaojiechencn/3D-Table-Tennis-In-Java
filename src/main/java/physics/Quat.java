package physics;

/**
 * Unit quaternion, used only to carry the ball's ORIENTATION.
 *
 * Orientation does not affect the flight at all — a sphere is a sphere — so it is kept out
 * of the RK4 state and advanced separately. It exists purely so the renderer can paint a
 * marked ball whose tumbling makes the spin visible. A demo that claims "the ball is
 * spinning" without showing it spin is not a demo.
 *
 * Euler angles were the obvious alternative and are rejected: a serve with combined
 * side- and topspin walks straight into gimbal lock, and the ball would visibly stutter.
 */
public record Quat(double w, double x, double y, double z) {

    public static final Quat IDENTITY = new Quat(1, 0, 0, 0);

    /** Rotation of {@code angle} radians about {@code axis} (right-hand rule). */
    public static Quat fromAxisAngle(Vec3 axis, double angle) {
        Vec3 n = axis.normalized();
        if (n.lengthSquared() == 0) return IDENTITY;
        double h = angle * 0.5, s = Math.sin(h);
        return new Quat(Math.cos(h), n.x() * s, n.y() * s, n.z() * s);
    }

    /** Hamilton product: the rotation {@code this} followed by nothing else — q*r applies r first. */
    public Quat times(Quat r) {
        return new Quat(
            w * r.w - x * r.x - y * r.y - z * r.z,
            w * r.x + x * r.w + y * r.z - z * r.y,
            w * r.y - x * r.z + y * r.w + z * r.x,
            w * r.z + x * r.y - y * r.x + z * r.w);
    }

    /** Renormalise. Called every step: float drift over a long rally would otherwise shear the ball. */
    public Quat normalized() {
        double len = Math.sqrt(w * w + x * x + y * y + z * z);
        if (len < 1e-12) return IDENTITY;
        return new Quat(w / len, x / len, y / len, z / len);
    }

    /** Rotation angle in radians, in [0, pi]. */
    public double angle() {
        return 2.0 * Math.acos(Math.min(1.0, Math.abs(w)));
    }

    /** Rotation axis; arbitrary unit vector when the angle is ~0. */
    public Vec3 axis() {
        double s = Math.sqrt(1.0 - w * w);
        if (s < 1e-9) return Vec3.UP;
        Vec3 a = new Vec3(x / s, y / s, z / s);
        return w < 0 ? a.negate() : a;   // keep axis/angle on the same branch as angle()
    }

    /** Shortest-arc interpolation, used to smooth the ball between physics steps. */
    public static Quat slerp(Quat a, Quat b, double t) {
        double d = a.w * b.w + a.x * b.x + a.y * b.y + a.z * b.z;
        if (d < 0) { b = new Quat(-b.w, -b.x, -b.y, -b.z); d = -d; }
        if (d > 0.9995) {   // nearly parallel: lerp, or sin(theta) underflows
            return new Quat(a.w + (b.w - a.w) * t, a.x + (b.x - a.x) * t,
                            a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t).normalized();
        }
        double theta = Math.acos(d), sin = Math.sin(theta);
        double ka = Math.sin((1 - t) * theta) / sin, kb = Math.sin(t * theta) / sin;
        return new Quat(a.w * ka + b.w * kb, a.x * ka + b.x * kb,
                        a.y * ka + b.y * kb, a.z * ka + b.z * kb);
    }
}
