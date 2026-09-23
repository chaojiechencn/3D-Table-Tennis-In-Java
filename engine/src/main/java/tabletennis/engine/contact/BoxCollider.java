package tabletennis.engine.contact;

import tabletennis.engine.BallSpec;
import tabletennis.engine.math.Vec3;

import static tabletennis.engine.math.Numeric.Clamp;

/** An axis-aligned, static volume: the table, the net and the floor. */
public record BoxCollider(Vec3 Min, Vec3 Max) implements Collider {

    public static BoxCollider Centered(Vec3 Centre, Vec3 Size) {
        return new BoxCollider(
                new Vec3(Centre.X() - Size.X() / 2, Centre.Y() - Size.Y() / 2, Centre.Z() - Size.Z() / 2),
                new Vec3(Centre.X() + Size.X() / 2, Centre.Y() + Size.Y() / 2, Centre.Z() + Size.Z() / 2));
    }

    @Override public Vec3 ClosestPoint(Vec3 Point) {
        return new Vec3(Clamp(Point.X(), Min.X(), Max.X()),
                        Clamp(Point.Y(), Min.Y(), Max.Y()),
                        Clamp(Point.Z(), Min.Z(), Max.Z()));
    }

    /** Normal of the nearest face. */
    @Override public Vec3 EscapeNormal(Vec3 Point) {
        double Nearest = Point.X() - Min.X();
        Vec3 Normal = new Vec3(-1, 0, 0);

        double Distance = Max.X() - Point.X();
        if (Distance < Nearest) { Nearest = Distance; Normal = new Vec3(1, 0, 0); }
        Distance = Point.Y() - Min.Y();
        if (Distance < Nearest) { Nearest = Distance; Normal = new Vec3(0, -1, 0); }
        Distance = Max.Y() - Point.Y();
        if (Distance < Nearest) { Nearest = Distance; Normal = new Vec3(0, 1, 0); }
        Distance = Point.Z() - Min.Z();
        if (Distance < Nearest) { Nearest = Distance; Normal = new Vec3(0, 0, -1); }
        Distance = Max.Z() - Point.Z();
        if (Distance < Nearest) { Normal = new Vec3(0, 0, 1); }
        return Normal;
    }

    /**
     * A ray against the box grown by the ball radius. Square corners can register a corner clip
     * up to one radius early; the rounded normal from ClosestPoint is still correct.
     */
    @Override public double Sweep(Vec3 From, Vec3 To) {
        Vec3 Travel = To.Minus(From);
        double R = BallSpec.Radius;

        double[] Origin = { From.X(), From.Y(), From.Z() };
        double[] Direction = { Travel.X(), Travel.Y(), Travel.Z() };
        double[] Low = { Min.X() - R, Min.Y() - R, Min.Z() - R };
        double[] High = { Max.X() + R, Max.Y() + R, Max.Z() + R };

        double Enter = 0.0, Exit = 1.0;
        for (int Axis = 0; Axis < 3; Axis++) {
            if (Math.abs(Direction[Axis]) < 1e-12) {
                boolean Outside = Origin[Axis] < Low[Axis] || Origin[Axis] > High[Axis];
                if (Outside) return -1;   // parallel to this slab and outside it
                continue;
            }
            double Near = (Low[Axis] - Origin[Axis]) / Direction[Axis];
            double Far = (High[Axis] - Origin[Axis]) / Direction[Axis];
            if (Near > Far) { double Swap = Near; Near = Far; Far = Swap; }
            Enter = Math.max(Enter, Near);
            Exit = Math.min(Exit, Far);
            if (Enter > Exit) return -1;
        }
        return Enter;
    }

    @Override public Vec3 VelocityAt(Vec3 Point) { return Vec3.Zero; }
}
