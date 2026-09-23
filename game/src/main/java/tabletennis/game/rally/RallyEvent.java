package tabletennis.game.rally;

import tabletennis.engine.math.Vec3;
import tabletennis.game.Side;

/** One rally fact: what happened, where, and for a racket hit, whose racket. */
public record RallyEvent(EventType Type, Vec3 Point, Side HitBy) {

    public static RallyEvent Hit(Side By, Vec3 Point) { return new RallyEvent(EventType.RacketHit, Point, By); }

    public static RallyEvent Of(EventType Type, Vec3 Point) { return new RallyEvent(Type, Point, null); }

    /** For a table bounce: whose half it landed on. */
    public Side Half() { return Side.HalfAt(Point.Z()); }
}
