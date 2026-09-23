package tabletennis.engine.contact;

import tabletennis.engine.BallSpec;
import tabletennis.engine.RacketSpec;
import tabletennis.engine.math.Vec3;

import static tabletennis.engine.math.Numeric.Clamp;

/** A racket blade frozen for one step, as a disc slab, so the solver gets consistent answers. */
public record BladeCollider(Vec3 Centre, Vec3 Normal, Vec3 Velocity, Vec3 AngularVelocity) implements Collider {

    private static final double HalfThickness = RacketSpec.BladeThickness / 2;

    @Override public Vec3 ClosestPoint(Vec3 Point) {
        Vec3 Offset = Point.Minus(Centre);
        double Along = Offset.Dot(Normal);
        Vec3 InPlane = Offset.Minus(Normal.Scale(Along));

        double Radial = InPlane.Length();
        if (Radial > RacketSpec.BladeRadius) InPlane = InPlane.Scale(RacketSpec.BladeRadius / Radial);

        return Centre.Plus(InPlane).PlusScaled(Normal, Clamp(Along, -HalfThickness, HalfThickness));
    }

    /** Through the nearer face, which for a 15 mm by 150 mm disc is always the way out. */
    @Override public Vec3 EscapeNormal(Vec3 Point) {
        return Point.Minus(Centre).Dot(Normal) >= 0 ? Normal : Normal.Negate();
    }

    /** A plane crossing plus a rim check: exact for a thin blade. */
    @Override public double Sweep(Vec3 From, Vec3 To) {
        double Surface = HalfThickness + BallSpec.Radius;
        double FromHeight = From.Minus(Centre).Dot(Normal);
        double ToHeight = To.Minus(Centre).Dot(Normal);

        double Change = ToHeight - FromHeight;
        if (Math.abs(Change) < 1e-12) return -1;   // moving parallel to the face

        double Target = FromHeight >= 0 ? Surface : -Surface;
        double Along = (Target - FromHeight) / Change;
        if (Along < 0 || Along > 1) return -1;

        Vec3 At = Vec3.Lerp(From, To, Along);
        Vec3 Offset = At.Minus(Centre);
        Vec3 InPlane = Offset.Minus(Normal.Scale(Offset.Dot(Normal)));
        double Rim = RacketSpec.BladeRadius + BallSpec.Radius;
        return InPlane.LengthSquared() > Rim * Rim ? -1 : Along;
    }

    @Override public Vec3 VelocityAt(Vec3 Point) {
        return Velocity.Plus(AngularVelocity.Cross(Point.Minus(Centre)));
    }
}
