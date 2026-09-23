package tabletennis.engine;

import tabletennis.engine.Constants.Material;
import tabletennis.engine.Contacts.Box;
import tabletennis.engine.Contacts.Hit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static tabletennis.engine.Constants.*;

/** The ball, everything it can hit, and a log of what happened. Stepped only by a fixed DT. */
public final class World {

    /** Top face on y = 0, the physics origin. */
    public static final Box Table = Box.Centered(
            0, -TableThick / 2, 0,
            TableWidth, TableThick, TableLength);

    public static final Box Net = Box.Centered(
            0, NetHeight / 2, 0,
            NetWidth, NetHeight, NetThick);

    /** 120 m across because a missed ball rolls 17-20+ m; the visible floor is 14 x 16 m. */
    public static final Box Floor = Box.Centered(
            0, -TableHeight - 0.5, 0,
            120, 1.0, 120);

    public enum EventType { TableBounce, Net, Floor, OutOfBounds, PaddleHit }

    /** {@code side} is +1 on the far half (z &lt; 0), -1 on the near half, 0 for none. */
    public record Event(EventType Type, Vec3 At, double Speed, double Time, int Side) {}

    // Slower contacts are real but not worth logging: a dying ball bounces dozens of times.
    private static final double LoggableNet = 0.05;
    private static final double LoggableBounce = 0.35;
    private static final double LoggableFloor = 0.4;
    private static final double LoggablePaddle = 0.3;

    /** Same-type events closer than this are one contact retriggering while the ball settles. */
    private static final double EventMergeWindow = 0.12;
    private static final int MaxEvents = 12;
    private static final int MaxMarks = 24;
    private static final int MaxContactPasses = 8;
    private static final BallState ParkedBall = BallState.At(new Vec3(0, 0.30, 1.20), Vec3.Zero, Vec3.Zero);

    private BallState State;
    private BallState Previous;
    private double Time;
    private double Apex;
    private int TableBounces;       // per stroke: reset by each paddle contact
    private boolean OutReported;
    private int BounceSerial;       // never reset: "has anything landed since I looked?"
    private int PaddleHits;

    /** Null in a paddle-free world, which is what {@link #Predict} needs. */
    private Paddle Player;
    private Paddle Opponent;

    private final Deque<Event> Events = new ArrayDeque<>();
    private final List<Vec3> BounceMarks = new ArrayList<>();

    public World() {
        Reset(ParkedBall);
    }

    public void Reset(BallState S) {
        State = S;
        Previous = S;
        Time = 0;
        Apex = S.Pos().Y();
        TableBounces = 0;
        OutReported = false;
        PaddleHits = 0;
        Events.clear();
        BounceMarks.clear();
    }

    public void Launch(BallState S) {
        Reset(S);
    }

    /** Flight, then contacts: an impulse is a discontinuity RK4 must never straddle. */
    public void Step() {
        Previous = State;
        State = ResolveContacts(Previous, Integrator.Step(State, Dt));
        Time += Dt;

        if (State.Pos().Y() > Apex) Apex = State.Pos().Y();
        DetectOutOfBounds(Previous, State);

        if (!State.IsFinite()) Reset(ParkedBall);   // unreachable, but never freeze on a NaN
    }

    private record Surface(Collider Shape, Material SurfaceMaterial, EventType Kind) {}

    private record SurfaceContact(Surface Struck, Contacts.Contact Touch) {}

    private List<Surface> Surfaces() {
        List<Surface> All = new ArrayList<>(5);
        All.add(new Surface(Net,   NetMat,   EventType.Net));
        All.add(new Surface(Table, TableMat, EventType.TableBounce));
        All.add(new Surface(Floor, FloorMat, EventType.Floor));
        if (Player != null)   All.add(new Surface(Player.Collider(),   RacketMat, EventType.PaddleHit));
        if (Opponent != null) All.add(new Surface(Opponent.Collider(), RacketMat, EventType.PaddleHit));
        return All;
    }

    /**
     * Resolve the EARLIEST contact, repeatedly: a ball that clips the cord can be pushed into the
     * table in the same step. After a swept contact the rest of the step is flown, not skipped.
     */
    private BallState ResolveContacts(BallState From, BallState To) {
        BallState Before = From, Current = To;
        double StepLeft = Dt;
        List<Surface> All = Surfaces();   // one snapshot of each blade per step

        for (int Pass = 0; Pass < MaxContactPasses; Pass++) {
            SurfaceContact Outcome = EarliestContact(All, Before, Current);
            if (Outcome == null) return Current;
            Contacts.Contact C = Outcome.Touch();

            // Bounce the state AT impact; the end-of-step velocity would add energy.
            BallState AtContact = C.Swept() ? Integrator.Step(Before, StepLeft * C.Toi()) : Current;
            Hit Result = Contacts.Respond(AtContact, Outcome.Struck().Shape(), C, Outcome.Struck().SurfaceMaterial());
            Record(Outcome.Struck(), Result);
            Current = Result.State();

            if (!C.Swept()) continue;
            double Left = StepLeft * (1.0 - C.Toi());
            if (Left < 1e-9) continue;

            Before = Current;
            Current = Integrator.Step(Current, Left);
            StepLeft = Left;
        }
        return Current;
    }

    private static SurfaceContact EarliestContact(List<Surface> All, BallState Before, BallState After) {
        SurfaceContact Earliest = null;
        for (Surface S : All) {
            Contacts.Contact C = Contacts.Detect(Before, After, S.Shape());
            if (C != null && (Earliest == null || C.Toi() < Earliest.Touch().Toi())) {
                Earliest = new SurfaceContact(S, C);
            }
        }
        return Earliest;
    }

    private void Record(Surface S, Hit Outcome) {
        Vec3 P = Outcome.Point();
        int Half = P.Z() < 0 ? 1 : -1;
        switch (S.Kind()) {
            case Net -> {
                if (Outcome.ImpactSpeed() > LoggableNet) Record(EventType.Net, P, Outcome.ImpactSpeed(), 0);
            }
            case TableBounce -> {
                if (!Outcome.Resting() && Outcome.ImpactSpeed() > LoggableBounce) {
                    TableBounces++;
                    BounceSerial++;
                    Record(EventType.TableBounce, P, Outcome.ImpactSpeed(), Half);
                    AddMark(P);
                }
            }
            case Floor -> {
                if (!Outcome.Resting() && Outcome.ImpactSpeed() > LoggableFloor) {
                    Record(EventType.Floor, P, Outcome.ImpactSpeed(), 0);
                }
            }
            case PaddleHit -> {
                if (Outcome.ImpactSpeed() > LoggablePaddle) {
                    PaddleHits++;
                    Record(EventType.PaddleHit, P, Outcome.ImpactSpeed(), Half);
                    // A new stroke is a new shot: the in/out rules start again.
                    TableBounces = 0;
                    OutReported = false;
                }
            }
            default -> { }
        }
    }

    /**
     * OUT fires where the ball descends through the table plane off the table -- only before the
     * shot has landed; after a legal bounce, sailing off the end is the receiver's problem.
     */
    private void DetectOutOfBounds(BallState Before, BallState After) {
        if (OutReported || TableBounces > 0) return;

        boolean CrossedDown = Before.Pos().Y() > BallR && After.Pos().Y() <= BallR;
        if (!CrossedDown || After.Vel().Y() >= 0) return;

        Vec3 P = After.Pos();
        boolean OverTable = Math.abs(P.X()) <= TableWidth / 2 + BallR
                         && Math.abs(P.Z()) <= TableLength / 2 + BallR;
        if (!OverTable) {
            Record(EventType.OutOfBounds, P, After.Speed(), 0);
            OutReported = true;
        }
    }

    private void Record(EventType Type, Vec3 At, double Speed, int Side) {
        Event Last = Events.peekLast();
        if (Last != null && Last.Type() == Type && Time - Last.Time() < EventMergeWindow) return;

        Events.addLast(new Event(Type, At, Speed, Time, Side));
        while (Events.size() > MaxEvents) Events.removeFirst();
    }

    private void AddMark(Vec3 P) {
        BounceMarks.add(new Vec3(P.X(), 0.001, P.Z()));
        while (BounceMarks.size() > MaxMarks) BounceMarks.remove(0);
    }

    public BallState State()    { return State; }
    public BallState Previous() { return Previous; }

    /** Only play.ShotAssist replaces the state; SelfTest and predict never do. */
    public void SetState(BallState S) { State = S; }
    public double Time()        { return Time; }
    public double Apex()        { return Apex; }
    public int TableBounces()   { return TableBounces; }
    public int BounceSerial()   { return BounceSerial; }
    public int PaddleHits()     { return PaddleHits; }


    /** Null for both makes this a plain flight simulator. */
    public void SetPaddles(Paddle Player, Paddle Opponent) {
        this.Player = Player;
        this.Opponent = Opponent;
    }

    public List<Event> Events()    { return List.copyOf(Events); }
    public Event LastEvent()       { return Events.peekLast(); }
    public List<Vec3> BounceMarks() { return List.copyOf(BounceMarks); }

    public boolean OverTable() {
        Vec3 P = State.Pos();
        return Math.abs(P.X()) <= TableWidth / 2 && Math.abs(P.Z()) <= TableLength / 2;
    }

    /** A paddle-free flight of {@code seconds}, keeping one point every {@code stride} steps. */
    public static List<Vec3> Predict(BallState Start, double Seconds, int Stride) {
        List<Vec3> Path = new ArrayList<>();
        World W = new World();
        W.Launch(Start);

        int Steps = (int) Math.round(Seconds / Dt);
        for (int I = 0; I < Steps; I++) {
            if (I % Stride == 0) Path.add(W.State().Pos());
            W.Step();
            boolean RestingOnFloor = W.State().Pos().Y() < -TableHeight + BallR && W.State().Speed() < 0.5;
            if (RestingOnFloor) break;
        }
        return Path;
    }
}
