package tabletennis.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static tabletennis.testing.Claims.Check;

/**
 * The shipped tuning, restated here rather than read back from the builder, so a changed default
 * fails loudly; and a tuning the model cannot run on refuses to build.
 */
final class ShotTuningTest {

    private record Invalid(String What, UnaryOperator<ShotTuning.Builder> Edit) {}

    @Test
    void TheShotTuningKeepsItsDefaultsAndRejectsNonsense() {
        Object[][] Expected = {
            {"MinShotSpeed", 5.0}, {"MaxShotSpeed", 17.0}, {"MaxSwingSpeed", 16.0},
            {"LateralEffort", 0.25}, {"SwingInfluence", 1.0}, {"SwingCurve", 0.7},
            {"IncomingPaceNeutral", 9.0}, {"IncomingPaceGain", 0.05}, {"IncomingPaceNudge", 0.6},
            {"AimInfluence", 0.20}, {"DepthInfluence", 0.100}, {"ArcInfluence", 0.018},
            {"FaceInfluence", 0.25}, {"ContactPointInfluence", 0.30}, {"BaseDepthFrac", 0.35},
            {"ContactDepthShare", 0.5}, {"PhysicalBlend", 0.15},
            {"QualityCore", 0.58}, {"QualityFalloff", 0.50}, {"QualityPaceFrom", 6.0},
            {"QualityPaceSpan", 12.0}, {"QualityPaceLoss", 0.15}, {"QualityCoreMin", 0.26},
            {"AssistFloor", 0.35}, {"RescueQualityFloor", 0.60},
            {"DriveBrush", 0.8}, {"ReflectionCap", 6.0}, {"MaxHorizontalDeviationDeg", 30.0},
            {"MaxLateralVelocity", 4.5}, {"MaxVerticalLaunchAngleDeg", 45.0},
            {"MinVerticalLaunchAngleDeg", -20.0}, {"MinForwardVelocity", 4.5},
            {"SpeedCandidates", 5}, {"SpeedSpread", 0.42}, {"SpeedPreference", 1.0},
            {"PassPenalty", 2.0}, {"MinSearchSpeed", 3.0}, {"SearchSpeedFloorFrac", 0.60},
            {"RescueEffortCeiling", 0.75}, {"MaxCorrectionPasses", 2}, {"TargetAssist", 0.18},
            {"SpeedBackoffPerPass", 0.13}, {"TargetHalfWidthFrac", 0.90},
            {"TargetDepthMinFrac", 0.20}, {"TargetDepthMaxFrac", 0.92}, {"SafeDepthFrac", 0.55},
            {"NetClearance", 0.055}, {"LandingMargin", 0.05}, {"RescueMinSpeed", 3.0},
            {"RescueSpeedSteps", 9}, {"RescueDepthFracs", List.of(0.55, 0.72, 0.88, 0.40)},
            {"RescueAimFracs", List.of(1.0, 0.6, 0.3, 0.0)}, {"SpinInfluence", 1.0},
            {"BaseTopspin", 14.0}, {"TopspinPerLift", 2.6}, {"SidespinPerSwipe", 4.5},
            {"MaxSpin", 55.0},
        };
        ShotTuning Defaults = ShotTuning.Defaults();
        List<String> Changed = new ArrayList<>();
        for (Object[] E : Expected) {
            try {
                Object Got = ShotTuning.class.getField((String) E[0]).get(Defaults);
                if (!Got.equals(E[1])) Changed.add(E[0] + "=" + Got + " (expected " + E[1] + ")");
            } catch (ReflectiveOperationException Ex) {
                Changed.add(E[0] + " missing");
            }
        }
        int Knobs = ShotTuning.class.getFields().length;
        Check("every default shot-tuning value is unchanged",
              Changed.isEmpty() && Knobs == Expected.length,
              Changed.isEmpty() ? String.format("%d of %d knobs match", Expected.length, Knobs)
                                : String.join("; ", Changed));

        Invalid[] Rejected = {
            new Invalid("NaN shot speed",            B -> B.MaxShotSpeed(Double.NaN)),
            new Invalid("infinite spin",             B -> B.BaseTopspin(Double.POSITIVE_INFINITY)),
            new Invalid("speed range upside down",   B -> B.MinShotSpeed(18)),
            new Invalid("rescue faster than the top", B -> B.RescueMinSpeed(20)),
            new Invalid("depth range upside down",   B -> B.TargetDepthMinFrac(0.95)),
            new Invalid("zero quality falloff",      B -> B.QualityFalloff(0)),
            new Invalid("zero pace span",            B -> B.QualityPaceSpan(0)),
            new Invalid("zero swing speed",          B -> B.MaxSwingSpeed(0)),
            new Invalid("zero swing curve",          B -> B.SwingCurve(0)),
            new Invalid("blend past 1",              B -> B.PhysicalBlend(1.5)),
            new Invalid("negative assist floor",     B -> B.AssistFloor(-0.1)),
            new Invalid("aim fraction past 1",       B -> B.RescueAimFracs(1.0, 1.2)),
            new Invalid("NaN rescue depth",          B -> B.RescueDepthFracs(0.5, Double.NaN)),
            new Invalid("vertical angle at 90",      B -> B.MaxVerticalLaunchAngleDeg(90)),
            new Invalid("elevation band inverted",   B -> B.MinVerticalLaunchAngleDeg(50)),
            new Invalid("horizontal cone at 90",     B -> B.MaxHorizontalDeviationDeg(90)),
            new Invalid("no speed candidates",       B -> B.SpeedCandidates(0)),
            new Invalid("one rescue speed step",     B -> B.RescueSpeedSteps(1)),
            new Invalid("backoff that stops the shot", B -> B.SpeedBackoffPerPass(0.5)),
            new Invalid("negative reflection cap",   B -> B.ReflectionCap(-1)),
        };
        List<String> Accepted = new ArrayList<>();
        for (Invalid B : Rejected) {
            try { B.Edit().apply(ShotTuning.Builder()).Build(); Accepted.add(B.What()); }
            catch (IllegalArgumentException ExpectedRejection) { /* rejected, as it should be */ }
        }
        Check("a shot tuning the model cannot run on is rejected when built",
              Accepted.isEmpty(),
              Accepted.isEmpty() ? Rejected.length + " invalid tunings rejected" : "accepted: " + Accepted);

        // The limits are the model's own, not arbitrary: each boundary it can run on is allowed.
        String Refused = null;
        try {
            ShotTuning.Builder().PhysicalBlend(0).AssistFloor(1).SpeedCandidates(1)
                    .RescueSpeedSteps(2).MaxCorrectionPasses(0).SpinInfluence(0)
                    .MinForwardVelocity(0).LandingMargin(0).Build();
            ShotTuning.Builder().PhysicalBlend(1).TargetDepthMinFrac(0.92).Build();
        } catch (IllegalArgumentException E) {
            Refused = E.getMessage();
        }
        Check("boundary values the model can run on are accepted",
              Refused == null, Refused == null ? "blend 0 and 1, one candidate, two rescue steps, no passes"
                                               : "refused: " + Refused);
    }
}
