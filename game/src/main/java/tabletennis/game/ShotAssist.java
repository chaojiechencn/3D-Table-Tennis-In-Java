package tabletennis.game;

import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;
import tabletennis.engine.NetSpec;
import tabletennis.engine.RacketSpec;
import tabletennis.engine.TableSpec;
import tabletennis.engine.flight.LaunchSolver;
import tabletennis.engine.flight.SpinVector;
import tabletennis.engine.flight.TrialFlight;
import tabletennis.engine.flight.TrialFlight.Flight;
import tabletennis.engine.world.Racket;
import tabletennis.engine.math.Vec3;

/**
 * The arcade shot model for both rackets. The exact impulse still runs on every contact; this
 * turns it into a playable shot: read the racket's motion as intent, build a target inside the
 * opponent's court, solve the launch with {@link LaunchSolver}, constrain it, then fly and grade every
 * candidate. Only the launch is authored; the flight after it is the real simulation.
 */
public final class ShotAssist {

    /** Everything the last shot was built from, for the V overlay. */
    public record Debug(Vec3 Contact, Vec3 RacketVel, Vec3 IncomingVel, Vec3 ReflectDir,
                        Vec3 IntendDir, Vec3 FinalDir, Vec3 Goal, Vec3 Landing,
                        double Speed, Vec3 SpinPlan, int Passes, boolean Legal) {}

    private record Intent(double Drive, double SwipeX, double Lift, double Brush,
                          double SwingAmount, double OffX, double OffY, double FaceX) {}

    private record Target(Vec3 Want, Vec3 Safe) {}

    private record Spin(double Top, double Side) {}

    private record Candidate(Vec3 Vel, Vec3 Goal, Flight Trajectory, double Cost, int Passes, Spin SpinPlan) {}

    private static final double HalfWidth = TableSpec.HalfWidth, HalfLength = TableSpec.HalfLength;

    /** TUNED: 4x the game step; RK4 error stays two orders below the 5 cm landing margin. */
    private static final double ValidateDt = 1.0 / 120;
    private static final double MaxFlightTime = 3.0;

    // Cost per metre of violation: missing the net dominates, the cord counts double the lines,
    // and any illegality outweighs the speed and pass preferences by two orders of magnitude.
    private static final double NeverCrossedCost = 10;
    private static final double NetShortfallWeight = 20;
    private static final double LandingMissWeight = 10;
    private static final double IllegalityWeight = 100;

    private static final Vec3 DownTable = new Vec3(0, 0, -1);

    private final ShotTuning T;
    private Debug LastDecision = new Debug(Vec3.Zero, Vec3.Zero, Vec3.Zero, DownTable,
            DownTable, DownTable, Vec3.Zero, Vec3.Zero, 0, Vec3.Zero, 0, true);

    public ShotAssist()                  { this(ShotTuning.Defaults()); }
    public ShotAssist(ShotTuning Tuning) { this.T = Tuning; }

    public Debug Debug() { return LastDecision; }

    public double TargetHalfWidth() { return T.TargetHalfWidthFrac * TableSpec.Width / 2; }
    public double TargetNearDepth() { return T.TargetDepthMinFrac * TableSpec.Length / 2; }
    public double TargetFarDepth()  { return T.TargetDepthMaxFrac * TableSpec.Length / 2; }

    /**
     * @param incoming  the ball just before the contact
     * @param physical  the raw impulse result
     * @param playerHit true sends the ball toward -Z, false toward +Z
     */
    public BallState Assist(BallState Incoming, BallState Physical, Racket Struck, boolean PlayerHit) {
        double ToOpp = PlayerHit ? -1.0 : 1.0;
        Vec3 Contact = Physical.Position();
        Vec3 Reflect = Physical.Velocity();

        Intent In = ReadIntent(Contact, Struck, ToOpp);
        double Quality = Quality(Incoming, In.OffX(), In.OffY());
        double Assist = PlayerHit ? T.AssistFloor + (1 - T.AssistFloor) * Quality : 1.0;
        Target Goal = ReadTarget(In, ToOpp);
        double WantSpeed = ReadSpeed(In.SwingAmount(), Incoming);
        Spin SpinPlan = ReadSpin(In.Brush(), In.SwipeX());

        Candidate Best = Search(Contact, Reflect, Goal, WantSpeed, SpinPlan, ToOpp);
        boolean MayRescue = !PlayerHit
                || (Quality >= T.RescueQualityFloor && In.SwingAmount() <= T.RescueEffortCeiling);
        if (Best.Cost() > 0 && MayRescue) Best = Rescue(Contact, Goal.Want().X(), SpinPlan, ToOpp, Best);

        // A clean hit gets the authored shot; a shank keeps proportionally more raw physics.
        Vec3 FinalVel  = Vec3.Lerp(CapReflection(Reflect), Best.Vel(), Assist);
        Vec3 FinalSpin = Vec3.Lerp(Physical.Spin(), SpinFor(Best.Vel(), Best.SpinPlan()), Assist);

        LastDecision = new Debug(Contact, Struck.Velocity(), Incoming.Velocity(), SafeDir(Reflect),
                          SafeDir(new Vec3(Best.Goal().X() - Contact.X(), 0,
                                           Best.Goal().Z() - Contact.Z())),
                          SafeDir(FinalVel), Best.Goal(), Best.Trajectory().Landing(),
                          FinalVel.Length(), FinalSpin, Best.Passes(), Best.Cost() == 0);

        return new BallState(Physical.Position(), FinalVel, FinalSpin, Physical.Orientation());
    }

    /**
     * Correction passes pull the target toward safe and slow the pace; within a pass a speed ladder
     * starts at the asked-for pace. The first legal candidate ends the search.
     */
    private Candidate Search(Vec3 Contact, Vec3 Reflect, Target Goal, double WantSpeed, Spin SpinPlan,
                             double ToOpp) {
        Candidate Best = null;
        double BestScore = Double.MAX_VALUE;
        double Floor = Math.max(T.MinSearchSpeed, WantSpeed * T.SearchSpeedFloorFrac);

        for (int Pass = 0; Pass <= T.MaxCorrectionPasses; Pass++) {
            double Give = Math.min(1, Pass * T.TargetAssist);
            Vec3 AimAt = Vec3.Lerp(Goal.Want(), Goal.Safe(), Give);
            double Pace = WantSpeed * (1 - T.SpeedBackoffPerPass * Pass);

            for (int K = 0; K < T.SpeedCandidates; K++) {
                double Speed = Clamp(Pace * SpeedFactor(K), Math.min(Floor, T.MaxShotSpeed),
                                     T.MaxShotSpeed);
                LaunchSolver.Solution Sol = LaunchSolver.AtTarget(Contact, AimAt, Speed, SpinPlan.Top(), SpinPlan.Side());
                Vec3 Blended = Vec3.Lerp(Sol.State().Velocity(), CapReflection(Reflect), T.PhysicalBlend);
                Candidate C = Evaluate(Contact, AimAt, Constrain(Blended, ToOpp, Speed), SpinPlan, ToOpp, Pass);

                double Score = C.Cost() * IllegalityWeight
                             + Math.abs(Speed - WantSpeed) * T.SpeedPreference
                             + Pass * T.PassPenalty;
                if (Score < BestScore) { BestScore = Score; Best = C; }
                if (C.Cost() == 0) return Best;
            }
        }
        return Best;
    }

    /**
     * Every sensible depth down the middle from a soft lift to full pace, keeping as much of the
     * player's aim, then spin, as still works. Some contacts only have a slow, honest answer.
     */
    private Candidate Rescue(Vec3 Contact, double WantX, Spin SpinPlan, double ToOpp, Candidate Best) {
        Spin[] Spins = {SpinPlan, new Spin(T.BaseTopspin, 0)};
        int RescuedPasses = T.MaxCorrectionPasses + 1;
        for (double AimFrac : T.RescueAimFracs) {
            for (Spin Sp : Spins) {
                for (double Depth : T.RescueDepthFracs) {
                    Vec3 AimAt = new Vec3(WantX * AimFrac, 0, ToOpp * Depth * HalfLength);
                    for (int K = 0; K < T.RescueSpeedSteps; K++) {
                        double Speed = T.RescueMinSpeed + (T.MaxShotSpeed - T.RescueMinSpeed)
                                * K / (double) (T.RescueSpeedSteps - 1);
                        LaunchSolver.Solution Sol = LaunchSolver.AtTarget(Contact, AimAt, Speed, Sp.Top(), Sp.Side());
                        Vec3 Vel = Constrain(Sol.State().Velocity(), ToOpp, Speed);
                        Candidate C = Evaluate(Contact, AimAt, Vel, Sp, ToOpp, RescuedPasses);
                        if (C.Cost() < Best.Cost()) Best = C;
                        if (C.Cost() == 0) return Best;
                    }
                }
            }
        }
        return Best;
    }

    private Candidate Evaluate(Vec3 Contact, Vec3 Goal, Vec3 Vel, Spin SpinPlan, double ToOpp, int Passes) {
        Flight F = Fly(Contact, Vel, SpinFor(Vel, SpinPlan), ToOpp);
        return new Candidate(Vel, Goal, F, Illegality(F, ToOpp), Passes, SpinPlan);
    }

    private static Vec3 SpinFor(Vec3 Vel, Spin SpinPlan) {
        return SpinVector.Of(new Vec3(Vel.X(), 0, Vel.Z()), SpinPlan.Top(), SpinPlan.Side());
    }

    /**
     * Drive is toward the opponent, swipe to the player's right, lift upward (live only while
     * brushing). Strength comes from the forward drive on a saturating curve; the contact offset
     * is measured in the face's own plane as a fraction of the blade radius.
     */
    private Intent ReadIntent(Vec3 Contact, Racket Struck, double ToOpp) {
        Vec3 Swing = Struck.Velocity();
        double Drive  = Swing.Z() * ToOpp;
        double SwipeX = Swing.X();
        double Lift   = Swing.Y();
        double Brush = Lift + Drive * T.DriveBrush;

        double Effort = Math.max(0, Drive) + T.LateralEffort * Math.hypot(SwipeX, Lift);
        double SwingAmount = Clamp(Math.pow(
                Clamp(Effort / T.MaxSwingSpeed, 0, 1), T.SwingCurve), 0, 1) * T.SwingInfluence;

        Vec3 Off = Contact.Minus(Struck.Position());
        Vec3 InPlane = Off.Minus(Struck.Normal().Scale(Off.Dot(Struck.Normal())));
        double OffX = Clamp(InPlane.X() / RacketSpec.BladeRadius, -1, 1);
        double OffY = Clamp(InPlane.Y() / RacketSpec.BladeRadius, -1, 1);
        double FaceX = Clamp(Struck.Normal().X() * -ToOpp, -1, 1);

        return new Intent(Drive, SwipeX, Lift, Brush, SwingAmount, OffX, OffY, FaceX);
    }

    /** 1 mid-blade to 0 at the rim, with a core that shrinks as the ball arrives faster. */
    private double Quality(BallState Incoming, double OffX, double OffY) {
        double OffR = Math.min(1, Math.hypot(OffX, OffY));
        double PaceFrac = Clamp((Incoming.Speed() - T.QualityPaceFrom) / T.QualityPaceSpan, 0, 1);
        double Core = Math.max(T.QualityCoreMin, T.QualityCore - T.QualityPaceLoss * PaceFrac);
        double Rim  = Core + T.QualityFalloff;
        return Clamp((Rim - OffR) / (Rim - Core), 0, 1);
    }

    private Target ReadTarget(Intent In, double ToOpp) {
        double AimFraction = In.SwipeX() * T.AimInfluence
                   + In.FaceX() * T.FaceInfluence
                   + In.OffX() * T.ContactPointInfluence;
        double WantX = Clamp(AimFraction, -1, 1) * TargetHalfWidth();

        double DepthFrac = T.TargetDepthMinFrac
                + (T.TargetDepthMaxFrac - T.TargetDepthMinFrac)
                  * Clamp(T.BaseDepthFrac + In.Drive() * T.DepthInfluence - In.Brush() * T.ArcInfluence
                               - In.OffY() * T.ContactPointInfluence * T.ContactDepthShare, 0, 1);
        double WantZ = ToOpp * Clamp(DepthFrac, T.TargetDepthMinFrac, T.TargetDepthMaxFrac) * HalfLength;

        return new Target(new Vec3(WantX, 0, WantZ),
                          new Vec3(0, 0, ToOpp * T.SafeDepthFrac * HalfLength));
    }

    /** The incoming pace only nudges the band, never adds to it, so a rally cannot compound. */
    private double ReadSpeed(double SwingAmount, BallState Incoming) {
        double WantSpeed = T.MinShotSpeed + (T.MaxShotSpeed - T.MinShotSpeed) * SwingAmount;
        WantSpeed += Clamp((Incoming.Speed() - T.IncomingPaceNeutral) * T.IncomingPaceGain,
                           -T.IncomingPaceNudge, T.IncomingPaceNudge);
        return Clamp(WantSpeed, T.MinShotSpeed, T.MaxShotSpeed);
    }

    private Spin ReadSpin(double Brush, double SwipeX) {
        double TopRevs  = Clamp((T.BaseTopspin + Brush * T.TopspinPerLift) * T.SpinInfluence,
                                -T.MaxSpin, T.MaxSpin);
        double SideRevs = Clamp(SwipeX * T.SidespinPerSwipe * T.SpinInfluence,
                                -T.MaxSpin, T.MaxSpin);
        return new Spin(TopRevs, SideRevs);
    }

    /** 1.0 first, then alternately slower and faster, so a legal shot costs one solve. */
    private double SpeedFactor(int K) {
        if (K == 0) return 1.0;
        int Step = (K + 1) / 2;
        double D = T.SpeedSpread * Step / Math.max(1, T.SpeedCandidates / 2);
        return (K % 2 == 1) ? 1.0 - D : 1.0 + D;
    }

    private Vec3 CapReflection(Vec3 Raw) {
        double Sp = Raw.Length();
        return Sp > T.ReflectionCap ? Raw.Scale(T.ReflectionCap / Sp) : Raw;
    }

    /**
     * Forward pace, a lateral cone and cap, an elevation band, and the candidate's OWN top speed:
     * a shot that only works slowly must be allowed to stay slow.
     */
    private Vec3 Constrain(Vec3 V, double ToOpp, double Cap) {
        double Fwd = Math.max(Math.min(T.MinForwardVelocity, Cap), V.Z() * ToOpp);

        double ConeLimit = Math.tan(Math.toRadians(T.MaxHorizontalDeviationDeg)) * Fwd;
        double Vx = Clamp(V.X(), -Math.min(ConeLimit, T.MaxLateralVelocity),
                                  Math.min(ConeLimit, T.MaxLateralVelocity));

        double Horiz = Math.hypot(Vx, Fwd);
        double Up = Clamp(V.Y(),
                Math.tan(Math.toRadians(T.MinVerticalLaunchAngleDeg)) * Horiz,
                Math.tan(Math.toRadians(T.MaxVerticalLaunchAngleDeg)) * Horiz);

        Vec3 Out = new Vec3(Vx, Up, ToOpp * Fwd);
        double Sp = Out.Length();
        return Sp > Cap ? Out.Scale(Cap / Sp) : Out;
    }

    /** 0 clears the net and lands in; otherwise the size of the violation. */
    private double Illegality(Flight F, double ToOpp) {
        double Cost = 0;

        double Needed = NetSpec.Height + BallSpec.Radius + T.NetClearance;
        if (Double.isNaN(F.NetHeight())) Cost += NeverCrossedCost;
        else if (F.NetHeight() < Needed) Cost += (Needed - F.NetHeight()) * NetShortfallWeight;

        Vec3 Landing = F.Landing();
        double Depth = Landing.Z() * ToOpp;                                // + is into their half
        if (Depth < T.LandingMargin) Cost += (T.LandingMargin - Depth) * LandingMissWeight;
        double MaxDepth = HalfLength - T.LandingMargin;
        if (Depth > MaxDepth) Cost += (Depth - MaxDepth) * LandingMissWeight;

        double Side = Math.abs(Landing.X()) - (HalfWidth - T.LandingMargin);
        if (Side > 0) Cost += Side * LandingMissWeight;

        return Cost;
    }

    /** Contact-free flight: asking a World with a table in it where a shot lands is circular. */
    private static Flight Fly(Vec3 From, Vec3 Vel, Vec3 SpinPlan, double ToOpp) {
        TrialFlight.Heading Toward = ToOpp < 0 ? TrialFlight.Heading.TowardNegativeZ
                                               : TrialFlight.Heading.TowardPositiveZ;
        return TrialFlight.Fly(BallState.At(From, Vel, SpinPlan), Toward, ValidateDt,
                               (int) (MaxFlightTime / ValidateDt));
    }

    private static Vec3 SafeDir(Vec3 V) {
        Vec3 N = V.Normalized();
        return N.LengthSquared() < 1e-6 ? DownTable : N;
    }

    private static double Clamp(double X, double Lo, double Hi) {
        return X < Lo ? Lo : (X > Hi ? Hi : X);
    }
}
