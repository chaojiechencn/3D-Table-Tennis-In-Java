package play;

import physics.BallState;
import physics.Vec3;
import physics.World;

import java.util.List;

import static physics.Constants.DT;
import static physics.Constants.TABLE_LENGTH;
import static physics.Constants.TABLE_WIDTH;

/**
 * A stand-in HAND: it plays the game the way a competent person would, by moving the cursor.
 *
 * <h2>Why this is not the auto-follow bug coming back</h2>
 *
 * The rule this project enforces is that the ball never moves the PLAYER'S PADDLE -- {@link
 * Stroke#advance} takes no {@link BallState}, so the rule is carried by the signature rather
 * than by discipline. Nothing here weakens that. This class produces a <b>cursor point</b>, the
 * same thing {@code MouseAim} produces from a real mouse, and hands it to the same {@link
 * Stroke} through the same {@link PlayerReach} envelope at the same {@code TRACK_SPEED}. It is
 * a simulated pair of eyes and a simulated hand, not a shortcut into the blade.
 *
 * The distinction that makes it legitimate: a demo is <b>opt-in and visible</b>. It replaces the
 * human, it does not assist one. It must never be wired into the path that runs while a person
 * is playing -- the moment it supplies the aim for a human's mouse, it is auto-aim and the whole
 * Sep-4 argument applies again.
 *
 * <h2>What it knows that a beginner does not</h2>
 *
 * Two things, and they are the entire difference between a rally and a whiff:
 *
 * <ol>
 *   <li><b>Where the ball is GOING, not where it is.</b> It asks {@link World#predict} for the
 *       bounce-aware path and finds where the ball will cross the bat's plane. A beginner
 *       chases the ball's current position and arrives behind it every time.</li>
 *   <li><b>It meets the ball in the MIDDLE of the bat.</b> Of all the points on that path it
 *       could reach, it takes the one passing closest to the bat's own height, rather than the
 *       first one technically within reach -- which is always at the edge of the disc, where a
 *       contact is a graze. This is also what earns the contact-quality grade {@code
 *       ShotAssist} uses to decide how much of the shot the player gets.</li>
 *   <li><b>It swings ACROSS and THROUGH, rather than poking.</b> It waits {@link #SETBACK}
 *       behind and a little to one side, then comes across to the ball, so the bat is still
 *       travelling when they meet. A bat that arrives early and parks dinks: measured, that is
 *       a 3.0-4.75 m/s return against this one's 8.7.</li>
 * </ol>
 *
 * Measured end to end against {@link Follower}: 71-79 exchanges a point on every rally feed,
 * every return landing in, placed 0.79-1.01 m deep and up to 0.51 m wide. The same driver with
 * a hand that merely points at the ball manages 1.9 exchanges at 4.8 m/s -- which is the whole
 * argument for predicting rather than chasing.
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
     * {@link Stroke} walks the blade toward the cursor at TRACK_SPEED and STOPS when it
     * arrives, so a cursor placed ON a ball the bat is already sitting at produces a bat with
     * no velocity at contact -- and ShotAssist reads forward drive off exactly that velocity.
     * The bat has to still be TRAVELLING when the ball gets there.
     *
     * The obvious way to arrange that -- aim past the ball -- is a trap, and an expensive one:
     * the bat then arrives early, keeps going, ends up BETWEEN the ball and the net and hits
     * the ball backwards. Measured, that put three of twelve returns behind the player, landing
     * at z = +2.6 to +3.0.
     *
     * So the swing aims exactly AT the meeting point and buys its speed from DISTANCE instead:
     * wait {@link #SETBACK} behind, then start late enough that the bat is still closing when
     * the ball arrives. The bat physically cannot get in front of a point it is aiming at.
     */
    public static final double SWING_THROUGH = 0.0;

    /** How far behind the meeting point to wait while setting up, metres. Waiting slightly
     *  behind leaves room to come forward THROUGH the ball rather than reaching back for it. */
    public static final double SETBACK = 0.20;

    /**
     * When to start the stroke, in seconds before contact -- DERIVED, not chosen.
     *
     * It is how long the blade needs to cover the stroke at the speed it actually moves:
     * SETBACK metres at {@link Stroke#TRACK_SPEED}, times a margin under 1 so the bat is still
     * short of the ball -- and therefore still moving -- at the moment of contact. Starting
     * earlier just means arriving early and parking, which is the dink this class exists to
     * avoid.
     *
     * Deriving it means it stays correct if TRACK_SPEED or SETBACK is ever retuned.
     */
    public static double swingLead() {
        return (SETBACK / Stroke.TRACK_SPEED) * 0.85;
    }

    /** How far to the side of the meeting point to set up, metres, so the bat has somewhere to
     *  swing ACROSS from. This is the entire aiming mechanism -- see the comment at its use. */
    public static final double PREP_ACROSS = 0.18;

    /**
     * How often to re-predict, seconds.
     *
     * {@link World#predict} flies a whole private simulation -- at LOOKAHEAD 2 s that is ~960
     * RK4 steps per call. Calling it every physics step (480 a second) would cost more than the
     * game itself; this file's neighbour {@code ShotAssist} already has a documented history of
     * exactly that mistake with {@code Aim.atTarget}. A person re-reads the ball a few times a
     * second, not five hundred, so re-predicting at 20 Hz is both cheaper and more honest.
     */
    public static final double REPREDICT = 0.05;

    /** Inside this many seconds of contact the meeting point is fixed and the stroke plays out.
     *  See the comment at its use: re-deriving a meeting point that is already imminent makes
     *  the bat chase a receding target instead of hitting the ball. */
    public static final double COMMIT = 0.30;

    /** How long past the planned contact to keep holding the stroke before looking again,
     *  seconds. Long enough that the swing finishes, short enough that a missed ball does not
     *  freeze the bat for the rest of the rally. */
    public static final double STALE = 0.12;

    private Vec3 lastAim = PlayerReach.NEUTRAL;
    private Meeting cached;
    private double sincePredict = Double.MAX_VALUE;

    /**
     * The cursor position this hand wants, on the bat's hitting plane.
     *
     * @param ball   where the ball is now -- read like a player's eyes, never written back
     * @param mayHit whether the ball has already bounced on our half, so it is legal to play.
     *               The caller knows this: it is the same one-bounce state that decides whether
     *               to hand {@code World} a racket at all. It is passed in rather than inferred
     *               because inferring it does not work -- the bounce can only be SEEN as the
     *               ball starting to rise, and by the time the ball is past its apex there is
     *               no rise left to find. That bug made the demo abandon a ball it was 0.34 m
     *               from and walk back to its ready position, on the game's own default feed.
     * @param dt     time since the last call, for the re-prediction throttle
     * @return a point to hand to {@link Stroke#aimAt}, already inside the legal envelope
     */
    public Vec3 cursorFor(BallState ball, boolean mayHit, double dt) {
        sincePredict += dt;
        if (cached != null) cached = new Meeting(cached.point(), cached.time() - dt);

        // COMMIT: once the ball is nearly here, stop re-deriving where to meet it.
        //
        // Re-predicting every 50 ms right up to contact does not refine the answer, it moves
        // it: the ball travels on, so each new prediction names a later, further-back meeting
        // point, and the bat spends the whole rally walking backwards after a target that
        // keeps retreating. Measured, the bat tracked z = 1.80 -> 1.94 -> 2.08 while the ball
        // ran away from it. A player picks a spot and commits; so does this.
        // ...and RELEASE the commitment once the moment has passed, or the bat stays frozen on
        // a meeting point that already happened and never plays another ball. Measured: the
        // demo returned the first ball of every rally at 8 m/s and then stood still.
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

        /*
         * Which way to put the ball -- and, crucially, HOW.
         *
         * The aim does NOT come from standing somewhere else. Offsetting the cursor sideways to
         * "aim" simply moves the bat off the ball: measured, it parked the bat at x = +0.42
         * against a ball at x = +0.87 and missed it entirely. ShotAssist reads the aim off the
         * bat's lateral VELOCITY -- swipe right, ball goes right -- so the aim has to come from
         * the direction the bat is travelling as it arrives.
         *
         * So the stroke sets up to one SIDE of the meeting point and swings across to it. The
         * bat is moving toward the target's side at contact, and it meets the ball dead centre,
         * which is also what earns the contact-quality grade. Away from where the ball came
         * from, since the follower tracks the ball and the far side is its longer trip.
         */
        double side = m.point.x() >= 0 ? -1 : 1;

        Vec3 aim;
        if (m.time > swingLead()) {
            // Set up: behind the ball and a little to the side it is coming FROM, ready to
            // come across and through it.
            aim = new Vec3(m.point.x() - side * PREP_ACROSS,
                           PlayerReach.HIT_Y, m.point.z() + SETBACK);
        } else {
            // Swing: straight at the meeting point. The bat arrives travelling forward and
            // across, which is the whole shot -- pace from the depth, aim from the sideways.
            aim = new Vec3(m.point.x(), PlayerReach.HIT_Y, m.point.z());
        }

        lastAim = PlayerReach.clamp(aim);
        return lastAim;
    }

    /** Where and when the ball will cross the bat's plane on our side. */
    private record Meeting(Vec3 point, double time) {}

    /**
     * Find the meeting point from the predicted path.
     *
     * The path comes from {@link World#predict}, which flies a paddle-free copy -- so it
     * includes the bounce off our own half, which is the whole reason a prediction is needed.
     * A ball still on the opponent's side has no meeting point yet.
     */
    private Meeting meeting(BallState ball, boolean mayHit) {
        // Already past us, or going away: nothing to meet.
        if (ball.vel().z() <= 0 && ball.pos().z() > 0) return null;

        List<Vec3> path = World.predict(ball, LOOKAHEAD, STRIDE);

        /*
         * The meeting point must be AFTER the ball has bounced on our half.
         *
         * This is the ITTF one-bounce rule, and getting it wrong is not a subtle error: the
         * game hands World a null racket until the bounce has happened, so a bat positioned on
         * the ball's pre-bounce descent has the ball pass straight through it. Measured, that
         * failure looked exactly like the demo never swinging -- 0.00 m/s of player shot across
         * every feed -- because it was standing in a legal-looking place at an illegal time.
         *
         * The bounce is found as the point where the ball stops falling and starts rising
         * again, rather than by a height threshold, so it works for a hard flat drive and a
         * dying lob alike. A ball already rising when we are called has bounced already.
         */
        boolean bounced = mayHit;
        double prevY = ball.pos().y();

        /*
         * Pick the point where the ball passes closest to the MIDDLE of the bat -- not the
         * first point that is technically reachable.
         *
         * Taking the first one looks equivalent and is not, because the first point inside the
         * band is always at the band's EDGE, which is the one place a contact is a graze or a
         * miss. VERTICAL_CAPTURE is BLADE_R + BALL_R: the limit at which the rim of the ball
         * touches the rim of the disc. Measured, the demo put the bat 12 mm from the ball in
         * the horizontal plane -- a perfect interception -- and still whiffed every time,
         * because the ball was 9 cm above a blade of radius 7.5 cm and sailed over the top.
         *
         * Meeting it in the meat of the bat is also what earns the contact-quality grade that
         * ShotAssist uses to decide how much of the shot the player gets. So this is the same
         * instinct a coach would give: take the ball at bat height.
         */
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
