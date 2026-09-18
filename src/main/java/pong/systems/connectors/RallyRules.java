package pong.systems.connectors;

import pong.game_objects.ball.Ball;
import pong.game_objects.racket.Racket;
import pong.game_world.World;
import pong.systems.scoring.Scoreboard;

import java.util.List;

import static pong.config.Physical.DT;

/**
 * The rules of a rally: who is allowed to hit, and who just won the point.
 *
 * A CONNECTOR in the sense of the layout this project follows -- it owns no simulation and no
 * rendering of its own, and exists only to make three systems that know nothing about each other
 * ({@link World}, the two {@link Racket}s, and the {@link Scoreboard}) interact correctly. The
 * alternative is for each of them to learn about the others, which is how a physics engine ends
 * up with a scoreboard in it.
 *
 * It lived inside the JavaFX Application until this was extracted, which had two costs. It could
 * not be tested -- the rules ran only when a window was open -- and the rally suite had to
 * REIMPLEMENT the one-bounce gating twice to drive its own rallies, so the thing being tested was
 * a copy of the rules rather than the rules. Both suites now drive this.
 *
 * <h2>The rules</h2>
 *
 * ITTF: you may only return the ball after it has bounced once on YOUR side. Enforced by handing
 * {@code World} a null racket for whoever is not yet allowed to hit -- the blade still tracks the
 * ball on screen, it just cannot make contact. A ball that bounces twice on one side, falls back
 * on its own hitter's half, lands past the end line, or reaches the floor decides the point.
 *
 * A net cord is deliberately NOT a point-ender. {@code World} emits NET for any cord contact
 * above 0.05 m/s, and under ITTF a rally ball that clips the net and still lands legally is a
 * good shot -- often a lucky one, never a lost point. A cord that genuinely kills the ball still
 * decides the point here, a moment later, through the own-half or floor rule. (A SERVICE that
 * touches the net is a let, replayed rather than scored; that belongs with serving, which is not
 * built.)
 */
public final class RallyRules {

    /**
     * How long after a racket contact a table bounce is treated as part of that contact rather
     * than as a shot falling back.
     *
     * A legal push dug out at surface height fires its table contact on the SAME physics step as
     * the racket contact, and without this window that reads as "your own shot bounced on your
     * own half" and ends the point on a legal stroke. Two steps is enough: the ball is long gone
     * from the surface before that at any speed the shot model can produce.
     */
    private static final double CONTACT_BOUNCE_WINDOW = DT * 2;

    /**
     * What one physics step turned out to be.
     *
     * @param racketHit     a racket struck the ball on this step
     * @param playerHit     and it was the player's racket rather than the opponent's
     * @param pointDecided  the point was decided ON THIS STEP -- an edge, not a state, so a
     *                      caller can act on it once. {@link #pointOver()} is the state.
     */
    public record Step(boolean racketHit, boolean playerHit, boolean pointDecided) {}

    private final World world;
    private final Scoreboard score;

    private boolean playerMayHit;
    private boolean opponentMayHit;
    private int lastBounceSerial;
    private int lastRacketHits;

    /**
     * Latched the instant a point is decided, cleared by the next {@link #serve}.
     *
     * Without it the ball stays live while the screen waits to cut away: the rackets keep making
     * contact, every contact runs through the shot model and cuts the camera, and a second
     * point-ending event is counted as a second point. A decided point has to stop being playable
     * at the moment it is decided, not when a replay timer happens to fire.
     */
    private boolean pointOver;

    /** Who put the ball in the air: 0 = the serve, +1 = the opponent hit it, -1 = the player. */
    private int lastHitSide;

    private double lastHitTime;

    public RallyRules(World world) { this(world, new Scoreboard()); }

    public RallyRules(World world, Scoreboard score) {
        this.world = world;
        this.score = score;
    }

    /** Put a ball in play and start the rule state over. Does not touch the score. */
    public void serve(Ball ball) {
        world.launch(ball);
        playerMayHit = false;
        opponentMayHit = false;
        pointOver = false;
        lastHitSide = 0;
        lastHitTime = -1;
        lastRacketHits = 0;
        lastBounceSerial = world.bounceSerial();
    }

    /**
     * Put only the racket that is currently allowed to hit into the collision set. Call this
     * BEFORE {@link World#step()}, after both blades have been posed for the step.
     *
     * A decided point withdraws BOTH rackets: the blades still track on screen, they just stop
     * being able to touch a ball whose point has already been awarded.
     */
    public void gate(Racket player, Racket opponent) {
        world.setRackets(playerMayHit && !pointOver ? player : null,
                         opponentMayHit && !pointOver ? opponent : null);
    }

    /**
     * Apply the rules to the step that just ran. Call this immediately after
     * {@link World#step()}.
     *
     * The returned {@link Step} reports a racket contact BEFORE the shot model has touched it, so
     * the caller can author the outgoing shot and still be told whose contact it was.
     */
    public Step observe() {
        boolean racketHit = false, playerHit = false;
        boolean wasOver = pointOver;

        if (world.racketHits() > lastRacketHits) {
            lastRacketHits = world.racketHits();
            racketHit = true;
            playerHit = lastHitByPlayer();
            lastHitSide = playerHit ? -1 : 1;
            lastHitTime = world.time();
            playerMayHit = false;
            opponentMayHit = false;
        }

        // A table bounce opens the receiver's racket -- unless it is the SECOND bounce on that
        // side, or the ball has fallen back onto the hitter's own half, either of which decides
        // the point.
        if (world.bounceSerial() > lastBounceSerial
                && world.time() - lastHitTime > CONTACT_BOUNCE_WINDOW) {
            boolean near = lastBounceSide() < 0;
            if (near) {
                if (lastHitSide >= 0) {
                    // Their ball (or the serve) has bounced on our side. The first bounce opens
                    // our racket; a second means we never got to it.
                    if (playerMayHit) endPoint(Scoreboard.Side.OPPONENT);
                    else playerMayHit = true;
                } else {
                    endPoint(Scoreboard.Side.OPPONENT);   // our own shot never crossed
                }
            } else {
                if (lastHitSide <= 0) {
                    if (opponentMayHit) endPoint(Scoreboard.Side.PLAYER);
                    else opponentMayHit = true;
                } else {
                    endPoint(Scoreboard.Side.PLAYER);
                }
            }
        }

        // A bounce inside the contact window is the racket contact's own table touch. Not a rally
        // event, but the serial still has to move on or the next real bounce is measured against
        // a stale one.
        lastBounceSerial = world.bounceSerial();

        // A ball past the end line, or one that reached the floor, decides the point against
        // whoever hit it last. The serve counts as the player's, so a feed that never lands is
        // the player's fault, the same way a missed service toss is.
        World.Event last = world.lastEvent();
        if (last != null && world.time() - last.time() < DT * 2
                && (last.type() == World.EventType.OUT_OF_BOUNDS
                 || last.type() == World.EventType.FLOOR)) {
            endPoint(lastHitSide > 0 ? Scoreboard.Side.PLAYER : Scoreboard.Side.OPPONENT);
        }

        return new Step(racketHit, playerHit, pointOver && !wasOver);
    }

    /**
     * Award the point, once.
     *
     * The latch is the whole method. Several rules can fire on the same step -- a ball can be
     * long AND reach the floor a moment later -- and every one of them is a legitimate reason to
     * end the rally, but only the FIRST says who won it. Awarding on each would score one rally
     * two or three times.
     */
    private void endPoint(Scoreboard.Side winner) {
        if (pointOver) return;
        pointOver = true;
        score.pointTo(winner);
    }

    // ------------------------------------------------------------------ reading the state

    public Scoreboard score()        { return score; }
    public boolean pointOver()       { return pointOver; }

    /** Whether the ball has bounced on the player's half, so the player's racket is live. */
    public boolean playerMayHit()    { return playerMayHit && !pointOver; }

    /** Whose racket the most recent contact was: near half (z &lt; 0) is the player. */
    private boolean lastHitByPlayer() {
        List<World.Event> es = world.events();
        for (int i = es.size() - 1; i >= 0; i--) {
            if (es.get(i).type() == World.EventType.RACKET_HIT) return es.get(i).side() < 0;
        }
        return true;
    }

    /** Side of the most recent table bounce: -1 near (player), +1 far (opponent), 0 if none. */
    private int lastBounceSide() {
        List<World.Event> es = world.events();
        for (int i = es.size() - 1; i >= 0; i--) {
            if (es.get(i).type() == World.EventType.TABLE_BOUNCE) return es.get(i).side();
        }
        return 0;
    }
}
