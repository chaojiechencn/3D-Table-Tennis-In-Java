package play;

import physics.BallState;
import physics.Paddle;
import physics.Vec3;

import static physics.Constants.*;

/**
 * An opponent that simply follows the ball, and is therefore unbeatable.
 *
 * It looks at where the ball is RIGHT NOW, moves its blade to those coordinates as fast as it
 * likes, and swings when the ball arrives. There is no prediction, no lookahead, and no
 * attempt to read the shot -- it does not need any of that, because it is allowed to move
 * faster than a person can.
 *
 * WHAT THIS DELIBERATELY IS NOT. The project's stated goal for the October opponent is one
 * that "reads where the ball is going and moves to meet it", using World.predict -- which is
 * headless and side-effect free for exactly that purpose. This is not that, by request: an
 * opponent that cannot be beaten is a useful thing to have while the player's controls are
 * being built, because it guarantees the ball always comes back and the rally never dies
 * waiting for someone to miss. Swapping in a predicting version means writing one more class
 * next to this one and changing which is constructed.
 *
 * The honest limitation, written down rather than discovered later: a follower CANNOT be made
 * beatable by turning its speed down, it just becomes erratic. Difficulty has to come from
 * prediction quality, which is the real argument for the October version.
 */
public final class Follower implements Opponent {

    /**
     * Blade speed limit, m/s.
     *
     * Deliberately far beyond a human. Advanced players swing the racket itself at about
     * 17.8 m/s, but that is the swing, not repositioning across the table; a person cannot
     * carry the bat sideways at 25 m/s. That is the point -- this is a wall, not a player.
     */
    private static final double MAX_SPEED = 25.0;

    /** The plane the opponent's blade lives on: just beyond its own end of the table. */
    public static final double PLANE_Z = -(TABLE_LENGTH / 2 + 0.12);

    /**
     * How high above the table the blade will go, in metres.
     *
     * A ceiling is not a detail here, it is the difference between an opponent and a rocket
     * engine. The tracked height used to be max(0.04, ball.y()) with nothing above it, so the
     * blade followed a ball to ANY height -- and since it may move at MAX_SPEED it stayed in
     * contact all the way up, striking again on every step. No contact ever added energy and
     * SelfTest stayed green throughout; the blade was simply hitting the ball over and over,
     * and one lifted return went past 6 m still climbing.
     *
     * 0.55 m is about as high as a player takes a bat over a table without leaving the ground.
     * Above that the ball is out of reach, the blade waits, and the ball comes back down to
     * it -- which is what a person does.
     */
    private static final double MAX_REACH_Y = 0.55;

    /** Where the blade waits before a rally and after it has played its shot. */
    private static final Vec3 READY = new Vec3(0, 0.20, PLANE_Z);

    /**
     * How far in over the table the blade will step to meet a dying ball, in metres.
     *
     * Without this the blade waits on PLANE_Z for every ball, and a soft return that lands
     * short simply falls below it before it arrives -- measured: a 4.3 m/s push bouncing at
     * z = -0.62 was still descending through the blade's plane and hit the floor at z = -1.88,
     * untouched, ending the rally at two hits. It is not that the blade was too slow; it was
     * in the right place at the wrong time.
     *
     * A player does not wait on the baseline for a short ball, they step in. So does this --
     * but only over the last {@code REACH_FWD} metres, and only for a LOW ball (see
     * {@link #STEP_IN_HEIGHT}). Both guards are load-bearing: stepping in from further out,
     * or for every ball, leaves the blade parked in the middle of the table and out of
     * position for everything, which RallyTest catches immediately.
     */
    private static final double REACH_FWD = 0.55;

    /** Ball height, in metres, below which it is worth stepping in rather than waiting. */
    private static final double STEP_IN_HEIGHT = 0.22;

    /** How close the ball has to get before it commits to a stroke. */
    private static final double SWING_RANGE = 0.30;

    /** How long the stroke lasts. */
    private static final double SWING_TIME = 0.10;

    // ------------------------------------------------------------------ the tuned stroke
    //
    // These three decide whether a return is legal, and they were originally derived against
    // the wrong question. The criterion was "does the return get back over the net", and every
    // preset did -- by going nearly straight up. Asked the right question, "where does the
    // return LAND", the old values (an OPEN face and a 9 m/s swing) put 0 of 9 presets on the
    // table: the ball left at 19 m/s and came down 3 to 7 m past the end line.
    //
    // These come out of a sweep over all three, and the sweep found a trade-off rather than
    // an optimum. Scored two ways over the nine presets:
    //
    //   maximise how many returns LAND          -> best is 3 of 9, and one preset then fails
    //                                              to get back over the net at all
    //   require all nine to clear the net       -> 318 settings manage it, and the best of
    //                                              those lands 1 of 9, at an apex of 1.13 m
    //
    // No setting does both. That is not a tuning failure, it is the design showing its edge:
    // the presets arrive between 3.5 and 18.4 m/s carrying 25 to 125 rev/s, and one fixed
    // answer cannot be right for a 4.5 m/s serve and an 18 m/s smash at once -- whatever
    // returns the slow ball sends the fast one long. Returning all nine legally means choosing
    // the stroke FROM the ball, which means reading it: World.predict, and the October
    // opponent. This is the measurement behind "difficulty has to come from prediction
    // quality", which until now was only ever asserted.
    //
    // The net constraint wins, because being a wall is this class's entire job -- it exists so
    // the ball always comes back while the player's controls are being built. So: 0.20 / 8.5 /
    // 5.0, which clears the net on all nine and holds the worst apex to 1.13 m. The old values
    // cleared the net too, but by lobbing to 5.42 m and landing 0 of 9.
    //
    // RallyTest prints the full landing table on every run so the 1 of 9 stays in front of
    // whoever runs it, rather than being rediscovered in front of a grader.

    /**
     * How far the face is CLOSED over the incoming ball.
     *
     * Closed, not open -- which reverses what this comment used to say. The old reasoning was
     * that a closed face drives the ball into the net, and on a HORIZONTAL swing that is true.
     * This swing is not horizontal: it has an upward component, so a closed face brushes up
     * the back of the ball and loops it. The topspin that puts on is what drags the return
     * back down onto the table, which is the whole reason topspin dominates the sport and the
     * one thing this simulation models most carefully. Opening the face instead produced a
     * float that could only clear the net by being hit into the air.
     */
    private static final double FACE_CLOSED = 0.20;

    /**
     * Peak blade speed of the return, m/s.
     *
     * It has to stay high. Slowing it to 2.5 m/s lands more balls -- the incoming ball already
     * supplies most of the pace, since rubber has e ~ 0.9 and a 12 m/s ball comes off a
     * stationary blade at about 11 m/s -- but a slow blade cannot get the Cross-court loop back
     * over the net, and returning everything is the job. See the sweep note above.
     */
    private static final double SWING_SPEED = 8.5;

    /** Upward component of the stroke -- the brush that makes the topspin that brings the
     *  return down. It is what holds the apex to 1.13 m instead of the old 5.42 m. */
    private static final double SWING_LIFT = 5.0;

    private final double faceClosed;
    private final double swingSpeed;
    private final double swingLift;

    private double swinging = -1;      // seconds into a stroke, negative when not swinging

    /**
     * Where the ball was when the current stroke was committed to.
     *
     * Captured once, at the start of the swing, and then not updated -- which is the whole
     * point of it. The blade used to keep tracking the ball's height right through its own
     * stroke, so it stayed glued to the ball it had just hit and struck it again on the next
     * step, and the next, each hit steepening the return until the ball was going straight up.
     * A player follows through along the stroke, not after the ball.
     */
    private Vec3 swingAim = READY;

    public Follower() {
        this(FACE_CLOSED, SWING_SPEED, SWING_LIFT);
    }

    /**
     * A follower with a stroke of its own.
     *
     * Exists so the three numbers above can be swept rather than argued about -- that sweep is
     * where "the best a fixed stroke manages is three of nine" came from, and it can be rerun
     * against any change to the contact model. It is also the seam the October opponent will
     * want, since difficulty is going to mean a different stroke as well as a different brain.
     */
    Follower(double faceClosed, double swingSpeed, double swingLift) {
        this.faceClosed = faceClosed;
        this.swingSpeed = swingSpeed;
        this.swingLift = swingLift;
    }

    @Override public String name() { return "follower (unbeatable)"; }

    @Override
    public void advance(BallState ball, Paddle blade, double dt) {
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
            // Same half-sine profile the player's stroke uses, for the same reason: a blade
            // that starts and stops instantly reads as an impossible velocity when Paddle
            // differences its pose.
            double s = (1 - Math.cos(Math.PI * t)) / 2;
            double travel = SWING_TIME * s * 2 / Math.PI;
            want = new Vec3(swingAim.x(),
                            swingAim.y() + swingLift * travel,
                            swingAim.z() + swingSpeed * travel);
            if (t >= 1) swinging = -1;
        } else if (incoming) {
            // Move to meet it. Still no prediction: this is where the ball IS, not where it
            // is going, and that is the whole design. The one concession is depth -- a ball
            // already down near the table gets met further out, before it falls any lower.
            want = reachable(b);
        } else {
            // The ball is on its way back to the other end. Reset, rather than follow it --
            // following a departing ball is how the blade ended up chasing one into the roof.
            want = READY;
        }

        // Speed limit, so the blade sweeps rather than teleporting. Even a wall has to move.
        Vec3 step = want.minus(blade.pos());
        double maxStep = MAX_SPEED * dt;
        if (step.length() > maxStep) want = blade.pos().plusScaled(step.normalized(), maxStep);

        // Face pointing back up the table at the ball, closed over it so the upward part of
        // the stroke brushes topspin on. See FACE_CLOSED for why this leans over rather than
        // back, which is the opposite of what it used to do.
        blade.moveTo(want, new Vec3(0, -faceClosed, 1).normalized(), dt);
    }

    /**
     * The ball's position, brought back to somewhere the blade can actually be.
     *
     * Clamped at both ends of the height: the floor so it cannot dive through the table
     * chasing a dead ball, the ceiling so it cannot chase a lob into the roof.
     */
    private static Vec3 reachable(Vec3 b) {
        // Step in only over the last stretch, and only for a ball that is already low. Doing
        // it for every ball, or from further out, parks the blade in the middle of the table
        // and it is out of position for everything -- measured: RallyTest dropped from 10 of
        // 10 shots reached to 3 of 10 the first time this was written without the range guard.
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
