package tabletennis.engine;

/**
 * Unit quaternion carrying the ball's visual orientation only; a sphere flies the same however
 * it is turned. Chosen over Euler angles, which gimbal-lock on combined side- and topspin.
 */
public record Quat(double W, double X, double Y, double Z) {

    public static final Quat Identity = new Quat(1, 0, 0, 0);

    public static Quat FromAxisAngle(Vec3 Axis, double Angle) {
        Vec3 N = Axis.Normalized();
        if (N.LengthSquared() == 0) return Identity;
        double H = Angle * 0.5, S = Math.sin(H);
        return new Quat(Math.cos(H), N.X() * S, N.Y() * S, N.Z() * S);
    }

    /** Hamilton product; q.times(r) applies r first. */
    public Quat Times(Quat R) {
        return new Quat(
            W * R.W - X * R.X - Y * R.Y - Z * R.Z,
            W * R.X + X * R.W + Y * R.Z - Z * R.Y,
            W * R.Y - X * R.Z + Y * R.W + Z * R.X,
            W * R.Z + X * R.Y - Y * R.X + Z * R.W);
    }

    public Quat Normalized() {
        double Len = Math.sqrt(W * W + X * X + Y * Y + Z * Z);
        if (Len < 1e-12) return Identity;
        return new Quat(W / Len, X / Len, Y / Len, Z / Len);
    }

    /** In [0, pi]. */
    public double Angle() {
        return 2.0 * Math.acos(Math.min(1.0, Math.abs(W)));
    }

    /** Arbitrary unit vector when the angle is ~0. */
    public Vec3 Axis() {
        double S = Math.sqrt(1.0 - W * W);
        if (S < 1e-9) return Vec3.Up;
        Vec3 A = new Vec3(X / S, Y / S, Z / S);
        return W < 0 ? A.Negate() : A;   // same branch as angle()
    }

    public static Quat Slerp(Quat A, Quat B, double T) {
        double D = A.W * B.W + A.X * B.X + A.Y * B.Y + A.Z * B.Z;
        if (D < 0) { B = new Quat(-B.W, -B.X, -B.Y, -B.Z); D = -D; }
        if (D > 0.9995) {   // nearly parallel: lerp, or sin(theta) underflows
            return new Quat(A.W + (B.W - A.W) * T, A.X + (B.X - A.X) * T,
                            A.Y + (B.Y - A.Y) * T, A.Z + (B.Z - A.Z) * T).Normalized();
        }
        double Theta = Math.acos(D), Sin = Math.sin(Theta);
        double Ka = Math.sin((1 - T) * Theta) / Sin, Kb = Math.sin(T * Theta) / Sin;
        return new Quat(A.W * Ka + B.W * Kb, A.X * Ka + B.X * Kb,
                        A.Y * Ka + B.Y * Kb, A.Z * Ka + B.Z * Kb);
    }
}
