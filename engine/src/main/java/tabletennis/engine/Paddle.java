package tabletennis.engine;

import static tabletennis.engine.Constants.*;

/**
 * A kinematic racket: posed from outside, its velocity measured by differencing its own pose over
 * a physics step. Treated as infinitely massive: M_eff ~ 0.13 kg against a 2.7 g ball is a ~2%
 * recoil, and published racket restitution was measured on rigid mounts anyway.
 */
public final class Paddle {

    /** A frozen disc-slab snapshot, so the solver gets consistent answers within a step. */
    public record Blade(Vec3 Centre, Vec3 Normal, Vec3 Vel, Vec3 AngVel) implements Collider {

        private static final double HalfThick = BladeThick / 2;

        @Override public Vec3 ClosestPoint(Vec3 P) {
            Vec3 D = P.Minus(Centre);
            double Along = D.Dot(Normal);
            Vec3 InPlane = D.Minus(Normal.Scale(Along));

            double R = InPlane.Length();
            if (R > BladeR) InPlane = InPlane.Scale(BladeR / R);

            double Clamped = Along < -HalfThick ? -HalfThick
                           : (Along > HalfThick ? HalfThick : Along);
            return Centre.Plus(InPlane).PlusScaled(Normal, Clamped);
        }

        /** Through the nearer face, which for a 15 mm by 150 mm disc is always the way out. */
        @Override public Vec3 EscapeNormal(Vec3 P) {
            return P.Minus(Centre).Dot(Normal) >= 0 ? Normal : Normal.Negate();
        }

        /** A plane crossing plus a rim check: exact for a thin blade. */
        @Override public double Sweep(Vec3 P0, Vec3 P1) {
            double Surface = HalfThick + BallR;
            double D0 = P0.Minus(Centre).Dot(Normal);
            double D1 = P1.Minus(Centre).Dot(Normal);

            double Delta = D1 - D0;
            if (Math.abs(Delta) < 1e-12) return -1;          // parallel to the face

            double Target = D0 >= 0 ? Surface : -Surface;
            double T = (Target - D0) / Delta;
            if (T < 0 || T > 1) return -1;

            Vec3 At = Vec3.Lerp(P0, P1, T);
            Vec3 D = At.Minus(Centre);
            Vec3 InPlane = D.Minus(Normal.Scale(D.Dot(Normal)));
            double Rim = BladeR + BallR;
            return InPlane.LengthSquared() > Rim * Rim ? -1 : T;
        }

        @Override public Vec3 VelocityAt(Vec3 Point) {
            return Vel.Plus(AngVel.Cross(Point.Minus(Centre)));
        }
    }

    private Vec3 Pos;
    private Vec3 Normal;
    private Vec3 Vel = Vec3.Zero;
    private Vec3 AngVel = Vec3.Zero;

    public Paddle(Vec3 Pos, Vec3 Normal) {
        this.Pos = Pos;
        this.Normal = Normal.Normalized();
    }

    /** Pose the blade over one PHYSICS step, deriving its velocity from the move. */
    public void MoveTo(Vec3 NewPos, Vec3 NewNormal, double Dt) {
        Vec3 N = NewNormal.Normalized();
        if (Dt > 1e-12) {
            Vel = NewPos.Minus(Pos).Scale(1.0 / Dt);
            Vec3 Axis = Normal.Cross(N);
            double Sin = Axis.Length();
            AngVel = Sin < 1e-9 ? Vec3.Zero
                                : Axis.Scale(Math.asin(Math.min(1, Sin)) / (Sin * Dt));
        }
        Pos = NewPos;
        Normal = N;
    }

    /** Pose the blade with no implied motion. */
    public void PlaceAt(Vec3 NewPos, Vec3 NewNormal) {
        Pos = NewPos;
        Normal = NewNormal.Normalized();
        Vel = Vec3.Zero;
        AngVel = Vec3.Zero;
    }

    public Vec3 Pos()    { return Pos; }
    public Vec3 Normal() { return Normal; }
    public Vec3 Vel()    { return Vel; }

    public Blade Collider() { return new Blade(Pos, Normal, Vel, AngVel); }
}
