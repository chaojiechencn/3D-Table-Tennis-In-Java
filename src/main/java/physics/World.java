package physics;

import physics.Constants.Material;
import physics.Contacts.Box;
import physics.Contacts.Hit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static physics.Constants.*;

/** The ball, everything it can hit, and a log of what happened. Stepped only by a fixed DT. */
public final class World {

    /** Top face on y = 0, the physics origin. */
    public static final Box TABLE = Box.centered(
            0, -TABLE_THICK / 2, 0,
            TABLE_WIDTH, TABLE_THICK, TABLE_LENGTH);

    public static final Box NET = Box.centered(
            0, NET_HEIGHT / 2, 0,
            NET_WIDTH, NET_HEIGHT, NET_THICK);

    /** 120 m across because a missed ball rolls 17-20+ m; the visible floor is 14 x 16 m. */
    public static final Box FLOOR = Box.centered(
            0, -TABLE_HEIGHT - 0.5, 0,
            120, 1.0, 120);

    public enum EventType { TABLE_BOUNCE, NET, FLOOR, OUT_OF_BOUNDS, PADDLE_HIT }

    /** {@code side} is +1 on the far half (z &lt; 0), -1 on the near half, 0 for none. */
    public record Event(EventType type, Vec3 at, double speed, double time, int side) {}

    // Slower contacts are real but not worth logging: a dying ball bounces dozens of times.
    private static final double LOGGABLE_NET = 0.05;
    private static final double LOGGABLE_BOUNCE = 0.35;
    private static final double LOGGABLE_FLOOR = 0.4;
    private static final double LOGGABLE_PADDLE = 0.3;

    /** Same-type events closer than this are one contact retriggering while the ball settles. */
    private static final double EVENT_MERGE_WINDOW = 0.12;
    private static final int MAX_EVENTS = 12;
    private static final int MAX_MARKS = 24;
    private static final int MAX_CONTACT_PASSES = 8;
    private static final BallState START = BallState.at(new Vec3(0, 0.30, 1.20), Vec3.ZERO, Vec3.ZERO);

    private BallState state;
    private BallState previous;
    private double time;
    private double apex;
    private int tableBounces;       // per stroke: reset by each paddle contact
    private boolean outReported;
    private int bounceSerial;       // never reset: "has anything landed since I looked?"
    private int paddleHits;

    /** Null in a paddle-free world, which is what {@link #predict} needs. */
    private Paddle player;
    private Paddle opponent;

    private final Deque<Event> events = new ArrayDeque<>();
    private final List<Vec3> bounceMarks = new ArrayList<>();

    public World() {
        reset(START);
    }

    public void reset(BallState s) {
        state = s;
        previous = s;
        time = 0;
        apex = s.pos().y();
        tableBounces = 0;
        outReported = false;
        paddleHits = 0;
        events.clear();
        bounceMarks.clear();
    }

    public void launch(BallState s) {
        reset(s);
    }

    /** Flight, then contacts: an impulse is a discontinuity RK4 must never straddle. */
    public void step() {
        previous = state;
        state = resolveContacts(previous, Integrator.step(state, DT));
        time += DT;

        if (state.pos().y() > apex) apex = state.pos().y();
        detectOutOfBounds(previous, state);

        if (!state.isFinite()) reset(START);   // unreachable, but never freeze on a NaN
    }

    private record Surface(Collider shape, Material material, EventType event) {}

    private record SurfaceContact(Surface surface, Contacts.Contact contact) {}

    private List<Surface> surfaces() {
        List<Surface> all = new ArrayList<>(5);
        all.add(new Surface(NET,   NET_MAT,   EventType.NET));
        all.add(new Surface(TABLE, TABLE_MAT, EventType.TABLE_BOUNCE));
        all.add(new Surface(FLOOR, FLOOR_MAT, EventType.FLOOR));
        if (player != null)   all.add(new Surface(player.collider(),   RACKET_MAT, EventType.PADDLE_HIT));
        if (opponent != null) all.add(new Surface(opponent.collider(), RACKET_MAT, EventType.PADDLE_HIT));
        return all;
    }

    /**
     * Resolve the EARLIEST contact, repeatedly: a ball that clips the cord can be pushed into the
     * table in the same step. After a swept contact the rest of the step is flown, not skipped.
     */
    private BallState resolveContacts(BallState from, BallState to) {
        BallState before = from, current = to;
        double stepLeft = DT;
        List<Surface> all = surfaces();   // one snapshot of each blade per step

        for (int pass = 0; pass < MAX_CONTACT_PASSES; pass++) {
            SurfaceContact hit = earliestContact(all, before, current);
            if (hit == null) return current;
            Contacts.Contact c = hit.contact();

            // Bounce the state AT impact; the end-of-step velocity would add energy.
            BallState atContact = c.swept() ? Integrator.step(before, stepLeft * c.toi()) : current;
            Hit result = Contacts.respond(atContact, hit.surface().shape(), c, hit.surface().material());
            record(hit.surface(), result);
            current = result.state();

            if (!c.swept()) continue;
            double left = stepLeft * (1.0 - c.toi());
            if (left < 1e-9) continue;

            before = current;
            current = Integrator.step(current, left);
            stepLeft = left;
        }
        return current;
    }

    private static SurfaceContact earliestContact(List<Surface> all, BallState before, BallState after) {
        SurfaceContact earliest = null;
        for (Surface s : all) {
            Contacts.Contact c = Contacts.detect(before, after, s.shape());
            if (c != null && (earliest == null || c.toi() < earliest.contact().toi())) {
                earliest = new SurfaceContact(s, c);
            }
        }
        return earliest;
    }

    private void record(Surface s, Hit hit) {
        Vec3 p = hit.point();
        int half = p.z() < 0 ? 1 : -1;
        switch (s.event()) {
            case NET -> {
                if (hit.impactSpeed() > LOGGABLE_NET) record(EventType.NET, p, hit.impactSpeed(), 0);
            }
            case TABLE_BOUNCE -> {
                if (!hit.resting() && hit.impactSpeed() > LOGGABLE_BOUNCE) {
                    tableBounces++;
                    bounceSerial++;
                    record(EventType.TABLE_BOUNCE, p, hit.impactSpeed(), half);
                    addMark(p);
                }
            }
            case FLOOR -> {
                if (!hit.resting() && hit.impactSpeed() > LOGGABLE_FLOOR) {
                    record(EventType.FLOOR, p, hit.impactSpeed(), 0);
                }
            }
            case PADDLE_HIT -> {
                if (hit.impactSpeed() > LOGGABLE_PADDLE) {
                    paddleHits++;
                    record(EventType.PADDLE_HIT, p, hit.impactSpeed(), half);
                    // A new stroke is a new shot: the in/out rules start again.
                    tableBounces = 0;
                    outReported = false;
                }
            }
            default -> { }
        }
    }

    /**
     * OUT fires where the ball descends through the table plane off the table -- only before the
     * shot has landed; after a legal bounce, sailing off the end is the receiver's problem.
     */
    private void detectOutOfBounds(BallState before, BallState after) {
        if (outReported || tableBounces > 0) return;

        boolean crossedDown = before.pos().y() > BALL_R && after.pos().y() <= BALL_R;
        if (!crossedDown || after.vel().y() >= 0) return;

        Vec3 p = after.pos();
        boolean overTable = Math.abs(p.x()) <= TABLE_WIDTH / 2 + BALL_R
                         && Math.abs(p.z()) <= TABLE_LENGTH / 2 + BALL_R;
        if (!overTable) {
            record(EventType.OUT_OF_BOUNDS, p, after.speed(), 0);
            outReported = true;
        }
    }

    private void record(EventType type, Vec3 at, double speed, int side) {
        Event last = events.peekLast();
        if (last != null && last.type() == type && time - last.time() < EVENT_MERGE_WINDOW) return;

        events.addLast(new Event(type, at, speed, time, side));
        while (events.size() > MAX_EVENTS) events.removeFirst();
    }

    private void addMark(Vec3 p) {
        bounceMarks.add(new Vec3(p.x(), 0.001, p.z()));
        while (bounceMarks.size() > MAX_MARKS) bounceMarks.remove(0);
    }

    public BallState state()    { return state; }
    public BallState previous() { return previous; }

    /** Only play.ShotAssist replaces the state; SelfTest and predict never do. */
    public void setState(BallState s) { state = s; }
    public double time()        { return time; }
    public double apex()        { return apex; }
    public int tableBounces()   { return tableBounces; }
    public int bounceSerial()   { return bounceSerial; }
    public int paddleHits()     { return paddleHits; }


    /** Null for both makes this a plain flight simulator. */
    public void setPaddles(Paddle player, Paddle opponent) {
        this.player = player;
        this.opponent = opponent;
    }

    public List<Event> events()    { return List.copyOf(events); }
    public Event lastEvent()       { return events.peekLast(); }
    public List<Vec3> bounceMarks() { return List.copyOf(bounceMarks); }

    public boolean overTable() {
        Vec3 p = state.pos();
        return Math.abs(p.x()) <= TABLE_WIDTH / 2 && Math.abs(p.z()) <= TABLE_LENGTH / 2;
    }

    /** A paddle-free flight of {@code seconds}, keeping one point every {@code stride} steps. */
    public static List<Vec3> predict(BallState start, double seconds, int stride) {
        List<Vec3> path = new ArrayList<>();
        World w = new World();
        w.launch(start);

        int steps = (int) Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            if (i % stride == 0) path.add(w.state().pos());
            w.step();
            boolean restingOnFloor = w.state().pos().y() < -TABLE_HEIGHT + BALL_R && w.state().speed() < 0.5;
            if (restingOnFloor) break;
        }
        return path;
    }
}
