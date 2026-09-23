package tabletennis.game;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Every number {@link ShotAssist} uses, immutable and validated when built. Defaults and their
 * reasoning live on the {@link Builder}. Measured restitution and friction stay in physics.Constants.
 */
public final class ShotTuning {

    public final double MinShotSpeed, MaxShotSpeed, MaxSwingSpeed;
    public final double LateralEffort, SwingInfluence, SwingCurve;
    public final double IncomingPaceNeutral, IncomingPaceGain, IncomingPaceNudge;
    public final double AimInfluence, DepthInfluence, ArcInfluence, FaceInfluence;
    public final double ContactPointInfluence, BaseDepthFrac, ContactDepthShare, PhysicalBlend;
    public final double QualityCore, QualityFalloff, QualityPaceFrom, QualityPaceSpan;
    public final double QualityPaceLoss, QualityCoreMin, AssistFloor, RescueQualityFloor;
    public final double DriveBrush, ReflectionCap;
    public final double MaxHorizontalDeviationDeg, MaxLateralVelocity;
    public final double MaxVerticalLaunchAngleDeg, MinVerticalLaunchAngleDeg;
    public final double MinForwardVelocity;
    public final int SpeedCandidates;
    public final double SpeedSpread, SpeedPreference, PassPenalty;
    public final double MinSearchSpeed, SearchSpeedFloorFrac, RescueEffortCeiling;
    public final int MaxCorrectionPasses;
    public final double TargetAssist, SpeedBackoffPerPass;
    public final double TargetHalfWidthFrac, TargetDepthMinFrac, TargetDepthMaxFrac, SafeDepthFrac;
    public final double NetClearance, LandingMargin;
    public final double RescueMinSpeed;
    public final int RescueSpeedSteps;
    public final List<Double> RescueDepthFracs, RescueAimFracs;
    public final double SpinInfluence, BaseTopspin, TopspinPerLift, SidespinPerSwipe, MaxSpin;

    private static final ShotTuning Defaults = Builder().Build();

    /** The shipped tuning. */
    public static ShotTuning Defaults() { return Defaults; }

    public static Builder Builder() { return new Builder(); }

    private ShotTuning(Builder B) {
        MinShotSpeed = B.MinShotSpeed; MaxShotSpeed = B.MaxShotSpeed; MaxSwingSpeed = B.MaxSwingSpeed;
        LateralEffort = B.LateralEffort; SwingInfluence = B.SwingInfluence; SwingCurve = B.SwingCurve;
        IncomingPaceNeutral = B.IncomingPaceNeutral; IncomingPaceGain = B.IncomingPaceGain;
        IncomingPaceNudge = B.IncomingPaceNudge;
        AimInfluence = B.AimInfluence; DepthInfluence = B.DepthInfluence; ArcInfluence = B.ArcInfluence;
        FaceInfluence = B.FaceInfluence; ContactPointInfluence = B.ContactPointInfluence;
        BaseDepthFrac = B.BaseDepthFrac; ContactDepthShare = B.ContactDepthShare;
        PhysicalBlend = B.PhysicalBlend;
        QualityCore = B.QualityCore; QualityFalloff = B.QualityFalloff;
        QualityPaceFrom = B.QualityPaceFrom; QualityPaceSpan = B.QualityPaceSpan;
        QualityPaceLoss = B.QualityPaceLoss; QualityCoreMin = B.QualityCoreMin;
        AssistFloor = B.AssistFloor; RescueQualityFloor = B.RescueQualityFloor;
        DriveBrush = B.DriveBrush; ReflectionCap = B.ReflectionCap;
        MaxHorizontalDeviationDeg = B.MaxHorizontalDeviationDeg; MaxLateralVelocity = B.MaxLateralVelocity;
        MaxVerticalLaunchAngleDeg = B.MaxVerticalLaunchAngleDeg;
        MinVerticalLaunchAngleDeg = B.MinVerticalLaunchAngleDeg;
        MinForwardVelocity = B.MinForwardVelocity;
        SpeedCandidates = B.SpeedCandidates; SpeedSpread = B.SpeedSpread;
        SpeedPreference = B.SpeedPreference; PassPenalty = B.PassPenalty;
        MinSearchSpeed = B.MinSearchSpeed; SearchSpeedFloorFrac = B.SearchSpeedFloorFrac;
        RescueEffortCeiling = B.RescueEffortCeiling;
        MaxCorrectionPasses = B.MaxCorrectionPasses; TargetAssist = B.TargetAssist;
        SpeedBackoffPerPass = B.SpeedBackoffPerPass;
        TargetHalfWidthFrac = B.TargetHalfWidthFrac; TargetDepthMinFrac = B.TargetDepthMinFrac;
        TargetDepthMaxFrac = B.TargetDepthMaxFrac; SafeDepthFrac = B.SafeDepthFrac;
        NetClearance = B.NetClearance; LandingMargin = B.LandingMargin;
        RescueMinSpeed = B.RescueMinSpeed; RescueSpeedSteps = B.RescueSpeedSteps;
        RescueDepthFracs = List.copyOf(B.RescueDepthFracs);
        RescueAimFracs = List.copyOf(B.RescueAimFracs);
        SpinInfluence = B.SpinInfluence; BaseTopspin = B.BaseTopspin; TopspinPerLift = B.TopspinPerLift;
        SidespinPerSwipe = B.SidespinPerSwipe; MaxSpin = B.MaxSpin;
        Validate();
    }

    /** Each knob is constrained only by how the model uses it; unrelated knobs are left alone. */
    private void Validate() {
        RequireFinite();
        RequireSigns();
        RequireOrderedRanges();
        RequireFractions();
        RequireCounts();
    }

    private void RequireFinite() {
        for (Field F : ShotTuning.class.getFields()) {
            if (F.getType() != double.class) continue;
            try {
                double V = F.getDouble(this);
                if (!Double.isFinite(V)) Fail(F.getName(), "must be finite", V);
            } catch (IllegalAccessException E) {
                throw new AssertionError(E);
            }
        }
    }

    private void RequireSigns() {
        Positive("MinShotSpeed", MinShotSpeed);
        Positive("RescueMinSpeed", RescueMinSpeed);
        Positive("MaxSwingSpeed", MaxSwingSpeed);          // divides the swing effort
        Positive("SwingCurve", SwingCurve);                // exponent of the strength curve
        Positive("QualityFalloff", QualityFalloff);        // divides the quality slope
        Positive("QualityPaceSpan", QualityPaceSpan);      // divides the pace fraction

        NonNegative("ReflectionCap", ReflectionCap);
        NonNegative("MaxLateralVelocity", MaxLateralVelocity);
        NonNegative("MaxSpin", MaxSpin);
        NonNegative("TargetAssist", TargetAssist);
        NonNegative("SpeedBackoffPerPass", SpeedBackoffPerPass);
        NonNegative("IncomingPaceNudge", IncomingPaceNudge);
    }

    private void RequireOrderedRanges() {
        Ordered("MinShotSpeed", MinShotSpeed, "MaxShotSpeed", MaxShotSpeed);
        Ordered("RescueMinSpeed", RescueMinSpeed, "MaxShotSpeed", MaxShotSpeed);
        Ordered("TargetDepthMinFrac", TargetDepthMinFrac, "TargetDepthMaxFrac", TargetDepthMaxFrac);
        if (!(MinVerticalLaunchAngleDeg < MaxVerticalLaunchAngleDeg)) {
            Fail("MinVerticalLaunchAngleDeg", "must be below MaxVerticalLaunchAngleDeg", MinVerticalLaunchAngleDeg);
        }
        Within("MinVerticalLaunchAngleDeg", MinVerticalLaunchAngleDeg, -90, 90);   // fed to tan()
        Within("MaxVerticalLaunchAngleDeg", MaxVerticalLaunchAngleDeg, -90, 90);
        if (MaxHorizontalDeviationDeg < 0 || MaxHorizontalDeviationDeg >= 90) {
            Fail("MaxHorizontalDeviationDeg", "must be in [0, 90)", MaxHorizontalDeviationDeg);
        }
    }

    /** Lerp weights and fractions of the table. */
    private void RequireFractions() {
        Fraction("PhysicalBlend", PhysicalBlend);
        Fraction("AssistFloor", AssistFloor);
        Fraction("SearchSpeedFloorFrac", SearchSpeedFloorFrac);
        Fraction("TargetHalfWidthFrac", TargetHalfWidthFrac);
        Fraction("TargetDepthMinFrac", TargetDepthMinFrac);
        Fraction("TargetDepthMaxFrac", TargetDepthMaxFrac);
        Fraction("SafeDepthFrac", SafeDepthFrac);
        for (double F : RescueDepthFracs) Fraction("RescueDepthFracs", F);
        for (double F : RescueAimFracs) Fraction("RescueAimFracs", F);
    }

    private void RequireCounts() {
        if (SpeedCandidates < 1) Fail("SpeedCandidates", "must be at least 1", SpeedCandidates);
        if (RescueSpeedSteps < 2) Fail("RescueSpeedSteps", "must be at least 2", RescueSpeedSteps);   // divides by steps - 1
        if (MaxCorrectionPasses < 0) Fail("MaxCorrectionPasses", "must not be negative", MaxCorrectionPasses);
        if (SpeedBackoffPerPass * MaxCorrectionPasses >= 1) {   // the last pass must still move forward
            Fail("SpeedBackoffPerPass", "times maxCorrectionPasses must stay below 1", SpeedBackoffPerPass);
        }
    }

    private static void Positive(String Name, double V)    { if (!(V > 0)) Fail(Name, "must be > 0", V); }
    private static void NonNegative(String Name, double V) { if (!(V >= 0)) Fail(Name, "must be >= 0", V); }
    private static void Fraction(String Name, double V) {
        if (!(V >= 0 && V <= 1)) Fail(Name, "must be in [0, 1]", V);
    }
    private static void Within(String Name, double V, double Lo, double Hi) {
        if (!(V > Lo && V < Hi)) Fail(Name, "must be in (" + Lo + ", " + Hi + ")", V);
    }
    private static void Ordered(String LoName, double Lo, String HiName, double Hi) {
        if (!(Lo <= Hi)) Fail(LoName, "must not exceed " + HiName + " (" + Hi + ")", Lo);
    }
    private static void Fail(String Name, String Rule, double V) {
        throw new IllegalArgumentException("ShotTuning." + Name + " " + Rule + ", was " + V);
    }

    /** The defaults, adjusted knob by knob; {@link #Build} validates. */
    public static final class Builder {

        private Builder() {}

        // Strength. The top speed is aspirational: geometry caps most shots well under it.
        private double MinShotSpeed = 5.0;
        private double MaxShotSpeed = 17.0;
        /** Racket speed for a full-strength shot; faster adds nothing, so hits cannot compound. */
        private double MaxSwingSpeed = 16.0;
        /** Low on purpose: driving through the ball makes it go, moving across aims it. */
        private double LateralEffort = 0.25;
        private double SwingInfluence = 1.0;
        /** Below 1: quick early response, then diminishing returns. */
        private double SwingCurve = 0.7;
        /** TUNED: incoming pace nudges the asked-for pace about 9 m/s, capped at 0.6 m/s. */
        private double IncomingPaceNeutral = 9.0;
        private double IncomingPaceGain = 0.05;
        private double IncomingPaceNudge = 0.6;

        // Aim, as fractions of the target box per m/s of racket motion.
        /** TUNED: an ordinary firm sweep reaches the edge of the box. */
        private double AimInfluence = 0.20;
        /** Pulls against arcInfluence by design; see docs/DESIGN.md before retuning either. */
        private double DepthInfluence = 0.100;
        private double ArcInfluence = 0.018;
        private double FaceInfluence = 0.25;
        /** Small: an edge contact should feel different, not random. */
        private double ContactPointInfluence = 0.30;
        /** TUNED: a still, centred contact aims a little short, leaving a drive room to deepen. */
        private double BaseDepthFrac = 0.35;
        /** TUNED: contact height moves depth half as much as contact width moves aim. */
        private double ContactDepthShare = 0.5;
        /** Enough reflection to feel like an impact, too little to steer. */
        private double PhysicalBlend = 0.15;

        // Contact quality: what lets a shank lose the point. See docs/DESIGN.md.
        /** Clean core as a fraction of blade radius. FLOOR 0.50, TUNED to 0.58 for average players. */
        private double QualityCore = 0.58;
        /** TUNED wider than 0.42 so clean-to-mishit is a slope, not a cliff. */
        private double QualityFalloff = 0.50;
        /** A fast ball must be met more precisely: the core shrinks from this pace over this span. */
        private double QualityPaceFrom = 6.0;
        private double QualityPaceSpan = 12.0;
        /** TUNED down from 0.22 so fast balls keep the forgiveness qualityCore bought. */
        private double QualityPaceLoss = 0.15;
        private double QualityCoreMin = 0.26;
        /** TUNED: raw rim impulses land 11 times in 75, so a shank still gets some help. */
        private double AssistFloor = 0.35;
        /** A rim contact does not qualify for the rescue search. */
        private double RescueQualityFloor = 0.60;

        // Constraints.
        /** Lets a hard pull-back reach genuine backspin, not merely less topspin. */
        private double DriveBrush = 0.8;
        /** Caps the reflection before blending, so a violent impulse cannot leak through. */
        private double ReflectionCap = 6.0;
        private double MaxHorizontalDeviationDeg = 30.0;
        private double MaxLateralVelocity = 4.5;
        /** A sanity guard, not a shaping tool: real drives off low balls launch downward. */
        private double MaxVerticalLaunchAngleDeg = 45.0;
        private double MinVerticalLaunchAngleDeg = -20.0;
        private double MinForwardVelocity = 4.5;

        // Search. Illegality outweighs the speed and pass preferences by two orders of magnitude.
        private int SpeedCandidates = 5;
        private double SpeedSpread = 0.42;
        private double SpeedPreference = 1.0;
        private double PassPenalty = 2.0;
        /** Slowest shot the main search considers, below the slowest the swing may ask for. */
        private double MinSearchSpeed = 3.0;
        /** Stops the ladder disguising an over-ambitious swing as a legal dink. */
        private double SearchSpeedFloorFrac = 0.60;
        /** A hard swing that would not land is not rescued. */
        private double RescueEffortCeiling = 0.75;
        /** Kept small, or an over-hit is silently dragged back to the middle. */
        private int MaxCorrectionPasses = 2;
        private double TargetAssist = 0.18;
        private double SpeedBackoffPerPass = 0.13;

        // Target box on the opponent's half, as fractions of half-width and half-length.
        private double TargetHalfWidthFrac = 0.90;
        private double TargetDepthMinFrac = 0.20;
        private double TargetDepthMaxFrac = 0.92;
        private double SafeDepthFrac = 0.55;
        /** Metres above the cord and inside the lines the validator insists on. */
        private double NetClearance = 0.055;
        private double LandingMargin = 0.05;

        // Rescue: slower than minShotSpeed and re-aimed, keeping the full aim first.
        private double RescueMinSpeed = 3.0;
        private int RescueSpeedSteps = 9;
        private List<Double> RescueDepthFracs = List.of(0.55, 0.72, 0.88, 0.40);
        private List<Double> RescueAimFracs = List.of(1.0, 0.6, 0.3, 0.0);

        // Spin, rev/s, capped so it stays a secondary influence.
        private double SpinInfluence = 1.0;
        private double BaseTopspin = 14.0;
        private double TopspinPerLift = 2.6;
        /** TUNED, and saturating: validation re-aims rather than curving more. Swipe right bends left. */
        private double SidespinPerSwipe = 4.5;
        private double MaxSpin = 55.0;

        public ShotTuning Build() { return new ShotTuning(this); }

        public Builder MinShotSpeed(double V)              { MinShotSpeed = V; return this; }
        public Builder MaxShotSpeed(double V)              { MaxShotSpeed = V; return this; }
        public Builder MaxSwingSpeed(double V)             { MaxSwingSpeed = V; return this; }
        public Builder LateralEffort(double V)             { LateralEffort = V; return this; }
        public Builder SwingInfluence(double V)            { SwingInfluence = V; return this; }
        public Builder SwingCurve(double V)                { SwingCurve = V; return this; }
        public Builder IncomingPaceNeutral(double V)       { IncomingPaceNeutral = V; return this; }
        public Builder IncomingPaceGain(double V)          { IncomingPaceGain = V; return this; }
        public Builder IncomingPaceNudge(double V)         { IncomingPaceNudge = V; return this; }
        public Builder AimInfluence(double V)              { AimInfluence = V; return this; }
        public Builder DepthInfluence(double V)            { DepthInfluence = V; return this; }
        public Builder ArcInfluence(double V)              { ArcInfluence = V; return this; }
        public Builder FaceInfluence(double V)             { FaceInfluence = V; return this; }
        public Builder ContactPointInfluence(double V)     { ContactPointInfluence = V; return this; }
        public Builder BaseDepthFrac(double V)             { BaseDepthFrac = V; return this; }
        public Builder ContactDepthShare(double V)         { ContactDepthShare = V; return this; }
        public Builder PhysicalBlend(double V)             { PhysicalBlend = V; return this; }
        public Builder QualityCore(double V)               { QualityCore = V; return this; }
        public Builder QualityFalloff(double V)            { QualityFalloff = V; return this; }
        public Builder QualityPaceFrom(double V)           { QualityPaceFrom = V; return this; }
        public Builder QualityPaceSpan(double V)           { QualityPaceSpan = V; return this; }
        public Builder QualityPaceLoss(double V)           { QualityPaceLoss = V; return this; }
        public Builder QualityCoreMin(double V)            { QualityCoreMin = V; return this; }
        public Builder AssistFloor(double V)               { AssistFloor = V; return this; }
        public Builder RescueQualityFloor(double V)        { RescueQualityFloor = V; return this; }
        public Builder DriveBrush(double V)                { DriveBrush = V; return this; }
        public Builder ReflectionCap(double V)             { ReflectionCap = V; return this; }
        public Builder MaxHorizontalDeviationDeg(double V) { MaxHorizontalDeviationDeg = V; return this; }
        public Builder MaxLateralVelocity(double V)        { MaxLateralVelocity = V; return this; }
        public Builder MaxVerticalLaunchAngleDeg(double V) { MaxVerticalLaunchAngleDeg = V; return this; }
        public Builder MinVerticalLaunchAngleDeg(double V) { MinVerticalLaunchAngleDeg = V; return this; }
        public Builder MinForwardVelocity(double V)        { MinForwardVelocity = V; return this; }
        public Builder SpeedCandidates(int V)              { SpeedCandidates = V; return this; }
        public Builder SpeedSpread(double V)               { SpeedSpread = V; return this; }
        public Builder SpeedPreference(double V)           { SpeedPreference = V; return this; }
        public Builder PassPenalty(double V)               { PassPenalty = V; return this; }
        public Builder MinSearchSpeed(double V)            { MinSearchSpeed = V; return this; }
        public Builder SearchSpeedFloorFrac(double V)      { SearchSpeedFloorFrac = V; return this; }
        public Builder RescueEffortCeiling(double V)       { RescueEffortCeiling = V; return this; }
        public Builder MaxCorrectionPasses(int V)          { MaxCorrectionPasses = V; return this; }
        public Builder TargetAssist(double V)              { TargetAssist = V; return this; }
        public Builder SpeedBackoffPerPass(double V)       { SpeedBackoffPerPass = V; return this; }
        public Builder TargetHalfWidthFrac(double V)       { TargetHalfWidthFrac = V; return this; }
        public Builder TargetDepthMinFrac(double V)        { TargetDepthMinFrac = V; return this; }
        public Builder TargetDepthMaxFrac(double V)        { TargetDepthMaxFrac = V; return this; }
        public Builder SafeDepthFrac(double V)             { SafeDepthFrac = V; return this; }
        public Builder NetClearance(double V)              { NetClearance = V; return this; }
        public Builder LandingMargin(double V)             { LandingMargin = V; return this; }
        public Builder RescueMinSpeed(double V)            { RescueMinSpeed = V; return this; }
        public Builder RescueSpeedSteps(int V)             { RescueSpeedSteps = V; return this; }
        public Builder RescueDepthFracs(double... V)       { RescueDepthFracs = Boxed(V); return this; }
        public Builder RescueAimFracs(double... V)         { RescueAimFracs = Boxed(V); return this; }
        public Builder SpinInfluence(double V)             { SpinInfluence = V; return this; }
        public Builder BaseTopspin(double V)               { BaseTopspin = V; return this; }
        public Builder TopspinPerLift(double V)            { TopspinPerLift = V; return this; }
        public Builder SidespinPerSwipe(double V)          { SidespinPerSwipe = V; return this; }
        public Builder MaxSpin(double V)                   { MaxSpin = V; return this; }

        private static List<Double> Boxed(double[] V) {
            return java.util.Arrays.stream(V).boxed().toList();
        }
    }
}
