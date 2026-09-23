package tabletennis.game.rally;

import tabletennis.engine.world.Racket;
import tabletennis.engine.world.StepReport;
import tabletennis.engine.world.SurfaceHit;

import java.util.ArrayList;
import java.util.List;

/** A physics step's notable contacts, in order, as the facts the rally rules read. */
public final class RallyFacts {

    private RallyFacts() {}

    /** Any racket other than PlayerRacket is the opponent's. */
    public static List<RallyEvent> Of(StepReport Report, Racket PlayerRacket) {
        List<RallyEvent> Facts = new ArrayList<>();
        for (SurfaceHit Hit : Report.NotableHits()) {
            Facts.add(switch (Hit.Kind()) {
                case Blade -> RallyEvent.Hit(Hit.Owner() == PlayerRacket ? Side.Player : Side.Opponent, Hit.Point());
                case Table -> RallyEvent.Of(EventType.TableBounce, Hit.Point());
                case Net -> RallyEvent.Of(EventType.NetTouch, Hit.Point());
                case Floor -> RallyEvent.Of(EventType.FloorTouch, Hit.Point());
            });
        }
        return Facts;
    }
}
