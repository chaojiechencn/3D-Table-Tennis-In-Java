package tabletennis.engine.flight;

import tabletennis.engine.BallState;
import tabletennis.engine.aero.Aerodynamics;
import tabletennis.engine.aero.Aerodynamics.Derivative;
import tabletennis.engine.aero.DragModel;
import tabletennis.engine.math.Quat;
import tabletennis.engine.math.Vec3;

/**
 * Classical RK4 over the flight equations at a fixed step. Flight only: contacts are
 * discontinuities, which the world resolves separately after each step.
 */
public final class Integrator {

    private Integrator() {}

    public static BallState Step(BallState State, double Seconds) {
        return Step(State, Seconds, DragModel.Measured);
    }

    /** Under a named drag law; the closed-form checks fly a constant coefficient. */
    public static BallState Step(BallState State, double Seconds, DragModel Drag) {
        Vec3 P = State.Position(), V = State.Velocity(), W = State.Spin();

        Derivative K1 = Aerodynamics.DerivativeOf(V, W, Drag);
        Derivative K2 = Sample(V, W, K1, Seconds * 0.5, Drag);
        Derivative K3 = Sample(V, W, K2, Seconds * 0.5, Drag);
        Derivative K4 = Sample(V, W, K3, Seconds, Drag);

        Vec3 NewPosition = P.PlusScaled(Weighted(K1.PositionRate(), K2.PositionRate(), K3.PositionRate(), K4.PositionRate()), Seconds);
        Vec3 NewVelocity = V.PlusScaled(Weighted(K1.VelocityRate(), K2.VelocityRate(), K3.VelocityRate(), K4.VelocityRate()), Seconds);
        Vec3 NewSpin = W.PlusScaled(Weighted(K1.SpinRate(), K2.SpinRate(), K3.SpinRate(), K4.SpinRate()), Seconds);

        return new BallState(NewPosition, NewVelocity, NewSpin,
                             SpinOrientation(State.Orientation(), W, NewSpin, Seconds));
    }

    /** The forces depend on velocity and spin only, so position never enters a sample. */
    private static Derivative Sample(Vec3 V, Vec3 W, Derivative Slope, double Seconds, DragModel Drag) {
        return Aerodynamics.DerivativeOf(V.PlusScaled(Slope.VelocityRate(), Seconds),
                                         W.PlusScaled(Slope.SpinRate(), Seconds),
                                         Drag);
    }

    private static Vec3 Weighted(Vec3 K1, Vec3 K2, Vec3 K3, Vec3 K4) {
        return K1.Plus(K2.Scale(2)).Plus(K3.Scale(2)).Plus(K4).Scale(1.0 / 6.0);
    }

    /** Rotates by |omega| * dt about the mean spin axis: exact while the axis is steady. */
    private static Quat SpinOrientation(Quat Orientation, Vec3 SpinBefore, Vec3 SpinAfter, double Seconds) {
        Vec3 Mean = SpinBefore.Plus(SpinAfter).Scale(0.5);
        double Rate = Mean.Length();
        if (Rate < 1e-9) return Orientation;
        return Quat.FromAxisAngle(Mean, Rate * Seconds).Times(Orientation).Normalized();
    }
}
