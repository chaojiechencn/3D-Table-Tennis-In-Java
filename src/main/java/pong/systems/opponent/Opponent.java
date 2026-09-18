package pong.systems.opponent;

import pong.game_objects.ball.Ball;
import pong.game_objects.racket.Racket;

/**
 * Whatever is playing the far end.
 *
 * An interface for one reason: the opponent that exists today is a follower, and the one the
 * project actually commits to for October predicts where the ball is going. Those are
 * different strategies over the same job -- look at the ball, move the blade, swing -- so the
 * second one should be a class added beside this, not a rewrite of it.
 */
public interface Opponent {

    /**
     * Move the blade for one physics step.
     *
     * @param ball  the ball as it is right now
     * @param blade the opponent's racket, to be moved
     * @param dt    the PHYSICS step, never a frame time
     */
    void advance(Ball ball, Racket blade, double dt);

    /** For the on-screen legend. */
    String name();
}
