package physics;

import physics.Constants.Material;
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

    /**
     * The floor, 76 cm below the playing surface.
     *
     * 120 m across, which sounds absurd until you work out how far a table tennis ball
     * actually rolls. Once it is rolling, the only things slowing it are quadratic drag and
     * rolling resistance, and drag gives distance = ln(v0/v)/k with k = 0.5*rho*A*Cd/m ~
     * 0.114 /m -- so a ball that starts rolling at 5 m/s covers about 17 m before it is down
     * to walking pace, and something over 20 m before it stops. The old 40 m slab was
     * narrower than that, so a missed ball reached the rim and fell off the edge of the world.
     *
     * This is a bounds convenience, not a real object -- the floor you can SEE is 14 x 16 m
     * (Court.floor). Nothing here is load bearing except being wider than the ball can roll.
     */
    public static final Box FLOOR = Box.centered(
            0, -TABLE_HEIGHT - 0.5, 0,
            120, 1.0, 120);

    // ------------------------------------------------------------------ events

    public enum EventType {
        /** Landed on the playing surface. */          TABLE_BOUNCE,
        /** Clipped or was killed by the net. */       NET,
        /** Hit the floor. */                          FLOOR,
        /** Passed the plane of the table top outside the playing surface. */ OUT_OF_BOUNDS,
        /** Struck by a paddle. */                    PADDLE_HIT
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
                case PADDLE_HIT -> (side < 0 ? "player" : "opponent") + " hit";
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
     * Total table bounces since the World was created, never reset.
     *
     * {@code tableBounces} is a per-STROKE count -- it resets on every paddle contact so the
     * in/out rules can be applied to each shot in a rally rather than once per rally. That
     * makes it useless as a "has anything landed since I last looked?" flag for the renderer,
     * which is what this is for.
     */
    private int bounceSerial;

    /**
     * The two rackets, or null in a world that has none.
     *
     * Null is not laziness -- World.predict builds a private World to fly a trajectory
     * forward, and a prediction that gets intercepted by a paddle is not a prediction of
     * anything. A paddle-free world is the honest tool for asking "where would this ball go
     * if nothing touched it".
     */
    private Paddle player;
    private Paddle opponent;

    /** Count of paddle contacts, so a caller can tell when a stroke has happened. */
    private int paddleHits;

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
        paddleHits = 0;
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

    /** One surface the ball can hit, paired with what it is made of. */
    private record Surface(Collider shape, Material material, EventType event) {}

    /**
     * Every surface in play, in no particular order -- the order stopped mattering when
     * resolution became time-ordered.
     */
    private Surface[] surfaces() {
        if (player == null && opponent == null) {
            return new Surface[] {
                    new Surface(NET,   NET_MAT,   EventType.NET),
                    new Surface(TABLE, TABLE_MAT, EventType.TABLE_BOUNCE),
                    new Surface(FLOOR, FLOOR_MAT, EventType.FLOOR),
            };
        }
        List<Surface> all = new ArrayList<>(5);
        all.add(new Surface(NET,   NET_MAT,   EventType.NET));
        all.add(new Surface(TABLE, TABLE_MAT, EventType.TABLE_BOUNCE));
        all.add(new Surface(FLOOR, FLOOR_MAT, EventType.FLOOR));
        if (player != null)   all.add(new Surface(player.collider(),   RACKET_MAT, EventType.PADDLE_HIT));
        if (opponent != null) all.add(new Surface(opponent.collider(), RACKET_MAT, EventType.PADDLE_HIT));
        return all.toArray(new Surface[0]);
    }

    /**
     * Resolve contacts, earliest first, repeating until nothing more is touching.
     *
     * The loop matters for the net: a ball that clips the cord can be pushed down into the
     * table in the same step, and resolving only once would leave it embedded.
     *
     * Two things changed here when the paddle arrived, and both are corrections rather than
     * additions:
     *
     * 1. It resolves the EARLIEST contact, not the first one in a fixed list. With three
     *    static surfaces those were the same answer often enough to get away with. With a
     *    paddle that can be over the table they are not: a ball touching both in one step
     *    must bounce off whichever it reached first, or a smash into the paddle resolves as
     *    a table bounce.
     *
     * 2. After a SWEPT contact it flies the rest of the step. The old code left the ball
     *    parked at the contact point for the remaining (1-t)*DT, which at table speeds was
     *    invisible and at paddle speeds is not: a blade caught at t=0.1 would drop the ball
     *    90% of a step short of where it belongs.
     */
    private BallState resolveContacts(BallState from, BallState to) {
        BallState before = from, current = to;
        double stepLeft = DT;

        // Built ONCE, outside the pass loop. The rackets are kinematic and do not move while
        // a step is being resolved, so re-asking for them each pass rebuilt the list up to
        // eight times a step and handed out a fresh Blade snapshot every time -- against the
        // promise in Blade's own doc that the solver gets consistent answers from one pose.
        Surface[] all = surfaces();

        for (int pass = 0; pass < 8; pass++) {
            Surface hitSurface = null;
            Contacts.Contact earliest = null;

            for (Surface s : all) {
                Contacts.Contact c = Contacts.detect(before, current, s.shape());
                if (c != null && (earliest == null || c.toi() < earliest.toi())) {
                    earliest = c;
                    hitSurface = s;
                }
            }
            if (earliest == null) return current;

            // Bounce the state the ball is actually IN at the moment of impact.
            //
            // A swept contact happens part way through the step, but the flown state carries
            // the velocity from the END of it -- for a ball falling at 60 m/s that is a
            // measurably faster ball than the one that touched the table. Reflecting the
            // end-of-step velocity and then flying the remainder hands the ball free energy
            // every bounce, which is exactly what the energy check is there to catch.
            BallState atContact = current;
            if (earliest.swept()) {
                atContact = Integrator.step(before, stepLeft * earliest.toi());
            }

            Hit hit = Contacts.respond(atContact, hitSurface.shape(), earliest, hitSurface.material());
            record(hitSurface, hit);
            current = hit.state();

            // An end-of-step overlap has already used the whole step, so there is nothing to
            // fly. Keep looping against the same segment: a ball that clips the cord can be
            // pushed down into the table in the same step, and it still has to bounce off it.
            if (!earliest.swept()) continue;

            // A swept contact happened part way through. Fly the rest of the step from there,
            // and test the remainder as a fresh segment.
            double left = stepLeft * (1.0 - earliest.toi());
            if (left < 1e-9) continue;

            before = current;
            current = Integrator.step(current, left);
            stepLeft = left;
        }
        return current;
    }

    /** Log a contact, at the reporting threshold that surface deserves. */
    private void record(Surface s, Hit hit) {
        switch (s.event()) {
            case NET -> {
                if (hit.impactSpeed() > 0.05) {
                    record(EventType.NET, hit.point(), hit.impactSpeed(), 0);
                }
            }
            case TABLE_BOUNCE -> {
                if (!hit.resting() && hit.impactSpeed() > LOGGABLE_BOUNCE) {
                    tableBounces++;
                    bounceSerial++;
                    Vec3 p = hit.point();
                    record(EventType.TABLE_BOUNCE, p, hit.impactSpeed(), p.z() < 0 ? 1 : -1);
                    addMark(p);
                }
            }
            case FLOOR -> {
                if (!hit.resting() && hit.impactSpeed() > 0.4) {
                    record(EventType.FLOOR, hit.point(), hit.impactSpeed(), 0);
                }
            }
            case PADDLE_HIT -> {
                if (hit.impactSpeed() > 0.3) {
                    paddleHits++;
                    Vec3 p = hit.point();
                    record(EventType.PADDLE_HIT, p, hit.impactSpeed(), p.z() < 0 ? 1 : -1);

                    // A new stroke is a new shot, so the in/out rules start again. Without
                    // this, out-of-bounds fires at most once per RALLY and gives up entirely
                    // after the first bounce -- which means "the return sails long" is never
                    // detected, and in a rally that is most of the calls there are.
                    tableBounces = 0;
                    outReported = false;
                }
            }
            default -> { }
        }
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

    /**
     * Replace the live ball state.
     *
     * The one caller is the arcade shot assist (`play.ShotAssist`): right after a paddle
     * contact it swaps the raw impulse result for a trajectory the game can rally on. Nothing
     * in `physics/` touches this -- SelfTest and {@link #predict} never call it, so the
     * validated model is unchanged underneath.
     */
    public void setState(BallState s) { state = s; }
    public double time()        { return time; }
    public double apex()        { return apex; }
    public int tableBounces()   { return tableBounces; }
    public int bounceSerial()   { return bounceSerial; }
    public int paddleHits()     { return paddleHits; }

    public Paddle player()      { return player; }
    public Paddle opponent()    { return opponent; }

    /** Give this world its rackets. Passing null for both makes it a plain flight simulator. */
    public void setPaddles(Paddle player, Paddle opponent) {
        this.player = player;
        this.opponent = opponent;
    }

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
        World w = new World();       // deliberately paddle-free; see the field comment
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
