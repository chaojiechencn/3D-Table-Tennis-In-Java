package tabletennis.game.shot;

import tabletennis.engine.BallState;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.Racket;
import tabletennis.game.shot.ShotPlanner.Plan;
import tabletennis.game.shot.ShotSearch.Candidate;
import tabletennis.game.shot.SwingReading.Swing;

/**
 * The arcade shot model for both rackets. The exact impulse still runs on every contact; this
 * turns it into a playable shot: read the swing, plan a target inside the opponent's court,
 * search for the legal launch closest to it, then blend. A clean contact gets the authored shot;
 * the player's shank keeps proportionally more raw physics and usually dies. Only the launch is
 * authored; the flight after it is the real simulation.
 */
public final class ShotAssist {

    private final ShotTuning.QualityKnobs Quality;
    private final ShotTuning.RescueKnobs Fallback;
    private final SwingReading Reading;
    private final ShotPlanner Planner;
    private final ShotSearch Searcher;
    private ShotDecision LastDecision;

    public ShotAssist() { this(ShotTuning.Defaults()); }

    public ShotAssist(ShotTuning Tuning) {
        Quality = Tuning.Quality();
        Fallback = Tuning.Rescue();
        Reading = new SwingReading(Tuning);
        Planner = new ShotPlanner(Tuning);
        Searcher = new ShotSearch(Tuning);
        LastDecision = ShotDecision.None(Planner.Area());
    }

    public ShotDecision LastDecision() { return LastDecision; }

    /**
     * The shot that leaves the racket. Incoming is the ball just before the contact, Physical the
     * raw impulse result; a player hit heads toward -Z, an opponent hit toward +Z. Only the
     * player is graded on contact quality: grading the opponent made it shank ordinary feeds into
     * the net, which reads as broken rather than beatable.
     */
    public BallState Assist(BallState Incoming, BallState Physical, Racket Struck, boolean PlayerHit) {
        double TowardOpponent = PlayerHit ? -1.0 : 1.0;
        Vec3 Contact = Physical.Position();
        Vec3 Reflect = Physical.Velocity();

        Swing Read = Reading.Read(Contact, Struck, TowardOpponent);
        double Clean = Reading.ContactQuality(Incoming, Read);
        double Authored = PlayerHit ? Quality.AssistFloor() + (1 - Quality.AssistFloor()) * Clean : 1.0;
        Plan Intended = Planner.For(Read, Incoming, TowardOpponent);

        Candidate Best = Searcher.Search(Contact, Reflect, Intended, TowardOpponent);
        boolean MayRescue = !PlayerHit
                || (Clean >= Fallback.RescueQualityFloor() && Read.Amount() <= Fallback.RescueEffortCeiling());
        if (Best.Cost() > 0 && MayRescue) {
            Best = Searcher.Rescue(Contact, Intended.Want().X(), Intended.Spin(), TowardOpponent, Best);
        }

        Vec3 FinalVelocity = Vec3.Lerp(Searcher.CapReflection(Reflect), Best.Velocity(), Authored);
        Vec3 FinalSpin = Vec3.Lerp(Physical.Spin(), Best.Spin().VectorFor(Best.Velocity()), Authored);

        LastDecision = new ShotDecision(Contact, Struck.Velocity(), Incoming.Velocity(),
                ShotDecision.DirectionOf(Reflect),
                ShotDecision.DirectionOf(new Vec3(Best.Target().X() - Contact.X(), 0, Best.Target().Z() - Contact.Z())),
                ShotDecision.DirectionOf(FinalVelocity), Best.Target(), Best.Trajectory().Landing(),
                FinalVelocity.Length(), FinalSpin, Best.Passes(), Best.Cost() == 0, Planner.Area());

        return new BallState(Physical.Position(), FinalVelocity, FinalSpin, Physical.Orientation());
    }
}
