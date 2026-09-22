package play;

import physics.Aim;
import physics.BallState;
import physics.Integrator;
import physics.Paddle;
import physics.Vec3;

import static physics.Constants.BALL_R;
import static physics.Constants.BLADE_R;
import static physics.Constants.DT;
import static physics.Constants.NET_HEIGHT;
import static physics.Constants.TABLE_LENGTH;
import static physics.Constants.TABLE_WIDTH;

/**
 * The assisted, arcade shot model: what the ball does after a racket hits it.
 *
 * The impulse solver in physics/ stays exact and still runs on every contact, but an exact
 * bounce off a moving blade is not a table tennis shot -- see docs/DESIGN.md for the 75-velocity
 * sweep that motivated this. So this sits on top, in play/, and turns the contact into a shot:
 * read the racket's motion as INTENT, build a TARGET inside the opponent's court by construction,
 * turn swing speed into strength on a saturating curve, ask {@link Aim} for the launch that lands
 * on the target, blend in a little of the physical reflection for feel, clamp, then VALIDATE by
 * flying the result and re-solving if it does not clear the net and land in. Nothing here mutates
 * a solved trajectory without re-validating it. Both rackets run through this.
 *
 * What this does NOT do, deliberately: write {@code ball += racket}, reflect the ball off the
 * blade as a rigid body, or let the ball inherit an arbitrary racket movement's direction. Flight,
 * gravity, drag, Magnus and every bounce after the shot are still the real simulation -- only the
 * launch is authored.
 */
public final class ShotAssist {

    // Every number the shot model uses lives in ShotTuning; nothing below is hardcoded.
    private final ShotTuning t;

    public ShotAssist()                  { this(ShotTuning.defaults()); }
    public ShotAssist(ShotTuning tuning) { this.t = tuning; }
    public ShotTuning tuning()           { return t; }

    private static final double HALF_WIDTH = TABLE_WIDTH / 2, HALF_LENGTH = TABLE_LENGTH / 2;

    // ================================================================== debug

    /** Everything the last shot was built from, for the on-screen overlay. */
    public record Debug(Vec3 contact, Vec3 racketVel, Vec3 incomingVel, Vec3 reflectDir,
                        Vec3 intendDir, Vec3 finalDir, Vec3 target, Vec3 landing,
                        double speed, Vec3 spin, int passes, boolean legal) {}

    private Debug debug = new Debug(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0, -1),
            new Vec3(0, 0, -1), new Vec3(0, 0, -1), Vec3.ZERO, Vec3.ZERO, 0, Vec3.ZERO, 0, true);
    public Debug debug() { return debug; }

    /** The legal target box, for the overlay to outline. x = half width, z = near/far depth. */
    public double targetHalfWidth() { return t.targetHalfWidthFrac * TABLE_WIDTH / 2; }
    public double targetNearDepth() { return t.targetDepthMinFrac * TABLE_LENGTH / 2; }
    public double targetFarDepth()  { return t.targetDepthMaxFrac * TABLE_LENGTH / 2; }

    // ================================================================== the shot

    /**
     * @param incoming  the ball as it was just before the contact
     * @param physical  the ball straight out of the impulse solver -- the raw reflection
     * @param racket    the racket that hit it
     * @param playerHit true for the player's racket (ball should travel toward -Z), false for
     *                  the opponent (toward +Z)
     * @return the ball state to actually put back in play
     */
    public BallState assist(BallState incoming, BallState physical, Paddle racket, boolean playerHit) {
        double toOpp = playerHit ? -1.0 : 1.0;
        Vec3 contact = physical.pos();
        Vec3 reflect = physical.vel();

        // Intent, contact quality, target, strength and spin: pure reads of the contact.
        Intent in = readIntent(contact, racket, toOpp);
        double quality = quality(incoming, in.offX(), in.offY());
        double assist = playerHit ? t.assistFloor + (1 - t.assistFloor) * quality : 1.0;
        Target target = readTarget(in, toOpp);
        double wantSpeed = readSpeed(in.swingAmount(), incoming);
        Spin spin = readSpin(in.brush(), in.swipeX());

        Candidate best = search(contact, reflect, target, wantSpeed, spin, toOpp);

        // Nothing legal: rather than let a wild trajectory through, sweep the whole envelope --
        // unless a player's mishit or over-ambitious swing is what put it there.
        boolean mayRescue = !playerHit
                || (quality >= t.rescueQualityFloor && in.swingAmount() <= t.rescueEffortCeiling);
        if (best.cost() > 0 && mayRescue) best = rescue(contact, target.want().x(), spin, toOpp, best);

        // The authored shot is what a properly hit ball gets; the raw bounce is what a shank
        // gets, blended by `assist` so a mishit keeps the most real physics and never carries
        // authored spin it did nothing to earn.
        Vec3 finalVel  = Vec3.lerp(reflect(reflect), best.vel(), assist);
        Vec3 finalSpin = Vec3.lerp(physical.spin(), spinFor(best.vel(), best.spin()), assist);

        debug = new Debug(contact, racket.vel(), incoming.vel(), safeDir(reflect),
                          safeDir(new Vec3(best.target().x() - contact.x(), 0,
                                           best.target().z() - contact.z())),
                          safeDir(finalVel), best.target(), best.flight().landing(),
                          finalVel.length(), finalSpin, best.passes(), best.cost() == 0);

        return new BallState(physical.pos(), finalVel, finalSpin, physical.orient());
    }

    /** One launch that was solved, constrained and flown, and how illegal it came out. */
    private record Candidate(Vec3 vel, Vec3 target, Flight flight, double cost, int passes, Spin spin) {}

    /** {@link Debug#passes} for a shot the rescue produced. */
    private int rescuedPasses() { return t.maxCorrectionPasses + 1; }

    /**
     * The main search. Every candidate is a (target, speed) pair: solved by Aim, blended with a
     * little reflection, constrained, then FLOWN and graded -- nothing is mutated after its last
     * check. Each correction pass pulls the target toward the safe middle and slows the pace;
     * the speed ladder inside a pass exists because depth and speed constrain each other. The
     * lowest score wins, and illegality outweighs any preference, so an illegal candidate only
     * wins when nothing legal was found.
     */
    private Candidate search(Vec3 contact, Vec3 reflect, Target target, double wantSpeed, Spin spin,
                             double toOpp) {
        Candidate best = null;
        double bestScore = Double.MAX_VALUE;
        double floor = Math.max(t.minSearchSpeed, wantSpeed * t.searchSpeedFloorFrac);

        for (int pass = 0; pass <= t.maxCorrectionPasses; pass++) {
            double give = Math.min(1, pass * t.targetAssist);
            Vec3 aimAt = Vec3.lerp(target.want(), target.safe(), give);
            double pace = wantSpeed * (1 - t.speedBackoffPerPass * pass);

            for (int k = 0; k < t.speedCandidates; k++) {
                double speed = clamp(pace * speedFactor(k), Math.min(floor, t.maxShotSpeed),
                                     t.maxShotSpeed);
                Aim.Solution sol = Aim.atTarget(contact, aimAt, speed, spin.top(), spin.side());
                // Capped at the candidate's OWN speed, not the band's top -- a shot that only
                // works slowly must be allowed to stay slow.
                Vec3 vel = constrain(Vec3.lerp(sol.state().vel(), reflect(reflect), t.physicalBlend),
                                     toOpp, speed);
                Candidate c = evaluate(contact, aimAt, vel, spin, toOpp, pass);

                double score = c.cost() * ILLEGALITY_WEIGHT
                             + Math.abs(speed - wantSpeed) * t.speedPreference
                             + pass * t.passPenalty;
                if (score < bestScore) { bestScore = score; best = c; }

                // Legal: stop. The ladder tries the asked-for pace first then alternates outward,
                // so the first legal candidate is already the closest -- and each costs a solve.
                if (c.cost() == 0) return best;
            }
        }
        return best;
    }

    /**
     * The rescue: every sensible depth down the middle, from a soft lift up to full pace, keeping
     * as much of the player's aim as still works. The player's own spin is tried first and plain
     * topspin only as a last resort -- a chop that has to be rescued should come back a chop.
     * Some contacts have no fast answer at all, and the honest shot is a slow one.
     */
    private Candidate rescue(Vec3 contact, double wantX, Spin spin, double toOpp, Candidate best) {
        Spin[] spins = {spin, new Spin(t.baseTopspin, 0)};
        for (double aimFrac : t.rescueAimFracs) {
            for (Spin sp : spins) {
                for (double depth : t.rescueDepthFracs) {
                    Vec3 aimAt = new Vec3(wantX * aimFrac, 0, toOpp * depth * HALF_LENGTH);
                    for (int k = 0; k < t.rescueSpeedSteps; k++) {
                        double speed = t.rescueMinSpeed + (t.maxShotSpeed - t.rescueMinSpeed)
                                * k / (double) (t.rescueSpeedSteps - 1);
                        Aim.Solution sol = Aim.atTarget(contact, aimAt, speed, sp.top(), sp.side());
                        Vec3 vel = constrain(sol.state().vel(), toOpp, speed);
                        Candidate c = evaluate(contact, aimAt, vel, sp, toOpp, rescuedPasses());
                        if (c.cost() < best.cost()) best = c;
                        if (c.cost() == 0) return best;
                    }
                }
            }
        }
        return best;
    }

    private Candidate evaluate(Vec3 contact, Vec3 target, Vec3 vel, Spin spin, double toOpp, int passes) {
        Flight f = fly(contact, vel, spinFor(vel, spin), toOpp);
        return new Candidate(vel, target, f, illegality(f, toOpp), passes, spin);
    }

    /** The spin a launch carries: topspin and sidespin about its own horizontal heading. */
    private static Vec3 spinFor(Vec3 vel, Spin spin) {
        return Aim.spin(new Vec3(vel.x(), 0, vel.z()), spin.top(), spin.side());
    }

    // ================================================================== intent

    /** The racket's motion and contact point, split into the things they can mean. */
    private record Intent(double drive, double swipeX, double lift, double brush,
                          double swingAmount, double offX, double offY, double faceX) {}

    private Intent readIntent(Vec3 contact, Paddle racket, double toOpp) {
        Vec3 swing = racket.vel();

        // For the player, x and y are purely the cursor (depth reach is all in z), so they are
        // deliberate input: drive toward the opponent, swipeX to the player's right, lift up.
        double drive  = swing.z() * toOpp;
        double swipeX = swing.x();
        double lift   = swing.y();

        // The BRUSH -- how much the blade rolls over the ball rather than pushing through it --
        // authors spin, from two places: `lift`, the true vertical one (live only while the
        // brush modifier is held, since the blade is otherwise pinned to PlayerReach.HIT_Y), and
        // `drive`, the brush the player always has. See docs/DESIGN.md for the gain derivation.
        double brush = lift + drive * t.driveBrush;

        // Strength comes from the FORWARD drive, not the blade's total speed -- a still blade
        // dinks, a blade driven through the ball hits, with quick early response then
        // diminishing returns so swinging harder always does a little more and never a lot more.
        double effort = Math.max(0, drive) + t.lateralEffort * Math.hypot(swipeX, lift);
        double swingAmount = clamp(Math.pow(
                clamp(effort / t.maxSwingSpeed, 0, 1), t.swingCurve), 0, 1) * t.swingInfluence;

        // Where on the blade it was struck, in the face's own plane, as -1..1 of the radius.
        Vec3 off = contact.minus(racket.pos());
        Vec3 inPlane = off.minus(racket.normal().scale(off.dot(racket.normal())));
        double offX = clamp(inPlane.x() / BLADE_R, -1, 1);
        double offY = clamp(inPlane.y() / BLADE_R, -1, 1);

        // Which way the racket face is pointing, sideways, as a fraction.
        double faceX = clamp(racket.normal().x() * -toOpp, -1, 1);

        return new Intent(drive, swipeX, lift, brush, swingAmount, offX, offY, faceX);
    }

    /**
     * How cleanly this ball was struck, 1 in the middle of the blade and 0 at the rim, shrinking
     * as the ball arrives faster. Measured IN THE FACE'S OWN PLANE (offX, offY), so being
     * early/late counts the same as being wide.
     */
    private double quality(BallState incoming, double offX, double offY) {
        double offR = Math.min(1, Math.hypot(offX, offY));
        double paceFrac = clamp((incoming.speed() - t.qualityPaceFrom) / t.qualityPaceSpan, 0, 1);
        double core = Math.max(t.qualityCoreMin, t.qualityCore - t.qualityPaceLoss * paceFrac);
        double rim  = core + t.qualityFalloff;
        return clamp((rim - offR) / (rim - core), 0, 1);
    }

    /** Where the shot is aimed, inside the opponent's court by construction. */
    private record Target(Vec3 want, Vec3 safe) {}

    private Target readTarget(Intent in, double toOpp) {
        double aim = in.swipeX() * t.aimInfluence
                   + in.faceX() * t.faceInfluence
                   + in.offX() * t.contactPointInfluence;
        double wantX = clamp(aim, -1, 1) * targetHalfWidth();

        double depthFrac = t.targetDepthMinFrac
                + (t.targetDepthMaxFrac - t.targetDepthMinFrac)
                  * clamp(t.baseDepthFrac + in.drive() * t.depthInfluence - in.brush() * t.arcInfluence
                               - in.offY() * t.contactPointInfluence * t.contactDepthShare, 0, 1);
        double wantZ = toOpp * clamp(depthFrac, t.targetDepthMinFrac, t.targetDepthMaxFrac) * HALF_LENGTH;

        return new Target(new Vec3(wantX, 0, wantZ),
                          new Vec3(0, 0, toOpp * t.safeDepthFrac * HALF_LENGTH));
    }

    /** Swing maps onto a fixed band; the incoming ball nudges it slightly but is never ADDED to
     *  it, which is what stops a rally from compounding into a rocket. */
    private double readSpeed(double swingAmount, BallState incoming) {
        double wantSpeed = t.minShotSpeed + (t.maxShotSpeed - t.minShotSpeed) * swingAmount;
        wantSpeed += clamp((incoming.speed() - t.incomingPaceNeutral) * t.incomingPaceGain,
                           -t.incomingPaceNudge, t.incomingPaceNudge);
        return clamp(wantSpeed, t.minShotSpeed, t.maxShotSpeed);
    }

    /** Topspin from the brush, sidespin from the swipe. */
    private record Spin(double top, double side) {}

    private Spin readSpin(double brush, double swipeX) {
        double topRevs  = clamp((t.baseTopspin + brush * t.topspinPerLift) * t.spinInfluence,
                                -t.maxSpin, t.maxSpin);
        double sideRevs = clamp(swipeX * t.sidespinPerSwipe * t.spinInfluence,
                                -t.maxSpin, t.maxSpin);
        return new Spin(topRevs, sideRevs);
    }

    /**
     * The k-th speed to try, as a multiple of the pace the swing asked for: 1.0 first, then
     * alternately slower and faster. Trying the asked-for pace first means an already-legal
     * shot costs a single solve, and the spread only comes into play when the target needs it.
     */
    private double speedFactor(int k) {
        if (k == 0) return 1.0;
        int step = (k + 1) / 2;
        double d = t.speedSpread * step / Math.max(1, t.speedCandidates / 2);
        return (k % 2 == 1) ? 1.0 - d : 1.0 + d;
    }

    // ================================================================== constraints

    /** The physical reflection, capped before it is allowed anywhere near the shot. */
    private Vec3 reflect(Vec3 raw) {
        double sp = raw.length();
        return sp > t.reflectionCap ? raw.scale(t.reflectionCap / sp) : raw;
    }

    /**
     * Pull a velocity into the playable envelope: a guaranteed forward component, a horizontal
     * cone AND an absolute lateral cap, an elevation band, and a top speed.
     *
     * @param cap the top speed this candidate is allowed -- its own. The rescue passes speeds
     *            below {@code minForwardVelocity}, because forcing a minimum pace onto a shot
     *            that only works slowly would undo the search that just found it.
     */
    private Vec3 constrain(Vec3 v, double toOpp, double cap) {
        double fwd = Math.max(Math.min(t.minForwardVelocity, cap), v.z() * toOpp);

        double coneLimit = Math.tan(Math.toRadians(t.maxHorizontalDeviationDeg)) * fwd;
        double vx = clamp(v.x(), -Math.min(coneLimit, t.maxLateralVelocity),
                                  Math.min(coneLimit, t.maxLateralVelocity));

        double horiz = Math.hypot(vx, fwd);
        double up = clamp(v.y(),
                Math.tan(Math.toRadians(t.minVerticalLaunchAngleDeg)) * horiz,
                Math.tan(Math.toRadians(t.maxVerticalLaunchAngleDeg)) * horiz);

        Vec3 out = new Vec3(vx, up, toOpp * fwd);
        double sp = out.length();
        return sp > cap ? out.scale(cap / sp) : out;
    }

    // ================================================================== validation

    /** Where a launched shot goes: its height at the net plane, and where it first comes back
     *  down to the table plane. */
    private record Flight(double netHeight, Vec3 landing) {}

    /**
     * Step size for the validation flights, seconds -- deliberately COARSER than the game's DT,
     * since a search that gives ground can fly dozens of these on the one frame a contact lands
     * on. TUNED: 1/120 s, four times the game's step; RK4 error is O(h^4), so that stays two
     * orders below the 5 cm landing margin it feeds. Do not take it coarser without re-checking
     * that arithmetic -- see docs/DESIGN.md.
     */
    private static final double VALIDATE_DT = 1.0 / 120;

    /** Long enough for any shot to come down; a flight still up after this has no landing. */
    private static final double MAX_FLIGHT_TIME = 3.0;

    // Illegality is measured in metres of violation, weighted so the parts compare: missing the
    // net outright dominates, a shortfall at the cord counts double a miss at the lines, and any
    // illegality outweighs the search's speed and pass preferences by two orders of magnitude.
    private static final double NEVER_CROSSED_COST = 10;
    private static final double NET_SHORTFALL_WEIGHT = 20;
    private static final double LANDING_MISS_WEIGHT = 10;
    private static final double ILLEGALITY_WEIGHT = 100;

    /**
     * How illegal a flight is: 0 means it clears the net and lands inside the opponent's half.
     * Anything else is the size of the violation, so a correction pass can keep the least-bad
     * candidate if none is perfect.
     */
    private double illegality(Flight f, double toOpp) {
        double cost = 0;

        double needed = NET_HEIGHT + BALL_R + t.netClearance;
        if (Double.isNaN(f.netHeight())) cost += NEVER_CROSSED_COST;
        else if (f.netHeight() < needed) cost += (needed - f.netHeight()) * NET_SHORTFALL_WEIGHT;

        Vec3 L = f.landing();
        double depth = L.z() * toOpp;                                      // + is into their half
        if (depth < t.landingMargin) cost += (t.landingMargin - depth) * LANDING_MISS_WEIGHT;
        double maxDepth = HALF_LENGTH - t.landingMargin;
        if (depth > maxDepth) cost += (depth - maxDepth) * LANDING_MISS_WEIGHT;

        double side = Math.abs(L.x()) - (HALF_WIDTH - t.landingMargin);
        if (side > 0) cost += side * LANDING_MISS_WEIGHT;

        return cost;
    }

    /**
     * Free flight of a launch, to the first descending crossing of the table plane.
     *
     * Deliberately contact-free -- the same thing Aim does internally when it solves a shot.
     * Asking "where would this land" through a World with a table in it is circular: the ball
     * bounces and the answer becomes "wherever it stopped". This measures the shot, not the
     * rally after it.
     */
    private static Flight fly(Vec3 from, Vec3 vel, Vec3 spin, double toOpp) {
        BallState s = BallState.at(from, vel, spin);
        double netHeight = Double.NaN;
        double prevZ = from.z();

        for (int i = 0; i < (int) (MAX_FLIGHT_TIME / VALIDATE_DT); i++) {
            BallState next = Integrator.step(s, VALIDATE_DT);
            Vec3 p = next.pos();

            if (Double.isNaN(netHeight)) {
                boolean crossed = toOpp < 0 ? (prevZ > 0 && p.z() <= 0)
                                            : (prevZ < 0 && p.z() >= 0);
                if (crossed && prevZ != p.z()) {
                    double f = prevZ / (prevZ - p.z());
                    netHeight = s.pos().y() + (p.y() - s.pos().y()) * f;
                }
            }
            prevZ = p.z();

            if (p.y() <= BALL_R && next.vel().y() < 0) {
                double f = (s.pos().y() - BALL_R) / (s.pos().y() - p.y());
                return new Flight(netHeight, Vec3.lerp(s.pos(), p, clamp(f, 0, 1)));
            }
            s = next;
        }
        return new Flight(netHeight, s.pos());
    }

    // ================================================================== helpers

    private static Vec3 safeDir(Vec3 v) {
        Vec3 n = v.normalized();
        return n.lengthSquared() < 1e-6 ? new Vec3(0, 0, -1) : n;
    }

    private static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : (x > hi ? hi : x);
    }
}
