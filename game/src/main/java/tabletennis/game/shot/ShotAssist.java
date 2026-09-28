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
    private final ShotTuning.SpinKnobs SpinTuning;
    private final ShotTuning.ContactKnobs ContactTuning;
    private final ContactModel RacketContact;
    private ShotDecision LastDecision;

    public ShotAssist() { this(ShotTuning.Defaults()); }

    public ShotAssist(ShotTuning Tuning) {
        Quality = Tuning.Quality();
        Fallback = Tuning.Rescue();
        Reading = new SwingReading(Tuning);
        Planner = new ShotPlanner(Tuning);
        Searcher = new ShotSearch(Tuning);
        SpinTuning = Tuning.Spin();
        ContactTuning = Tuning.Contact();
        RacketContact = new ContactModel(ContactTuning);
        LastDecision = ShotDecision.None(Planner.Area());
    }

    public ShotDecision LastDecision() { return LastDecision; }

    /** The sideways-lift gain the shot just authored must fly under, for the world to apply. */
    public double CurveGainFor(boolean PlayerHit) {
        return Searcher.CurveGainFor(PlayerHit ? -1.0 : 1.0);
    }

    /**
     * The shot that leaves the racket. Incoming is the ball just before the contact, Physical the
     * raw impulse result; a player hit heads toward -Z, an opponent hit toward +Z. Only the
     * player is graded on contact quality: grading the opponent made it shank ordinary feeds into
     * the net, which reads as broken rather than beatable. The racket's velocity this step stands in
     * for the swing.
     */
    public BallState Assist(BallState Incoming, BallState Physical, Racket Struck, boolean PlayerHit) {
        return Assist(Incoming, Physical, Struck, PlayerHit, Struck.Velocity());
    }

    /**
     * As above, with the swing averaged over the contact window. A player's spin comes from that
     * swing through the contact model, and the search then solves a launch that lands WITH that
     * spin, so a chop really chops and a swipe really curves.
     */
    public BallState Assist(BallState Incoming, BallState Physical, Racket Struck, boolean PlayerHit,
                            Vec3 SwingVelocity) {
        double TowardOpponent = PlayerHit ? -1.0 : 1.0;
        Vec3 Contact = Physical.Position();
        Vec3 Reflect = Physical.Velocity();

        Swing Read = Reading.Read(Contact, Struck, TowardOpponent);
        double Clean = Reading.ContactQuality(Incoming, Read);
        double Authored = PlayerHit ? Quality.AssistFloor() + (1 - Quality.AssistFloor()) * Clean : 1.0;
        Plan Intended = Planner.For(Read, Incoming, TowardOpponent);
        SpinPlan FromRacket = PlayerHit ? RacketSpin(Incoming, Contact, Struck, SwingVelocity) : null;
        if (FromRacket != null) {
            SpinPlan Blended = SpinPlan.Lerp(Intended.Spin(), FromRacket, ContactTuning.RacketSpinShare());
            Intended = new Plan(Intended.Want(), Intended.Safe(), Intended.Speed(), Blended);
        }

        if (PlayerHit) Intended = WithLandableCurve(Contact, Intended, TowardOpponent);

        Candidate Best = Searcher.Search(Contact, Reflect, Intended, TowardOpponent);
        if (PlayerHit && Best.Cost() > 0 && Intended.Spin().Side() != 0) {
            Plan Straight = WithSide(Intended, 0);
            Candidate Uncurved = Searcher.Search(Contact, Reflect, Straight, TowardOpponent);
            if (Uncurved.Cost() < Best.Cost()) Best = Uncurved;
        }
        boolean MayRescue = !PlayerHit
                || (Clean >= Fallback.RescueQualityFloor() && Read.Amount() <= Fallback.RescueEffortCeiling());
        if (Best.Cost() > 0 && MayRescue) {
            Best = Searcher.Rescue(Contact, Intended.Want().X(), Intended.Spin(), TowardOpponent, Best);
        }

        Vec3 FinalVelocity = Vec3.Lerp(Searcher.CapReflection(Reflect), Best.Velocity(), Authored);
        Vec3 FinalSpin = Vec3.Lerp(Physical.Spin(), Best.Spin().VectorFor(Best.Velocity()), Authored);
        SpinPlan Played = SpinPlan.Of(FinalVelocity, FinalSpin);

        LastDecision = new ShotDecision(Contact, Struck.Velocity(), Incoming.Velocity(),
                ShotDecision.DirectionOf(Reflect),
                ShotDecision.DirectionOf(new Vec3(Best.Target().X() - Contact.X(), 0, Best.Target().Z() - Contact.Z())),
                ShotDecision.DirectionOf(FinalVelocity), Best.Target(), Best.Trajectory().Landing(),
                FinalVelocity.Length(), FinalSpin, Best.Passes(), Best.Cost() == 0, Planner.Area(),
                Played.Top(), Played.Side(), FromRacket != null);

        return new BallState(Physical.Position(), FinalVelocity, FinalSpin, Physical.Orientation());
    }

    /**
     * Under the curve gain a hard deep drive can bend further than the table is wide, which no
     * launch lands. Scale the sidespin so the bend, measured once on the planned shot, fits
     * MaxCurve: one search instead of a ladder of failed ones.
     */
    private Plan WithLandableCurve(Vec3 Contact, Plan Intended, double TowardOpponent) {
        double Side = Intended.Spin().Side();
        if (Side == 0) return Intended;
        double Bend = Math.abs(Searcher.CurveOf(Contact, Intended.Want(), Intended.Speed(), Intended.Spin(),
                                                TowardOpponent));
        return Bend <= ContactTuning.MaxCurve() ? Intended : WithSide(Intended, Side * ContactTuning.MaxCurve() / Bend);
    }

    private static Plan WithSide(Plan Intended, double Side) {
        return new Plan(Intended.Want(), Intended.Safe(), Intended.Speed(), new SpinPlan(Intended.Spin().Top(), Side));
    }

    /**
     * The spin the racket's motion gives the ball, its sidespin amplified so a swipe curves, then
     * capped; null if the swing was leaving the ball.
     */
    private SpinPlan RacketSpin(BallState Incoming, Vec3 At, Racket Struck, Vec3 SwingVelocity) {
        BallState Hit = RacketContact.Strike(Incoming, At, Struck.Position(), Struck.Normal(), SwingVelocity);
        if (Hit == null) return null;
        SpinPlan Made = SpinPlan.Of(Hit.Velocity(), Hit.Spin());
        return new SpinPlan(Made.Top(), Made.Side() * ContactTuning.SidespinGain())
                .CappedAt(SpinTuning.MaxSpin(), ContactTuning.MaxSidespin());
    }
}
