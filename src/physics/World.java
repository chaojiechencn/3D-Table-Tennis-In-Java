package physics;

import physics.Contacts.Box;
import physics.Contacts.Hit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static physics.Constants.*;

/**
 * The simulated world: the ball, the three things it can hit, and the log of what happened.
 *
 * Deliberately free of JavaFX. Nothing in this package imports the renderer, which is what
 * lets {@link SelfTest} run the exact same physics headlessly and check it against published
 * numbers. If the physics could only be observed by looking at it, "checking my simulation
 * against real numbers" would not be possible.
 *
 * World also never sees a frame time. It is stepped by a fixed DT, always.
 */
public final class World {

    // ------------------------------------------------------------------ geometry

    /** The playing surface. Top face sits exactly on y = 0, which is the physics origin. */
    public static final Box TABLE = Box.centered(
            0, -TABLE_THICK / 2, 0,
            TABLE_WIDTH, TABLE_THICK, TABLE_LENGTH);

    /** The net, straddling z = 0 and overhanging the table by 15.25 cm each side. */
    public static final Box NET = Box.centered(
            0, NET_HEIGHT / 2, 0,
            NET_WIDTH, NET_HEIGHT, NET_THICK);

    /** The floor, 76 cm below the playing surface. Big enough that a ball never runs off it. */
    public static final Box FLOOR = Box.centered(
            0, -TABLE_HEIGHT - 0.5, 0,
            40, 1.0, 40);

    // ------------------------------------------------------------------ events

    public enum EventType {
        /** Landed on the playing surface. */          TABLE_BOUNCE,
        /** Clipped or was killed by the net. */       NET,
        /** Hit the floor. */                          FLOOR,
        /** Passed the plane of the table top outside the playing surface. */ OUT_OF_BOUNDS
    }

    /**
     * @param side +1 if it happened on the far half (z &lt; 0), -1 on the near half, 0 for
     *             events with no meaningful side.
     */
    public record Event(EventType type, Vec3 at, double speed, double time, int side) {
        public String label() {
            return switch (type) {
                case TABLE_BOUNCE -> (side < 0 ? "near" : "far") + " court bounce";
                case NET -> "net";
                case FLOOR -> "floor";
                case OUT_OF_BOUNDS -> "out";
            };
        }
    }

    // ------------------------------------------------------------------ state

    private BallState state;
    private BallState previous;

    private double time;
    private double apex;
    private int tableBounces;
    private boolean outReported;

    /**
     * Bounces slower than this are real but not worth reporting. A ball settling on the table
     * genuinely bounces dozens of times as it dies, and logging all of them buries the one
     * bounce the viewer cares about under a wall of noise.
     */
    private static final double LOGGABLE_BOUNCE = 0.35;

    /** Bounded so a demo left running overnight cannot grow the heap. */
    private final Deque<Event> events = new ArrayDeque<>();
    private static final int MAX_EVENTS = 12;

    private final List<Vec3> bounceMarks = new ArrayList<>();
    private static final int MAX_MARKS = 24;

    public World() {
        reset(BallState.at(new Vec3(0, 0.30, 1.20), Vec3.ZERO, Vec3.ZERO));
    }

    /** Put the ball somewhere and clear the history. */
    public void reset(BallState s) {
        state = s;
        previous = s;
        time = 0;
        apex = s.pos().y();
        tableBounces = 0;
        outReported = false;
        events.clear();
        bounceMarks.clear();
    }

    // ------------------------------------------------------------------ stepping

    /**
     * Advance one fixed step: flight, then contacts.
     *
     * Flight and contact are separated because an impulse is a discontinuity. Feeding a
     * bounce through RK4 would have the integrator sample the derivative on both sides of
     * the table at once and average them, which produces a ball that sinks into the surface
     * and leaves at the wrong angle.
     */
    public void step() {
        previous = state;
        BallState flown = Integrator.step(state, DT);

        state = resolveContacts(previous, flown);
        time += DT;

        if (state.pos().y() > apex) apex = state.pos().y();
        detectOutOfBounds(previous, state);

        if (!state.isFinite()) {
            // Should be unreachable. If a bad shot ever does produce a NaN, the demo must
            // recover rather than freeze with an invisible ball.
            reset(BallState.at(new Vec3(0, 0.30, 1.20), Vec3.ZERO, Vec3.ZERO));
        }
    }

    /**
     * Resolve against all three surfaces, repeating until nothing more is touching.
     *
     * The loop matters for the net: a ball that clips the cord can be pushed down into the
     * table in the same step, and resolving only once would leave it embedded.
     */
    private BallState resolveContacts(BallState from, BallState to) {
        BallState current = to;

        for (int pass = 0; pass < 4; pass++) {
            Hit net = Contacts.resolve(from, current, NET, NET_MAT);
            if (net != null) {
                current = net.state();
                if (net.impactSpeed() > 0.05) {
                    record(EventType.NET, net.point(), net.impactSpeed(), 0);
                }
                continue;
            }

            Hit table = Contacts.resolve(from, current, TABLE, TABLE_MAT);
            if (table != null) {
                current = table.state();
                if (!table.resting() && table.impactSpeed() > LOGGABLE_BOUNCE) {
                    tableBounces++;
                    Vec3 p = table.point();
                    record(EventType.TABLE_BOUNCE, p, table.impactSpeed(), p.z() < 0 ? 1 : -1);
                    addMark(p);
                }
                continue;
            }

            Hit floor = Contacts.resolve(from, current, FLOOR, FLOOR_MAT);
            if (floor != null) {
                current = floor.state();
                if (!floor.resting() && floor.impactSpeed() > 0.4) {
                    record(EventType.FLOOR, floor.point(), floor.impactSpeed(), 0);
                }
                continue;
            }
            break;
        }
        return current;
    }

    /**
     * Fire an OUT event the moment the ball descends past the height of the table top
     * without being over it. This is the "or goes out of bounds" half of the contract, and
     * catching it at the plane rather than waiting for the floor puts the marker where the
     * ball actually missed by, which is the useful information.
     *
     * Only meaningful BEFORE the ball has landed. Once a shot has legally bounced it is the
     * other player's problem, and the ball sailing off the end afterwards is not a miss --
     * an earlier version reported every good shot as "out" one bounce later.
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
        // Contacts can retrigger across consecutive steps while a ball settles; collapse
        // those so the log reads as one bounce rather than nine.
        if (last != null && last.type() == type && time - last.time() < 0.12) return;

        events.addLast(new Event(type, at, speed, time, side));
        while (events.size() > MAX_EVENTS) events.removeFirst();
    }

    private void addMark(Vec3 p) {
        bounceMarks.add(new Vec3(p.x(), 0.001, p.z()));
        while (bounceMarks.size() > MAX_MARKS) bounceMarks.remove(0);
    }

    // ------------------------------------------------------------------ launching

    /** Launch the ball, clearing the previous rally. */
    public void launch(BallState s) {
        reset(s);
    }

    // ------------------------------------------------------------------ queries

    public BallState state()    { return state; }
    public BallState previous() { return previous; }
    public double time()        { return time; }
    public double apex()        { return apex; }
    public int tableBounces()   { return tableBounces; }

    public List<Event> events()    { return List.copyOf(events); }
    public Event lastEvent()       { return events.peekLast(); }
    public List<Vec3> bounceMarks() { return List.copyOf(bounceMarks); }

    /** True while the ball is over the playing surface, at any height. */
    public boolean overTable() {
        Vec3 p = state.pos();
        return Math.abs(p.x()) <= TABLE_WIDTH / 2 && Math.abs(p.z()) <= TABLE_LENGTH / 2;
    }

    // ------------------------------------------------------------------ offline prediction

    /**
     * Run a shot forward without touching the live world, and return the path.
     *
     * Used two ways: the demo draws a zero-spin ghost of the current shot so the Magnus
     * curve is visible as a difference rather than something you have to take on faith, and
     * from September the AI will use exactly this to work out where to stand. Building it
     * now, headless and side-effect free, is what makes that reuse possible.
     *
     * @param seconds how far ahead to run
     * @param stride  keep one point every {@code stride} steps, to keep the returned path small
     */
    public static List<Vec3> predict(BallState start, double seconds, int stride) {
        List<Vec3> path = new ArrayList<>();
        World w = new World();
        w.launch(start);

        int steps = (int) Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            if (i % stride == 0) path.add(w.state().pos());
            w.step();
            // Stop once it is done doing anything interesting.
            if (w.state().pos().y() < -TABLE_HEIGHT + BALL_R && w.state().speed() < 0.5) break;
        }
        return path;
    }
}
