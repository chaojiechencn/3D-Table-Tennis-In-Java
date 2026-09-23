package tabletennis.engine.contact;

import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;
import tabletennis.engine.Environment;
import tabletennis.engine.math.Vec3;

/**
 * The ONE collision solver: a normal impulse with restitution plus a tangential impulse that
 * grips, springs back or slides, differing per surface only by its Material. Spin coupling falls
 * out of the impulse rather than being scripted. Every velocity is taken relative to the
 * surface: in absolute terms a blade swung into a ball reads as already separating and does
 * nothing, and a brushing stroke generates no spin at all.
 */
public final class ContactSolver {

    private ContactSolver() {}

    /**
     * A detected touch, before any response, so the world can resolve the EARLIEST of all
     * surfaces. TimeOfImpact is the step fraction (1.0 for an end-of-step overlap); Swept means
     * the ball would otherwise have passed through and still has step left to fly.
     */
    public record Contact(double TimeOfImpact, Vec3 Point, Vec3 Normal, boolean Swept) {}

    /** The ball after the impulse. ImpactSpeed is the approach speed along the normal. */
    public record Response(BallState State, Vec3 Point, Vec3 Normal, double ImpactSpeed, boolean Resting) {}

    /** Below this normal speed there is no bounce: under 1 mm of height, and it ends the jitter. */
    private static final double RestingSpeed = 0.15;

    /** Applied only while resting, so a settled ball stops instead of rolling forever. */
    private static final double RollingFriction = 0.02;

    /** Gap left after a contact so the next step starts clean. */
    private static final double Skin = 1e-4;

    /**
     * The contact for the motion Before -> After, or null if the ball never touched the surface.
     * StepSeconds carries a moving surface back to where it stood at the start of the motion.
     */
    public static Contact Detect(BallState Before, BallState After, Collider Surface, double StepSeconds) {
        Vec3 From = Before.Position(), To = After.Position();
        double R = BallSpec.Radius;

        if (Surface.ClosestPoint(To).Minus(To).LengthSquared() < R * R) {
            return new Contact(1.0, To, NormalAt(Surface, To), false);
        }

        // Swept in the surface's frame: carry the start into the collider's end-of-step pose.
        // For every static surface the carry is zero and this is a plain sweep.
        Vec3 Carried = From.PlusScaled(Surface.VelocityAt(To), StepSeconds);
        double Along = Surface.Sweep(Carried, To);
        if (Along < 0) return null;

        Vec3 At = Vec3.Lerp(Carried, To, Along);
        return new Contact(Along, At, NormalAt(Surface, At), true);
    }

    /** The impulse for a detected contact. StepSeconds is how long a resting ball rolls for. */
    public static Response Respond(BallState After, Collider Surface, Contact Touch, Material Finish,
                                   double StepSeconds) {
        BallState AtContact = After.WithPosition(Touch.Point());
        return ApplyImpulse(AtContact, Surface, Touch.Normal(), Finish, StepSeconds);
    }

    private static Vec3 NormalAt(Collider Surface, Vec3 Point) {
        Vec3 Offset = Point.Minus(Surface.ClosestPoint(Point));
        return Offset.LengthSquared() < 1e-18 ? Surface.EscapeNormal(Point) : Offset.Normalized();
    }

    private static Response ApplyImpulse(BallState Ball, Collider Surface, Vec3 Normal, Material Finish,
                                         double StepSeconds) {
        Vec3 V = Ball.Velocity(), W = Ball.Spin();
        Vec3 ContactPoint = Ball.Position().PlusScaled(Normal, -BallSpec.Radius);
        Vec3 SurfaceVelocity = Surface.VelocityAt(ContactPoint);

        double NormalSpeed = V.Minus(SurfaceVelocity).Dot(Normal);
        double ImpactSpeed = Math.abs(NormalSpeed);
        if (NormalSpeed > 0) {
            // Already leaving: reflecting would spit the ball out of the surface.
            return new Response(PushOut(Ball, Surface, Normal), ContactPoint, Normal, 0, false);
        }

        boolean Resting = ImpactSpeed < RestingSpeed;
        double E = Resting ? 0.0 : Finish.RestitutionAt(ImpactSpeed);
        double NormalImpulse = -(1.0 + E) * NormalSpeed * BallSpec.Mass;

        Vec3 Arm = Normal.Scale(-BallSpec.Radius);
        Vec3 Slip = V.Plus(W.Cross(Arm)).Minus(SurfaceVelocity).TangentTo(Normal);
        Vec3 TangentialImpulse = TangentialImpulse(Slip, NormalImpulse, Finish);

        Vec3 NewVelocity = V.PlusScaled(Normal.Scale(NormalImpulse).Plus(TangentialImpulse), 1.0 / BallSpec.Mass);
        Vec3 NewSpin = W.PlusScaled(Arm.Cross(TangentialImpulse), 1.0 / BallSpec.Inertia);   // the normal impulse has no torque

        boolean RestingOnStaticSurface = Resting && SurfaceVelocity.LengthSquared() < 1e-18 && Normal.Y() > 0.5;
        if (RestingOnStaticSurface) NewVelocity = WithRollingResistance(NewVelocity, Normal, StepSeconds);
        NewSpin = DampDrillSpin(NewSpin, Normal, Finish);

        NewVelocity = NewVelocity.Scale(Finish.VelocityDamping());
        NewSpin = NewSpin.Scale(Finish.SpinDamping());

        BallState Out = PushOut(Ball.WithVelocity(NewVelocity).WithSpin(NewSpin), Surface, Normal);
        return new Response(Out, ContactPoint, Normal, ImpactSpeed, Resting);
    }

    /**
     * Killing the slip of a HOLLOW shell (I = (2/3)mr²) takes J = -(2/5) m slip; the (1 + e_t)
     * factor springs the patch back, which is what reverses spin off rubber. Past the friction
     * cone the patch slides instead.
     */
    private static Vec3 TangentialImpulse(Vec3 Slip, double NormalImpulse, Material Finish) {
        double SpringBack = Finish.TangentialRestitutionAt(Slip.Length());
        Vec3 Grip = Slip.Scale(-(2.0 / 5.0) * (1.0 + SpringBack) * BallSpec.Mass);

        double MaxFriction = Finish.Friction() * NormalImpulse;
        boolean InsideCone = Grip.Length() <= MaxFriction || Slip.LengthSquared() < 1e-18;
        return InsideCone ? Grip : Slip.Normalized().Scale(-MaxFriction);
    }

    /** Only on a static, upward-facing surface: on a swinging paddle it would steal pace. */
    private static Vec3 WithRollingResistance(Vec3 Velocity, Vec3 Normal, double StepSeconds) {
        Vec3 Tangential = Velocity.TangentTo(Normal);
        double Drop = RollingFriction * Environment.Gravity * StepSeconds;
        return Tangential.Length() > Drop
             ? Velocity.Minus(Tangential.Normalized().Scale(Drop))
             : Velocity.Minus(Tangential);
    }

    /** Damps only spin about the normal; applied to all of it, it would erase a stroke's topspin. */
    private static Vec3 DampDrillSpin(Vec3 Spin, Vec3 Normal, Material Finish) {
        if (Finish.DrillSpinDamping() == 1.0) return Spin;
        Vec3 Drill = Spin.ProjectOnto(Normal);
        return Spin.Minus(Drill).PlusScaled(Drill, Finish.DrillSpinDamping());
    }

    private static BallState PushOut(BallState Ball, Collider Surface, Vec3 Normal) {
        Vec3 Nearest = Surface.ClosestPoint(Ball.Position());
        Vec3 Offset = Ball.Position().Minus(Nearest);
        double Distance = Offset.Length();

        if (Distance < 1e-9) return Ball.WithPosition(Nearest.PlusScaled(Normal, BallSpec.Radius + Skin));
        if (Distance >= BallSpec.Radius) return Ball;
        return Ball.WithPosition(Nearest.PlusScaled(Offset.Scale(1.0 / Distance), BallSpec.Radius + Skin));
    }
}
