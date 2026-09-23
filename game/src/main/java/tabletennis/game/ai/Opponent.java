package tabletennis.game.ai;

import tabletennis.engine.BallState;
import tabletennis.engine.world.Racket;

/** Whatever plays the far end. A predicting opponent is a second implementation, not a rewrite. */
public interface Opponent {

    /** Move the blade for one PHYSICS step, never a frame time. */
    void Advance(BallState Ball, Racket Blade, double Seconds);
}
