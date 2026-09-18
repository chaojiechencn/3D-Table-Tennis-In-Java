package pong.systems.opponent;

import static pong.config.Physical.*;
import pong.core.math.Vec3;
import pong.game_objects.ball.Ball;
import pong.game_objects.racket.Racket;

/**
 * An opponent that simply follows the ball, and is therefore unbeatable in a rally: it moves to
 * the ball's CURRENT position, as fast as it likes, and swings when the ball arrives -- no
 * prediction, no lookahead, no reading of the shot. Difficulty cannot come from slowing it down
 * (a follower just becomes erratic); it has to come from prediction quality, which is why a
 * predicting opponent using {@link pong.World#predict} is the intended replacement, not a
 * tuned version of this class. See docs/DESIGN.md.
 */
public final class Follower implements Opponent {

    /** Blade speed limit, m/s -- deliberately far beyond a human (an advanced player's own
     *  swing is ~17.8 m/s, and that is the swing, not repositioning across the table). This is
     *  a wall, not a player. */
    private static final double MAX_SPEED = 25.0;

    /** The plane the opponent's blade lives on: just beyond its own end of the table. */
    public static final double PLANE_Z = -(TABLE_LENGTH / 2 + 0.12);

    /**
     * How high above the table the blade will go, metres. Without a ceiling the blade (which
     * may move at MAX_SPEED) stays in contact with a rising ball all the way up, striking it
     * over and over without ever adding detectable energy. TUNED: 0.55 m, about as high as a
     * player takes a bat without leaving the ground.
     */
    private static final double MAX_REACH_Y = 0.55;

    /** Where the blade waits before a rally and after it has played its shot. */
    private static final Vec3 READY = new Vec3(0, 0.20, PLANE_Z);

    /**
     * How far in over the table the blade will step to meet a dying ball, metres. Without this
     * the blade waits on PLANE_Z for every ball and a short return falls below it before
     * arriving. Guarded to the last {@code REACH_FWD} and to a genuinely LOW ball only --
     * stepping in for every ball parks the blade mid-table and out of position for everything
     * else (measured: RallyTest dropped from 10/10 shots reached to 3/10 without the guard).
     */
    private static final double REACH_FWD = 0.55;

    /** Ball height, metres, below which it is worth stepping in rather than waiting. */
    private static final double STEP_IN_HEIGHT = 0.22;

    /** How close the ball has to get before it commits to a stroke. */
    private static final double SWING_RANGE = 0.30;

    /** How long the stroke lasts. */
    private static final double SWING_TIME = 0.10;

    // ------------------------------------------------------------------ the tuned stroke
    //
    // These three were originally tuned against the wrong question ("does the return clear the
    // net", which every setting managed by going nearly straight up) instead of the right one
    // ("where does it land"). A sweep over all three found a trade-off, not an optimum: no fixed
    // stroke returns every preset legally AND lands most of them, because the presets span a
    // 3.5-18.4 m/s range and one answer cannot suit a serve and a smash at once. The net
    // constraint wins here, because staying a wall is this class's whole job. See docs/DESIGN.md
    // for the full sweep and the landing table RallyTest prints every run.

    /** How far the face is CLOSED over the incoming ball. Closed, not open: the swing has an
     *  upward component, so a closed face brushes up the back of the ball into topspin, which
     *  is what drags the return back down onto the table. */
    private static final double FACE_CLOSED = 0.20;

    /** Peak blade speed of the return, m/s. Has to stay high: the incoming ball already
     *  supplies most of the pace (rubber's e ~ 0.9), but a slow blade cannot get a cross-court
     *  loop back over the net. */
    private static final double SWING_SPEED = 8.5;

    /** Upward component of the stroke -- the brush that makes the topspin that brings the
     *  return down, holding the apex to ~1.1 m instead of several metres. */
    private static final double SWING_LIFT = 5.0;

    private final double faceClosed;
    private final double swingSpeed;
    private final double swingLift;

    private double swinging = -1;      // seconds into a stroke, negative when not swinging

    /**
     * Where the ball was when the current stroke was committed to. Captured once and not
     * updated, or the blade stays glued to the ball it just hit and strikes it again on the
     * next step -- and the next -- steepening the return until it goes straight up. A player
     * follows through along the stroke, not after the ball.
     */
    private Vec3 swingAim = READY;

    public Follower() {
        this(FACE_CLOSED, SWING_SPEED, SWING_LIFT);
    }

    /** A follower with a stroke of its own, so the three constants above can be swept rather
     *  than argued about -- also the seam a future skill dial will want. */
    Follower(double faceClosed, double swingSpeed, double swingLift) {
        this.faceClosed = faceClosed;
        this.swingSpeed = swingSpeed;
        this.swingLift = swingLift;
    }

    @Override public String name() { return "follower (unbeatable)"; }

    @Override
    public void advance(Ball ball, Racket blade, double dt) {
        Vec3 b = ball.pos();

        boolean incoming = b.z() < 0 && ball.vel().z() < 0;
        double reach = b.z() - PLANE_Z;

        if (incoming && reach < SWING_RANGE && swinging < 0) {
            swingAim = reachable(b);        // commit, and stop following the ball
            swinging = 0;
        }

        Vec3 want;
        if (swinging >= 0) {
            swinging += dt;
            double t = Math.min(1, swinging / SWING_TIME);
            // Same half-sine profile the player's stroke uses: a blade that starts and stops
            // instantly reads as an impossible velocity when Racket differences its pose.
            double s = (1 - Math.cos(Math.PI * t)) / 2;
            double travel = SWING_TIME * s * 2 / Math.PI;
            want = new Vec3(swingAim.x(),
                            swingAim.y() + swingLift * travel,
                            swingAim.z() + swingSpeed * travel);
            if (t >= 1) swinging = -1;
        } else if (incoming) {
            // Move to meet it -- where the ball IS, not where it is going. One concession: a
            // ball already low gets met further out, before it falls any lower.
            want = reachable(b);
        } else {
            // The ball is heading back to the other end. Reset rather than follow it, or the
            // blade ends up chasing a departing ball into the roof.
            want = READY;
        }

        // Speed limit, so the blade sweeps rather than teleporting. Even a wall has to move.
        Vec3 step = want.minus(blade.pos());
        double maxStep = MAX_SPEED * dt;
        if (step.length() > maxStep) want = blade.pos().plusScaled(step.normalized(), maxStep);

        // Face pointing back up the table at the ball, closed over it so the upward part of the
        // stroke brushes topspin on.
        blade.moveTo(want, new Vec3(0, -faceClosed, 1).normalized(), dt);
    }

    /**
     * The ball's position, brought back to somewhere the blade can actually be. Clamped at both
     * ends of height: the floor so it cannot dive through the table chasing a dead ball, the
     * ceiling so it cannot chase a lob into the roof.
     */
    private static Vec3 reachable(Vec3 b) {
        double toPlane = b.z() - PLANE_Z;
        double z = (b.y() < STEP_IN_HEIGHT && toPlane > 0 && toPlane < REACH_FWD)
                 ? b.z()
                 : PLANE_Z;
        return new Vec3(clamp(b.x(), -TABLE_WIDTH, TABLE_WIDTH),
                        clamp(b.y(), 0.04, MAX_REACH_Y),
                        z);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
