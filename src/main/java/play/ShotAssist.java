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

    // ================================================================== tuning
    //
    // Every number the shot model uses lives here. Nothing is hardcoded further down; to change
    // the feel, change these. (Bounce restitution and friction are deliberately NOT duplicated
    // here -- they are real measured values with citations, already single-sourced in
    // physics/Constants.TABLE_MAT and RACKET_MAT, and SelfTest grades them.)

    public static final class Tuning {

        /** Shot strength, m/s. Swing speed maps onto this range and never beyond it. The ceiling
         *  is ASPIRATIONAL -- a shot still has to land, and geometry alone caps most contacts
         *  well under it, so raising this alone does nothing. */
        public double minShotSpeed = 5.0;
        public double maxShotSpeed = 17.0;

        /** Racket speed, m/s, that produces a full-strength shot. Faster adds nothing -- this is
         *  what stops repeated hits from compounding. */
        public double maxSwingSpeed = 16.0;

        /** How much a sideways or upward swipe counts toward shot STRENGTH, next to the forward
         *  drive. Low on purpose: driving through the ball is what makes it go, moving across is
         *  how you aim. */
        public double lateralEffort = 0.25;

        /** How much of the swing reaches the shot at all (0 = every shot the same strength). */
        public double swingInfluence = 1.0;

        /** Shape of swing -> strength. 1 = linear; below 1 = quick early response then
         *  diminishing returns, which is what makes a hard swing feel controlled. */
        public double swingCurve = 0.7;

        /** How far a sideways swipe moves the aim, as a fraction of the target box per m/s.
         *  TUNED: 0.20 puts an ordinary firm sweep on the edge of the box. */
        public double aimInfluence = 0.20;

        /** How much a forward drive deepens the target, per m/s. Pulls against `arcInfluence`
         *  below by design -- driving forward deepens the target through this term and raises
         *  `brush`, which shortens it through that one. See docs/DESIGN.md before retuning
         *  either. */
        public double depthInfluence = 0.100;

        /** How much an up/down swipe arcs the shot: up = shorter and higher, down = flatter and
         *  deeper. Fraction of the target depth range per m/s; see `depthInfluence`, which this
         *  partly cancels on purpose. */
        public double arcInfluence = 0.018;

        /** How much the racket's own tilt aims the shot, on top of where it is moving. */
        public double faceInfluence = 0.25;

        /** How much hitting off-centre on the blade shifts the aim. Deliberately small -- edge
         *  contacts should feel different, not random. */
        public double contactPointInfluence = 0.30;

        /** Fraction of the physical reflection blended into the authored shot -- there so a
         *  contact feels like an impact, not so it can steer the shot on its own. */
        public double physicalBlend = 0.15;

        // ---- contact quality -------------------------------------------------------------
        // How well the ball was struck, and how much help the shot earns for it. Without these
        // a shank could never be punished and a rally could not be won or lost on skill; see
        // docs/DESIGN.md.

        /**
         * The clean core of the blade, as a fraction of its radius: inside this a contact counts
         * as fully struck. FLOOR 0.50, measured against RallyTest's own competent-play probe;
         * TUNED up to 0.58 above that floor for an average player. See docs/DESIGN.md.
         */
        public double qualityCore = 0.58;

        /** How far past the core the quality falls from 1 to 0; core + this is the rim. TUNED
         *  wider (was 0.42) so the drop from clean to mishit is a slope, not a cliff. */
        public double qualityFalloff = 0.50;

        /** Incoming speed (m/s) at which the core starts shrinking, and the span over which it
         *  shrinks the whole way. A fast ball has to be met more precisely than a slow one. */
        public double qualityPaceFrom = 6.0;
        public double qualityPaceSpan = 12.0;

        /** How much of the core the fastest ball takes away, and the floor it cannot shrink
         *  below. TUNED down from 0.22 so a fast ball -- already the hardest thing to time --
         *  does not lose the forgiveness qualityCore just bought. */
        public double qualityPaceLoss = 0.15;
        public double qualityCoreMin = 0.26;

        /**
         * The assist a zero-quality contact still gets. Not zero -- the raw rim impulse lands on
         * the table only 11 times in 75 (see docs/DESIGN.md), which would make a shank fatal
         * every time. TUNED up to 0.35 so a shank stays clearly worse than a clean hit without
         * being close to an automatic loss.
         */
        public double assistFloor = 0.35;

        /** The quality below which the rescue search does not run at all -- it still exists for
         *  a ball met right at the net with no fast legal shot, but a contact off the rim no
         *  longer qualifies for it. */
        public double rescueQualityFloor = 0.60;

        /** How much forward drive counts as brushing over the ball; 0.8 is what lets a hard
         *  pull-back reach genuine backspin rather than merely less topspin. */
        public double driveBrush = 0.8;

        /** The reflection is capped at this speed before blending, so a violent impulse cannot
         *  leak through even at a small blend fraction. */
        public double reflectionCap = 6.0;

        /** Hard ceiling on the sideways component of the finished shot: a cone (degrees off
         *  straight) and an absolute m/s, whichever binds first. Widening either cannot make a
         *  shot illegal on its own -- every candidate is still flown and graded. */
        public double maxHorizontalDeviationDeg = 30.0;
        public double maxLateralVelocity = 4.5;

        /** Launch elevation band -- a SANITY GUARD, not a shaping tool. Aim owns the elevation; a
         *  real drive off a low ball near the baseline genuinely launches downward, so do not
         *  raise the floor above zero without re-deriving it (see docs/DESIGN.md). */
        public double maxVerticalLaunchAngleDeg = 45.0;
        public double minVerticalLaunchAngleDeg = -20.0;

        /** Shot speed and target depth are not independent, so the search tries a spread of
         *  speeds around the one the swing asked for and keeps the legal candidate closest to
         *  it. */
        public int speedCandidates = 5;
        public double speedSpread = 0.42;

        /** Penalty per m/s for not being the speed the swing asked for, and per correction pass
         *  for having had to give ground. Only ever separates candidates that are both already
         *  legal -- illegality outweighs both by two orders of magnitude. */
        public double speedPreference = 1.0;
        public double passPenalty = 2.0;

        /** Always at least this much pace toward the opponent. */
        public double minForwardVelocity = 4.5;

        /** The slowest shot the MAIN search may consider, m/s -- separate from minShotSpeed (the
         *  slowest the swing may ASK for) because some contacts have no legal fast answer and
         *  must fall back to a slow, legal one rather than the rescue. See docs/DESIGN.md. */
        public double minSearchSpeed = 3.0;

        /** The search may not slow a shot below this fraction of the pace the swing ASKED for,
         *  or the ladder could disguise any over-ambitious swing as a legal dink -- see
         *  docs/DESIGN.md for the measured case this guards against. */
        public double searchSpeedFloorFrac = 0.60;

        /** Above this much swing, the rescue does not run at all -- it is for a ball met right
         *  at the net with no fast legal answer, not for a hard swing that simply would not
         *  land. */
        public double rescueEffortCeiling = 0.75;

        /** The target box on the opponent's half, as fractions of half-width / half-length.
         *  Deliberately not the whole table -- `landingMargin` keeps the last few centimetres
         *  out of reach. See docs/DESIGN.md for the widening history. */
        public double targetHalfWidthFrac = 0.90;
        public double targetDepthMinFrac = 0.20;
        public double targetDepthMaxFrac = 0.92;

        /** Where the "safe" shot goes when a correction pass has to give ground. */
        public double safeDepthFrac = 0.55;

        /** How far each correction pass pulls the target toward safe, and how much it slows the
         *  shot. Kept small -- too much silently drags an over-hit shot back to the middle
         *  instead of letting the player see they overhit it. */
        public int maxCorrectionPasses = 2;
        public double targetAssist = 0.18;
        public double speedBackoffPerPass = 0.13;

        /** Clearance above the cord the validator insists on, metres. */
        public double netClearance = 0.055;

        /** Margin inside the sidelines / end line the landing must keep, metres. */
        public double landingMargin = 0.05;

        /** The rescue search, used only when the normal search finds nothing legal. Allowed to
         *  go slower than minShotSpeed and to re-aim, for a ball met right at the net that can
         *  only be lifted softly over. */
        public double rescueMinSpeed = 3.0;
        public int rescueSpeedSteps = 9;
        public double[] rescueDepthFracs = {0.55, 0.72, 0.88, 0.40};

        /** How much of the player's lateral aim the rescue keeps, tried in this order -- the
         *  full aim first, given up only if nothing there is legal, so a rescued shot keeps the
         *  player's aim instead of always centring. */
        public double[] rescueAimFracs = {1.0, 0.6, 0.3, 0.0};

        /** Spin, rev/s. Topspin comes from an upward swipe, sidespin from a sideways one. Capped
         *  so spin stays a secondary influence and never a source of chaos. */
        public double spinInfluence = 1.0;
        public double baseTopspin = 14.0;
        public double topspinPerLift = 2.6;

        /**
         * TUNED, standing in for how hard a player brushes ACROSS the back of the ball. This
         * knob SATURATES: every candidate shot is solved to a target and validated, so more spin
         * mostly makes the solver pick a different launch to the same legal landing rather than
         * visibly curving the ball more. See docs/DESIGN.md for the measured A/B before reaching
         * for this again. Note the sign: a swipe right AIMS the ball right but SPINS it to bend
         * back left, which is real -- brushing across curves the ball opposite the brush.
         */
        public double sidespinPerSwipe = 4.5;
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

        // The BRUSH -- how much the blade rolls over the ball rather than pushing through it --
        // authors spin, from two places: `lift`, the true vertical one (live only while the
        // brush modifier is held, since the blade is otherwise pinned to PlayerReach.HIT_Y), and
        // `drive`, the brush the player always has (forward rolls the face over for topspin,
        // pulling back opens it for backspin). See docs/DESIGN.md for the gain derivation.
        double brush = lift + drive * t.driveBrush;

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

        // ---- contact quality: how cleanly this ball was struck, 1 in the middle of the blade
        // and 0 at the rim, shrinking as the ball arrives faster. Measured IN THE FACE'S OWN
        // PLANE (offX, offY above), so being early/late counts the same as being wide.
        double offR = Math.min(1, Math.hypot(offX, offY));
        double paceFrac = clamp((incoming.speed() - t.qualityPaceFrom) / t.qualityPaceSpan, 0, 1);
        double core = Math.max(t.qualityCoreMin, t.qualityCore - t.qualityPaceLoss * paceFrac);
        double rim  = core + t.qualityFalloff;
        double quality = clamp((rim - offR) / (rim - core), 0, 1);

        // The share of the authored shot this contact earned; see Tuning.assistFloor. Only the
        // PLAYER is graded -- Follower tracks the ball rather than reading where it is going, so
        // grading it punishes a placeholder's limitation rather than a real skill. See
        // docs/DESIGN.md.
        double assist = playerHit ? t.assistFloor + (1 - t.assistFloor) * quality : 1.0;

        // ---- 2. target -------------------------------------------------------------------
        // Built inside the box by construction, so the aim can never be absurd.
        double aim = swipeX * t.aimInfluence
                   + faceX * t.faceInfluence
                   + offX * t.contactPointInfluence;
        double wantX = clamp(aim, -1, 1) * targetHalfWidth();

        double depthFrac = t.targetDepthMinFrac
                + (t.targetDepthMaxFrac - t.targetDepthMinFrac)
                  * clamp(0.35 + drive * t.depthInfluence - brush * t.arcInfluence
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
        double topRevs  = clamp((t.baseTopspin + brush * t.topspinPerLift) * t.spinInfluence,
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
                double floor = Math.max(t.minSearchSpeed, wantSpeed * t.searchSpeedFloorFrac);
                double speed = clamp(pace * speedFactor(k), Math.min(floor, t.maxShotSpeed),
                                     t.maxShotSpeed);

                Aim.Solution sol = Aim.atTarget(contact, target, speed, topRevs, sideRevs);
                // Capped at the candidate's OWN speed, not the band's top -- a shot that only
                // works slowly must be allowed to stay slow.
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
                // Legal: stop. The ladder tries the asked-for pace first then alternates
                // outward, so the first legal candidate in a pass is already the closest one --
                // finishing the pass can only find worse, and each candidate costs a real solve.
                if (cost == 0) break search;
            }
            if (bestCost == 0) break;
        }

        // Nothing legal came out of the normal search. Rather than let a wild trajectory
        // through -- the "ball must not fly everywhere" floor -- sweep the whole envelope:
        // every sensible depth down the middle, at speeds from a soft lift up to full pace.
        // Some contacts have no fast answer at all and the honest shot is a slow one.
        boolean mayRescue = !playerHit
                || (quality >= t.rescueQualityFloor && swingAmount <= t.rescueEffortCeiling);
        if (bestCost > 0 && mayRescue) {
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

        // The authored shot is what a properly hit ball gets; the raw bounce is what a shank
        // gets, blended by `assist` so a mishit keeps the most real physics and never carries
        // authored spin it did nothing to earn.
        Vec3 authoredVel = bestVel;
        Vec3 authoredSpin = Aim.spin(new Vec3(authoredVel.x(), 0, authoredVel.z()), topRevs, sideRevs);

        Vec3 finalVel  = Vec3.lerp(reflect(reflect), authoredVel, assist);
        Vec3 finalSpin = Vec3.lerp(physical.spin(), authoredSpin, assist);

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
     * Step size for the validation flights, seconds -- deliberately COARSER than the game's DT,
     * since a search that gives ground can fly dozens of these on the one frame a contact lands
     * on. TUNED: 1/120 s, four times the game's step; RK4 error is O(h^4), so that stays two
     * orders below the 5 cm landing margin it feeds. Do not take it coarser without re-checking
     * that arithmetic -- see docs/DESIGN.md.
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
