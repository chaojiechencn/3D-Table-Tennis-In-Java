package play;

import physics.Aim;
import physics.BallState;
import physics.Integrator;
import physics.Paddle;
import physics.Vec3;

import static physics.Constants.BALL_R;
import static physics.Constants.BLADE_R;
import static physics.Constants.NET_HEIGHT;
import static physics.Constants.TABLE_LENGTH;
import static physics.Constants.TABLE_WIDTH;

/**
 * The arcade shot model for both rackets. The exact impulse still runs on every contact; this
 * turns it into a playable shot: read the racket's motion as intent, build a target inside the
 * opponent's court, solve the launch with {@link Aim}, constrain it, then fly and grade every
 * candidate. Only the launch is authored; the flight after it is the real simulation.
 */
public final class ShotAssist {

    /** Everything the last shot was built from, for the V overlay. */
    public record Debug(Vec3 contact, Vec3 racketVel, Vec3 incomingVel, Vec3 reflectDir,
                        Vec3 intendDir, Vec3 finalDir, Vec3 target, Vec3 landing,
                        double speed, Vec3 spin, int passes, boolean legal) {}

    private record Intent(double drive, double swipeX, double lift, double brush,
                          double swingAmount, double offX, double offY, double faceX) {}

    private record Target(Vec3 want, Vec3 safe) {}

    private record Spin(double top, double side) {}

    /** Height at the net plane (NaN if never crossed) and first descent to the table plane. */
    private record Flight(double netHeight, Vec3 landing) {}

    private record Candidate(Vec3 vel, Vec3 target, Flight flight, double cost, int passes, Spin spin) {}

    private static final double HALF_WIDTH = TABLE_WIDTH / 2, HALF_LENGTH = TABLE_LENGTH / 2;

    /** TUNED: 4x the game step; RK4 error stays two orders below the 5 cm landing margin. */
    private static final double VALIDATE_DT = 1.0 / 120;
    private static final double MAX_FLIGHT_TIME = 3.0;

    // Cost per metre of violation: missing the net dominates, the cord counts double the lines,
    // and any illegality outweighs the speed and pass preferences by two orders of magnitude.
    private static final double NEVER_CROSSED_COST = 10;
    private static final double NET_SHORTFALL_WEIGHT = 20;
    private static final double LANDING_MISS_WEIGHT = 10;
    private static final double ILLEGALITY_WEIGHT = 100;

    private static final Vec3 DOWN_TABLE = new Vec3(0, 0, -1);

    private final ShotTuning t;
    private Debug debug = new Debug(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, DOWN_TABLE,
            DOWN_TABLE, DOWN_TABLE, Vec3.ZERO, Vec3.ZERO, 0, Vec3.ZERO, 0, true);

    public ShotAssist()                  { this(ShotTuning.defaults()); }
    public ShotAssist(ShotTuning tuning) { this.t = tuning; }

    public Debug debug() { return debug; }

    public double targetHalfWidth() { return t.targetHalfWidthFrac * TABLE_WIDTH / 2; }
    public double targetNearDepth() { return t.targetDepthMinFrac * TABLE_LENGTH / 2; }
    public double targetFarDepth()  { return t.targetDepthMaxFrac * TABLE_LENGTH / 2; }

    /**
     * @param incoming  the ball just before the contact
     * @param physical  the raw impulse result
     * @param playerHit true sends the ball toward -Z, false toward +Z
     */
    public BallState assist(BallState incoming, BallState physical, Paddle racket, boolean playerHit) {
        double toOpp = playerHit ? -1.0 : 1.0;
        Vec3 contact = physical.pos();
        Vec3 reflect = physical.vel();

        Intent in = readIntent(contact, racket, toOpp);
        double quality = quality(incoming, in.offX(), in.offY());
        double assist = playerHit ? t.assistFloor + (1 - t.assistFloor) * quality : 1.0;
        Target target = readTarget(in, toOpp);
        double wantSpeed = readSpeed(in.swingAmount(), incoming);
        Spin spin = readSpin(in.brush(), in.swipeX());

        Candidate best = search(contact, reflect, target, wantSpeed, spin, toOpp);
        boolean mayRescue = !playerHit
                || (quality >= t.rescueQualityFloor && in.swingAmount() <= t.rescueEffortCeiling);
        if (best.cost() > 0 && mayRescue) best = rescue(contact, target.want().x(), spin, toOpp, best);

        // A clean hit gets the authored shot; a shank keeps proportionally more raw physics.
        Vec3 finalVel  = Vec3.lerp(capReflection(reflect), best.vel(), assist);
        Vec3 finalSpin = Vec3.lerp(physical.spin(), spinFor(best.vel(), best.spin()), assist);

        debug = new Debug(contact, racket.vel(), incoming.vel(), safeDir(reflect),
                          safeDir(new Vec3(best.target().x() - contact.x(), 0,
                                           best.target().z() - contact.z())),
                          safeDir(finalVel), best.target(), best.flight().landing(),
                          finalVel.length(), finalSpin, best.passes(), best.cost() == 0);

        return new BallState(physical.pos(), finalVel, finalSpin, physical.orient());
    }

    /**
     * Correction passes pull the target toward safe and slow the pace; within a pass a speed ladder
     * starts at the asked-for pace. The first legal candidate ends the search.
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
                Vec3 blended = Vec3.lerp(sol.state().vel(), capReflection(reflect), t.physicalBlend);
                Candidate c = evaluate(contact, aimAt, constrain(blended, toOpp, speed), spin, toOpp, pass);

                double score = c.cost() * ILLEGALITY_WEIGHT
                             + Math.abs(speed - wantSpeed) * t.speedPreference
                             + pass * t.passPenalty;
                if (score < bestScore) { bestScore = score; best = c; }
                if (c.cost() == 0) return best;
            }
        }
        return best;
    }

    /**
     * Every sensible depth down the middle from a soft lift to full pace, keeping as much of the
     * player's aim, then spin, as still works. Some contacts only have a slow, honest answer.
     */
    private Candidate rescue(Vec3 contact, double wantX, Spin spin, double toOpp, Candidate best) {
        Spin[] spins = {spin, new Spin(t.baseTopspin, 0)};
        int rescuedPasses = t.maxCorrectionPasses + 1;
        for (double aimFrac : t.rescueAimFracs) {
            for (Spin sp : spins) {
                for (double depth : t.rescueDepthFracs) {
                    Vec3 aimAt = new Vec3(wantX * aimFrac, 0, toOpp * depth * HALF_LENGTH);
                    for (int k = 0; k < t.rescueSpeedSteps; k++) {
                        double speed = t.rescueMinSpeed + (t.maxShotSpeed - t.rescueMinSpeed)
                                * k / (double) (t.rescueSpeedSteps - 1);
                        Aim.Solution sol = Aim.atTarget(contact, aimAt, speed, sp.top(), sp.side());
                        Vec3 vel = constrain(sol.state().vel(), toOpp, speed);
                        Candidate c = evaluate(contact, aimAt, vel, sp, toOpp, rescuedPasses);
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

    private static Vec3 spinFor(Vec3 vel, Spin spin) {
        return Aim.spin(new Vec3(vel.x(), 0, vel.z()), spin.top(), spin.side());
    }

    /**
     * Drive is toward the opponent, swipe to the player's right, lift upward (live only while
     * brushing). Strength comes from the forward drive on a saturating curve; the contact offset
     * is measured in the face's own plane as a fraction of the blade radius.
     */
    private Intent readIntent(Vec3 contact, Paddle racket, double toOpp) {
        Vec3 swing = racket.vel();
        double drive  = swing.z() * toOpp;
        double swipeX = swing.x();
        double lift   = swing.y();
        double brush = lift + drive * t.driveBrush;

        double effort = Math.max(0, drive) + t.lateralEffort * Math.hypot(swipeX, lift);
        double swingAmount = clamp(Math.pow(
                clamp(effort / t.maxSwingSpeed, 0, 1), t.swingCurve), 0, 1) * t.swingInfluence;

        Vec3 off = contact.minus(racket.pos());
        Vec3 inPlane = off.minus(racket.normal().scale(off.dot(racket.normal())));
        double offX = clamp(inPlane.x() / BLADE_R, -1, 1);
        double offY = clamp(inPlane.y() / BLADE_R, -1, 1);
        double faceX = clamp(racket.normal().x() * -toOpp, -1, 1);

        return new Intent(drive, swipeX, lift, brush, swingAmount, offX, offY, faceX);
    }

    /** 1 mid-blade to 0 at the rim, with a core that shrinks as the ball arrives faster. */
    private double quality(BallState incoming, double offX, double offY) {
        double offR = Math.min(1, Math.hypot(offX, offY));
        double paceFrac = clamp((incoming.speed() - t.qualityPaceFrom) / t.qualityPaceSpan, 0, 1);
        double core = Math.max(t.qualityCoreMin, t.qualityCore - t.qualityPaceLoss * paceFrac);
        double rim  = core + t.qualityFalloff;
        return clamp((rim - offR) / (rim - core), 0, 1);
    }

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

    /** The incoming pace only nudges the band, never adds to it, so a rally cannot compound. */
    private double readSpeed(double swingAmount, BallState incoming) {
        double wantSpeed = t.minShotSpeed + (t.maxShotSpeed - t.minShotSpeed) * swingAmount;
        wantSpeed += clamp((incoming.speed() - t.incomingPaceNeutral) * t.incomingPaceGain,
                           -t.incomingPaceNudge, t.incomingPaceNudge);
        return clamp(wantSpeed, t.minShotSpeed, t.maxShotSpeed);
    }

    private Spin readSpin(double brush, double swipeX) {
        double topRevs  = clamp((t.baseTopspin + brush * t.topspinPerLift) * t.spinInfluence,
                                -t.maxSpin, t.maxSpin);
        double sideRevs = clamp(swipeX * t.sidespinPerSwipe * t.spinInfluence,
                                -t.maxSpin, t.maxSpin);
        return new Spin(topRevs, sideRevs);
    }

    /** 1.0 first, then alternately slower and faster, so a legal shot costs one solve. */
    private double speedFactor(int k) {
        if (k == 0) return 1.0;
        int step = (k + 1) / 2;
        double d = t.speedSpread * step / Math.max(1, t.speedCandidates / 2);
        return (k % 2 == 1) ? 1.0 - d : 1.0 + d;
    }

    private Vec3 capReflection(Vec3 raw) {
        double sp = raw.length();
        return sp > t.reflectionCap ? raw.scale(t.reflectionCap / sp) : raw;
    }

    /**
     * Forward pace, a lateral cone and cap, an elevation band, and the candidate's OWN top speed:
     * a shot that only works slowly must be allowed to stay slow.
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

    /** 0 clears the net and lands in; otherwise the size of the violation. */
    private double illegality(Flight f, double toOpp) {
        double cost = 0;

        double needed = NET_HEIGHT + BALL_R + t.netClearance;
        if (Double.isNaN(f.netHeight())) cost += NEVER_CROSSED_COST;
        else if (f.netHeight() < needed) cost += (needed - f.netHeight()) * NET_SHORTFALL_WEIGHT;

        Vec3 landing = f.landing();
        double depth = landing.z() * toOpp;                                // + is into their half
        if (depth < t.landingMargin) cost += (t.landingMargin - depth) * LANDING_MISS_WEIGHT;
        double maxDepth = HALF_LENGTH - t.landingMargin;
        if (depth > maxDepth) cost += (depth - maxDepth) * LANDING_MISS_WEIGHT;

        double side = Math.abs(landing.x()) - (HALF_WIDTH - t.landingMargin);
        if (side > 0) cost += side * LANDING_MISS_WEIGHT;

        return cost;
    }

    /** Contact-free flight: asking a World with a table in it where a shot lands is circular. */
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

    private static Vec3 safeDir(Vec3 v) {
        Vec3 n = v.normalized();
        return n.lengthSquared() < 1e-6 ? DOWN_TABLE : n;
    }

    private static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : (x > hi ? hi : x);
    }
}
