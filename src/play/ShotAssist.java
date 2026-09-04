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
 * The impulse solver in physics/ stays exact -- it is what SelfTest grades, and it still runs
 * on every contact. But an exact bounce off a moving blade is not a table tennis shot: measured
 * over a grid of 75 racket velocities, the raw solver put ZERO of them on the table, threw the
 * ball up to 2.4 m sideways (the table is 0.76 m half-wide) and launched it at up to 24 m/s.
 * You cannot rally against that. So this sits on top, in play/, and turns the contact into a
 * SHOT the way an arcade game does:
 *
 *   1. read the racket's motion and the contact point as the player's INTENT
 *   2. turn that intent into a TARGET inside the opponent's court -- clamped there by
 *      construction, so it can never be an absurd aim
 *   3. turn swing speed into a shot strength on a saturating curve -- never incoming + racket,
 *      so repeated hits cannot grow without bound
 *   4. ask Aim (the same solver the shot presets use) for the launch that LANDS on that target
 *   5. blend in a small amount of the physical reflection, for feel
 *   6. clamp lateral velocity and speed
 *   7. VALIDATE: fly the finished velocity forward and check it clears the net and lands in.
 *      If it does not, pull the target toward the middle, slow it down, and solve again. If
 *      nothing works, fall back to a shot that is guaranteed legal.
 *
 * Step 7 is the one that was missing before. The old version solved a trajectory and then
 * mutated it (side-angle clamp, speed cap, net lift) without ever re-checking -- so Aim's
 * answer was correct and the ball still went out. Nothing here mutates a solved trajectory
 * without re-validating it.
 *
 * Both rackets run through this, so the opponent's returns are playable too.
 *
 * What this does NOT do, deliberately: it never writes {@code ball += racket}, never reflects
 * the ball off the blade as a rigid body, and never lets the ball inherit the direction of an
 * arbitrary racket movement. Flight, gravity, drag, Magnus and every bounce after the shot are
 * still the real simulation -- only the launch is authored.
 */
public final class ShotAssist {

    // ================================================================== tuning
    //
    // Every number the shot model uses lives here. Nothing is hardcoded further down; to change
    // the feel, change these. (Bounce restitution and friction are deliberately NOT duplicated
    // here -- they are real measured values with citations, already single-sourced in
    // physics/Constants.TABLE_MAT and RACKET_MAT, and SelfTest grades them.)

    public static final class Tuning {

        /** Shot strength, m/s. Swing speed is mapped onto this range and never beyond it. */
        public double minShotSpeed = 7.0;
        public double maxShotSpeed = 13.0;

        /** Racket speed, m/s, that produces a full-strength shot. Faster than this adds
         *  nothing -- this is what stops repeated hits from compounding. */
        public double maxSwingSpeed = 12.0;

        /** How much a sideways or upward swipe counts toward shot STRENGTH, next to the
         *  forward drive. Low on purpose: driving the blade through the ball is what makes it
         *  go, and moving it across is how you aim. A racket that is only travelling sideways
         *  is brushing the ball, not hitting it. */
        public double lateralEffort = 0.25;

        /** How much of the swing reaches the shot at all (0 = every shot the same strength). */
        public double swingInfluence = 1.0;

        /** Shape of swing -> strength. 1 = linear; below 1 = quick early response then
         *  diminishing returns, which is what makes a hard swing feel controlled. */
        public double swingCurve = 0.7;

        /** How far a sideways swipe moves the aim, as a fraction of the target box per m/s.
         *  0.16 puts a 6 m/s swipe on the edge of the box, which is a firm but ordinary sweep
         *  of the mouse -- the point of the number is that a player who swipes ACROSS the ball
         *  sees the ball go there, rather than seeing a hint of it. */
        public double aimInfluence = 0.16;

        /** How much a forward drive deepens the target, per m/s. */
        public double depthInfluence = 0.045;

        /** How much an up/down swipe arcs the shot: up = shorter and higher, down = flatter
         *  and deeper. Fraction of the target depth range per m/s. */
        public double arcInfluence = 0.035;

        /** How much the racket's own tilt aims the shot, on top of where it is moving. */
        public double faceInfluence = 0.25;

        /** How much hitting off-centre on the blade shifts the aim. Deliberately small -- edge
         *  contacts should feel different, not random. */
        public double contactPointInfluence = 0.30;

        /** Fraction of the physical reflection blended into the authored shot. Small: it is
         *  there so contacts feel alive, not so it can steer. */
        public double physicalBlend = 0.06;

        /** The reflection is capped at this speed before blending, so a violent impulse cannot
         *  leak through even at 6%. */
        public double reflectionCap = 6.0;

        /** Hard ceiling on the sideways component of the finished shot. Both a cone (degrees
         *  off straight) and an absolute m/s -- whichever binds first. 20 degrees is what it
         *  takes to reach the corner of the widened target box from a contact behind the end
         *  line; at 15 the clamp was quietly overruling the aim before the validator ever saw
         *  it. Widening it cannot make a shot illegal on its own -- every candidate is still
         *  flown and graded. */
        public double maxHorizontalDeviationDeg = 20.0;
        public double maxLateralVelocity = 3.0;

        /** Launch elevation band. This is a SANITY GUARD, not a shaping tool -- Aim owns the
         *  elevation, and a real drive off a waist-high ball near the baseline genuinely
         *  launches DOWNWARD (measured: -7 deg at 13 m/s to a target 2 m away). Forcing a
         *  positive floor here is exactly what used to throw every shot 2 m past the end
         *  line: Aim solved the shot correctly, and then this clamp tilted it up again. */
        public double maxVerticalLaunchAngleDeg = 45.0;
        public double minVerticalLaunchAngleDeg = -20.0;

        /** Shot speed and target depth are not independent: a short target cannot be reached
         *  fast, a deep one cannot be reached slowly. So the search tries a spread of speeds
         *  around the one the swing asked for, and keeps the legal candidate closest to it. */
        public int speedCandidates = 5;
        public double speedSpread = 0.42;

        /** Penalty per m/s for not being the speed the swing asked for, and per correction
         *  pass for having had to give ground. These only ever separate candidates that are
         *  both already legal -- illegality outweighs them by two orders of magnitude. */
        public double speedPreference = 1.0;
        public double passPenalty = 2.0;

        /** Always at least this much pace toward the opponent. */
        public double minForwardVelocity = 4.5;

        /**
         * The slowest shot the MAIN search may consider, m/s -- as opposed to minShotSpeed,
         * which is the slowest the swing may ASK for.
         *
         * These have to be separate numbers. A contact low over the table, or behind the end
         * line off a ball that has already dropped, has no legal answer at 7 m/s at all: the
         * shot has to be lifted, and a lifted shot is slow. With the floor at minShotSpeed the
         * whole search failed on those contacts and they fell through to the rescue -- which
         * re-aims down the middle, so EVERY such shot came back to the centre of the table no
         * matter where the player swiped. That was the bug: not that the aim was weak, but that
         * the aim was being discarded by a fallback nobody expected to be the normal path.
         *
         * Letting the ladder go this low costs nothing in feel, because the score still prefers
         * the speed the swing asked for -- a slow candidate only wins when the fast ones are
         * illegal, which is exactly when it should.
         */
        public double minSearchSpeed = 3.0;

        /** The target box on the opponent's half, as fractions of half-width / half-length.
         *  Chosen so a shot that lands on target is comfortably inside the lines: 0.75 of the
         *  half-width is 0.57 m, leaving 19 cm of table outside the box for the solve to be
         *  wrong by. It was 0.60, which put the corners of the box so far inside the table
         *  that a fully committed swipe still landed mid-court. */
        public double targetHalfWidthFrac = 0.75;
        public double targetDepthMinFrac = 0.32;
        public double targetDepthMaxFrac = 0.80;

        /** Where the "safe" shot goes when a correction pass has to give ground. */
        public double safeDepthFrac = 0.55;

        /** How far each correction pass pulls the target toward safe, and how much it slows
         *  the shot. */
        public int maxCorrectionPasses = 3;
        public double targetAssist = 0.34;
        public double speedBackoffPerPass = 0.13;

        /** Clearance above the cord the validator insists on, metres. */
        public double netClearance = 0.055;

        /** Margin inside the sidelines / end line the landing must keep, metres. */
        public double landingMargin = 0.05;

        /** The rescue search, used only when the normal search finds nothing legal. It is
         *  allowed to go slower than minShotSpeed and to re-aim, because some contacts
         *  genuinely have no fast answer: a ball met right at the net, barely cord-high, can
         *  only be lifted softly over -- which is exactly what a real player does with it.
         *  Without this the shot model has to pick between the net and a wild trajectory, and
         *  it was picking the net. */
        public double rescueMinSpeed = 3.0;
        public int rescueSpeedSteps = 9;
        public double[] rescueDepthFracs = {0.55, 0.72, 0.88, 0.40};

        /**
         * How much of the player's lateral aim the rescue keeps, tried in this order.
         *
         * It used to be {0} implicitly -- every rescued shot was re-aimed down the middle. That
         * is a safe answer and a terrible one: the rescue turned out to be the path most player
         * contacts take, so "the ball always comes back to the centre" was really "the aim is
         * thrown away whenever the shot has to be lifted". Trying the full aim first and only
         * giving it up if nothing there is legal keeps the guarantee and returns the aim.
         */
        public double[] rescueAimFracs = {1.0, 0.6, 0.3, 0.0};

        /** Spin, rev/s. Topspin comes from an upward swipe, sidespin from a sideways one.
         *  Capped so spin stays a secondary influence and never a source of chaos. */
        public double spinInfluence = 1.0;
        public double baseTopspin = 14.0;
        public double topspinPerLift = 2.6;
        public double sidespinPerSwipe = 2.2;
        public double maxSpin = 55.0;
    }

    private final Tuning t;

    public ShotAssist()            { this(new Tuning()); }
    public ShotAssist(Tuning tune) { this.t = tune; }
    public Tuning tuning()         { return t; }

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
        double halfW = TABLE_WIDTH / 2, halfLen = TABLE_LENGTH / 2;

        Vec3 contact = physical.pos();
        Vec3 reflect = physical.vel();
        Vec3 swing = racket.vel();

        // ---- 1. intent -------------------------------------------------------------------
        // Split the racket's motion into the three things it can mean. For the player, x and y
        // are purely the cursor (the depth reach is all in z), so they are deliberate input.
        double drive   = swing.z() * toOpp;      // + toward the opponent, - pulling away
        double swipeX  = swing.x();              // + to the player's right
        double lift    = swing.y();              // + upward

        // Strength comes from the FORWARD drive, not the blade's total speed -- a still blade
        // dinks, a blade driven through the ball hits. The curve below 1 gives a quick early
        // response then diminishing returns, so swinging harder always does a little more and
        // never a lot more.
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

        // ---- 2. target -------------------------------------------------------------------
        // Built inside the box by construction, so the aim can never be absurd.
        double aim = swipeX * t.aimInfluence
                   + faceX * t.faceInfluence
                   + offX * t.contactPointInfluence;
        double wantX = clamp(aim, -1, 1) * targetHalfWidth();

        double depthFrac = t.targetDepthMinFrac
                + (t.targetDepthMaxFrac - t.targetDepthMinFrac)
                  * clamp(0.35 + drive * t.depthInfluence - lift * t.arcInfluence
                               - offY * t.contactPointInfluence * 0.5, 0, 1);
        double wantZ = toOpp * clamp(depthFrac, t.targetDepthMinFrac, t.targetDepthMaxFrac) * halfLen;

        Vec3 wantTarget = new Vec3(wantX, 0, wantZ);
        Vec3 safeTarget = new Vec3(0, 0, toOpp * t.safeDepthFrac * halfLen);

        // ---- 3. strength -----------------------------------------------------------------
        // Swing maps onto a fixed band. The incoming ball nudges it slightly, but is never
        // ADDED to it -- that is what stops a rally from compounding into a rocket.
        double wantSpeed = t.minShotSpeed + (t.maxShotSpeed - t.minShotSpeed) * swingAmount;
        wantSpeed += clamp((incoming.speed() - 9.0) * 0.05, -0.6, 0.6);
        wantSpeed = clamp(wantSpeed, t.minShotSpeed, t.maxShotSpeed);

        // ---- 4. spin ---------------------------------------------------------------------
        double topRevs  = clamp((t.baseTopspin + lift * t.topspinPerLift) * t.spinInfluence,
                                -t.maxSpin, t.maxSpin);
        double sideRevs = clamp(swipeX * t.sidespinPerSwipe * t.spinInfluence,
                                -t.maxSpin, t.maxSpin);
        // Both may be reset below: the safe fallback flies with plain topspin.


        // ---- 5-7. solve, constrain, validate, correct ------------------------------------
        //
        // Every candidate is a (target, speed) pair: solved by Aim, blended, clamped, and then
        // FLOWN and graded. Nothing is mutated after its last check -- that is the whole point.
        // A correction pass pulls the target toward the middle of the opponent's court; the
        // speed ladder inside each pass is there because target depth and speed constrain each
        // other. The winner is the legal candidate closest to what the swing asked for. An
        // illegal one can only win if nothing legal was found at all.
        Vec3 bestVel = null;
        Vec3 bestTarget = wantTarget;
        Flight bestFlight = null;
        double bestCost = Double.MAX_VALUE;
        double bestScore = Double.MAX_VALUE;
        int passes = 0;

        search:
        for (int pass = 0; pass <= t.maxCorrectionPasses; pass++) {
            double give = Math.min(1, pass * t.targetAssist);
            Vec3 target = Vec3.lerp(wantTarget, safeTarget, give);
            double pace = wantSpeed * (1 - t.speedBackoffPerPass * pass);

            for (int k = 0; k < t.speedCandidates; k++) {
                double speed = clamp(pace * speedFactor(k), t.minSearchSpeed, t.maxShotSpeed);

                Aim.Solution sol = Aim.atTarget(contact, target, speed, topRevs, sideRevs);
                // Capped at the candidate's OWN speed, not at the band's top: a shot that only
                // works slowly must be allowed to stay slow. Handing this the band's minimum
                // forward pace instead would undo the solve that just found it -- the same
                // reason the rescue passes its own cap.
                Vec3 vel = constrain(
                        Vec3.lerp(sol.state().vel(), reflect(reflect), t.physicalBlend),
                        toOpp, speed);
                Vec3 spin = Aim.spin(new Vec3(vel.x(), 0, vel.z()), topRevs, sideRevs);

                Flight f = fly(contact, vel, spin, toOpp);
                double cost = illegality(f, toOpp, halfW, halfLen);
                double score = cost * 100
                             + Math.abs(speed - wantSpeed) * t.speedPreference
                             + pass * t.passPenalty;

                if (score < bestScore) {
                    bestScore = score; bestCost = cost;
                    bestVel = vel; bestTarget = target; bestFlight = f; passes = pass;
                }
                // Legal: stop. The ladder tries the asked-for pace first and then alternates
                // outward, so the first legal candidate in a pass is already the one closest to
                // what the swing asked for -- finishing the pass can only find worse. This is
                // not a micro-optimisation: every candidate costs an Aim solve, which is 60
                // bisection steps each flying a trajectory, and the whole search runs inside
                // the single frame the contact lands on.
                if (cost == 0) break search;
            }
            if (bestCost == 0) break;
        }

        // Nothing legal came out of the normal search. Rather than let a wild trajectory
        // through -- the "ball must not fly everywhere" floor -- sweep the whole envelope:
        // every sensible depth down the middle, at speeds from a soft lift up to full pace.
        // Some contacts have no fast answer at all and the honest shot is a slow one.
        if (bestCost > 0) {
            // The player's own spin first, plain topspin only as a last resort: a chop that
            // has to be rescued should still come back as a chop if any speed works with it.
            double[][] spins = {{topRevs, sideRevs}, {t.baseTopspin, 0}};
            rescue:
            for (double aimFrac : t.rescueAimFracs) {
              for (double[] sp : spins) {
                for (double depth : t.rescueDepthFracs) {
                    Vec3 target = new Vec3(wantX * aimFrac, 0, toOpp * depth * halfLen);
                    for (int k = 0; k < t.rescueSpeedSteps; k++) {
                        double speed = t.rescueMinSpeed + (t.maxShotSpeed - t.rescueMinSpeed)
                                * k / (double) (t.rescueSpeedSteps - 1);
                        Aim.Solution sol = Aim.atTarget(contact, target, speed, sp[0], sp[1]);
                        Vec3 vel = constrain(sol.state().vel(), toOpp, speed);
                        Vec3 spin = Aim.spin(new Vec3(vel.x(), 0, vel.z()), sp[0], sp[1]);
                        Flight f = fly(contact, vel, spin, toOpp);
                        double cost = illegality(f, toOpp, halfW, halfLen);
                        if (cost < bestCost) {
                            bestCost = cost; bestVel = vel; bestTarget = target; bestFlight = f;
                            topRevs = sp[0]; sideRevs = sp[1];
                            passes = t.maxCorrectionPasses + 1;   // "rescued", for the overlay
                        }
                        if (cost == 0) break rescue;
                    }
                }
              }
            }
        }

        Vec3 finalVel = bestVel;
        Vec3 finalSpin = Aim.spin(new Vec3(finalVel.x(), 0, finalVel.z()), topRevs, sideRevs);

        debug = new Debug(contact, swing, incoming.vel(), safeDir(reflect),
                          safeDir(new Vec3(bestTarget.x() - contact.x(), 0,
                                           bestTarget.z() - contact.z())),
                          safeDir(finalVel), bestTarget, bestFlight.landing(),
                          finalVel.length(), finalSpin, passes, bestCost == 0);

        return new BallState(physical.pos(), finalVel, finalSpin, physical.orient());
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
     */
    private Vec3 constrain(Vec3 v, double toOpp) {
        return constrain(v, toOpp, t.maxShotSpeed);
    }

    /**
     * @param cap the top speed this candidate is allowed. The rescue search passes its own,
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
     * Step size for the validation flights, seconds -- deliberately COARSER than the game's DT.
     *
     * This is the assist's whole cost. Every candidate is flown to its landing, and a search
     * that gives ground can fly a hundred of them on the one frame a contact lands on; at the
     * game's 1/480 s that measured 25.8 ms for an ordinary two-pass shot, which is longer than
     * the 16.7 ms frame it happens inside. Nothing about the answer needs that resolution: the
     * flight is asked two cm-scale questions (does it clear the cord, where does it pitch) and
     * graded against a 5 cm landing margin.
     *
     * 1/120 s is a quarter of the steps. RK4's error is O(h^4), so four times the step is 256
     * times the error -- off a per-flight error that SelfTest measures in tenths of a
     * millimetre over three seconds, which lands it at millimetres. That is two orders below
     * the margin it feeds. Do not take it coarser without redoing that arithmetic: at 1/60 the
     * error is 16x again and starts to matter, and this is a validator -- a flight that
     * disagrees with the simulation is worse than no flight at all.
     */
    private static final double VALIDATE_DT = 1.0 / 120;

    /**
     * How illegal a flight is: 0 means it clears the net and lands inside the opponent's half.
     * Anything else is the size of the violation, so a correction pass can keep the least-bad
     * candidate if none is perfect.
     */
    private double illegality(Flight f, double toOpp, double halfW, double halfLen) {
        double cost = 0;

        double needed = NET_HEIGHT + BALL_R + t.netClearance;
        if (Double.isNaN(f.netHeight())) cost += 10;                       // never crossed
        else if (f.netHeight() < needed) cost += (needed - f.netHeight()) * 20;

        Vec3 L = f.landing();
        double depth = L.z() * toOpp;                                      // + is into their half
        if (depth < t.landingMargin) cost += (t.landingMargin - depth) * 10;
        double maxDepth = halfLen - t.landingMargin;
        if (depth > maxDepth) cost += (depth - maxDepth) * 10;

        double side = Math.abs(L.x()) - (halfW - t.landingMargin);
        if (side > 0) cost += side * 10;

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

        for (int i = 0; i < (int) (3.0 / VALIDATE_DT); i++) {
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
