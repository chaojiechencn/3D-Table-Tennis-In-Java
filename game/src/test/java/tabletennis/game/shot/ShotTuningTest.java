package tabletennis.game.shot;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static tabletennis.testing.Claims.Check;

/**
 * The shipped tuning, restated here rather than read back from the builder, so a changed default
 * fails loudly; and a tuning the model cannot run on refuses to build.
 */
final class ShotTuningTest {

    private record Knob(String Name, Object Actual, Object Expected) {}

    private record Invalid(String What, UnaryOperator<ShotTuning.Builder> Edit) {}

    @Test
    void EveryDefaultIsUnchanged() {
        ShotTuning Defaults = ShotTuning.Defaults();
        ShotTuning.StrengthKnobs Strength = Defaults.Strength();
        ShotTuning.AimKnobs Aim = Defaults.Aim();
        ShotTuning.TargetKnobs Target = Defaults.Target();
        ShotTuning.QualityKnobs Quality = Defaults.Quality();
        ShotTuning.LimitKnobs Limits = Defaults.Limits();
        ShotTuning.SearchKnobs Search = Defaults.Search();
        ShotTuning.RescueKnobs Rescue = Defaults.Rescue();
        ShotTuning.SpinKnobs Spin = Defaults.Spin();

        Knob[] Expected = {
            new Knob("MinShotSpeed", Strength.MinShotSpeed(), 5.0), new Knob("MaxShotSpeed", Strength.MaxShotSpeed(), 17.0),
            new Knob("MaxSwingSpeed", Strength.MaxSwingSpeed(), 16.0), new Knob("LateralEffort", Strength.LateralEffort(), 0.25),
            new Knob("SwingInfluence", Strength.SwingInfluence(), 1.0), new Knob("SwingCurve", Strength.SwingCurve(), 0.7),
            new Knob("IncomingPaceNeutral", Strength.IncomingPaceNeutral(), 9.0),
            new Knob("IncomingPaceGain", Strength.IncomingPaceGain(), 0.05),
            new Knob("IncomingPaceNudge", Strength.IncomingPaceNudge(), 0.6),
            new Knob("AimInfluence", Aim.AimInfluence(), 0.20), new Knob("DepthInfluence", Aim.DepthInfluence(), 0.100),
            new Knob("ArcInfluence", Aim.ArcInfluence(), 0.018), new Knob("FaceInfluence", Aim.FaceInfluence(), 0.25),
            new Knob("ContactPointInfluence", Aim.ContactPointInfluence(), 0.30),
            new Knob("BaseDepthFrac", Aim.BaseDepthFrac(), 0.35), new Knob("ContactDepthShare", Aim.ContactDepthShare(), 0.5),
            new Knob("TargetHalfWidthFrac", Target.TargetHalfWidthFrac(), 0.90),
            new Knob("TargetDepthMinFrac", Target.TargetDepthMinFrac(), 0.20),
            new Knob("TargetDepthMaxFrac", Target.TargetDepthMaxFrac(), 0.92),
            new Knob("SafeDepthFrac", Target.SafeDepthFrac(), 0.55), new Knob("NetClearance", Target.NetClearance(), 0.055),
            new Knob("LandingMargin", Target.LandingMargin(), 0.05),
            new Knob("QualityCore", Quality.QualityCore(), 0.58), new Knob("QualityFalloff", Quality.QualityFalloff(), 0.50),
            new Knob("QualityPaceFrom", Quality.QualityPaceFrom(), 6.0), new Knob("QualityPaceSpan", Quality.QualityPaceSpan(), 12.0),
            new Knob("QualityPaceLoss", Quality.QualityPaceLoss(), 0.15), new Knob("QualityCoreMin", Quality.QualityCoreMin(), 0.26),
            new Knob("AssistFloor", Quality.AssistFloor(), 0.35),
            new Knob("PhysicalBlend", Limits.PhysicalBlend(), 0.15), new Knob("ReflectionCap", Limits.ReflectionCap(), 6.0),
            new Knob("MaxHorizontalDeviationDeg", Limits.MaxHorizontalDeviationDeg(), 30.0),
            new Knob("MaxLateralVelocity", Limits.MaxLateralVelocity(), 4.5),
            new Knob("MaxVerticalLaunchAngleDeg", Limits.MaxVerticalLaunchAngleDeg(), 45.0),
            new Knob("MinVerticalLaunchAngleDeg", Limits.MinVerticalLaunchAngleDeg(), -20.0),
            new Knob("MinForwardVelocity", Limits.MinForwardVelocity(), 4.5),
            new Knob("SpeedCandidates", Search.SpeedCandidates(), 5), new Knob("SpeedSpread", Search.SpeedSpread(), 0.42),
            new Knob("SpeedPreference", Search.SpeedPreference(), 1.0), new Knob("PassPenalty", Search.PassPenalty(), 2.0),
            new Knob("MinSearchSpeed", Search.MinSearchSpeed(), 3.0),
            new Knob("SearchSpeedFloorFrac", Search.SearchSpeedFloorFrac(), 0.60),
            new Knob("MaxCorrectionPasses", Search.MaxCorrectionPasses(), 2), new Knob("TargetAssist", Search.TargetAssist(), 0.18),
            new Knob("SpeedBackoffPerPass", Search.SpeedBackoffPerPass(), 0.13),
            new Knob("RescueQualityFloor", Rescue.RescueQualityFloor(), 0.60),
            new Knob("RescueEffortCeiling", Rescue.RescueEffortCeiling(), 0.75),
            new Knob("RescueMinSpeed", Rescue.RescueMinSpeed(), 3.0), new Knob("RescueSpeedSteps", Rescue.RescueSpeedSteps(), 9),
            new Knob("RescueDepthFracs", Rescue.RescueDepthFracs(), List.of(0.55, 0.72, 0.88, 0.40)),
            new Knob("RescueAimFracs", Rescue.RescueAimFracs(), List.of(1.0, 0.6, 0.3, 0.0)),
            new Knob("DriveBrush", Spin.DriveBrush(), 0.8), new Knob("SpinInfluence", Spin.SpinInfluence(), 1.0),
            new Knob("BaseTopspin", Spin.BaseTopspin(), 14.0), new Knob("TopspinPerLift", Spin.TopspinPerLift(), 2.6),
            new Knob("SidespinPerSwipe", Spin.SidespinPerSwipe(), 4.5), new Knob("MaxSpin", Spin.MaxSpin(), 55.0),
        };
        List<String> Changed = new ArrayList<>();
        for (Knob Each : Expected) {
            if (!Each.Actual().equals(Each.Expected())) {
                Changed.add(Each.Name() + "=" + Each.Actual() + " (expected " + Each.Expected() + ")");
            }
        }
        // Counted from the records themselves, so a knob added without a restated default fails here.
        int Knobs = Arrays.stream(ShotTuning.class.getRecordComponents())
                          .map(RecordComponent::getType)
                          .mapToInt(Group -> Group.getRecordComponents().length)
                          .sum();
        Check("every default shot-tuning value is unchanged",
              Changed.isEmpty() && Knobs == Expected.length,
              Changed.isEmpty() ? String.format("%d of %d knobs match", Expected.length, Knobs)
                                : String.join("; ", Changed));
    }

    @Test
    void ATuningTheModelCannotRunOnIsRejected() {
        Invalid[] Rejected = {
            new Invalid("NaN shot speed",              B -> B.MaxShotSpeed(Double.NaN)),
            new Invalid("infinite spin",               B -> B.BaseTopspin(Double.POSITIVE_INFINITY)),
            new Invalid("speed range upside down",     B -> B.MinShotSpeed(18)),
            new Invalid("rescue faster than the top",  B -> B.RescueMinSpeed(20)),
            new Invalid("depth range upside down",     B -> B.TargetDepthMinFrac(0.95)),
            new Invalid("zero quality falloff",        B -> B.QualityFalloff(0)),
            new Invalid("zero pace span",              B -> B.QualityPaceSpan(0)),
            new Invalid("zero swing speed",            B -> B.MaxSwingSpeed(0)),
            new Invalid("zero swing curve",            B -> B.SwingCurve(0)),
            new Invalid("blend past 1",                B -> B.PhysicalBlend(1.5)),
            new Invalid("negative assist floor",       B -> B.AssistFloor(-0.1)),
            new Invalid("aim fraction past 1",         B -> B.RescueAimFracs(1.0, 1.2)),
            new Invalid("NaN rescue depth",            B -> B.RescueDepthFracs(0.5, Double.NaN)),
            new Invalid("vertical angle at 90",        B -> B.MaxVerticalLaunchAngleDeg(90)),
            new Invalid("elevation band inverted",     B -> B.MinVerticalLaunchAngleDeg(50)),
            new Invalid("horizontal cone at 90",       B -> B.MaxHorizontalDeviationDeg(90)),
            new Invalid("no speed candidates",         B -> B.SpeedCandidates(0)),
            new Invalid("one rescue speed step",       B -> B.RescueSpeedSteps(1)),
            new Invalid("backoff that stops the shot", B -> B.SpeedBackoffPerPass(0.5)),
            new Invalid("negative reflection cap",     B -> B.ReflectionCap(-1)),
        };
        List<String> Accepted = new ArrayList<>();
        for (Invalid Each : Rejected) {
            try {
                Each.Edit().apply(ShotTuning.Builder()).Build();
                Accepted.add(Each.What());
            } catch (IllegalArgumentException ExpectedRejection) {
                // rejected, as it should be
            }
        }
        Check("a shot tuning the model cannot run on is rejected when built",
              Accepted.isEmpty(),
              Accepted.isEmpty() ? Rejected.length + " invalid tunings rejected" : "accepted: " + Accepted);
    }

    /** The limits are the model's own, not arbitrary: each boundary it can run on is allowed. */
    @Test
    void BoundaryValuesTheModelCanRunOnAreAccepted() {
        String Refused = null;
        try {
            ShotTuning.Builder().PhysicalBlend(0).AssistFloor(1).SpeedCandidates(1)
                    .RescueSpeedSteps(2).MaxCorrectionPasses(0).SpinInfluence(0)
                    .MinForwardVelocity(0).LandingMargin(0).Build();
            ShotTuning.Builder().PhysicalBlend(1).TargetDepthMinFrac(0.92).Build();
        } catch (IllegalArgumentException Rejection) {
            Refused = Rejection.getMessage();
        }
        Check("boundary values the model can run on are accepted",
              Refused == null,
              Refused == null ? "blend 0 and 1, one candidate, two rescue steps, no passes" : "refused: " + Refused);
    }
}
