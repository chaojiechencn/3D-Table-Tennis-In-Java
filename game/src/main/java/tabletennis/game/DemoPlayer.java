package tabletennis.game;

import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.FlightPredictor;

import java.util.List;


/**
 * A stand-in hand for demo mode. It produces a CURSOR point, as the mouse does, through the same
 * envelope and tracking speed; it must never be wired in while a person is playing. It predicts
 * the meeting point, meets the ball mid-blade, and swings across and through it.
 */
public final class DemoPlayer {

    public static final double Lookahead = 2.0;

    /** Every step: the meeting point is a crossing that a coarser stride steps over. */
    private static final int Stride = 1;

    /** Room to come forward THROUGH the ball; aiming past it would overshoot and hit it backwards. */
    public static final double Setback = 0.20;

    /** Sideways setup offset: ShotAssist reads aim from the bat's lateral velocity. */
    public static final double PrepAcross = 0.18;

    /** TUNED: 20 Hz re-prediction; each flies a whole private simulation. */
    public static final double Repredict = 0.05;

    /** Inside this, the meeting point is fixed, or re-prediction walks the bat backwards. */
    public static final double Commit = 0.30;

    /** Hold the stroke this long past the planned contact, then look again. */
    public static final double Stale = 0.12;

    /** Derived: time to cover SETBACK at TrackSpeed, with margin so the bat is still closing. */
    public static double SwingLead() {
        return (Setback / Stroke.TrackSpeed) * 0.85;
    }

    private record Meeting(Vec3 Point, double Time) {}

    private Meeting Cached;
    private double SincePredict = Double.MAX_VALUE;

    /**
     * The cursor this hand wants, already inside the envelope. {@code mayHit} says whether the
     * ball has bounced on our half, which cannot be seen once it is past its apex.
     */
    public Vec3 CursorFor(BallState Ball, boolean MayHit, double Dt) {
        SincePredict += Dt;
        if (Cached != null) Cached = new Meeting(Cached.Point(), Cached.Time() - Dt);

        boolean Committed = Cached != null && Cached.Time() < Commit && Cached.Time() > -Stale;
        boolean Due = SincePredict >= Repredict || Cached == null || Cached.Time() < -Stale;
        if (!Committed && Due) {
            Cached = Meeting(Ball, MayHit);
            SincePredict = 0;
        }
        if (Cached == null) return PlayerReach.Clamp(PlayerReach.Neutral);

        Vec3 P = Cached.Point();
        double Side = P.X() >= 0 ? -1 : 1;
        Vec3 Aim = Cached.Time() > SwingLead()
                 ? new Vec3(P.X() - Side * PrepAcross, PlayerReach.HitY, P.Z() + Setback)
                 : new Vec3(P.X(), PlayerReach.HitY, P.Z());
        return PlayerReach.Clamp(Aim);
    }

    /**
     * The point after our-half bounce (seen as the ball starting to rise) where the ball passes
     * closest to the middle of the bat; the first reachable point is always a rim graze.
     */
    private Meeting Meeting(BallState Ball, boolean MayHit) {
        boolean GoingAway = Ball.Velocity().Z() <= 0 && Ball.Position().Z() > 0;
        if (GoingAway) return null;

        List<Vec3> Path = FlightPredictor.Path(Ball, Lookahead, Stride);
        boolean Bounced = MayHit;
        double PrevY = Ball.Position().Y();

        Meeting Best = null;
        double BestErr = Double.MAX_VALUE;
        for (int I = 0; I < Path.size(); I++) {
            Vec3 P = Path.get(I);
            if (P.Z() > 0 && P.Y() > PrevY) Bounced = true;
            PrevY = P.Y();

            if (!Bounced || P.Z() <= 0) continue;
            if (P.Z() < PlayerReach.ZNear || P.Z() > PlayerReach.ZFar) continue;

            double Err = Math.abs(P.Y() - PlayerReach.HitY);
            if (Err > PlayerReach.VerticalCapture) continue;
            if (Err < BestErr) { BestErr = Err; Best = new Meeting(P, I * Stride * Simulation.Step); }
        }
        return Best;
    }
}
