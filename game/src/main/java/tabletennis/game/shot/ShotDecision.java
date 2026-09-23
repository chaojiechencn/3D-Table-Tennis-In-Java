package tabletennis.game.shot;

import tabletennis.engine.math.Vec3;

/**
 * Everything the last shot was built from, for the shot overlay: the velocities in, the raw
 * reflection, the intended and final directions, the target and predicted landing, and whether
 * the search found a legal shot or fell back.
 */
public record ShotDecision(Vec3 Contact, Vec3 RacketVelocity, Vec3 IncomingVelocity, Vec3 ReflectDirection,
                           Vec3 IntendedDirection, Vec3 FinalDirection, Vec3 Target, Vec3 Landing,
                           double Speed, Vec3 Spin, int Passes, boolean Legal, TargetArea Area) {

    private static final Vec3 DownTable = new Vec3(0, 0, -1);

    /** Before any contact: nothing to draw. */
    static ShotDecision None(TargetArea Area) {
        return new ShotDecision(Vec3.Zero, Vec3.Zero, Vec3.Zero, DownTable, DownTable, DownTable,
                                Vec3.Zero, Vec3.Zero, 0, Vec3.Zero, 0, true, Area);
    }

    /** A unit direction, or down-table when the vector has none. */
    static Vec3 DirectionOf(Vec3 Vector) {
        Vec3 Unit = Vector.Normalized();
        return Unit.LengthSquared() < 1e-6 ? DownTable : Unit;
    }
}
