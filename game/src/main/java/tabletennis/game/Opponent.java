package tabletennis.game;

import tabletennis.engine.BallState;
import tabletennis.engine.Paddle;

/** Whatever plays the far end. A predicting opponent will be a second implementation. */
public interface Opponent {

    /** Move the blade for one PHYSICS step, never a frame time. */
    void advance(BallState ball, Paddle blade, double dt);
}
