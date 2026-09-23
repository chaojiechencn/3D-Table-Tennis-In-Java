package tabletennis.engine;

import tabletennis.engine.Aero.Derivative;

/**
 * Classical RK4 over the flight equations at a fixed step. Flight only: contacts are
 * discontinuities, which World resolves separately after each step.
 */
public final class Integrator {

    private Integrator() {}

    public static BallState Step(BallState S, double Dt) {
        return Step(S, Dt, Aero.DefaultDrag);
    }

    /** Under a named drag law; SelfTest uses a constant coefficient to compare with the closed form. */
    public static BallState Step(BallState S, double Dt, Aero.DragModel Drag) {
        Vec3 P = S.Pos(), V = S.Vel(), W = S.Spin();

        Derivative A = Aero.Derivative(P, V, W, Drag);
        Derivative B = Sample(P, V, W, A, Dt * 0.5, Drag);
        Derivative C = Sample(P, V, W, B, Dt * 0.5, Drag);
        Derivative D = Sample(P, V, W, C, Dt, Drag);

        Vec3 NewPos  = P.PlusScaled(Weighted(A.DPos(),  B.DPos(),  C.DPos(),  D.DPos()),  Dt);
        Vec3 NewVel  = V.PlusScaled(Weighted(A.DVel(),  B.DVel(),  C.DVel(),  D.DVel()),  Dt);
        Vec3 NewSpin = W.PlusScaled(Weighted(A.DSpin(), B.DSpin(), C.DSpin(), D.DSpin()), Dt);

        return new BallState(NewPos, NewVel, NewSpin, SpinOrientation(S.Orient(), W, NewSpin, Dt));
    }

    private static Derivative Sample(Vec3 P, Vec3 V, Vec3 W, Derivative D, double Dt,
                                     Aero.DragModel Drag) {
        return Aero.Derivative(P.PlusScaled(D.DPos(), Dt),
                               V.PlusScaled(D.DVel(), Dt),
                               W.PlusScaled(D.DSpin(), Dt),
                               Drag);
    }

    private static Vec3 Weighted(Vec3 A, Vec3 B, Vec3 C, Vec3 D) {
        return A.Plus(B.Scale(2)).Plus(C.Scale(2)).Plus(D).Scale(1.0 / 6.0);
    }

    /** Rotates by |omega|*dt about the mean spin axis: exact while the axis is steady. */
    private static Quat SpinOrientation(Quat Orient, Vec3 SpinBefore, Vec3 SpinAfter, double Dt) {
        Vec3 Mean = SpinBefore.Plus(SpinAfter).Scale(0.5);
        double Rate = Mean.Length();
        if (Rate < 1e-9) return Orient;
        return Quat.FromAxisAngle(Mean, Rate * Dt).Times(Orient).Normalized();
    }
}
