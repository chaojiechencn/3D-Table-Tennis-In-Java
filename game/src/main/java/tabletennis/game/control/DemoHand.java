package tabletennis.game.control;

import tabletennis.engine.BallState;
import tabletennis.engine.math.Vec3;
import tabletennis.game.ai.MeetingPoint;
import tabletennis.game.ai.MeetingPoint.Meeting;
import tabletennis.game.rally.Side;

/**
 * A stand-in hand for demo mode. It produces a CURSOR point, as the mouse does, through the same
 * envelope and tracking speed; it must never be wired in while a person is playing. It predicts
 * the meeting point, meets the ball mid-blade, and swings across and through it.
 */
public final class DemoHand {

    private static final double LookaheadSeconds = 2.0;

    /** Every step: the meeting point is a crossing that a coarser stride steps over. */
    private static final int Stride = 1;

    /** Room to come forward THROUGH the ball; aiming past it would overshoot and hit it backwards. */
    private static final double Setback = 0.20;

    /** Sideways setup offset: the shot's aim is read from the bat's lateral velocity. */
    private static final double PrepAcross = 0.18;

    /** TUNED: 20 Hz re-prediction; each flies a whole private simulation. */
    private static final double Repredict = 0.05;

    /** Inside this, the meeting point is fixed, or re-prediction walks the bat backwards. */
    private static final double Commit = 0.30;

    /** Hold the stroke this long past the planned contact, then look again. */
    private static final double Stale = 0.12;

    /** Derived: time to cover Setback at TrackSpeed, with margin so the bat is still closing. */
    private static final double SwingLead = (Setback / CursorFollower.TrackSpeed) * 0.85;

    private Meeting Planned;
    private double SincePredict = Double.MAX_VALUE;

    /**
     * The cursor this hand wants, already inside the envelope. MayHit says whether the ball has
     * bounced on the player's half, which cannot be seen once it is past its apex.
     */
    public Vec3 CursorFor(BallState Ball, boolean MayHit, double Seconds) {
        SincePredict += Seconds;
        if (Planned != null) Planned = new Meeting(Planned.Point(), Planned.Time() - Seconds);

        boolean Committed = Planned != null && Planned.Time() < Commit && Planned.Time() > -Stale;
        boolean Due = SincePredict >= Repredict || Planned == null || Planned.Time() < -Stale;
        if (!Committed && Due) {
            Planned = MeetingPoint.Find(Ball, MayHit, Side.Player, ReachEnvelope.Zone, LookaheadSeconds, Stride);
            SincePredict = 0;
        }
        if (Planned == null) return ReachEnvelope.Clamp(ReachEnvelope.Neutral);

        Vec3 Point = Planned.Point();
        double Across = Point.X() >= 0 ? -1 : 1;
        Vec3 Aim = Planned.Time() > SwingLead
                 ? new Vec3(Point.X() - Across * PrepAcross, ReachEnvelope.HitY, Point.Z() + Setback)
                 : new Vec3(Point.X(), ReachEnvelope.HitY, Point.Z());
        return ReachEnvelope.Clamp(Aim);
    }
}
