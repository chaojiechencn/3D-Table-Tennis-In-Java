package tabletennis.engine;

import tabletennis.engine.Constants.Material;

import static tabletennis.engine.Constants.*;

/**
 * The ONE collision solver (AGENTS.md invariant 4): a normal impulse with restitution plus a
 * tangential impulse that grips, springs back or slides, differing per surface only by
 * {@link Material}. Spin coupling falls out of the impulse rather than being scripted.
 */
public final class Contacts {

    private Contacts() {}

    /** An axis-aligned volume: the table, the net and the floor. */
    public record Box(Vec3 Min, Vec3 Max) implements Collider {
        public static Box Centered(double Cx, double Cy, double Cz,
                                   double Sx, double Sy, double Sz) {
            return new Box(new Vec3(Cx - Sx / 2, Cy - Sy / 2, Cz - Sz / 2),
                           new Vec3(Cx + Sx / 2, Cy + Sy / 2, Cz + Sz / 2));
        }

        @Override public Vec3 ClosestPoint(Vec3 P) {
            return new Vec3(Clamp(P.X(), Min.X(), Max.X()),
                            Clamp(P.Y(), Min.Y(), Max.Y()),
                            Clamp(P.Z(), Min.Z(), Max.Z()));
        }

        /** Normal of the nearest face. */
        @Override public Vec3 EscapeNormal(Vec3 P) {
            double Best = P.X() - Min.X();
            Vec3 N = new Vec3(-1, 0, 0);

            double D = Max.X() - P.X();
            if (D < Best) { Best = D; N = new Vec3(1, 0, 0); }
            D = P.Y() - Min.Y();
            if (D < Best) { Best = D; N = new Vec3(0, -1, 0); }
            D = Max.Y() - P.Y();
            if (D < Best) { Best = D; N = new Vec3(0, 1, 0); }
            D = P.Z() - Min.Z();
            if (D < Best) { Best = D; N = new Vec3(0, 0, -1); }
            D = Max.Z() - P.Z();
            if (D < Best) { N = new Vec3(0, 0, 1); }
            return N;
        }

        /**
         * A ray against the box grown by the ball radius. Square corners can register a corner
         * clip up to one radius early; the rounded normal from closestPoint is still correct.
         */
        @Override public double Sweep(Vec3 P0, Vec3 P1) {
            Vec3 D = P1.Minus(P0);

            double[] O = { P0.X(), P0.Y(), P0.Z() };
            double[] Dd = { D.X(), D.Y(), D.Z() };
            double[] Lo = { Min.X() - BallR, Min.Y() - BallR, Min.Z() - BallR };
            double[] Hi = { Max.X() + BallR, Max.Y() + BallR, Max.Z() + BallR };

            double TEnter = 0.0, TExit = 1.0;

            for (int I = 0; I < 3; I++) {
                if (Math.abs(Dd[I]) < 1e-12) {
                    if (O[I] < Lo[I] || O[I] > Hi[I]) return -1;      // parallel and outside
                    continue;
                }
                double T1 = (Lo[I] - O[I]) / Dd[I];
                double T2 = (Hi[I] - O[I]) / Dd[I];
                if (T1 > T2) { double Tmp = T1; T1 = T2; T2 = Tmp; }
                TEnter = Math.max(TEnter, T1);
                TExit = Math.min(TExit, T2);
                if (TEnter > TExit) return -1;
            }
            return TEnter;
        }

        @Override public Vec3 VelocityAt(Vec3 Point) { return Vec3.Zero; }
    }

    public record Hit(BallState State, Vec3 Point, Vec3 Normal,
                      double ImpactSpeed, boolean Resting) {}

    /** Below this normal speed there is no bounce: under 1 mm of height, and it ends the jitter. */
    private static final double RestingSpeed = 0.15;

    /** Applied only while resting, so a settled ball stops instead of rolling forever. */
    private static final double RollingMu = 0.02;

    /** Gap left after a contact so the next step starts clean. */
    private static final double Skin = 1e-4;

    /**
     * A detected touch, before any response, so World can resolve the EARLIEST of all surfaces.
     * {@code toi} is the step fraction (1.0 for an end-of-step overlap); {@code swept} means the
     * ball would otherwise have passed through and still has step left to fly.
     */
    public record Contact(double Toi, Vec3 Point, Vec3 Normal, boolean Swept) {}

    /** The contact for the motion prev -> next, or null if the ball never touched the surface. */
    public static Contact Detect(BallState Prev, BallState Next, Collider Surface) {
        Vec3 P0 = Prev.Pos(), P1 = Next.Pos();

        if (Surface.ClosestPoint(P1).Minus(P1).LengthSquared() < BallR * BallR) {
            return new Contact(1.0, P1, NormalAt(Surface, P1), false);
        }

        // Swept in the SURFACE's frame (invariant 5): carry the start into the collider's
        // end-of-step pose. For every static surface u is zero and this is a plain sweep.
        Vec3 U = Surface.VelocityAt(P1);
        Vec3 Q0 = P0.PlusScaled(U, Dt);

        double T = Surface.Sweep(Q0, P1);
        if (T < 0) return null;

        Vec3 At = Vec3.Lerp(Q0, P1, T);
        return new Contact(T, At, NormalAt(Surface, At), true);
    }

    public static Hit Respond(BallState Next, Collider Surface, Contact Detected, Material Mat) {
        return ApplyImpulse(Next.WithPos(Detected.Point()), Surface, Detected.Normal(), Mat);
    }

    private static Vec3 NormalAt(Collider Surface, Vec3 P) {
        Vec3 Offset = P.Minus(Surface.ClosestPoint(P));
        return Offset.LengthSquared() < 1e-18 ? Surface.EscapeNormal(P) : Offset.Normalized();
    }

    private static Hit ApplyImpulse(BallState S, Collider Surface, Vec3 N, Material Mat) {
        Vec3 V = S.Vel(), W = S.Spin();
        Vec3 ContactPoint = S.Pos().PlusScaled(N, -BallR);
        // Relative to the surface (invariant 5): in absolute terms a blade catching a receding
        // ball would read as already separating.
        Vec3 U = Surface.VelocityAt(ContactPoint);

        double Vn = V.Minus(U).Dot(N);
        double ImpactSpeed = Math.abs(Vn);
        if (Vn > 0) {
            // Already leaving: reflecting would spit the ball out of the surface.
            return new Hit(PushOut(S, Surface, N), ContactPoint, N, 0, false);
        }

        boolean Resting = ImpactSpeed < RestingSpeed;
        double E = Resting ? 0.0 : Mat.RestitutionAt(ImpactSpeed);
        double Jn = -(1.0 + E) * Vn * BallM;

        Vec3 Arm = N.Scale(-BallR);
        Vec3 Slip = V.Plus(W.Cross(Arm)).Minus(U).TangentTo(N);
        Vec3 Jt = TangentialImpulse(Slip, Jn, Mat);

        Vec3 NewVel = V.PlusScaled(N.Scale(Jn).Plus(Jt), 1.0 / BallM);
        Vec3 NewSpin = W.PlusScaled(Arm.Cross(Jt), 1.0 / BallI);   // normal impulse has no torque

        boolean RestingOnStaticFloor = Resting && U.LengthSquared() < 1e-18 && N.Y() > 0.5;
        if (RestingOnStaticFloor) NewVel = WithRollingResistance(NewVel, N);
        NewSpin = DampDrillSpin(NewSpin, N, Mat);

        NewVel = NewVel.Scale(Mat.VelDamping());
        NewSpin = NewSpin.Scale(Mat.SpinDamping());

        BallState Out = PushOut(S.WithVel(NewVel).WithSpin(NewSpin), Surface, N);
        return new Hit(Out, ContactPoint, N, ImpactSpeed, Resting);
    }

    /**
     * Killing the slip of a HOLLOW shell (I = (2/3)mr²) takes J = -(2/5) m slip; the (1 + e_t)
     * factor springs the patch back, which is what reverses spin off rubber. Past the friction
     * cone the patch slides instead.
     */
    private static Vec3 TangentialImpulse(Vec3 Slip, double Jn, Material Mat) {
        double Et = Mat.TangentialRestitutionAt(Slip.Length());
        Vec3 Grip = Slip.Scale(-(2.0 / 5.0) * (1.0 + Et) * BallM);

        double MaxFriction = Mat.Friction() * Jn;
        boolean InsideCone = Grip.Length() <= MaxFriction || Slip.LengthSquared() < 1e-18;
        return InsideCone ? Grip : Slip.Normalized().Scale(-MaxFriction);
    }

    /** Only on a static, upward-facing surface: on a swinging paddle it would steal pace. */
    private static Vec3 WithRollingResistance(Vec3 Vel, Vec3 N) {
        Vec3 Tangential = Vel.TangentTo(N);
        double Drop = RollingMu * G * Dt;
        return Tangential.Length() > Drop
             ? Vel.Minus(Tangential.Normalized().Scale(Drop))
             : Vel.Minus(Tangential);
    }

    /** Damps only spin about the normal; applied to all of it, it would erase a stroke's topspin. */
    private static Vec3 DampDrillSpin(Vec3 Spin, Vec3 N, Material Mat) {
        if (Mat.DrillSpinDamping() == 1.0) return Spin;
        Vec3 Drill = Spin.ProjectOnto(N);
        return Spin.Minus(Drill).PlusScaled(Drill, Mat.DrillSpinDamping());
    }

    private static BallState PushOut(BallState S, Collider Surface, Vec3 N) {
        Vec3 Nearest = Surface.ClosestPoint(S.Pos());
        Vec3 Offset = S.Pos().Minus(Nearest);
        double Dist = Offset.Length();

        if (Dist < 1e-9) return S.WithPos(Nearest.PlusScaled(N, BallR + Skin));
        if (Dist >= BallR) return S;
        return S.WithPos(Nearest.PlusScaled(Offset.Scale(1.0 / Dist), BallR + Skin));
    }

    private static double Clamp(double V, double Lo, double Hi) {
        return V < Lo ? Lo : (V > Hi ? Hi : V);
    }
}
