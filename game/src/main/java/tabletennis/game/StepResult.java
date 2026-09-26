package tabletennis.game;

import tabletennis.game.rally.RallyEvent;
import tabletennis.game.rally.Side;
import tabletennis.game.shot.ShotComparison;

import java.util.List;

/**
 * What one physics step produced: who struck the ball, who won a point, and every rally event.
 * Shadow is the contact model's comparison on a player hit, and null on every other step.
 */
public record StepResult(Side HitBy, Side PointTo, List<RallyEvent> Events, ShotComparison Shadow) {

    public boolean Contact()      { return HitBy != null; }
    public boolean PointAwarded() { return PointTo != null; }
}
