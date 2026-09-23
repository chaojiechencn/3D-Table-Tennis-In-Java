package tabletennis.engine.world;

import tabletennis.engine.BallState;

import java.util.List;

/**
 * What one physics step did: the ball before it and after its contacts, and every contact in the
 * order it happened. Facts only; what they mean for a rally is the game's business.
 */
public record StepReport(BallState Before, BallState After, List<SurfaceHit> Hits) {

    /** The contacts hard enough to count as events, in order. */
    public List<SurfaceHit> NotableHits() {
        return Hits.stream().filter(SurfaceHit::IsNotable).toList();
    }

    public boolean Touched(SurfaceKind Kind) {
        return Hits.stream().anyMatch(Hit -> Hit.IsNotable() && Hit.Kind() == Kind);
    }
}
