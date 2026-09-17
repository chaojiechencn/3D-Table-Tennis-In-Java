package play;

import physics.BallState;
import physics.Vec3;
import physics.World;

import java.util.List;

import static physics.Constants.DT;
import static physics.Constants.TABLE_LENGTH;
import static physics.Constants.TABLE_WIDTH;

/**
 * A stand-in HAND: plays the game the way a competent person would, by moving the cursor.
 *
 * Not the auto-follow bug coming back: this produces a cursor point, the same thing
 * {@code MouseAim} produces from a real mouse, and hands it to {@link Stroke} through the same
 * {@link PlayerReach} envelope at the same {@code TRACK_SPEED} -- a simulated pair of eyes and a
 * simulated hand, not a shortcut into the blade. It must never be wired into the path that runs
 * while a person is playing.
 *
 * What it knows that a beginner does not: it predicts where the ball is GOING ({@link
 * World#predict}) rather than chasing where it is, it meets the ball at the MIDDLE of the bat
 * rather than the first technically-reachable point (always at the rim), and it swings ACROSS
 * and THROUGH rather than poking, so the bat is still travelling on contact. See docs/DESIGN.md
 * for the measured numbers behind each of those.
 */
public final class DemoPlayer {

    /** How far ahead to look for the meeting point, seconds. Beyond this the ball is not our
     *  problem yet, and a longer prediction is wasted work every frame. */
    public static final double LOOKAHEAD = 2.0;

    /** Sample every step: the meeting point is a crossing and a coarse stride steps over it. */
    private static final int STRIDE = 1;

    /**
     * How far past the meeting point to aim while striking, metres.
     *
     * Zero, deliberately: {@link Stroke} stops the blade when it arrives at the cursor, so
     * aiming exactly ON the ball gives a bat with no velocity at contact, and aiming PAST it
     * (the obvious fix) lets the bat overshoot in front of the ball and hit it backwards. The
     * swing buys its closing speed from {@link #SETBACK} instead -- see docs/DESIGN.md.
     */
    public static final double SWING_THROUGH = 0.0;

    /** How far behind the meeting point to wait while setting up, metres -- room to come
     *  forward THROUGH the ball rather than reaching back for it. */
    public static final double SETBACK = 0.20;

    /**
     * When to start the stroke, seconds before contact -- DERIVED, not chosen: how long the
     * blade needs to cover {@link #SETBACK} at {@link Stroke#TRACK_SPEED}, times a margin under
     * 1 so it is still closing (not parked) at contact. Stays correct if either input is
     * retuned.
     */
    public static double swingLead() {
        return (SETBACK / Stroke.TRACK_SPEED) * 0.85;
    }

    /** How far to the side of the meeting point to set up, metres, so the bat has somewhere to
     *  swing ACROSS from -- the entire aiming mechanism, since ShotAssist reads aim off the
     *  bat's lateral velocity rather than where it is standing. */
    public static final double PREP_ACROSS = 0.18;

    /**
     * How often to re-predict, seconds. {@link World#predict} flies a whole private simulation
     * (~960 RK4 steps at LOOKAHEAD); calling it every physics step would cost more than the game
     * itself. TUNED: 20 Hz -- a person re-reads the ball a few times a second, not five hundred.
     */
    public static final double REPREDICT = 0.05;

    /** Inside this many seconds of contact the meeting point is fixed and the stroke plays out,
     *  or re-predicting into an approaching ball would keep naming a later, further-back point
     *  and the bat would spend the rally walking backwards after a receding target. */
    public static final double COMMIT = 0.30;

    /** How long past the planned contact to keep holding the stroke before looking again,
     *  seconds -- long enough to finish the swing, short enough that a miss does not freeze the
     *  bat for the rest of the rally. */
    public static final double STALE = 0.12;

    private Vec3 lastAim = PlayerReach.NEUTRAL;
    private Meeting cached;
    private double sincePredict = Double.MAX_VALUE;

    /**
     * The cursor position this hand wants, on the bat's hitting plane.
     *
     * @param ball   where the ball is now -- read like a player's eyes, never written back
     * @param mayHit whether the ball has already bounced on our half. Passed in rather than
     *               inferred, because the bounce can only be SEEN as the ball starting to rise,
     *               and by the time it is past its apex there is no rise left to find.
     * @param dt     time since the last call, for the re-prediction throttle
     * @return a point to hand to {@link Stroke#aimAt}, already inside the legal envelope
     */
    public Vec3 cursorFor(BallState ball, boolean mayHit, double dt) {
        sincePredict += dt;
        if (cached != null) cached = new Meeting(cached.point(), cached.time() - dt);

        // Commit once the ball is nearly here (see COMMIT), and release the commitment once the
        // moment has passed (see STALE), or the bat stays frozen on a meeting point that already
        // happened and never plays another ball.
        boolean committed = cached != null && cached.time() < COMMIT && cached.time() > -STALE;
        if (!committed && (sincePredict >= REPREDICT || cached == null
                           || cached.time() < -STALE)) {
            cached = meeting(ball, mayHit);
            sincePredict = 0;
        }
        Meeting m = cached;

        // Nothing coming: stand ready rather than drifting wherever the last shot left us.
        if (m == null) {
            lastAim = PlayerReach.clamp(PlayerReach.NEUTRAL);
            return lastAim;
        }

        // The aim comes from the direction the bat is TRAVELLING as it arrives, not from where
        // it is standing (ShotAssist reads aim off lateral velocity) -- so the stroke sets up to
        // one side of the meeting point and swings across to it, away from the side the ball
        // came from.
        double side = m.point.x() >= 0 ? -1 : 1;

        Vec3 aim;
        if (m.time > swingLead()) {
            // Set up: behind the ball and to the side it is coming FROM, ready to swing across
            // and through it.
            aim = new Vec3(m.point.x() - side * PREP_ACROSS,
                           PlayerReach.HIT_Y, m.point.z() + SETBACK);
        } else {
            // Swing: straight at the meeting point, arriving forward and across.
            aim = new Vec3(m.point.x(), PlayerReach.HIT_Y, m.point.z());
        }

        lastAim = PlayerReach.clamp(aim);
        return lastAim;
    }

    /** Where and when the ball will cross the bat's plane on our side. */
    private record Meeting(Vec3 point, double time) {}

    /**
     * Find the meeting point from the predicted path. The path comes from {@link
     * World#predict}, which flies a paddle-free copy, so it includes the bounce off our own
     * half -- the whole reason a prediction is needed. A ball still on the opponent's side has
     * no meeting point yet.
     */
    private Meeting meeting(BallState ball, boolean mayHit) {
        // Already past us, or going away: nothing to meet.
        if (ball.vel().z() <= 0 && ball.pos().z() > 0) return null;

        List<Vec3> path = World.predict(ball, LOOKAHEAD, STRIDE);

        // The meeting point must be AFTER the ball has bounced on our half (the ITTF one-bounce
        // rule) -- found as the point where the ball stops falling and starts rising again,
        // rather than by a height threshold, so it works for a flat drive and a dying lob alike.
        boolean bounced = mayHit;
        double prevY = ball.pos().y();

        // Pick the point where the ball passes closest to the MIDDLE of the bat, not the first
        // point that is technically reachable -- which is always at the band's edge, where a
        // contact is a graze or a miss. VERTICAL_CAPTURE is BLADE_R + BALL_R, the limit at which
        // the ball's rim touches the disc's rim.
        Meeting best = null;
        double bestErr = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            Vec3 p = path.get(i);
            if (p.z() > 0 && p.y() > prevY) bounced = true;
            prevY = p.y();

            if (!bounced) continue;
            if (p.z() <= 0) continue;                       // still the opponent's side
            if (p.z() < PlayerReach.Z_NEAR || p.z() > PlayerReach.Z_FAR) continue;

            double err = Math.abs(p.y() - PlayerReach.HIT_Y);
            if (err > PlayerReach.VERTICAL_CAPTURE) continue;

            if (err < bestErr) { bestErr = err; best = new Meeting(p, i * STRIDE * DT); }

            // Once the ball is dropping away below the bat there is nothing better coming on
            // this pass, so stop rather than picking up its next bounce.
            if (best != null && p.y() < PlayerReach.HIT_Y - PlayerReach.VERTICAL_CAPTURE) break;
        }
        return best;
    }

    /** Where the hand is pointing, for the overlay. */
    public Vec3 lastAim() { return lastAim; }

    /** The far edge of the table, for callers that want to draw the intended target. */
    public static double tableHalfLength() { return TABLE_LENGTH / 2; }
}
