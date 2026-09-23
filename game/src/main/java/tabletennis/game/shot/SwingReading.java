package tabletennis.game.shot;

import tabletennis.engine.BallState;
import tabletennis.engine.RacketSpec;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.Racket;

import static tabletennis.engine.math.Numeric.Clamp;

/** What the racket's motion through the ball says the player meant, and how cleanly it was struck. */
final class SwingReading {

    /**
     * Drive is toward the opponent, swipe to the player's right, lift upward (live only while
     * brushing). Brush is what they add up to for spin; Amount is the effort on a 0..1 curve.
     * OffX and OffY place the contact on the face as fractions of the blade radius; FaceX is the
     * face's sideways tilt.
     */
    record Swing(double Drive, double SwipeX, double Lift, double Brush, double Amount,
                 double OffX, double OffY, double FaceX) {}

    private final ShotTuning.StrengthKnobs Strength;
    private final ShotTuning.QualityKnobs Quality;
    private final ShotTuning.SpinKnobs SpinTuning;

    SwingReading(ShotTuning Tuning) {
        Strength = Tuning.Strength();
        Quality = Tuning.Quality();
        SpinTuning = Tuning.Spin();
    }

    /** Strength comes from the forward drive on a saturating curve, so hard swings cannot compound. */
    Swing Read(Vec3 Contact, Racket Struck, double TowardOpponent) {
        Vec3 Motion = Struck.Velocity();
        double Drive = Motion.Z() * TowardOpponent;
        double SwipeX = Motion.X();
        double Lift = Motion.Y();
        double Brush = Lift + Drive * SpinTuning.DriveBrush();

        double Effort = Math.max(0, Drive) + Strength.LateralEffort() * Math.hypot(SwipeX, Lift);
        double Amount = Clamp(Math.pow(Clamp(Effort / Strength.MaxSwingSpeed(), 0, 1), Strength.SwingCurve()), 0, 1)
                      * Strength.SwingInfluence();

        Vec3 Offset = Contact.Minus(Struck.Position());
        Vec3 InPlane = Offset.Minus(Struck.Normal().Scale(Offset.Dot(Struck.Normal())));
        double OffX = Clamp(InPlane.X() / RacketSpec.BladeRadius, -1, 1);
        double OffY = Clamp(InPlane.Y() / RacketSpec.BladeRadius, -1, 1);
        double FaceX = Clamp(Struck.Normal().X() * -TowardOpponent, -1, 1);

        return new Swing(Drive, SwipeX, Lift, Brush, Amount, OffX, OffY, FaceX);
    }

    /** 1 mid-blade falling to 0 at the rim, with a clean core that shrinks as the ball arrives faster. */
    double ContactQuality(BallState Incoming, Swing Read) {
        double OffCentre = Math.min(1, Math.hypot(Read.OffX(), Read.OffY()));
        double PaceFraction = Clamp((Incoming.Speed() - Quality.QualityPaceFrom()) / Quality.QualityPaceSpan(), 0, 1);
        double Core = Math.max(Quality.QualityCoreMin(), Quality.QualityCore() - Quality.QualityPaceLoss() * PaceFraction);
        double Rim = Core + Quality.QualityFalloff();
        return Clamp((Rim - OffCentre) / (Rim - Core), 0, 1);
    }
}
