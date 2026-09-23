package tabletennis.engine.world;

import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.contact.ContactSolver;
import tabletennis.engine.contact.Materials;
import tabletennis.engine.flight.Integrator;
import tabletennis.engine.math.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The ball and everything it can hit, advanced only by the fixed Simulation.Step. Each step
 * reports what the ball touched; it never decides what a touch means for a rally.
 */
public final class PhysicsWorld {

    /** Where a fresh world holds the ball until something is launched. */
    public static final BallState ParkedBall = BallState.At(new Vec3(0, 0.30, 1.20), Vec3.Zero, Vec3.Zero);

    /** A ball that clips the cord can be pushed into the table in the same step. */
    private static final int MaxContactPasses = 8;

    private BallState Ball;
    private BallState PreviousBall;
    private double Time;
    private List<Racket> Rackets = List.of();

    public PhysicsWorld() {
        Launch(ParkedBall);
    }

    /** Put a ball in flight; the clock restarts. */
    public void Launch(BallState State) {
        Ball = State;
        PreviousBall = State;
        Time = 0;
    }

    /** The rackets that may be struck, in the order equal times of impact resolve; empty for none. */
    public void SetRackets(List<Racket> Strikable) {
        Rackets = List.copyOf(Strikable);
    }

    /** The arcade layer's one hook: the authored shot replaces the raw one after a racket contact. */
    public void ReplaceBall(BallState State) {
        Ball = State;
    }

    public BallState Ball()         { return Ball; }
    public BallState PreviousBall() { return PreviousBall; }
    public double Time()            { return Time; }

    /** Flight, then contacts: an impulse is a discontinuity RK4 must never straddle. */
    public StepReport Step() {
        PreviousBall = Ball;
        List<SurfaceHit> Hits = new ArrayList<>();
        Ball = ResolveContacts(PreviousBall, Integrator.Step(Ball, Simulation.Step), Hits);
        Time += Simulation.Step;

        StepReport Report = new StepReport(PreviousBall, Ball, List.copyOf(Hits));
        if (!Ball.IsFinite()) Launch(ParkedBall);   // unreachable, but never freeze on a NaN
        return Report;
    }

    private record Candidate(Surface Struck, ContactSolver.Contact Touch) {}

    /**
     * Resolve the EARLIEST contact, repeatedly. After a swept contact the rest of the step is
     * flown, not skipped, from the state AT impact: the end-of-step velocity would add energy.
     */
    private BallState ResolveContacts(BallState From, BallState To, List<SurfaceHit> Hits) {
        BallState Before = From, Current = To;
        double StepLeft = Simulation.Step;
        List<Surface> Surfaces = SurfacesThisStep();

        for (int Pass = 0; Pass < MaxContactPasses; Pass++) {
            Candidate Earliest = EarliestContact(Surfaces, Before, Current);
            if (Earliest == null) return Current;
            ContactSolver.Contact Touch = Earliest.Touch();

            BallState AtContact = Touch.Swept() ? Integrator.Step(Before, StepLeft * Touch.TimeOfImpact()) : Current;
            ContactSolver.Response Response = ContactSolver.Respond(
                    AtContact, Earliest.Struck().Shape(), Touch, Earliest.Struck().Finish(), Simulation.Step);
            Hits.add(new SurfaceHit(Earliest.Struck(), Response));
            Current = Response.State();

            if (!Touch.Swept()) continue;
            double Left = StepLeft * (1.0 - Touch.TimeOfImpact());
            if (Left < 1e-9) continue;

            Before = Current;
            Current = Integrator.Step(Current, Left);
            StepLeft = Left;
        }
        return Current;
    }

    /** One snapshot of each blade per step, after the fixed surfaces. */
    private List<Surface> SurfacesThisStep() {
        List<Surface> All = new ArrayList<>(Arena.FixedSurfaces);
        for (Racket Each : Rackets) {
            All.add(new Surface(Each.Blade(), Materials.Rubber, SurfaceKind.Blade, Each));
        }
        return All;
    }

    /**
     * Every pass detects against a whole step's carry of a moving blade, even after a swept
     * contact has used part of the step (a candidate defect, kept until deliberately changed).
     */
    private static Candidate EarliestContact(List<Surface> Surfaces, BallState Before, BallState After) {
        Candidate Earliest = null;
        for (Surface Each : Surfaces) {
            ContactSolver.Contact Touch = ContactSolver.Detect(Before, After, Each.Shape(), Simulation.Step);
            if (Touch != null && (Earliest == null || Touch.TimeOfImpact() < Earliest.Touch().TimeOfImpact())) {
                Earliest = new Candidate(Each, Touch);
            }
        }
        return Earliest;
    }
}
