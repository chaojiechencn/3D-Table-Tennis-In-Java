package tabletennis.game;

import tabletennis.game.rally.RallyEvent;
import tabletennis.game.rally.Side;

import java.util.List;

/** What one physics step produced: who struck the ball, who won a point, and every rally event. */
public record StepResult(Side HitBy, Side PointTo, List<RallyEvent> Events) {

    public boolean Contact()      { return HitBy != null; }
    public boolean PointAwarded() { return PointTo != null; }
}
