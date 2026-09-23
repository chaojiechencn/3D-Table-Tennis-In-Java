package tabletennis.game.shot;

import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;
import tabletennis.engine.NetSpec;
import tabletennis.engine.TableSpec;
import tabletennis.engine.flight.LaunchSolver;
import tabletennis.engine.flight.TrialFlight;
import tabletennis.engine.flight.TrialFlight.Flight;
import tabletennis.engine.math.Vec3;
import tabletennis.game.shot.ShotPlanner.Plan;

import static tabletennis.engine.math.Numeric.Clamp;

/**
 * Finds the legal launch closest to a plan. Every candidate is solved with LaunchSolver,
 * constrained, flown contact-free and graded before it can win; nothing is changed after its
 * last check. The main search is a speed ladder inside correction passes that pull toward safe;
 * the rescue re-aims down the middle at anything from a soft lift up.
 */
final class ShotSearch {

    /** A graded launch. Cost is 0 for a shot that clears the net and lands in. */
    record Candidate(Vec3 Velocity, Vec3 Target, Flight Trajectory, double Cost, int Passes, SpinPlan Spin) {}

    /** TUNED: 4x the game step; RK4 error stays two orders below the 5 cm landing margin. */
    private static final double TrialStep = 1.0 / 120;
    private static final double MaxFlightTime = 3.0;

    // Cost per metre of violation: missing the net dominates, the cord counts double the lines,
    // and any illegality outweighs the speed and pass preferences by two orders of magnitude.
    private static final double NeverCrossedCost = 10;
    private static final double NetShortfallWeight = 20;
    private static final double LandingMissWeight = 10;
    private static final double IllegalityWeight = 100;

    private final ShotTuning.StrengthKnobs Strength;
    private final ShotTuning.TargetKnobs Targets;
    private final ShotTuning.LimitKnobs Limits;
    private final ShotTuning.SearchKnobs Ladder;
    private final ShotTuning.RescueKnobs Fallback;
    private final ShotTuning.SpinKnobs SpinTuning;

    ShotSearch(ShotTuning Tuning) {
        Strength = Tuning.Strength();
        Targets = Tuning.Target();
        Limits = Tuning.Limits();
        Ladder = Tuning.Search();
        Fallback = Tuning.Rescue();
        SpinTuning = Tuning.Spin();
    }

    /**
     * Correction passes pull the target toward safe and slow the pace; within a pass a speed ladder
     * starts at the asked-for pace. The first legal candidate ends the search.
     */
    Candidate Search(Vec3 Contact, Vec3 Reflect, Plan Intended, double TowardOpponent) {
        Candidate Best = null;
        double BestScore = Double.MAX_VALUE;
        double SpeedFloor = Math.max(Ladder.MinSearchSpeed(), Intended.Speed() * Ladder.SearchSpeedFloorFrac());

        for (int Pass = 0; Pass <= Ladder.MaxCorrectionPasses(); Pass++) {
            double Give = Math.min(1, Pass * Ladder.TargetAssist());
            Vec3 AimAt = Vec3.Lerp(Intended.Want(), Intended.Safe(), Give);
            double Pace = Intended.Speed() * (1 - Ladder.SpeedBackoffPerPass() * Pass);

            for (int Rung = 0; Rung < Ladder.SpeedCandidates(); Rung++) {
                double Speed = Clamp(Pace * SpeedFactor(Rung), Math.min(SpeedFloor, Strength.MaxShotSpeed()),
                                     Strength.MaxShotSpeed());
                LaunchSolver.Solution Solved = LaunchSolver.AtTarget(Contact, AimAt, Speed,
                                                                     Intended.Spin().Top(), Intended.Spin().Side());
                Vec3 Blended = Vec3.Lerp(Solved.State().Velocity(), CapReflection(Reflect), Limits.PhysicalBlend());
                Candidate Graded = Evaluate(Contact, AimAt, Constrain(Blended, TowardOpponent, Speed),
                                            Intended.Spin(), TowardOpponent, Pass);

                double Score = Graded.Cost() * IllegalityWeight
                             + Math.abs(Speed - Intended.Speed()) * Ladder.SpeedPreference()
                             + Pass * Ladder.PassPenalty();
                if (Score < BestScore) { BestScore = Score; Best = Graded; }
                if (Graded.Cost() == 0) return Best;
            }
        }
        return Best;
    }

    /**
     * Every sensible depth down the middle from a soft lift to full pace, keeping as much of the
     * player's aim, then spin, as still works. Some contacts only have a slow, honest answer.
     */
    Candidate Rescue(Vec3 Contact, double WantX, SpinPlan Wanted, double TowardOpponent, Candidate Best) {
        SpinPlan[] Spins = { Wanted, new SpinPlan(SpinTuning.BaseTopspin(), 0) };
        int RescuedPasses = Ladder.MaxCorrectionPasses() + 1;
        for (double AimFraction : Fallback.RescueAimFracs()) {
            for (SpinPlan Spin : Spins) {
                for (double Depth : Fallback.RescueDepthFracs()) {
                    Vec3 AimAt = new Vec3(WantX * AimFraction, 0, TowardOpponent * Depth * TableSpec.HalfLength);
                    for (int Rung = 0; Rung < Fallback.RescueSpeedSteps(); Rung++) {
                        double Speed = Fallback.RescueMinSpeed() + (Strength.MaxShotSpeed() - Fallback.RescueMinSpeed())
                                * Rung / (double) (Fallback.RescueSpeedSteps() - 1);
                        LaunchSolver.Solution Solved = LaunchSolver.AtTarget(Contact, AimAt, Speed, Spin.Top(), Spin.Side());
                        Vec3 Velocity = Constrain(Solved.State().Velocity(), TowardOpponent, Speed);
                        Candidate Graded = Evaluate(Contact, AimAt, Velocity, Spin, TowardOpponent, RescuedPasses);
                        if (Graded.Cost() < Best.Cost()) Best = Graded;
                        if (Graded.Cost() == 0) return Best;
                    }
                }
            }
        }
        return Best;
    }

    /** The raw reflection, capped so a violent impulse cannot leak through a blend. */
    Vec3 CapReflection(Vec3 Raw) {
        double Speed = Raw.Length();
        return Speed > Limits.ReflectionCap() ? Raw.Scale(Limits.ReflectionCap() / Speed) : Raw;
    }

    private Candidate Evaluate(Vec3 Contact, Vec3 Target, Vec3 Velocity, SpinPlan Spin, double TowardOpponent, int Passes) {
        Flight Trajectory = Fly(Contact, Velocity, Spin.VectorFor(Velocity), TowardOpponent);
        return new Candidate(Velocity, Target, Trajectory, Illegality(Trajectory, TowardOpponent), Passes, Spin);
    }

    /** 1.0 first, then alternately slower and faster, so a legal shot costs one solve. */
    private double SpeedFactor(int Rung) {
        if (Rung == 0) return 1.0;
        int Step = (Rung + 1) / 2;
        double Spread = Ladder.SpeedSpread() * Step / Math.max(1, Ladder.SpeedCandidates() / 2);
        return (Rung % 2 == 1) ? 1.0 - Spread : 1.0 + Spread;
    }

    /**
     * Forward pace, a lateral cone and cap, an elevation band, and the candidate's OWN top speed:
     * a shot that only works slowly must be allowed to stay slow.
     */
    private Vec3 Constrain(Vec3 Velocity, double TowardOpponent, double TopSpeed) {
        double Forward = Math.max(Math.min(Limits.MinForwardVelocity(), TopSpeed), Velocity.Z() * TowardOpponent);

        double Cone = Math.tan(Math.toRadians(Limits.MaxHorizontalDeviationDeg())) * Forward;
        double Lateral = Clamp(Velocity.X(), -Math.min(Cone, Limits.MaxLateralVelocity()),
                                             Math.min(Cone, Limits.MaxLateralVelocity()));

        double Horizontal = Math.hypot(Lateral, Forward);
        double Up = Clamp(Velocity.Y(),
                Math.tan(Math.toRadians(Limits.MinVerticalLaunchAngleDeg())) * Horizontal,
                Math.tan(Math.toRadians(Limits.MaxVerticalLaunchAngleDeg())) * Horizontal);

        Vec3 Constrained = new Vec3(Lateral, Up, TowardOpponent * Forward);
        double Speed = Constrained.Length();
        return Speed > TopSpeed ? Constrained.Scale(TopSpeed / Speed) : Constrained;
    }

    /** 0 when it clears the net and lands in; otherwise the size of the violation. */
    private double Illegality(Flight Trajectory, double TowardOpponent) {
        double Cost = 0;

        double Needed = NetSpec.Height + BallSpec.Radius + Targets.NetClearance();
        if (Double.isNaN(Trajectory.NetHeight())) Cost += NeverCrossedCost;
        else if (Trajectory.NetHeight() < Needed) Cost += (Needed - Trajectory.NetHeight()) * NetShortfallWeight;

        Vec3 Landing = Trajectory.Landing();
        double Depth = Landing.Z() * TowardOpponent;                 // positive is into their half
        if (Depth < Targets.LandingMargin()) Cost += (Targets.LandingMargin() - Depth) * LandingMissWeight;
        double MaxDepth = TableSpec.HalfLength - Targets.LandingMargin();
        if (Depth > MaxDepth) Cost += (Depth - MaxDepth) * LandingMissWeight;

        double Wide = Math.abs(Landing.X()) - (TableSpec.HalfWidth - Targets.LandingMargin());
        if (Wide > 0) Cost += Wide * LandingMissWeight;

        return Cost;
    }

    private static Flight Fly(Vec3 From, Vec3 Velocity, Vec3 Spin, double TowardOpponent) {
        TrialFlight.Heading Toward = TowardOpponent < 0 ? TrialFlight.Heading.TowardNegativeZ
                                                        : TrialFlight.Heading.TowardPositiveZ;
        return TrialFlight.Fly(BallState.At(From, Velocity, Spin), Toward, TrialStep, (int) (MaxFlightTime / TrialStep));
    }
}
