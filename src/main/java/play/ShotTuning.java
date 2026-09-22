package play;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Every number the arcade shot model ({@link ShotAssist}) uses, immutable and validated when
 * built. Each knob's meaning, and the reasoning behind its default, is documented on the
 * {@link Builder} field of the same name -- that is where the value is chosen.
 *
 * Bounce restitution and friction are deliberately NOT here: they are measured values with
 * citations, single-sourced in physics/Constants and graded by SelfTest.
 */
public final class ShotTuning {

    // ---- strength
    public final double minShotSpeed, maxShotSpeed, maxSwingSpeed;
    public final double lateralEffort, swingInfluence, swingCurve;
    public final double incomingPaceNeutral, incomingPaceGain, incomingPaceNudge;
    // ---- aim
    public final double aimInfluence, depthInfluence, arcInfluence, faceInfluence;
    public final double contactPointInfluence, baseDepthFrac, contactDepthShare, physicalBlend;
    // ---- contact quality
    public final double qualityCore, qualityFalloff, qualityPaceFrom, qualityPaceSpan;
    public final double qualityPaceLoss, qualityCoreMin, assistFloor, rescueQualityFloor;
    // ---- constraints
    public final double driveBrush, reflectionCap;
    public final double maxHorizontalDeviationDeg, maxLateralVelocity;
    public final double maxVerticalLaunchAngleDeg, minVerticalLaunchAngleDeg;
    public final double minForwardVelocity;
    // ---- search
    public final int speedCandidates;
    public final double speedSpread, speedPreference, passPenalty;
    public final double minSearchSpeed, searchSpeedFloorFrac, rescueEffortCeiling;
    public final int maxCorrectionPasses;
    public final double targetAssist, speedBackoffPerPass;
    // ---- target box and validation
    public final double targetHalfWidthFrac, targetDepthMinFrac, targetDepthMaxFrac, safeDepthFrac;
    public final double netClearance, landingMargin;
    // ---- rescue
    public final double rescueMinSpeed;
    public final int rescueSpeedSteps;
    public final List<Double> rescueDepthFracs, rescueAimFracs;
    // ---- spin
    public final double spinInfluence, baseTopspin, topspinPerLift, sidespinPerSwipe, maxSpin;

    private static final ShotTuning DEFAULTS = builder().build();

    /** The shipped tuning. */
    public static ShotTuning defaults() { return DEFAULTS; }

    public static Builder builder() { return new Builder(); }

    private ShotTuning(Builder b) {
        minShotSpeed = b.minShotSpeed; maxShotSpeed = b.maxShotSpeed; maxSwingSpeed = b.maxSwingSpeed;
        lateralEffort = b.lateralEffort; swingInfluence = b.swingInfluence; swingCurve = b.swingCurve;
        incomingPaceNeutral = b.incomingPaceNeutral; incomingPaceGain = b.incomingPaceGain;
        incomingPaceNudge = b.incomingPaceNudge;
        aimInfluence = b.aimInfluence; depthInfluence = b.depthInfluence; arcInfluence = b.arcInfluence;
        faceInfluence = b.faceInfluence; contactPointInfluence = b.contactPointInfluence;
        baseDepthFrac = b.baseDepthFrac; contactDepthShare = b.contactDepthShare;
        physicalBlend = b.physicalBlend;
        qualityCore = b.qualityCore; qualityFalloff = b.qualityFalloff;
        qualityPaceFrom = b.qualityPaceFrom; qualityPaceSpan = b.qualityPaceSpan;
        qualityPaceLoss = b.qualityPaceLoss; qualityCoreMin = b.qualityCoreMin;
        assistFloor = b.assistFloor; rescueQualityFloor = b.rescueQualityFloor;
        driveBrush = b.driveBrush; reflectionCap = b.reflectionCap;
        maxHorizontalDeviationDeg = b.maxHorizontalDeviationDeg; maxLateralVelocity = b.maxLateralVelocity;
        maxVerticalLaunchAngleDeg = b.maxVerticalLaunchAngleDeg;
        minVerticalLaunchAngleDeg = b.minVerticalLaunchAngleDeg;
        minForwardVelocity = b.minForwardVelocity;
        speedCandidates = b.speedCandidates; speedSpread = b.speedSpread;
        speedPreference = b.speedPreference; passPenalty = b.passPenalty;
        minSearchSpeed = b.minSearchSpeed; searchSpeedFloorFrac = b.searchSpeedFloorFrac;
        rescueEffortCeiling = b.rescueEffortCeiling;
        maxCorrectionPasses = b.maxCorrectionPasses; targetAssist = b.targetAssist;
        speedBackoffPerPass = b.speedBackoffPerPass;
        targetHalfWidthFrac = b.targetHalfWidthFrac; targetDepthMinFrac = b.targetDepthMinFrac;
        targetDepthMaxFrac = b.targetDepthMaxFrac; safeDepthFrac = b.safeDepthFrac;
        netClearance = b.netClearance; landingMargin = b.landingMargin;
        rescueMinSpeed = b.rescueMinSpeed; rescueSpeedSteps = b.rescueSpeedSteps;
        rescueDepthFracs = List.copyOf(b.rescueDepthFracs);
        rescueAimFracs = List.copyOf(b.rescueAimFracs);
        spinInfluence = b.spinInfluence; baseTopspin = b.baseTopspin; topspinPerLift = b.topspinPerLift;
        sidespinPerSwipe = b.sidespinPerSwipe; maxSpin = b.maxSpin;
        validate();
    }

    /**
     * Reject a tuning the shot model cannot run on. Every number must be finite; beyond that, a
     * knob is constrained only by how the model actually uses it -- a divisor or exponent must be
     * positive, a cap non-negative, a range ordered, a lerp weight or table fraction in [0, 1].
     */
    private void validate() {
        for (Field f : ShotTuning.class.getFields()) {
            if (f.getType() != double.class) continue;
            try {
                double v = f.getDouble(this);
                if (!Double.isFinite(v)) fail(f.getName(), "must be finite", v);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }

        positive("minShotSpeed", minShotSpeed);
        positive("rescueMinSpeed", rescueMinSpeed);
        positive("maxSwingSpeed", maxSwingSpeed);          // divides the swing effort
        positive("swingCurve", swingCurve);                // exponent of the strength curve
        positive("qualityFalloff", qualityFalloff);        // divides the quality slope
        positive("qualityPaceSpan", qualityPaceSpan);      // divides the pace fraction

        nonNegative("reflectionCap", reflectionCap);
        nonNegative("maxLateralVelocity", maxLateralVelocity);
        nonNegative("maxSpin", maxSpin);
        nonNegative("targetAssist", targetAssist);
        nonNegative("speedBackoffPerPass", speedBackoffPerPass);
        nonNegative("incomingPaceNudge", incomingPaceNudge);

        ordered("minShotSpeed", minShotSpeed, "maxShotSpeed", maxShotSpeed);
        ordered("rescueMinSpeed", rescueMinSpeed, "maxShotSpeed", maxShotSpeed);
        ordered("targetDepthMinFrac", targetDepthMinFrac, "targetDepthMaxFrac", targetDepthMaxFrac);
        if (!(minVerticalLaunchAngleDeg < maxVerticalLaunchAngleDeg)) {
            fail("minVerticalLaunchAngleDeg", "must be below maxVerticalLaunchAngleDeg", minVerticalLaunchAngleDeg);
        }
        within("minVerticalLaunchAngleDeg", minVerticalLaunchAngleDeg, -90, 90);   // fed to tan()
        within("maxVerticalLaunchAngleDeg", maxVerticalLaunchAngleDeg, -90, 90);
        if (maxHorizontalDeviationDeg < 0 || maxHorizontalDeviationDeg >= 90) {
            fail("maxHorizontalDeviationDeg", "must be in [0, 90)", maxHorizontalDeviationDeg);
        }

        fraction("physicalBlend", physicalBlend);
        fraction("assistFloor", assistFloor);
        fraction("searchSpeedFloorFrac", searchSpeedFloorFrac);
        fraction("targetHalfWidthFrac", targetHalfWidthFrac);
        fraction("targetDepthMinFrac", targetDepthMinFrac);
        fraction("targetDepthMaxFrac", targetDepthMaxFrac);
        fraction("safeDepthFrac", safeDepthFrac);
        for (double f : rescueDepthFracs) fraction("rescueDepthFracs", f);
        for (double f : rescueAimFracs) fraction("rescueAimFracs", f);

        if (speedCandidates < 1) fail("speedCandidates", "must be at least 1", speedCandidates);
        if (rescueSpeedSteps < 2) fail("rescueSpeedSteps", "must be at least 2", rescueSpeedSteps);
        if (maxCorrectionPasses < 0) fail("maxCorrectionPasses", "must not be negative", maxCorrectionPasses);
        // Each pass slows the asked-for pace by this much; the last pass must still move forward.
        if (speedBackoffPerPass * maxCorrectionPasses >= 1) {
            fail("speedBackoffPerPass", "times maxCorrectionPasses must stay below 1", speedBackoffPerPass);
        }
    }

    private static void positive(String name, double v)    { if (!(v > 0)) fail(name, "must be > 0", v); }
    private static void nonNegative(String name, double v) { if (!(v >= 0)) fail(name, "must be >= 0", v); }
    private static void fraction(String name, double v) {
        if (!(v >= 0 && v <= 1)) fail(name, "must be in [0, 1]", v);
    }
    private static void within(String name, double v, double lo, double hi) {
        if (!(v > lo && v < hi)) fail(name, "must be in (" + lo + ", " + hi + ")", v);
    }
    private static void ordered(String loName, double lo, String hiName, double hi) {
        if (!(lo <= hi)) fail(loName, "must not exceed " + hiName + " (" + hi + ")", lo);
    }
    private static void fail(String name, String rule, double v) {
        throw new IllegalArgumentException("ShotTuning." + name + " " + rule + ", was " + v);
    }

    /** The defaults, adjusted knob by knob. {@link #build} validates. */
    public static final class Builder {

        private Builder() {}

        // ---- strength ----------------------------------------------------------------------

        /** Shot strength, m/s. Swing speed maps onto this range and never beyond it. The ceiling
         *  is ASPIRATIONAL -- a shot still has to land, and geometry alone caps most contacts
         *  well under it, so raising this alone does nothing. */
        private double minShotSpeed = 5.0;
        private double maxShotSpeed = 17.0;

        /** Racket speed, m/s, that produces a full-strength shot. Faster adds nothing -- this is
         *  what stops repeated hits from compounding. */
        private double maxSwingSpeed = 16.0;

        /** How much a sideways or upward swipe counts toward shot STRENGTH, next to the forward
         *  drive. Low on purpose: driving through the ball is what makes it go, moving across is
         *  how you aim. */
        private double lateralEffort = 0.25;

        /** How much of the swing reaches the shot at all (0 = every shot the same strength). */
        private double swingInfluence = 1.0;

        /** Shape of swing -> strength. 1 = linear; below 1 = quick early response then
         *  diminishing returns, which is what makes a hard swing feel controlled. */
        private double swingCurve = 0.7;

        /** The incoming ball nudges the asked-for pace -- faster in, slightly faster out, about
         *  this neutral speed (m/s), at this gain, capped at this many m/s either way. It is never
         *  ADDED to the swing, which is what stops a rally compounding into a rocket. TUNED. */
        private double incomingPaceNeutral = 9.0;
        private double incomingPaceGain = 0.05;
        private double incomingPaceNudge = 0.6;

        // ---- aim ---------------------------------------------------------------------------

        /** How far a sideways swipe moves the aim, as a fraction of the target box per m/s.
         *  TUNED: 0.20 puts an ordinary firm sweep on the edge of the box. */
        private double aimInfluence = 0.20;

        /** How much a forward drive deepens the target, per m/s. Pulls against `arcInfluence`
         *  below by design -- driving forward deepens the target through this term and raises
         *  `brush`, which shortens it through that one. See docs/DESIGN.md before retuning
         *  either. */
        private double depthInfluence = 0.100;

        /** How much an up/down swipe arcs the shot: up = shorter and higher, down = flatter and
         *  deeper. Fraction of the target depth range per m/s; see `depthInfluence`, which this
         *  partly cancels on purpose. */
        private double arcInfluence = 0.018;

        /** How much the racket's own tilt aims the shot, on top of where it is moving. */
        private double faceInfluence = 0.25;

        /** How much hitting off-centre on the blade shifts the aim. Deliberately small -- edge
         *  contacts should feel different, not random. */
        private double contactPointInfluence = 0.30;

        /** Where in the target depth range a still, centred contact aims, as a fraction of it.
         *  TUNED: a little short of the middle, so a drive has room to deepen it. */
        private double baseDepthFrac = 0.35;

        /** How much a high or low contact moves the depth, relative to how much a wide one moves
         *  the aim -- half, so the blade's height is felt without dominating. TUNED. */
        private double contactDepthShare = 0.5;

        /** Fraction of the physical reflection blended into the authored shot -- there so a
         *  contact feels like an impact, not so it can steer the shot on its own. */
        private double physicalBlend = 0.15;

        // ---- contact quality ---------------------------------------------------------------
        // How well the ball was struck, and how much help the shot earns for it. Without these
        // a shank could never be punished and a rally could not be won or lost on skill; see
        // docs/DESIGN.md.

        /** The clean core of the blade, as a fraction of its radius: inside this a contact counts
         *  as fully struck. FLOOR 0.50, measured against RallyTest's own competent-play probe;
         *  TUNED up to 0.58 above that floor for an average player. See docs/DESIGN.md. */
        private double qualityCore = 0.58;

        /** How far past the core the quality falls from 1 to 0; core + this is the rim. TUNED
         *  wider (was 0.42) so the drop from clean to mishit is a slope, not a cliff. */
        private double qualityFalloff = 0.50;

        /** Incoming speed (m/s) at which the core starts shrinking, and the span over which it
         *  shrinks the whole way. A fast ball has to be met more precisely than a slow one. */
        private double qualityPaceFrom = 6.0;
        private double qualityPaceSpan = 12.0;

        /** How much of the core the fastest ball takes away, and the floor it cannot shrink
         *  below. TUNED down from 0.22 so a fast ball -- already the hardest thing to time --
         *  does not lose the forgiveness qualityCore just bought. */
        private double qualityPaceLoss = 0.15;
        private double qualityCoreMin = 0.26;

        /** The assist a zero-quality contact still gets. Not zero -- the raw rim impulse lands on
         *  the table only 11 times in 75 (see docs/DESIGN.md), which would make a shank fatal
         *  every time. TUNED up to 0.35 so a shank stays clearly worse than a clean hit without
         *  being close to an automatic loss. */
        private double assistFloor = 0.35;

        /** The quality below which the rescue search does not run at all -- it still exists for
         *  a ball met right at the net with no fast legal shot, but a contact off the rim no
         *  longer qualifies for it. */
        private double rescueQualityFloor = 0.60;

        // ---- constraints -------------------------------------------------------------------

        /** How much forward drive counts as brushing over the ball; 0.8 is what lets a hard
         *  pull-back reach genuine backspin rather than merely less topspin. */
        private double driveBrush = 0.8;

        /** The reflection is capped at this speed before blending, so a violent impulse cannot
         *  leak through even at a small blend fraction. */
        private double reflectionCap = 6.0;

        /** Hard ceiling on the sideways component of the finished shot: a cone (degrees off
         *  straight) and an absolute m/s, whichever binds first. Widening either cannot make a
         *  shot illegal on its own -- every candidate is still flown and graded. */
        private double maxHorizontalDeviationDeg = 30.0;
        private double maxLateralVelocity = 4.5;

        /** Launch elevation band -- a SANITY GUARD, not a shaping tool. Aim owns the elevation; a
         *  real drive off a low ball near the baseline genuinely launches downward, so do not
         *  raise the floor above zero without re-deriving it (see docs/DESIGN.md). */
        private double maxVerticalLaunchAngleDeg = 45.0;
        private double minVerticalLaunchAngleDeg = -20.0;

        /** Always at least this much pace toward the opponent. */
        private double minForwardVelocity = 4.5;

        // ---- search ------------------------------------------------------------------------

        /** Shot speed and target depth are not independent, so the search tries a spread of
         *  speeds around the one the swing asked for and keeps the legal candidate closest to
         *  it. */
        private int speedCandidates = 5;
        private double speedSpread = 0.42;

        /** Penalty per m/s for not being the speed the swing asked for, and per correction pass
         *  for having had to give ground. Only ever separates candidates that are both already
         *  legal -- illegality outweighs both by two orders of magnitude. */
        private double speedPreference = 1.0;
        private double passPenalty = 2.0;

        /** The slowest shot the MAIN search may consider, m/s -- separate from minShotSpeed (the
         *  slowest the swing may ASK for) because some contacts have no legal fast answer and
         *  must fall back to a slow, legal one rather than the rescue. See docs/DESIGN.md. */
        private double minSearchSpeed = 3.0;

        /** The search may not slow a shot below this fraction of the pace the swing ASKED for,
         *  or the ladder could disguise any over-ambitious swing as a legal dink -- see
         *  docs/DESIGN.md for the measured case this guards against. */
        private double searchSpeedFloorFrac = 0.60;

        /** Above this much swing, the rescue does not run at all -- it is for a ball met right
         *  at the net with no fast legal answer, not for a hard swing that simply would not
         *  land. */
        private double rescueEffortCeiling = 0.75;

        /** How far each correction pass pulls the target toward safe, and how much it slows the
         *  shot. Kept small -- too much silently drags an over-hit shot back to the middle
         *  instead of letting the player see they overhit it. */
        private int maxCorrectionPasses = 2;
        private double targetAssist = 0.18;
        private double speedBackoffPerPass = 0.13;

        // ---- target box and validation -----------------------------------------------------

        /** The target box on the opponent's half, as fractions of half-width / half-length.
         *  Deliberately not the whole table -- `landingMargin` keeps the last few centimetres
         *  out of reach. See docs/DESIGN.md for the widening history. */
        private double targetHalfWidthFrac = 0.90;
        private double targetDepthMinFrac = 0.20;
        private double targetDepthMaxFrac = 0.92;

        /** Where the "safe" shot goes when a correction pass has to give ground. */
        private double safeDepthFrac = 0.55;

        /** Clearance above the cord the validator insists on, metres. */
        private double netClearance = 0.055;

        /** Margin inside the sidelines / end line the landing must keep, metres. */
        private double landingMargin = 0.05;

        // ---- rescue ------------------------------------------------------------------------

        /** The rescue search, used only when the normal search finds nothing legal. Allowed to
         *  go slower than minShotSpeed and to re-aim, for a ball met right at the net that can
         *  only be lifted softly over. */
        private double rescueMinSpeed = 3.0;
        private int rescueSpeedSteps = 9;
        private List<Double> rescueDepthFracs = List.of(0.55, 0.72, 0.88, 0.40);

        /** How much of the player's lateral aim the rescue keeps, tried in this order -- the
         *  full aim first, given up only if nothing there is legal, so a rescued shot keeps the
         *  player's aim instead of always centring. */
        private List<Double> rescueAimFracs = List.of(1.0, 0.6, 0.3, 0.0);

        // ---- spin --------------------------------------------------------------------------

        /** Spin, rev/s. Topspin comes from an upward swipe, sidespin from a sideways one. Capped
         *  so spin stays a secondary influence and never a source of chaos. */
        private double spinInfluence = 1.0;
        private double baseTopspin = 14.0;
        private double topspinPerLift = 2.6;

        /** TUNED, standing in for how hard a player brushes ACROSS the back of the ball. This
         *  knob SATURATES: every candidate shot is solved to a target and validated, so more spin
         *  mostly makes the solver pick a different launch to the same legal landing rather than
         *  visibly curving the ball more. See docs/DESIGN.md for the measured A/B before reaching
         *  for this again. Note the sign: a swipe right AIMS the ball right but SPINS it to bend
         *  back left, which is real -- brushing across curves the ball opposite the brush. */
        private double sidespinPerSwipe = 4.5;
        private double maxSpin = 55.0;

        public ShotTuning build() { return new ShotTuning(this); }

        public Builder minShotSpeed(double v)              { minShotSpeed = v; return this; }
        public Builder maxShotSpeed(double v)              { maxShotSpeed = v; return this; }
        public Builder maxSwingSpeed(double v)             { maxSwingSpeed = v; return this; }
        public Builder lateralEffort(double v)             { lateralEffort = v; return this; }
        public Builder swingInfluence(double v)            { swingInfluence = v; return this; }
        public Builder swingCurve(double v)                { swingCurve = v; return this; }
        public Builder incomingPaceNeutral(double v)       { incomingPaceNeutral = v; return this; }
        public Builder incomingPaceGain(double v)          { incomingPaceGain = v; return this; }
        public Builder incomingPaceNudge(double v)         { incomingPaceNudge = v; return this; }
        public Builder aimInfluence(double v)              { aimInfluence = v; return this; }
        public Builder depthInfluence(double v)            { depthInfluence = v; return this; }
        public Builder arcInfluence(double v)              { arcInfluence = v; return this; }
        public Builder faceInfluence(double v)             { faceInfluence = v; return this; }
        public Builder contactPointInfluence(double v)     { contactPointInfluence = v; return this; }
        public Builder baseDepthFrac(double v)             { baseDepthFrac = v; return this; }
        public Builder contactDepthShare(double v)         { contactDepthShare = v; return this; }
        public Builder physicalBlend(double v)             { physicalBlend = v; return this; }
        public Builder qualityCore(double v)               { qualityCore = v; return this; }
        public Builder qualityFalloff(double v)            { qualityFalloff = v; return this; }
        public Builder qualityPaceFrom(double v)           { qualityPaceFrom = v; return this; }
        public Builder qualityPaceSpan(double v)           { qualityPaceSpan = v; return this; }
        public Builder qualityPaceLoss(double v)           { qualityPaceLoss = v; return this; }
        public Builder qualityCoreMin(double v)            { qualityCoreMin = v; return this; }
        public Builder assistFloor(double v)               { assistFloor = v; return this; }
        public Builder rescueQualityFloor(double v)        { rescueQualityFloor = v; return this; }
        public Builder driveBrush(double v)                { driveBrush = v; return this; }
        public Builder reflectionCap(double v)             { reflectionCap = v; return this; }
        public Builder maxHorizontalDeviationDeg(double v) { maxHorizontalDeviationDeg = v; return this; }
        public Builder maxLateralVelocity(double v)        { maxLateralVelocity = v; return this; }
        public Builder maxVerticalLaunchAngleDeg(double v) { maxVerticalLaunchAngleDeg = v; return this; }
        public Builder minVerticalLaunchAngleDeg(double v) { minVerticalLaunchAngleDeg = v; return this; }
        public Builder minForwardVelocity(double v)        { minForwardVelocity = v; return this; }
        public Builder speedCandidates(int v)              { speedCandidates = v; return this; }
        public Builder speedSpread(double v)               { speedSpread = v; return this; }
        public Builder speedPreference(double v)           { speedPreference = v; return this; }
        public Builder passPenalty(double v)               { passPenalty = v; return this; }
        public Builder minSearchSpeed(double v)            { minSearchSpeed = v; return this; }
        public Builder searchSpeedFloorFrac(double v)      { searchSpeedFloorFrac = v; return this; }
        public Builder rescueEffortCeiling(double v)       { rescueEffortCeiling = v; return this; }
        public Builder maxCorrectionPasses(int v)          { maxCorrectionPasses = v; return this; }
        public Builder targetAssist(double v)              { targetAssist = v; return this; }
        public Builder speedBackoffPerPass(double v)       { speedBackoffPerPass = v; return this; }
        public Builder targetHalfWidthFrac(double v)       { targetHalfWidthFrac = v; return this; }
        public Builder targetDepthMinFrac(double v)        { targetDepthMinFrac = v; return this; }
        public Builder targetDepthMaxFrac(double v)        { targetDepthMaxFrac = v; return this; }
        public Builder safeDepthFrac(double v)             { safeDepthFrac = v; return this; }
        public Builder netClearance(double v)              { netClearance = v; return this; }
        public Builder landingMargin(double v)             { landingMargin = v; return this; }
        public Builder rescueMinSpeed(double v)            { rescueMinSpeed = v; return this; }
        public Builder rescueSpeedSteps(int v)             { rescueSpeedSteps = v; return this; }
        public Builder rescueDepthFracs(double... v)       { rescueDepthFracs = boxed(v); return this; }
        public Builder rescueAimFracs(double... v)         { rescueAimFracs = boxed(v); return this; }
        public Builder spinInfluence(double v)             { spinInfluence = v; return this; }
        public Builder baseTopspin(double v)               { baseTopspin = v; return this; }
        public Builder topspinPerLift(double v)            { topspinPerLift = v; return this; }
        public Builder sidespinPerSwipe(double v)          { sidespinPerSwipe = v; return this; }
        public Builder maxSpin(double v)                   { maxSpin = v; return this; }

        private static List<Double> boxed(double[] v) {
            return java.util.Arrays.stream(v).boxed().toList();
        }
    }
}
