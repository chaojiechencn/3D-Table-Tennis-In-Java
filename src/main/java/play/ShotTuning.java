package play;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Every number {@link ShotAssist} uses, immutable and validated when built. Defaults and their
 * reasoning live on the {@link Builder}. Measured restitution and friction stay in physics.Constants.
 */
public final class ShotTuning {

    public final double minShotSpeed, maxShotSpeed, maxSwingSpeed;
    public final double lateralEffort, swingInfluence, swingCurve;
    public final double incomingPaceNeutral, incomingPaceGain, incomingPaceNudge;
    public final double aimInfluence, depthInfluence, arcInfluence, faceInfluence;
    public final double contactPointInfluence, baseDepthFrac, contactDepthShare, physicalBlend;
    public final double qualityCore, qualityFalloff, qualityPaceFrom, qualityPaceSpan;
    public final double qualityPaceLoss, qualityCoreMin, assistFloor, rescueQualityFloor;
    public final double driveBrush, reflectionCap;
    public final double maxHorizontalDeviationDeg, maxLateralVelocity;
    public final double maxVerticalLaunchAngleDeg, minVerticalLaunchAngleDeg;
    public final double minForwardVelocity;
    public final int speedCandidates;
    public final double speedSpread, speedPreference, passPenalty;
    public final double minSearchSpeed, searchSpeedFloorFrac, rescueEffortCeiling;
    public final int maxCorrectionPasses;
    public final double targetAssist, speedBackoffPerPass;
    public final double targetHalfWidthFrac, targetDepthMinFrac, targetDepthMaxFrac, safeDepthFrac;
    public final double netClearance, landingMargin;
    public final double rescueMinSpeed;
    public final int rescueSpeedSteps;
    public final List<Double> rescueDepthFracs, rescueAimFracs;
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

    /** Each knob is constrained only by how the model uses it; unrelated knobs are left alone. */
    private void validate() {
        requireFinite();
        requireSigns();
        requireOrderedRanges();
        requireFractions();
        requireCounts();
    }

    private void requireFinite() {
        for (Field f : ShotTuning.class.getFields()) {
            if (f.getType() != double.class) continue;
            try {
                double v = f.getDouble(this);
                if (!Double.isFinite(v)) fail(f.getName(), "must be finite", v);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
    }

    private void requireSigns() {
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
    }

    private void requireOrderedRanges() {
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
    }

    /** Lerp weights and fractions of the table. */
    private void requireFractions() {
        fraction("physicalBlend", physicalBlend);
        fraction("assistFloor", assistFloor);
        fraction("searchSpeedFloorFrac", searchSpeedFloorFrac);
        fraction("targetHalfWidthFrac", targetHalfWidthFrac);
        fraction("targetDepthMinFrac", targetDepthMinFrac);
        fraction("targetDepthMaxFrac", targetDepthMaxFrac);
        fraction("safeDepthFrac", safeDepthFrac);
        for (double f : rescueDepthFracs) fraction("rescueDepthFracs", f);
        for (double f : rescueAimFracs) fraction("rescueAimFracs", f);
    }

    private void requireCounts() {
        if (speedCandidates < 1) fail("speedCandidates", "must be at least 1", speedCandidates);
        if (rescueSpeedSteps < 2) fail("rescueSpeedSteps", "must be at least 2", rescueSpeedSteps);   // divides by steps - 1
        if (maxCorrectionPasses < 0) fail("maxCorrectionPasses", "must not be negative", maxCorrectionPasses);
        if (speedBackoffPerPass * maxCorrectionPasses >= 1) {   // the last pass must still move forward
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

    /** The defaults, adjusted knob by knob; {@link #build} validates. */
    public static final class Builder {

        private Builder() {}

        // Strength. The top speed is aspirational: geometry caps most shots well under it.
        private double minShotSpeed = 5.0;
        private double maxShotSpeed = 17.0;
        /** Racket speed for a full-strength shot; faster adds nothing, so hits cannot compound. */
        private double maxSwingSpeed = 16.0;
        /** Low on purpose: driving through the ball makes it go, moving across aims it. */
        private double lateralEffort = 0.25;
        private double swingInfluence = 1.0;
        /** Below 1: quick early response, then diminishing returns. */
        private double swingCurve = 0.7;
        /** TUNED: incoming pace nudges the asked-for pace about 9 m/s, capped at 0.6 m/s. */
        private double incomingPaceNeutral = 9.0;
        private double incomingPaceGain = 0.05;
        private double incomingPaceNudge = 0.6;

        // Aim, as fractions of the target box per m/s of racket motion.
        /** TUNED: an ordinary firm sweep reaches the edge of the box. */
        private double aimInfluence = 0.20;
        /** Pulls against arcInfluence by design; see docs/DESIGN.md before retuning either. */
        private double depthInfluence = 0.100;
        private double arcInfluence = 0.018;
        private double faceInfluence = 0.25;
        /** Small: an edge contact should feel different, not random. */
        private double contactPointInfluence = 0.30;
        /** TUNED: a still, centred contact aims a little short, leaving a drive room to deepen. */
        private double baseDepthFrac = 0.35;
        /** TUNED: contact height moves depth half as much as contact width moves aim. */
        private double contactDepthShare = 0.5;
        /** Enough reflection to feel like an impact, too little to steer. */
        private double physicalBlend = 0.15;

        // Contact quality: what lets a shank lose the point. See docs/DESIGN.md.
        /** Clean core as a fraction of blade radius. FLOOR 0.50, TUNED to 0.58 for average players. */
        private double qualityCore = 0.58;
        /** TUNED wider than 0.42 so clean-to-mishit is a slope, not a cliff. */
        private double qualityFalloff = 0.50;
        /** A fast ball must be met more precisely: the core shrinks from this pace over this span. */
        private double qualityPaceFrom = 6.0;
        private double qualityPaceSpan = 12.0;
        /** TUNED down from 0.22 so fast balls keep the forgiveness qualityCore bought. */
        private double qualityPaceLoss = 0.15;
        private double qualityCoreMin = 0.26;
        /** TUNED: raw rim impulses land 11 times in 75, so a shank still gets some help. */
        private double assistFloor = 0.35;
        /** A rim contact does not qualify for the rescue search. */
        private double rescueQualityFloor = 0.60;

        // Constraints.
        /** Lets a hard pull-back reach genuine backspin, not merely less topspin. */
        private double driveBrush = 0.8;
        /** Caps the reflection before blending, so a violent impulse cannot leak through. */
        private double reflectionCap = 6.0;
        private double maxHorizontalDeviationDeg = 30.0;
        private double maxLateralVelocity = 4.5;
        /** A sanity guard, not a shaping tool: real drives off low balls launch downward. */
        private double maxVerticalLaunchAngleDeg = 45.0;
        private double minVerticalLaunchAngleDeg = -20.0;
        private double minForwardVelocity = 4.5;

        // Search. Illegality outweighs the speed and pass preferences by two orders of magnitude.
        private int speedCandidates = 5;
        private double speedSpread = 0.42;
        private double speedPreference = 1.0;
        private double passPenalty = 2.0;
        /** Slowest shot the main search considers, below the slowest the swing may ask for. */
        private double minSearchSpeed = 3.0;
        /** Stops the ladder disguising an over-ambitious swing as a legal dink. */
        private double searchSpeedFloorFrac = 0.60;
        /** A hard swing that would not land is not rescued. */
        private double rescueEffortCeiling = 0.75;
        /** Kept small, or an over-hit is silently dragged back to the middle. */
        private int maxCorrectionPasses = 2;
        private double targetAssist = 0.18;
        private double speedBackoffPerPass = 0.13;

        // Target box on the opponent's half, as fractions of half-width and half-length.
        private double targetHalfWidthFrac = 0.90;
        private double targetDepthMinFrac = 0.20;
        private double targetDepthMaxFrac = 0.92;
        private double safeDepthFrac = 0.55;
        /** Metres above the cord and inside the lines the validator insists on. */
        private double netClearance = 0.055;
        private double landingMargin = 0.05;

        // Rescue: slower than minShotSpeed and re-aimed, keeping the full aim first.
        private double rescueMinSpeed = 3.0;
        private int rescueSpeedSteps = 9;
        private List<Double> rescueDepthFracs = List.of(0.55, 0.72, 0.88, 0.40);
        private List<Double> rescueAimFracs = List.of(1.0, 0.6, 0.3, 0.0);

        // Spin, rev/s, capped so it stays a secondary influence.
        private double spinInfluence = 1.0;
        private double baseTopspin = 14.0;
        private double topspinPerLift = 2.6;
        /** TUNED, and saturating: validation re-aims rather than curving more. Swipe right bends left. */
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
