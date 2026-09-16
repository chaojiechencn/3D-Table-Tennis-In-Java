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

        /** Shot strength, m/s. Swing speed is mapped onto this range and never beyond it.
         *
         *  Widened from 7..13. That band was only 6 m/s wide, which is why a hard swing and a
         *  soft one produced near-identical shots: measured over a drive sweep, driving 14 m/s
         *  harder bought 1.0 m/s of extra shot speed. The floor now reaches a genuine touch
         *  shot and the ceiling a genuine drive. Note that the ceiling is ASPIRATIONAL -- the
         *  shot still has to land, and from a contact 1.1 m from the net at bat height the
         *  geometry tops out around 9.5 m/s. Raising this further does nothing on its own. */
        public double minShotSpeed = 5.0;
        public double maxShotSpeed = 17.0;

        /** Racket speed, m/s, that produces a full-strength shot. Faster than this adds
         *  nothing -- this is what stops repeated hits from compounding. */
        public double maxSwingSpeed = 16.0;

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
         *  0.20 puts a 5 m/s swipe on the edge of the box, which is a firm but ordinary sweep
         *  of the mouse -- the point of the number is that a player who swipes ACROSS the ball
         *  sees the ball go there, rather than seeing a hint of it. */
        public double aimInfluence = 0.20;

        /**
         * How much a forward drive deepens the target, per m/s.
         *
         * This and `arcInfluence` below are the two halves of the same gesture and they PULL
         * AGAINST EACH OTHER, which is the thing to understand before touching either. Driving
         * forward deepens the target through this term, and simultaneously raises `brush`,
         * which shortens it through `arcInfluence`. At the old 0.045 / 0.035 the second term
         * cancelled most of the first: measured, driving 14 m/s harder moved the landing 13 cm
         * and the depth control covered about a ninth of the table. It is now 51 cm over a
         * smooth, monotone curve.
         */
        public double depthInfluence = 0.100;

        /** How much an up/down swipe arcs the shot: up = shorter and higher, down = flatter
         *  and deeper. Fraction of the target depth range per m/s. Lowered from 0.035 -- see
         *  `depthInfluence`, which this was quietly cancelling. */
        public double arcInfluence = 0.018;

        /** How much the racket's own tilt aims the shot, on top of where it is moving. */
        public double faceInfluence = 0.25;

        /** How much hitting off-centre on the blade shifts the aim. Deliberately small -- edge
         *  contacts should feel different, not random. */
        public double contactPointInfluence = 0.30;

        /** Fraction of the physical reflection blended into the authored shot. Raised from
         *  0.06: at that level the ball was on rails, and the point of this pass was to let
         *  the player's actual stroke through. Still a minority share -- it is there so the
         *  contact feels like an impact, not so it can steer the shot on its own. */
        public double physicalBlend = 0.15;

        // ---- contact quality -------------------------------------------------------------
        //
        // How well the ball was struck, and therefore how much help the shot earns. Before
        // these existed the assist corrected EVERY contact equally and the rescue caught
        // anything it could not correct, which meant the ball could not be put out however
        // badly it was hit -- so the only way to lose a point was to miss entirely, and a
        // rally could not be won or lost on skill. These turn the assist into something the
        // player earns rather than something they are given.

        /**
         * The clean core of the blade, as a fraction of its radius: inside this the contact
         * counts as fully struck and earns the whole assist.
         *
         * 0.50 is measured, not chosen. A player pointing the cursor straight at the ball --
         * RallyTest's `aPlayerPointingAtTheBallCanReturnIt`, which is what competent play looks
         * like here -- lands the ball at 0.34 to 0.52 of the blade radius from centre, never at
         * zero. That offset is inherent: the ball is off the hitting plane in height, so the
         * cursor ray crosses the plane a little short of it (about 0.18-0.33 m, measured). A
         * core any tighter than this grades ordinary competent play as a mishit, which is
         * exactly what the first calibration did -- it failed 8 of 9 feeds.
         */
        public double qualityCore = 0.50;

        /** How far past the core the quality falls from 1 to 0. Core + this is the rim, beyond
         *  which a contact earns nothing but the floor. */
        public double qualityFalloff = 0.42;

        /** Incoming speed (m/s) at which the core starts shrinking, and the span over which it
         *  shrinks the whole way. A fast ball has to be met more precisely than a slow one. */
        public double qualityPaceFrom = 6.0;
        public double qualityPaceSpan = 12.0;

        /** How much of the core the fastest ball takes away, and the floor it cannot shrink
         *  below -- past which even a perfect player could not connect cleanly. */
        public double qualityPaceLoss = 0.22;
        public double qualityCoreMin = 0.26;

        /**
         * The assist a zero-quality contact still gets.
         *
         * Not zero, deliberately. At zero the rim of the blade returns the raw impulse, which
         * the project's own sweep puts on the table 11 times in 75 -- a shank would be fatal
         * every single time and the game would read as broken rather than hard. At 0.25 a badly
         * struck ball is usually lost and occasionally survives, which is what a mishit does in
         * the real game.
         */
        public double assistFloor = 0.25;

        /**
         * The quality below which the rescue search does not run at all.
         *
         * The rescue re-aims down the middle at any speed that works, and it was the reason no
         * contact could ever be punished. It still exists for the case it was written for -- a
         * ball met right at the net, where no fast shot is legal and the honest answer is a
         * soft lift -- but a contact off the rim no longer qualifies for it.
         */
        public double rescueQualityFloor = 0.60;

        /**
         * How much forward drive counts as brushing over the ball.
         *
         * See the derivation where `brush` is computed: 0.8 is what lets a hard pull-back reach
         * genuine backspin rather than merely less topspin.
         */
        public double driveBrush = 0.8;

        /** The reflection is capped at this speed before blending, so a violent impulse cannot
         *  leak through even at 6%. */
        public double reflectionCap = 6.0;

        /** Hard ceiling on the sideways component of the finished shot. Both a cone (degrees
         *  off straight) and an absolute m/s -- whichever binds first. This clamp has now
         *  overruled the aim twice: at 15 degrees, and again at 20 once the target box was
         *  widened to 0.90 -- the box grew and the landing did not, because the cone was
         *  binding first. 30 degrees and 4.5 m/s are what let the corner of the box actually
         *  be reached. Widening it cannot make a shot illegal on its own -- every candidate is
         *  still flown and graded. */
        public double maxHorizontalDeviationDeg = 30.0;
        public double maxLateralVelocity = 4.5;

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

        /**
         * The search may not slow a shot below this fraction of the pace the swing ASKED for.
         *
         * Without it the ladder can turn any over-ambitious swing into a legal dink, because
         * minSearchSpeed (3.0) is an absolute floor and there is nearly always SOME slow shot
         * that lands. Measured before this existed: driving at 14 m/s produced a 4.75 m/s shot
         * landing shorter than driving at 8 did -- swinging harder made a weaker shot, and the
         * ball could not be put out however hard it was hit.
         *
         * A relative floor keeps the absolute one working for the case it was written for -- a
         * contact low over the table that genuinely has no fast answer, where wantSpeed is
         * itself small and 0.70 of it is still slow -- while refusing to disguise a swing for
         * the fences as a touch shot. If the asked-for pace cannot land, the shot goes out,
         * which is the point.
         */
        public double searchSpeedFloorFrac = 0.60;

        /**
         * Above this much swing, the rescue does not run at all.
         *
         * The rescue re-aims down the middle at any speed that works. It exists for the ball met
         * right at the net, where no fast shot is legal and a soft lift is the honest answer --
         * not for a player who swung as hard as they could at a ball that would not take it.
         * Letting it cover that case is what kept the out-rate at zero on a grid of clean
         * contacts.
         */
        public double rescueEffortCeiling = 0.75;

        /** The target box on the opponent's half, as fractions of half-width / half-length.
         *  0.90 of the half-width is 0.69 m, leaving 7 cm for the solve to be wrong by. It was
         *  0.75, and before that 0.60; each widening was made for the same measured reason --
         *  a fully committed swipe still landed short of the corner. Measured now: the widest
         *  landing a swipe can reach is 0.583 m of a 0.763 m half-width, up from 0.484. It is
         *  deliberately not the whole table: landing reliably ON the line should not be
         *  available, and `landingMargin` keeps the last few centimetres out of reach. */
        public double targetHalfWidthFrac = 0.90;
        public double targetDepthMinFrac = 0.20;
        public double targetDepthMaxFrac = 0.92;

        /** Where the "safe" shot goes when a correction pass has to give ground. */
        public double safeDepthFrac = 0.55;

        /** How far each correction pass pulls the target toward safe, and how much it slows
         *  the shot. Both reduced (3 passes at 0.34 before): the correction ladder is the
         *  assist's most invisible form of help, and at the old settings it silently dragged
         *  an over-ambitious shot back to the middle of the table rather than letting the
         *  player see they had over-hit it. */
        public int maxCorrectionPasses = 2;
        public double targetAssist = 0.18;
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

        /**
         * TUNED, standing in for how hard a player brushes ACROSS the back of the ball.
         *
         * This was 2.2, which authored spin that existed on paper and did nothing you could
         * see: measured over a swipe sweep, a hard 10 m/s sweep produced 22 rev/s and bent the
         * ball 2.8 cm across the whole flight. (The lateral landing movement that came with it
         * -- half a metre of it -- is the AIM, `aimInfluence` below, not the spin.) A real
         * sidespin stroke carries 50-100 rev/s, so 2.2 was an order of magnitude short of the
         * gesture it was supposed to represent.
         *
         * 4.5 puts that same hard swipe at ~45 rev/s. It is deliberately still under the real
         * range: every candidate shot is flown WITH its spin and rejected if it does not land
         * in, so spin that curves harder makes the validator reject more and fall through to
         * the rescue -- and the rescue re-aims down the middle, which would make a committed
         * swipe feel WORSE than a weak one. Going further than this needs that measured, not
         * assumed.
         *
         * **This knob saturates, and that is the thing to know before reaching for it again.**
         * Measured A/B at 2.2 against 4.5, as in-flight bend (against the same launch with the
         * sidespin removed) and lateral travel over the 0.4 s after the bounce:
         *
         *   swipe m/s   2.2: bend / after     4.5: bend / after
         *       2.5      -4.0 cm / 5.2 cm      -7.5 cm / 1.1 cm
         *       5.0      -7.8 cm / 12.3 cm    -10.2 cm / 9.3 cm
         *      10.0      -9.4 cm / 21.8 cm     -9.7 cm / 18.3 cm
         *
         * Doubling the spin doubles the bend for an ordinary swipe and does almost nothing for
         * a hard one. The cause is not the aerodynamics -- it is that this class solves to a
         * TARGET and then validates. Given more spin the correction loop simply selects a
         * different launch that reaches the same legal landing, so the outcome is held roughly
         * fixed by the very machinery that guarantees the rally. Anyone wanting a visibly
         * bigger curve has to loosen what holds it (the lateral cone, the target box) or let
         * spin bend the shot AFTER validation -- and that second one breaks the
         * solve-constrain-validate-correct rule this class is built on. Do not just raise
         * this number and expect to see it.
         *
         * Note also the sign, which is correct and looks wrong: a swipe to the right AIMS the
         * ball right and SPINS it so it bends back left. That is real -- brushing across the
         * ball curves it opposite to the brush -- and it means the two halves of the gesture
         * partly oppose each other, exactly as they do on a real table.
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

        /*
         * The BRUSH -- how much the blade is rolling over the ball rather than pushing through
         * it -- is what authors spin, and it comes from two places.
         *
         * `lift` is the true vertical one. It used to be identically zero for the player, whose
         * blade was pinned to PlayerReach.HIT_Y, so every term reading it silently evaluated to
         * a constant and the player's topspin never varied however they swung. (The same
         * dead-axis mistake had already been found and fixed once, in Stroke.faceToward.) It is
         * live again now that the brush modifier can lift the blade, but only while that button
         * is held.
         *
         * `drive` is the other one, and it is the brush the player always has: driving up-table
         * rolls the face over the ball, pulling back opens it and cuts underneath. That is the
         * gesture the README advertises and it is closer to a real drive anyway -- forward and
         * over, not straight up.
         *
         * The gain is set so the gesture can actually reach backspin rather than merely less
         * topspin. A still blade authors baseTopspin (14 rev/s). A hard pull-back is about
         * -8 m/s of drive, which at 0.8 gives a brush of -6.4, and 14 + (-6.4 * 2.6) = -2.6
         * rev/s -- just past square, into a genuine chop. Anything less than about 0.7 here and
         * the whole backspin half of the gesture is unreachable.
         */
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

        /*
         * ---- contact quality -------------------------------------------------------------
         *
         * How cleanly this ball was struck, 1 in the middle of the blade and 0 at the rim, with
         * the usable middle shrinking as the ball arrives faster. It is measured IN THE FACE'S
         * OWN PLANE (offX, offY above), so it counts being late or early the same way it counts
         * being wide -- on a face-on disc those are the same error seen from different sides.
         *
         * Everything downstream scales off this: how much of the authored shot the player gets
         * instead of the raw bounce, and whether the rescue is willing to run at all.
         */
        double offR = Math.min(1, Math.hypot(offX, offY));
        double paceFrac = clamp((incoming.speed() - t.qualityPaceFrom) / t.qualityPaceSpan, 0, 1);
        double core = Math.max(t.qualityCoreMin, t.qualityCore - t.qualityPaceLoss * paceFrac);
        double rim  = core + t.qualityFalloff;
        double quality = clamp((rim - offR) / (rim - core), 0, 1);

        /*
         * The share of the authored shot this contact has earned. The floor is why a shank is
         * usually -- not always -- fatal; see Tuning.assistFloor.
         *
         * Only the PLAYER is graded. The opponent in the repo is `Follower`, which tracks the
         * ball's current position rather than reading where it is going, so where on its blade
         * the ball lands is an artefact of that placeholder and not a skill it is exercising.
         * Grading it made it shank four of ten ordinary feeds straight into the net, which
         * reads as a broken opponent rather than a beatable one -- difficulty has to come from
         * what the opponent CHOOSES to do, which is the October predicting opponent's job. When
         * that one arrives it can earn its assist on exactly these terms.
         */
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

        /*
         * The authored shot is what the player gets for hitting it properly; the raw bounce is
         * what they get for shanking it. A clean contact is almost all authored and lands where
         * it was aimed. A contact off the rim is mostly the real impulse off a real blade,
         * which goes wherever the geometry sends it -- usually off the table.
         *
         * This is also what stops the ball looking wrong coming off the bat. The authored
         * velocity is chosen by a solver rather than by the impact, so on a bad contact it used
         * to leave in a direction the visible collision plainly did not imply. Now the contacts
         * where the two disagree most are exactly the ones that keep the most real physics.
         *
         * The spin is blended on the same fraction, or a mishit would still come off carrying
         * an authored 14 rev/s of topspin it did nothing to earn.
         */
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
