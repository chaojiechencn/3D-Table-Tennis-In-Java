package tabletennis.game.shot;

import org.junit.jupiter.api.Test;
import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;
import tabletennis.engine.RacketSpec;
import tabletennis.engine.Simulation;
import tabletennis.engine.flight.TrialFlight;
import tabletennis.engine.math.Numeric;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.Racket;
import tabletennis.game.GameSession;
import tabletennis.game.StepResult;
import tabletennis.game.feed.Feed;
import tabletennis.game.feed.Feeds;
import tabletennis.game.rally.Side;

import java.util.ArrayList;
import java.util.List;

import static tabletennis.testing.Claims.Check;

/**
 * The shadow contact model, anchored against rigid-body contact mechanics rather than its own
 * output: spin comes only from slip across the face, its axis is the lever arm crossed with the
 * friction, and a blade far heavier than the ball can give it no more energy than the ball had in
 * the blade's frame.
 */
final class ContactModelTest {

    private static final ContactModel Model = new ContactModel(ShotTuning.Defaults().Contact());

    /** The player's blade, square to the table and facing the opponent. */
    private static final Vec3 Square = new Vec3(0, 0, -1);
    private static final Vec3 Blade = new Vec3(0, 0.16, 1.20);

    /** A ball coming at the player with no spin. */
    private static final BallState Arriving = BallState.At(Vec3.Zero, new Vec3(0, -1, 4), Vec3.Zero);

    private static final double RacketSpeed = 8.0;

    /**
     * Square means the relative velocity lies along the face normal, so the ball arrives straight
     * at the face: a falling ball slides across a square face and is spun by it, as it should be.
     */
    @Test
    void ASquareHitGivesLittleSpinAndMorePace() {
        BallState Straight = BallState.At(Vec3.Zero, new Vec3(0, 0, 4), Vec3.Zero);
        ShotReport Flat = ShotReport.Of(Hit(Straight, Square, Square.Scale(RacketSpeed)));
        ShotReport Glancing = ShotReport.Of(Hit(Straight, Square, Along(45, 0).Scale(RacketSpeed)));
        double FlatSpin = Math.hypot(Flat.TopRevs(), Flat.SideRevs());
        double GlancingSpin = Math.hypot(Glancing.TopRevs(), Glancing.SideRevs());
        Check("a square hit has under 5% of a 45-degree brush's spin and leaves faster",
              FlatSpin < 0.05 * GlancingSpin && Flat.Speed() > Glancing.Speed(),
              String.format("square %.1f rev/s at %.2f m/s; 45-degree brush %.1f rev/s at %.2f m/s, racket %.0f m/s",
                            FlatSpin, Flat.Speed(), GlancingSpin, Glancing.Speed(), RacketSpeed));
    }

    /** Friction drags the ball's back upward with the blade: the top rolls forward. */
    @Test
    void AnUpwardBrushGivesTopspin() {
        ShotReport Brush = ShotReport.Of(Hit(Arriving, Square, new Vec3(0, 6, -3)));
        Check("an upward brush gives topspin",
              Brush.TopRevs() > 0 && Brush.Speed() > 0,
              String.format("racket (0, +6, -3) m/s -> topspin %+.1f rev/s, %.2f m/s", Brush.TopRevs(), Brush.Speed()));
    }

    @Test
    void ADownwardChopGivesBackspin() {
        ShotReport Chop = ShotReport.Of(Hit(Arriving, Square, new Vec3(0, -6, -3)));
        Check("a downward chop gives backspin",
              Chop.TopRevs() < 0,
              String.format("racket (0, -6, -3) m/s -> topspin %+.1f rev/s, %.2f m/s", Chop.TopRevs(), Chop.Speed()));
    }

    /**
     * The friction on the ball points along the swipe, and the lever arm from the ball's centre to
     * the contact points at the blade (+Z): +Z x +X = +Y, so a swipe to the right spins about +Y.
     */
    @Test
    void ASidewaysSwipeGivesSidespinOfTheMatchingSign() {
        ShotReport Right = ShotReport.Of(Hit(Arriving, Square, new Vec3(6, 0, -3)));
        ShotReport Left = ShotReport.Of(Hit(Arriving, Square, new Vec3(-6, 0, -3)));
        boolean Mirrored = Math.abs(Right.SideRevs() + Left.SideRevs()) <= 1e-9 * Math.abs(Right.SideRevs());
        Check("a swipe to the right spins about +Y, a swipe to the left mirrors it",
              Right.SideRevs() > 0 && Left.SideRevs() < 0 && Mirrored,
              String.format("swipe +X -> side %+.2f rev/s, swipe -X -> side %+.2f rev/s; aim %+.1f / %+.1f deg",
                            Right.SideRevs(), Left.SideRevs(), Right.AimDeg(), Left.AimDeg()));
    }

    /**
     * An infinitely massive blade can only reflect the ball in its own frame, so the ball's energy
     * relative to the blade (translation plus spin) cannot grow. Swept over swings, face angles,
     * incoming balls and spins, with the default rubber and with every knob at its most energetic.
     */
    @Test
    void NoContactAddsEnergyBeyondWhatTheRacketSupplies() {
        ShotTuning Extreme = ShotTuning.Builder().SwipeToDirection(1).MinRestitution(1).MaxRestitution(1)
                                       .SpinTransfer(1).SpinTransferFade(0).Build();
        List<ContactModel> Models = List.of(Model, new ContactModel(Extreme.Contact()));
        Vec3[] Faces = {Square, new Vec3(0.5, 0, -1), new Vec3(-0.5, 0.3, -1), new Vec3(0, 0.55, -1), new Vec3(0, -0.4, -1)};
        Vec3[] Incoming = {new Vec3(0, -1, 4), new Vec3(1.5, -2, 9), new Vec3(-2, 0.5, 2), new Vec3(0, -3, 0.5)};
        Vec3[] Spins = {Vec3.Zero, new Vec3(-300, 0, 0), new Vec3(300, 0, 0), new Vec3(0, 250, 0), new Vec3(120, -200, 90)};
        double[] Components = {-9, -3, 0, 3, 9};

        int Contacts = 0, Gained = 0;
        double WorstRatio = 0;
        for (ContactModel Each : Models) for (Vec3 Face : Faces) for (Vec3 In : Incoming) for (Vec3 Spin : Spins)
            for (double X : Components) for (double Y : Components) for (double Z : Components) {
                Vec3 Swing = new Vec3(X, Y, Z);
                BallState Before = BallState.At(Vec3.Zero, In, Spin);
                BallState After = HitWith(Each, Before, Face, Swing);
                if (After == null) continue;
                Contacts++;
                double Ratio = RelativeEnergy(After, Swing) / RelativeEnergy(Before, Swing);
                WorstRatio = Math.max(WorstRatio, Ratio);
                if (Ratio > 1 + 1e-12) Gained++;
            }
        Check("no contact leaves the ball more energy in the blade's frame than it arrived with",
              Gained == 0 && Contacts > 1000,
              String.format("%d contacts, %d gained energy; largest out/in ratio %.4f", Contacts, Gained, WorstRatio));
    }

    /**
     * The face keys change the spin through the contact physics alone: the same flat drive into a
     * face closed by W (normal tilted down) slides the ball up the face and tops it, and a face
     * opened by S slides it down and cuts it.
     */
    @Test
    void ClosingTheFaceTopsAFlatDriveAndOpeningItCutsIt() {
        Vec3 Drive = new Vec3(0, 0, -8);
        BallState Straight = BallState.At(Vec3.Zero, new Vec3(0, 0, 4), Vec3.Zero);
        ShotReport Closed = ShotReport.Of(Hit(Straight, new Vec3(0, -0.6, -1), Drive));
        ShotReport Flat = ShotReport.Of(Hit(Straight, Square, Drive));
        ShotReport Opened = ShotReport.Of(Hit(Straight, new Vec3(0, 0.6, -1), Drive));
        Check("a closed face (W) tops a flat drive, a square face does not, an open face (S) cuts it",
              Closed.TopRevs() > 5 && Math.abs(Flat.TopRevs()) < 0.5 && Opened.TopRevs() < -5,
              String.format("closed %+.1f rev/s at %+.0f deg, square %+.1f, open %+.1f rev/s at %+.0f deg",
                            Closed.TopRevs(), Closed.ElevationDeg(), Flat.TopRevs(), Opened.TopRevs(),
                            Opened.ElevationDeg()));
    }

    @Test
    void TheSameContactGivesTheSameShot() {
        Vec3 Swing = new Vec3(2.5, 1.0, -7.3);
        BallState Spun = BallState.At(Vec3.Zero, new Vec3(0.4, -1.2, 6.1), new Vec3(-180, 40, 12));
        BallState First = Hit(Spun, new Vec3(0.3, 0.4, -1), Swing);
        BallState Second = Hit(Spun, new Vec3(0.3, 0.4, -1), Swing);
        Check("the same contact twice gives a bit-identical shot", First.equals(Second), "velocity " + First.Velocity());
    }

    /**
     * A mouse that reports every third step: the blade moves three steps at 12 m/s, then waits
     * three. One step reads 0 or 12 m/s; the hand's actual speed is 6.
     */
    @Test
    void TheWindowAveragesAwayMouseJitter() {
        SwingHistory History = new SwingHistory(ShotTuning.Defaults().Contact().VelocityWindow());
        Vec3 At = Vec3.Zero;
        double Worst = 0;
        for (int Step = 0; Step < 200; Step++) {
            if (Step % 6 < 3) At = At.Plus(new Vec3(0, 0, -12 * Simulation.Step));
            History.Record(At);
            if (Step >= 40) Worst = Math.max(Worst, Math.abs(History.AverageVelocity().Length() - 6) / 6);
        }
        int Steps = SwingHistory.StepsIn(ShotTuning.Defaults().Contact().VelocityWindow());
        Check("the swing window reads a jittery 6 m/s hand within 10%, where one step reads 0 or 12",
              Worst < 0.10,
              String.format("window %d steps (%.1f ms); worst error %.1f%%", Steps, Steps * Simulation.Step * 1000, Worst * 100));
    }

    /** The game still plays the assist's shot (the golden trace pins that); this pins the log. */
    @Test
    void EveryPlayerHitIsComparedAndNothingElse() {
        int PlayerHits = 0, Compared = 0, Stray = 0;
        List<String> Sample = new ArrayList<>();
        for (Feed Shot : Feeds.All) {
            GameSession Game = new GameSession();
            Game.SetDemoMode(true);
            Game.Launch(Shot);
            for (int Step = 0; Step < 6 * Simulation.StepsPerSecond; Step++) {
                StepResult Result = Game.Step();
                boolean PlayerHit = Result.HitBy() == Side.Player;
                if (PlayerHit) PlayerHits++;
                if (PlayerHit && Result.Shadow() != null) Compared++;
                if (!PlayerHit && Result.Shadow() != null) Stray++;
                if (PlayerHit && Sample.size() < 4) Sample.addAll(Result.Shadow().Lines());
                if (Game.ReplayDue()) break;
            }
        }
        Sample.forEach(Line -> System.out.println("    " + Line));
        Check("every player hit carries a shadow comparison, and no other step does",
              PlayerHits > 0 && Compared == PlayerHits && Stray == 0,
              String.format("%d player hits over %d demo rallies, %d compared, %d stray",
                            PlayerHits, Feeds.All.size(), Compared, Stray));
    }

    /**
     * Through the whole shot pipeline: the spin a stroke is PLAYED with is the one the racket's
     * motion made, and the search still lands it. Its sidespin is amplified by SidespinGain and
     * capped, then scaled down only as far as the curve must shrink to land. A player hit, clean
     * and mid-blade.
     */
    @Test
    void ThePlayedShotCarriesTheRacketsSpinAndStillLands() {
        record Stroke(String Name, Vec3 Swing) {}
        Stroke[] Strokes = {
            new Stroke("brush up", new Vec3(0, 6, -3)), new Stroke("chop down", new Vec3(0, -6, -3)),
            new Stroke("swipe right", new Vec3(6, 0, -3)), new Stroke("swipe left", new Vec3(-6, 0, -3)),
        };
        List<String> Measured = new ArrayList<>();
        boolean AllMatch = true;
        for (Stroke Each : Strokes) {
            ShotAssist Assist = new ShotAssist();
            Racket Bat = new Racket(Blade, Square);
            Bat.MoveTo(Blade, Square, Simulation.Step);   // still this step: only the averaged swing moves
            Vec3 At = Blade.PlusScaled(Square, BallSpec.Radius + RacketSpec.BladeThickness / 2);
            BallState Incoming = Arriving.WithPosition(At);
            BallState Raw = Hit(Arriving, Square, Each.Swing());
            ShotReport Made = ShotReport.Of(Raw);
            ShotReport Played = ShotReport.Of(Assist.Assist(Incoming, Raw, Bat, true, Each.Swing()),
                                              Assist.CurveGainFor(true));

            ShotTuning Tuning = ShotTuning.Defaults();
            double TopCap = Tuning.Spin().MaxSpin(), SideCap = Tuning.Contact().MaxSidespin();
            double WantSide = Numeric.Clamp(Made.SideRevs() * Tuning.Contact().SidespinGain(), -SideCap, SideCap);
            boolean SameTop = Math.abs(Played.TopRevs() - Numeric.Clamp(Made.TopRevs(), -TopCap, TopCap)) < 0.5;
            boolean SideFits = WantSide == 0 ? Math.abs(Played.SideRevs()) < 0.5
                             : Math.signum(Played.SideRevs()) == Math.signum(WantSide)
                               && Math.abs(Played.SideRevs()) <= Math.abs(WantSide) + 0.5;
            boolean SameSpin = SameTop && SideFits;
            AllMatch &= SameSpin && Played.LandsIn();
            Measured.add(String.format("%s: racket top %+.1f side %+.1f, played top %+.1f side %+.1f, %s",
                                       Each.Name(), Made.TopRevs(), Made.SideRevs(), Played.TopRevs(),
                                       Played.SideRevs(), Played.LandsIn() ? "lands in" : "MISSES"));
        }
        Check("each stroke is played with the racket's topspin and its sidespin's direction (scaled at most to "
              + "SidespinGain times it, less to fit MaxCurve) and lands in",
              AllMatch, String.join("; ", Measured));
    }

    /**
     * The curve a player sees: how far the landing sits from the line the ball set off along,
     * positive to the player's right. Swept over swipe speeds, both ways; by Magnus (w x v), spin
     * about +Y bends a ball heading down-table to the left. The measured lift alone bends this shot
     * about 12 cm; under the player's CurveGain of 5.625 the same launch bends 76 cm (7.5 bent
     * 93-98 cm, 10 bent 1.2 m), and the search still lands it.
     */
    @Test
    void ASwipeCurvesVisiblyAndStillLands() {
        List<String> Measured = new ArrayList<>();
        double SmallestFirmCurve = Double.MAX_VALUE;
        int Landed = 0, Swipes = 0, WrongWay = 0;
        double SmallestGainRatio = Double.MAX_VALUE;
        for (double Speed : new double[]{1, 2, 3, 4, 6, 8, -1, -2, -3, -4, -6, -8}) {
            ShotAssist Assist = new ShotAssist();
            Racket Bat = new Racket(Blade, Square);
            Bat.MoveTo(Blade, Square, Simulation.Step);
            Vec3 Swing = new Vec3(Speed, 0, -4);
            Vec3 At = Blade.PlusScaled(Square, BallSpec.Radius + RacketSpec.BladeThickness / 2);
            BallState Played = Assist.Assist(Arriving.WithPosition(At), Hit(Arriving, Square, Swing), Bat, true, Swing);
            double Gain = Assist.CurveGainFor(true);
            ShotReport Report = ShotReport.Of(Played, Gain);

            double Curve = Bend(Played, Gain);
            double Measured1x = Bend(Played, 1.0);
            SmallestGainRatio = Math.min(SmallestGainRatio, Math.abs(Curve / Measured1x));
            boolean RightWay = Report.SideRevs() > 0 ? Curve < 0 : Curve > 0;
            if (!RightWay) WrongWay++;
            Swipes++;
            if (Report.LandsIn()) Landed++;
            if (Math.abs(Speed) >= 3) SmallestFirmCurve = Math.min(SmallestFirmCurve, Math.abs(Curve));
            Measured.add(String.format("%+.0f m/s: side %+.0f rev/s, aim %+.0f deg, curve %+.0f cm, %s", Speed,
                                       Report.SideRevs(), Report.AimDeg(), Curve * 100, Report.LandsIn() ? "in" : "OUT"));
        }
        Check("a firm swipe (3 m/s and up) curves at least 60 cm the way its spin says, every swipe lands, "
              + "and the curve gain multiplies the bend at least 5-fold",
              SmallestFirmCurve >= 0.60 && Landed == Swipes && WrongWay == 0 && SmallestGainRatio >= 5,
              String.format("%d of %d land, %d bend the wrong way, smallest gain ratio %.1f; %s", Landed, Swipes,
                            WrongWay, SmallestGainRatio, String.join("; ", Measured)));
    }

    /** Sideways distance from the launch's own heading line to where it lands, positive to the right. */
    private static double Bend(BallState Launch, double CurveGain) {
        Vec3 Heading = new Vec3(Launch.Velocity().X(), 0, Launch.Velocity().Z()).Normalized();
        Vec3 Right = new Vec3(-Heading.Z(), 0, Heading.X());
        Vec3 Landing = TrialFlight.Fly(Launch, TrialFlight.Heading.TowardNegativeZ, Simulation.Step,
                                       3 * Simulation.StepsPerSecond, CurveGain).Landing();
        Vec3 Travel = Landing.Minus(Launch.Position());
        return Travel.Dot(Right);
    }

    /** The racket moving along this direction: Up degrees from driving straight ahead toward +Y, Right toward +X. */
    private static Vec3 Along(double UpDeg, double RightDeg) {
        double Up = Math.toRadians(UpDeg), Right = Math.toRadians(RightDeg);
        return new Vec3(Math.sin(Right) * Math.cos(Up), Math.sin(Up), -Math.cos(Right) * Math.cos(Up));
    }

    private static BallState Hit(BallState Ball, Vec3 Face, Vec3 Swing) {
        return HitWith(Model, Ball, Face, Swing);
    }

    /** The ball placed touching the face, in front of it. */
    private static BallState HitWith(ContactModel Each, BallState Ball, Vec3 Face, Vec3 Swing) {
        Vec3 Normal = Face.Normalized();
        Vec3 At = Blade.PlusScaled(Normal, BallSpec.Radius + RacketSpec.BladeThickness / 2);
        return Each.Strike(Ball.WithPosition(At), At, Blade, Normal, Swing);
    }

    private static double RelativeEnergy(BallState Ball, Vec3 Swing) {
        return 0.5 * BallSpec.Mass * Ball.Velocity().Minus(Swing).LengthSquared()
             + 0.5 * BallSpec.Inertia * Ball.Spin().LengthSquared();
    }
}
