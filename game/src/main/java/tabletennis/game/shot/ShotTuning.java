package tabletennis.game.shot;

import java.util.Arrays;
import java.util.List;

/**
 * Every number the arcade shot model uses, grouped by the part of the shot that reads it. Each
 * group checks its knobs when built, each only by how the model uses it; the defaults and the
 * reasoning behind them live on the Builder. Measured restitution and friction are not here: they
 * are physics, single-sourced in the engine's Materials.
 */
public record ShotTuning(StrengthKnobs Strength, AimKnobs Aim, TargetKnobs Target, QualityKnobs Quality,
                         LimitKnobs Limits, SearchKnobs Search, RescueKnobs Rescue, SpinKnobs Spin) {

    public ShotTuning {
        Ordered("RescueMinSpeed", Rescue.RescueMinSpeed(), "MaxShotSpeed", Strength.MaxShotSpeed());
    }

    private static final ShotTuning Shipped = Builder().Build();

    /** The shipped tuning. */
    public static ShotTuning Defaults() { return Shipped; }

    public static Builder Builder() { return new Builder(); }

    /** How hard the shot is: a band the swing picks from, never incoming pace plus racket pace. */
    public record StrengthKnobs(double MinShotSpeed, double MaxShotSpeed, double MaxSwingSpeed,
                                double LateralEffort, double SwingInfluence, double SwingCurve,
                                double IncomingPaceNeutral, double IncomingPaceGain, double IncomingPaceNudge) {
        public StrengthKnobs {
            Positive("MinShotSpeed", MinShotSpeed);
            Finite("MaxShotSpeed", MaxShotSpeed);
            Positive("MaxSwingSpeed", MaxSwingSpeed);          // divides the swing effort
            Finite("LateralEffort", LateralEffort);
            Finite("SwingInfluence", SwingInfluence);
            Positive("SwingCurve", SwingCurve);                // exponent of the strength curve
            Finite("IncomingPaceNeutral", IncomingPaceNeutral);
            Finite("IncomingPaceGain", IncomingPaceGain);
            NonNegative("IncomingPaceNudge", IncomingPaceNudge);
            Ordered("MinShotSpeed", MinShotSpeed, "MaxShotSpeed", MaxShotSpeed);
        }
    }

    /** Where the shot goes, as fractions of the target box per m/s of racket motion. */
    public record AimKnobs(double AimInfluence, double DepthInfluence, double ArcInfluence, double FaceInfluence,
                           double ContactPointInfluence, double BaseDepthFrac, double ContactDepthShare) {
        public AimKnobs {
            Finite("AimInfluence", AimInfluence);
            Finite("DepthInfluence", DepthInfluence);
            Finite("ArcInfluence", ArcInfluence);
            Finite("FaceInfluence", FaceInfluence);
            Finite("ContactPointInfluence", ContactPointInfluence);
            Finite("BaseDepthFrac", BaseDepthFrac);
            Finite("ContactDepthShare", ContactDepthShare);
        }
    }

    /** The target box on the opponent's half, as fractions of half-width and half-length, and the margins a legal shot needs. */
    public record TargetKnobs(double TargetHalfWidthFrac, double TargetDepthMinFrac, double TargetDepthMaxFrac,
                              double SafeDepthFrac, double NetClearance, double LandingMargin) {
        public TargetKnobs {
            Fraction("TargetHalfWidthFrac", TargetHalfWidthFrac);
            Fraction("TargetDepthMinFrac", TargetDepthMinFrac);
            Fraction("TargetDepthMaxFrac", TargetDepthMaxFrac);
            Fraction("SafeDepthFrac", SafeDepthFrac);
            Finite("NetClearance", NetClearance);
            Finite("LandingMargin", LandingMargin);
            Ordered("TargetDepthMinFrac", TargetDepthMinFrac, "TargetDepthMaxFrac", TargetDepthMaxFrac);
        }
    }

    /** How cleanly the ball was struck, and so how much of the authored shot it earns. */
    public record QualityKnobs(double QualityCore, double QualityFalloff, double QualityPaceFrom,
                               double QualityPaceSpan, double QualityPaceLoss, double QualityCoreMin,
                               double AssistFloor) {
        public QualityKnobs {
            Finite("QualityCore", QualityCore);
            Positive("QualityFalloff", QualityFalloff);        // divides the quality slope
            Finite("QualityPaceFrom", QualityPaceFrom);
            Positive("QualityPaceSpan", QualityPaceSpan);      // divides the pace fraction
            Finite("QualityPaceLoss", QualityPaceLoss);
            Finite("QualityCoreMin", QualityCoreMin);
            Fraction("AssistFloor", AssistFloor);              // a lerp weight
        }
    }

    /** Bounds on the launch itself: a sanity cone and band, not a shaping tool. */
    public record LimitKnobs(double PhysicalBlend, double ReflectionCap, double MaxHorizontalDeviationDeg,
                             double MaxLateralVelocity, double MaxVerticalLaunchAngleDeg,
                             double MinVerticalLaunchAngleDeg, double MinForwardVelocity) {
        public LimitKnobs {
            Fraction("PhysicalBlend", PhysicalBlend);          // a lerp weight
            NonNegative("ReflectionCap", ReflectionCap);
            if (!(MaxHorizontalDeviationDeg >= 0 && MaxHorizontalDeviationDeg < 90)) {
                Fail("MaxHorizontalDeviationDeg", "must be in [0, 90)", MaxHorizontalDeviationDeg);
            }
            NonNegative("MaxLateralVelocity", MaxLateralVelocity);
            Within("MaxVerticalLaunchAngleDeg", MaxVerticalLaunchAngleDeg, -90, 90);   // fed to tan()
            Within("MinVerticalLaunchAngleDeg", MinVerticalLaunchAngleDeg, -90, 90);
            if (!(MinVerticalLaunchAngleDeg < MaxVerticalLaunchAngleDeg)) {
                Fail("MinVerticalLaunchAngleDeg", "must be below MaxVerticalLaunchAngleDeg", MinVerticalLaunchAngleDeg);
            }
            Finite("MinForwardVelocity", MinForwardVelocity);
        }
    }

    /** The correction search: a speed ladder within each pass, passes pulling toward safe. */
    public record SearchKnobs(int SpeedCandidates, double SpeedSpread, double SpeedPreference, double PassPenalty,
                              double MinSearchSpeed, double SearchSpeedFloorFrac, int MaxCorrectionPasses,
                              double TargetAssist, double SpeedBackoffPerPass) {
        public SearchKnobs {
            AtLeast("SpeedCandidates", SpeedCandidates, 1);
            Finite("SpeedSpread", SpeedSpread);
            Finite("SpeedPreference", SpeedPreference);
            Finite("PassPenalty", PassPenalty);
            Finite("MinSearchSpeed", MinSearchSpeed);
            Fraction("SearchSpeedFloorFrac", SearchSpeedFloorFrac);
            AtLeast("MaxCorrectionPasses", MaxCorrectionPasses, 0);
            NonNegative("TargetAssist", TargetAssist);
            NonNegative("SpeedBackoffPerPass", SpeedBackoffPerPass);
            if (SpeedBackoffPerPass * MaxCorrectionPasses >= 1) {   // the last pass must still move forward
                Fail("SpeedBackoffPerPass", "times MaxCorrectionPasses must stay below 1", SpeedBackoffPerPass);
            }
        }
    }

    /** Who qualifies for the wider rescue search, and what it tries. */
    public record RescueKnobs(double RescueQualityFloor, double RescueEffortCeiling, double RescueMinSpeed,
                              int RescueSpeedSteps, List<Double> RescueDepthFracs, List<Double> RescueAimFracs) {
        public RescueKnobs {
            Finite("RescueQualityFloor", RescueQualityFloor);
            Finite("RescueEffortCeiling", RescueEffortCeiling);
            Positive("RescueMinSpeed", RescueMinSpeed);
            AtLeast("RescueSpeedSteps", RescueSpeedSteps, 2);   // the ladder divides by steps - 1
            RescueDepthFracs = List.copyOf(RescueDepthFracs);
            RescueAimFracs = List.copyOf(RescueAimFracs);
            for (double Each : RescueDepthFracs) Fraction("RescueDepthFracs", Each);
            for (double Each : RescueAimFracs) Fraction("RescueAimFracs", Each);
        }
    }

    /** Spin, in rev/s, capped so it stays a secondary influence. */
    public record SpinKnobs(double DriveBrush, double SpinInfluence, double BaseTopspin, double TopspinPerLift,
                            double SidespinPerSwipe, double MaxSpin) {
        public SpinKnobs {
            Finite("DriveBrush", DriveBrush);
            Finite("SpinInfluence", SpinInfluence);
            Finite("BaseTopspin", BaseTopspin);
            Finite("TopspinPerLift", TopspinPerLift);
            Finite("SidespinPerSwipe", SidespinPerSwipe);
            NonNegative("MaxSpin", MaxSpin);
        }
    }

    /** The defaults, adjusted knob by knob; Build() groups and validates them. */
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

        // Aim.
        /** TUNED: an ordinary firm sweep reaches the edge of the box. */
        private double AimInfluence = 0.20;
        /** Pulls against ArcInfluence by design; see docs/GAME-DESIGN.md before retuning either. */
        private double DepthInfluence = 0.100;
        private double ArcInfluence = 0.018;
        private double FaceInfluence = 0.25;
        /** Small: an edge contact should feel different, not random. */
        private double ContactPointInfluence = 0.30;
        /** TUNED: a still, centred contact aims a little short, leaving a drive room to deepen. */
        private double BaseDepthFrac = 0.35;
        /** TUNED: contact height moves depth half as much as contact width moves aim. */
        private double ContactDepthShare = 0.5;

        // Target box.
        private double TargetHalfWidthFrac = 0.90;
        private double TargetDepthMinFrac = 0.20;
        private double TargetDepthMaxFrac = 0.92;
        private double SafeDepthFrac = 0.55;
        /** Metres above the cord and inside the lines the validator insists on. */
        private double NetClearance = 0.055;
        private double LandingMargin = 0.05;

        // Contact quality: what lets a shank lose the point. See docs/GAME-DESIGN.md.
        /** Clean core as a fraction of blade radius. FLOOR 0.50, TUNED to 0.58 for average players. */
        private double QualityCore = 0.58;
        /** TUNED wider than 0.42 so clean-to-mishit is a slope, not a cliff. */
        private double QualityFalloff = 0.50;
        /** A fast ball must be met more precisely: the core shrinks from this pace over this span. */
        private double QualityPaceFrom = 6.0;
        private double QualityPaceSpan = 12.0;
        /** TUNED down from 0.22 so fast balls keep the forgiveness QualityCore bought. */
        private double QualityPaceLoss = 0.15;
        private double QualityCoreMin = 0.26;
        /** TUNED: raw rim impulses land 11 times in 75, so a shank still gets some help. */
        private double AssistFloor = 0.35;

        // Limits.
        /** Enough reflection to feel like an impact, too little to steer. */
        private double PhysicalBlend = 0.15;
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
        /** Kept small, or an over-hit is silently dragged back to the middle. */
        private int MaxCorrectionPasses = 2;
        private double TargetAssist = 0.18;
        private double SpeedBackoffPerPass = 0.13;

        // Rescue: slower than MinShotSpeed and re-aimed, keeping the full aim first.
        /** A rim contact does not qualify for the rescue search. */
        private double RescueQualityFloor = 0.60;
        /** A hard swing that would not land is not rescued. */
        private double RescueEffortCeiling = 0.75;
        private double RescueMinSpeed = 3.0;
        private int RescueSpeedSteps = 9;
        private List<Double> RescueDepthFracs = List.of(0.55, 0.72, 0.88, 0.40);
        private List<Double> RescueAimFracs = List.of(1.0, 0.6, 0.3, 0.0);

        // Spin.
        /** Lets a hard pull-back reach genuine backspin, not merely less topspin. */
        private double DriveBrush = 0.8;
        private double SpinInfluence = 1.0;
        private double BaseTopspin = 14.0;
        private double TopspinPerLift = 2.6;
        /** TUNED, and saturating: validation re-aims rather than curving more. Swipe right bends left. */
        private double SidespinPerSwipe = 4.5;
        private double MaxSpin = 55.0;

        public ShotTuning Build() {
            return new ShotTuning(
                    new StrengthKnobs(MinShotSpeed, MaxShotSpeed, MaxSwingSpeed, LateralEffort, SwingInfluence,
                                      SwingCurve, IncomingPaceNeutral, IncomingPaceGain, IncomingPaceNudge),
                    new AimKnobs(AimInfluence, DepthInfluence, ArcInfluence, FaceInfluence, ContactPointInfluence,
                                 BaseDepthFrac, ContactDepthShare),
                    new TargetKnobs(TargetHalfWidthFrac, TargetDepthMinFrac, TargetDepthMaxFrac, SafeDepthFrac,
                                    NetClearance, LandingMargin),
                    new QualityKnobs(QualityCore, QualityFalloff, QualityPaceFrom, QualityPaceSpan,
                                     QualityPaceLoss, QualityCoreMin, AssistFloor),
                    new LimitKnobs(PhysicalBlend, ReflectionCap, MaxHorizontalDeviationDeg, MaxLateralVelocity,
                                   MaxVerticalLaunchAngleDeg, MinVerticalLaunchAngleDeg, MinForwardVelocity),
                    new SearchKnobs(SpeedCandidates, SpeedSpread, SpeedPreference, PassPenalty, MinSearchSpeed,
                                    SearchSpeedFloorFrac, MaxCorrectionPasses, TargetAssist, SpeedBackoffPerPass),
                    new RescueKnobs(RescueQualityFloor, RescueEffortCeiling, RescueMinSpeed, RescueSpeedSteps,
                                    RescueDepthFracs, RescueAimFracs),
                    new SpinKnobs(DriveBrush, SpinInfluence, BaseTopspin, TopspinPerLift, SidespinPerSwipe, MaxSpin));
        }

        public Builder MinShotSpeed(double Value)              { MinShotSpeed = Value; return this; }
        public Builder MaxShotSpeed(double Value)              { MaxShotSpeed = Value; return this; }
        public Builder MaxSwingSpeed(double Value)             { MaxSwingSpeed = Value; return this; }
        public Builder LateralEffort(double Value)             { LateralEffort = Value; return this; }
        public Builder SwingInfluence(double Value)            { SwingInfluence = Value; return this; }
        public Builder SwingCurve(double Value)                { SwingCurve = Value; return this; }
        public Builder IncomingPaceNeutral(double Value)       { IncomingPaceNeutral = Value; return this; }
        public Builder IncomingPaceGain(double Value)          { IncomingPaceGain = Value; return this; }
        public Builder IncomingPaceNudge(double Value)         { IncomingPaceNudge = Value; return this; }
        public Builder AimInfluence(double Value)              { AimInfluence = Value; return this; }
        public Builder DepthInfluence(double Value)            { DepthInfluence = Value; return this; }
        public Builder ArcInfluence(double Value)              { ArcInfluence = Value; return this; }
        public Builder FaceInfluence(double Value)             { FaceInfluence = Value; return this; }
        public Builder ContactPointInfluence(double Value)     { ContactPointInfluence = Value; return this; }
        public Builder BaseDepthFrac(double Value)             { BaseDepthFrac = Value; return this; }
        public Builder ContactDepthShare(double Value)         { ContactDepthShare = Value; return this; }
        public Builder TargetHalfWidthFrac(double Value)       { TargetHalfWidthFrac = Value; return this; }
        public Builder TargetDepthMinFrac(double Value)        { TargetDepthMinFrac = Value; return this; }
        public Builder TargetDepthMaxFrac(double Value)        { TargetDepthMaxFrac = Value; return this; }
        public Builder SafeDepthFrac(double Value)             { SafeDepthFrac = Value; return this; }
        public Builder NetClearance(double Value)              { NetClearance = Value; return this; }
        public Builder LandingMargin(double Value)             { LandingMargin = Value; return this; }
        public Builder QualityCore(double Value)               { QualityCore = Value; return this; }
        public Builder QualityFalloff(double Value)            { QualityFalloff = Value; return this; }
        public Builder QualityPaceFrom(double Value)           { QualityPaceFrom = Value; return this; }
        public Builder QualityPaceSpan(double Value)           { QualityPaceSpan = Value; return this; }
        public Builder QualityPaceLoss(double Value)           { QualityPaceLoss = Value; return this; }
        public Builder QualityCoreMin(double Value)            { QualityCoreMin = Value; return this; }
        public Builder AssistFloor(double Value)               { AssistFloor = Value; return this; }
        public Builder PhysicalBlend(double Value)             { PhysicalBlend = Value; return this; }
        public Builder ReflectionCap(double Value)             { ReflectionCap = Value; return this; }
        public Builder MaxHorizontalDeviationDeg(double Value) { MaxHorizontalDeviationDeg = Value; return this; }
        public Builder MaxLateralVelocity(double Value)        { MaxLateralVelocity = Value; return this; }
        public Builder MaxVerticalLaunchAngleDeg(double Value) { MaxVerticalLaunchAngleDeg = Value; return this; }
        public Builder MinVerticalLaunchAngleDeg(double Value) { MinVerticalLaunchAngleDeg = Value; return this; }
        public Builder MinForwardVelocity(double Value)        { MinForwardVelocity = Value; return this; }
        public Builder SpeedCandidates(int Value)              { SpeedCandidates = Value; return this; }
        public Builder SpeedSpread(double Value)               { SpeedSpread = Value; return this; }
        public Builder SpeedPreference(double Value)           { SpeedPreference = Value; return this; }
        public Builder PassPenalty(double Value)               { PassPenalty = Value; return this; }
        public Builder MinSearchSpeed(double Value)            { MinSearchSpeed = Value; return this; }
        public Builder SearchSpeedFloorFrac(double Value)      { SearchSpeedFloorFrac = Value; return this; }
        public Builder MaxCorrectionPasses(int Value)          { MaxCorrectionPasses = Value; return this; }
        public Builder TargetAssist(double Value)              { TargetAssist = Value; return this; }
        public Builder SpeedBackoffPerPass(double Value)       { SpeedBackoffPerPass = Value; return this; }
        public Builder RescueQualityFloor(double Value)        { RescueQualityFloor = Value; return this; }
        public Builder RescueEffortCeiling(double Value)       { RescueEffortCeiling = Value; return this; }
        public Builder RescueMinSpeed(double Value)            { RescueMinSpeed = Value; return this; }
        public Builder RescueSpeedSteps(int Value)             { RescueSpeedSteps = Value; return this; }
        public Builder RescueDepthFracs(double... Values)      { RescueDepthFracs = Boxed(Values); return this; }
        public Builder RescueAimFracs(double... Values)        { RescueAimFracs = Boxed(Values); return this; }
        public Builder DriveBrush(double Value)                { DriveBrush = Value; return this; }
        public Builder SpinInfluence(double Value)             { SpinInfluence = Value; return this; }
        public Builder BaseTopspin(double Value)               { BaseTopspin = Value; return this; }
        public Builder TopspinPerLift(double Value)            { TopspinPerLift = Value; return this; }
        public Builder SidespinPerSwipe(double Value)          { SidespinPerSwipe = Value; return this; }
        public Builder MaxSpin(double Value)                   { MaxSpin = Value; return this; }

        private static List<Double> Boxed(double[] Values) {
            return Arrays.stream(Values).boxed().toList();
        }
    }

    private static void Finite(String Knob, double Value) {
        if (!Double.isFinite(Value)) Fail(Knob, "must be finite", Value);
    }

    private static void Positive(String Knob, double Value) {
        if (!(Double.isFinite(Value) && Value > 0)) Fail(Knob, "must be finite and > 0", Value);
    }

    private static void NonNegative(String Knob, double Value) {
        if (!(Double.isFinite(Value) && Value >= 0)) Fail(Knob, "must be finite and >= 0", Value);
    }

    private static void Fraction(String Knob, double Value) {
        if (!(Value >= 0 && Value <= 1)) Fail(Knob, "must be in [0, 1]", Value);
    }

    private static void Within(String Knob, double Value, double Low, double High) {
        if (!(Value > Low && Value < High)) Fail(Knob, "must be in (" + Low + ", " + High + ")", Value);
    }

    private static void AtLeast(String Knob, int Value, int Minimum) {
        if (Value < Minimum) Fail(Knob, "must be at least " + Minimum, Value);
    }

    private static void Ordered(String LowKnob, double Low, String HighKnob, double High) {
        if (!(Low <= High)) Fail(LowKnob, "must not exceed " + HighKnob + " (" + High + ")", Low);
    }

    private static void Fail(String Knob, String Rule, double Value) {
        throw new IllegalArgumentException("ShotTuning." + Knob + " " + Rule + ", was " + Value);
    }
}
