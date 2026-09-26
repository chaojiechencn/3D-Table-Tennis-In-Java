package tabletennis.game.shot;

import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.contact.BladeCollider;
import tabletennis.engine.contact.ContactSolver;
import tabletennis.engine.contact.Material;
import tabletennis.engine.math.Vec3;

/**
 * The shot the racket's own motion makes, with no authoring: the engine's one contact solver,
 * struck by a blade moving at the swing's window-averaged velocity. In the blade's frame the
 * normal component rebounds and the tangential slip is gripped into spin, so a square hit keeps
 * its pace and a glancing one trades pace for spin. Runs in shadow only; the game still plays
 * ShotAssist's shot.
 */
public final class ContactModel {

    private final ShotTuning.ContactKnobs Knobs;
    private final Material Rubber;

    public ContactModel(ShotTuning.ContactKnobs Knobs) {
        this.Knobs = Knobs;
        this.Rubber = Knobs.Rubber();
    }

    /**
     * The ball leaving a blade at BladeCentre facing FaceNormal and moving at RacketVelocity, for a
     * ball arriving as Incoming, placed at At. Null when the averaged swing says the two were
     * already separating: that contact happened on one step's motion the window does not share.
     */
    public BallState Strike(BallState Incoming, Vec3 At, Vec3 BladeCentre, Vec3 FaceNormal, Vec3 RacketVelocity) {
        BladeCollider Blade = new BladeCollider(BladeCentre, FaceNormal.Normalized(), RacketVelocity, Vec3.Zero);
        Vec3 Normal = Blade.EscapeNormal(At);
        BallState Arriving = Incoming.WithPosition(At);
        if (Arriving.Velocity().Minus(RacketVelocity).Dot(Normal) >= 0) return null;

        ContactSolver.Contact Touch = new ContactSolver.Contact(1.0, At, Normal, false);
        BallState Struck = ContactSolver.Respond(Arriving, Blade, Touch, Rubber, Simulation.Step).State();
        return SteerSwipe(Arriving, Struck, Normal, RacketVelocity).WithPosition(At);
    }

    /**
     * Moves SwipeToDirection of the sidespin's rotational energy into sideways speed, the way the
     * swipe pushed the ball. Speed relative to the blade grows by at most the energy the spin gave
     * up, so the knob redistributes energy and can never add any.
     */
    private BallState SteerSwipe(BallState Arriving, BallState Struck, Vec3 Normal, Vec3 RacketVelocity) {
        double Share = Knobs.SwipeToDirection();
        Vec3 FaceUp = Vec3.Up.TangentTo(Normal).Normalized();
        if (Share == 0 || FaceUp.LengthSquared() == 0) return Struck;

        Vec3 Across = FaceUp.Cross(Normal);
        double Push = Math.signum(Struck.Velocity().Minus(Arriving.Velocity()).Dot(Across));
        if (Push == 0) return Struck;

        double SideSpin = Struck.Spin().Dot(FaceUp);
        double FreedSpeedSquared = Share * BallSpec.Inertia * SideSpin * SideSpin / BallSpec.Mass;
        double Relative = Struck.Velocity().Minus(RacketVelocity).Dot(Across);
        double Gain = Math.sqrt(Relative * Relative + FreedSpeedSquared) - Math.abs(Relative);

        Vec3 Velocity = Struck.Velocity().PlusScaled(Across, Push * Gain);
        Vec3 Spin = Struck.Spin().PlusScaled(FaceUp, SideSpin * (Math.sqrt(1 - Share) - 1));
        return Struck.WithVelocity(Velocity).WithSpin(Spin);
    }
}
