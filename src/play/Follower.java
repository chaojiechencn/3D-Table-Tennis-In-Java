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

    /** How close the ball has to get before it commits to a stroke. */
    private static final double SWING_RANGE = 0.30;

    /** Peak blade speed of its return, and how long the stroke lasts. */
    private static final double SWING_SPEED = 9.0;
    private static final double SWING_TIME = 0.10;

    /** A small upward component to the stroke, so returns are lifted rather than pushed. */
    private static final double SWING_LIFT = 3.0;

    /**
     * How far the face is OPENED, leaning back away from the incoming ball.
     *
     * Open, not closed, and the sign matters more than the magnitude. The first version of
     * this closed the face over the ball because that is what a looping player does -- and
     * every fast shot went straight into the net, because a closed face on a horizontal swing
     * drives the ball DOWN and the ball has 15 cm of net to clear from 12 cm away. Opening the
     * face lifts the return over the cord. Swept across the presets, anything from 0.0 to 0.5
     * returns all of them; 0.35 sits in the middle of that range rather than on its edge.
     */
    private static final double FACE_OPEN = 0.35;

    private double swinging = -1;      // seconds into a stroke, negative when not swinging

    @Override public String name() { return "follower (unbeatable)"; }

    @Override
    public void advance(BallState ball, Paddle blade, double dt) {
        Vec3 b = ball.pos();

        // Track the ball's CURRENT position. No prediction: that is the whole design.
        // Height is clamped so the blade cannot dive through the table chasing a dead ball.
        double targetY = Math.max(0.04, b.y());
        double targetX = clamp(b.x(), -TABLE_WIDTH, TABLE_WIDTH);

        boolean incoming = b.z() < 0 && ball.vel().z() < 0;
        double reach = b.z() - PLANE_Z;
        if (incoming && reach < SWING_RANGE && swinging < 0) swinging = 0;
        if (!incoming && swinging < 0) swinging = -1;

        double z = PLANE_Z;
        if (swinging >= 0) {
            swinging += dt;
            double t = Math.min(1, swinging / SWING_TIME);
            // Same half-sine profile the player's stroke uses, for the same reason: a blade
            // that starts and stops instantly reads as an impossible velocity when Paddle
            // differences its pose.
            double s = (1 - Math.cos(Math.PI * t)) / 2;
            double travel = SWING_TIME * s * 2 / Math.PI;
            z = PLANE_Z + SWING_SPEED * travel;
            targetY += SWING_LIFT * travel;
            if (t >= 1) swinging = -1;
        }

        // Speed limit, so the blade sweeps rather than teleporting. Even a wall has to move.
        Vec3 want = new Vec3(targetX, targetY, z);
        Vec3 step = want.minus(blade.pos());
        double maxStep = MAX_SPEED * dt;
        if (step.length() > maxStep) want = blade.pos().plusScaled(step.normalized(), maxStep);

        // Face pointing back up the table at the ball, opened a little so returns clear
        // the net. See FACE_OPEN for why this leans back rather than over.
        blade.moveTo(want, new Vec3(0, FACE_OPEN, 1).normalized(), dt);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
