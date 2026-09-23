package tabletennis.game.shot;

import tabletennis.engine.BallState;
import tabletennis.engine.TableSpec;
import tabletennis.engine.math.Vec3;
import tabletennis.game.shot.SwingReading.Swing;

import static tabletennis.engine.math.Numeric.Clamp;

/**
 * From a swing to a plan: a target inside the opponent's court by construction, so the aim can
 * never be absurd; a pace from a fixed band; and spin from the brush and the swipe.
 */
final class ShotPlanner {

    /** Want is the target the swing asked for; Safe is where correction passes retreat toward. */
    record Plan(Vec3 Want, Vec3 Safe, double Speed, SpinPlan Spin) {}

    private final ShotTuning.StrengthKnobs Strength;
    private final ShotTuning.AimKnobs Aiming;
    private final ShotTuning.TargetKnobs Targets;
    private final ShotTuning.SpinKnobs SpinTuning;
    private final TargetArea Area;

    ShotPlanner(ShotTuning Tuning) {
        Strength = Tuning.Strength();
        Aiming = Tuning.Aim();
        Targets = Tuning.Target();
        SpinTuning = Tuning.Spin();
        Area = new TargetArea(Targets.TargetHalfWidthFrac() * TableSpec.Width / 2,
                              Targets.TargetDepthMinFrac() * TableSpec.Length / 2,
                              Targets.TargetDepthMaxFrac() * TableSpec.Length / 2);
    }

    TargetArea Area() { return Area; }

    Plan For(Swing Read, BallState Incoming, double TowardOpponent) {
        return new Plan(Want(Read, TowardOpponent), Safe(TowardOpponent),
                        Pace(Read.Amount(), Incoming), Spin(Read.Brush(), Read.SwipeX()));
    }

    /** Lateral from the swipe, the face and the contact point; depth from the drive, the brush and the contact height. */
    private Vec3 Want(Swing Read, double TowardOpponent) {
        double Across = Read.SwipeX() * Aiming.AimInfluence()
                      + Read.FaceX() * Aiming.FaceInfluence()
                      + Read.OffX() * Aiming.ContactPointInfluence();
        double X = Clamp(Across, -1, 1) * Area.HalfWidth();

        double DepthFraction = Targets.TargetDepthMinFrac()
                + (Targets.TargetDepthMaxFrac() - Targets.TargetDepthMinFrac())
                  * Clamp(Aiming.BaseDepthFrac() + Read.Drive() * Aiming.DepthInfluence() - Read.Brush() * Aiming.ArcInfluence()
                          - Read.OffY() * Aiming.ContactPointInfluence() * Aiming.ContactDepthShare(), 0, 1);
        double Z = TowardOpponent * Clamp(DepthFraction, Targets.TargetDepthMinFrac(), Targets.TargetDepthMaxFrac())
                 * TableSpec.HalfLength;
        return new Vec3(X, 0, Z);
    }

    private Vec3 Safe(double TowardOpponent) {
        return new Vec3(0, 0, TowardOpponent * Targets.SafeDepthFrac() * TableSpec.HalfLength);
    }

    /** The incoming pace only nudges the band, never adds to it, so a rally cannot compound. */
    private double Pace(double Amount, BallState Incoming) {
        double Speed = Strength.MinShotSpeed() + (Strength.MaxShotSpeed() - Strength.MinShotSpeed()) * Amount;
        Speed += Clamp((Incoming.Speed() - Strength.IncomingPaceNeutral()) * Strength.IncomingPaceGain(),
                       -Strength.IncomingPaceNudge(), Strength.IncomingPaceNudge());
        return Clamp(Speed, Strength.MinShotSpeed(), Strength.MaxShotSpeed());
    }

    private SpinPlan Spin(double Brush, double SwipeX) {
        double Top = Clamp((SpinTuning.BaseTopspin() + Brush * SpinTuning.TopspinPerLift()) * SpinTuning.SpinInfluence(),
                           -SpinTuning.MaxSpin(), SpinTuning.MaxSpin());
        double Side = Clamp(SwipeX * SpinTuning.SidespinPerSwipe() * SpinTuning.SpinInfluence(),
                            -SpinTuning.MaxSpin(), SpinTuning.MaxSpin());
        return new SpinPlan(Top, Side);
    }
}
