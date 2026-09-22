package tabletennis.game;

import tabletennis.engine.BallState;
import tabletennis.engine.Vec3;
import tabletennis.engine.World;

import java.util.List;

import static tabletennis.engine.Constants.DT;

/**
 * A stand-in hand for demo mode. It produces a CURSOR point, as the mouse does, through the same
 * envelope and tracking speed; it must never be wired in while a person is playing. It predicts
 * the meeting point, meets the ball mid-blade, and swings across and through it.
 */
public final class DemoPlayer {

    public static final double LOOKAHEAD = 2.0;

    /** Every step: the meeting point is a crossing that a coarser stride steps over. */
    private static final int STRIDE = 1;

    /** Room to come forward THROUGH the ball; aiming past it would overshoot and hit it backwards. */
    public static final double SETBACK = 0.20;

    /** Sideways setup offset: ShotAssist reads aim from the bat's lateral velocity. */
    public static final double PREP_ACROSS = 0.18;

    /** TUNED: 20 Hz re-prediction; each flies a whole private simulation. */
    public static final double REPREDICT = 0.05;

    /** Inside this, the meeting point is fixed, or re-prediction walks the bat backwards. */
    public static final double COMMIT = 0.30;

    /** Hold the stroke this long past the planned contact, then look again. */
    public static final double STALE = 0.12;

    /** Derived: time to cover SETBACK at TRACK_SPEED, with margin so the bat is still closing. */
    public static double swingLead() {
        return (SETBACK / Stroke.TRACK_SPEED) * 0.85;
    }

    private record Meeting(Vec3 point, double time) {}

    private Meeting cached;
    private double sincePredict = Double.MAX_VALUE;

    /**
     * The cursor this hand wants, already inside the envelope. {@code mayHit} says whether the
     * ball has bounced on our half, which cannot be seen once it is past its apex.
     */
    public Vec3 cursorFor(BallState ball, boolean mayHit, double dt) {
        sincePredict += dt;
        if (cached != null) cached = new Meeting(cached.point(), cached.time() - dt);

        boolean committed = cached != null && cached.time() < COMMIT && cached.time() > -STALE;
        boolean due = sincePredict >= REPREDICT || cached == null || cached.time() < -STALE;
        if (!committed && due) {
            cached = meeting(ball, mayHit);
            sincePredict = 0;
        }
        if (cached == null) return PlayerReach.clamp(PlayerReach.NEUTRAL);

        Vec3 p = cached.point();
        double side = p.x() >= 0 ? -1 : 1;
        Vec3 aim = cached.time() > swingLead()
                 ? new Vec3(p.x() - side * PREP_ACROSS, PlayerReach.HIT_Y, p.z() + SETBACK)
                 : new Vec3(p.x(), PlayerReach.HIT_Y, p.z());
        return PlayerReach.clamp(aim);
    }

    /**
     * The point after our-half bounce (seen as the ball starting to rise) where the ball passes
     * closest to the middle of the bat; the first reachable point is always a rim graze.
     */
    private Meeting meeting(BallState ball, boolean mayHit) {
        boolean goingAway = ball.vel().z() <= 0 && ball.pos().z() > 0;
        if (goingAway) return null;

        List<Vec3> path = World.predict(ball, LOOKAHEAD, STRIDE);
        boolean bounced = mayHit;
        double prevY = ball.pos().y();

        Meeting best = null;
        double bestErr = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            Vec3 p = path.get(i);
            if (p.z() > 0 && p.y() > prevY) bounced = true;
            prevY = p.y();

            if (!bounced || p.z() <= 0) continue;
            if (p.z() < PlayerReach.Z_NEAR || p.z() > PlayerReach.Z_FAR) continue;

            double err = Math.abs(p.y() - PlayerReach.HIT_Y);
            if (err > PlayerReach.VERTICAL_CAPTURE) continue;
            if (err < bestErr) { bestErr = err; best = new Meeting(p, i * STRIDE * DT); }
        }
        return best;
    }
}
