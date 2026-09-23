package tabletennis.game.rally;

import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.TableSpec;
import tabletennis.engine.math.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The rally rules (ITTF one-bounce rule), judged one physics step at a time. A racket may strike
 * only after the ball has bounced on its own half; a second bounce there, a return onto the
 * hitter's own half, a shot that goes out, or a ball that reaches the floor decides the point. A decided
 * point latches: it is awarded once however many rules fire, and both rackets are withdrawn.
 * A net cord is deliberately not a rule: a ball that clips the cord and lands legally is good.
 */
public final class Referee {

    /** A push dug off the surface touches the table on the contact's own step; that is not a bounce. */
    public static final double ContactBounceWindow = Simulation.Step * 2;

    /** What one step decided: every event, including an out call, and the point's winner if any. */
    public record Ruling(List<RallyEvent> Events, Side PointTo) {}

    private Side LastHitter;          // null while the feed is the last thing that struck the ball
    private double LastHitTime;
    private boolean PlayerMayHit;
    private boolean OpponentMayHit;
    private boolean PointOver;

    // Judged per shot: every racket hit starts the in/out question again.
    private int BouncesThisShot;
    private boolean OutCalled;

    public Referee() {
        StartRally();
    }

    public void StartRally() {
        LastHitter = null;
        LastHitTime = -1;
        PlayerMayHit = false;
        OpponentMayHit = false;
        PointOver = false;
        BouncesThisShot = 0;
        OutCalled = false;
    }

    /** Whether that side's racket may strike the ball now. */
    public boolean MayHit(Side Striker) {
        return !PointOver && (Striker == Side.Player ? PlayerMayHit : OpponentMayHit);
    }

    public boolean PointOver() { return PointOver; }

    /**
     * One step's contacts in the order they happened, the ball either side of the step, and the
     * time at its end. Rules apply in a fixed order: the hit, then the bounce, then out or floor.
     */
    public Ruling Judge(List<RallyEvent> Contacts, BallState Before, BallState After, double Time) {
        List<RallyEvent> Events = new ArrayList<>(Contacts);
        Contacts.forEach(this::TrackShot);
        if (CallsOut(Before, After)) {
            OutCalled = true;
            Events.add(RallyEvent.Of(EventType.OutOfBounds, After.Position()));
        }

        RallyEvent Hit = Latest(Contacts, EventType.RacketHit);
        if (Hit != null) RecordHit(Hit.HitBy(), Time);

        Side Winner = null;
        RallyEvent Bounce = Latest(Contacts, EventType.TableBounce);
        if (Bounce != null && Time - LastHitTime > ContactBounceWindow) Winner = JudgeBounce(Bounce.Half());

        boolean Terminal = Latest(Events, EventType.OutOfBounds) != null
                        || Latest(Contacts, EventType.FloorTouch) != null;
        if (Terminal) Winner = Award(Winner, TerminalWinner());

        return new Ruling(List.copyOf(Events), Winner);
    }

    /**
     * Once a shot has bounced legally, the ball is the receiver's to return, so reaching the floor
     * is the receiver's point lost. Before that, out or floor is the hitter's fault; a feed counts
     * as the player's shot.
     */
    private Side TerminalWinner() {
        if (PlayerMayHit) return Side.Opponent;
        if (OpponentMayHit) return Side.Player;
        return LastHitter == Side.Opponent ? Side.Player : Side.Opponent;
    }

    private void TrackShot(RallyEvent Event) {
        if (Event.Type() == EventType.RacketHit) {
            BouncesThisShot = 0;
            OutCalled = false;
        } else if (Event.Type() == EventType.TableBounce) {
            BouncesThisShot++;
        }
    }

    private void RecordHit(Side Hitter, double Time) {
        LastHitter = Hitter;
        LastHitTime = Time;
        PlayerMayHit = false;
        OpponentMayHit = false;
    }

    /** A first bounce opens the receiver's racket; a second, or the hitter's own half, decides the point. */
    private Side JudgeBounce(Side Half) {
        if (LastHitter == Half) return Award(null, Half.Other());   // it never crossed the net
        if (Half == Side.Player) {
            if (PlayerMayHit) return Award(null, Side.Opponent);
            PlayerMayHit = true;
        } else {
            if (OpponentMayHit) return Award(null, Side.Player);
            OpponentMayHit = true;
        }
        return null;
    }

    /** The latch: the first decision of a point stands, and nothing is awarded twice. */
    private Side Award(Side AlreadyDecided, Side Winner) {
        if (PointOver) return AlreadyDecided;
        PointOver = true;
        return Winner;
    }

    /**
     * Out: the shot came down through the table plane off the table before it bounced. After a
     * legal bounce, sailing off the end is the receiver's problem, not the hitter's.
     */
    private boolean CallsOut(BallState Before, BallState After) {
        if (OutCalled || BouncesThisShot > 0) return false;

        boolean CrossedDown = Before.Position().Y() > BallSpec.Radius && After.Position().Y() <= BallSpec.Radius;
        if (!CrossedDown || After.Velocity().Y() >= 0) return false;

        Vec3 At = After.Position();
        boolean OverTable = Math.abs(At.X()) <= TableSpec.HalfWidth + BallSpec.Radius
                         && Math.abs(At.Z()) <= TableSpec.HalfLength + BallSpec.Radius;
        return !OverTable;
    }

    private static RallyEvent Latest(List<RallyEvent> Events, EventType Type) {
        for (int Index = Events.size() - 1; Index >= 0; Index--) {
            if (Events.get(Index).Type() == Type) return Events.get(Index);
        }
        return null;
    }
}
