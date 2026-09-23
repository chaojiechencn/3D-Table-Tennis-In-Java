package tabletennis.game;

import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.contact.BladeCollider;
import tabletennis.engine.math.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A bit-level record of the game, used to prove a restructuring changed nothing. Every scenario
 * drives the real session with scripted input, hashes each step's ball and blades, and logs every
 * contact, point and replay. Tokens are fixed here, not taken from enum names, so the trace
 * survives renames. Run {@code main} to rewrite the fixture after a DELIBERATE behavior change.
 */
public final class GoldenTrace {

    static final String FixtureResource = "golden-trace.txt";

    private static final int StepsPerSecond = 480;
    private static final int ScenarioSteps = 12 * StepsPerSecond;
    private static final int MouseEventStride = 8;   // a 60 Hz mouse against the 480 Hz step

    private GoldenTrace() {}

    /** Scripted input for one step, applied before the session advances. */
    private interface Hand {
        void Apply(GameSession Session, int Step);
    }

    private record Scenario(String Name, Shots Feed, Hand Input) {}

    public static void main(String[] Args) throws IOException {
        Path Target = Path.of(Args[0]);
        Files.createDirectories(Target.getParent());
        Files.write(Target, Record(), StandardCharsets.UTF_8);
        System.out.println("wrote " + Target);
    }

    static List<String> Record() {
        List<String> Lines = new ArrayList<>();
        for (Scenario Each : Scenarios()) Lines.addAll(Run(Each));
        return Lines;
    }

    private static List<Scenario> Scenarios() {
        List<Scenario> All = new ArrayList<>();
        for (Shots Feed : Shots.All) {
            All.add(new Scenario(Feed.Name() + " / idle", Feed, (Session, Step) -> { }));
            All.add(new Scenario(Feed.Name() + " / pointer", Feed, GoldenTrace::PointAtTheBall));
            All.add(new Scenario(Feed.Name() + " / demo", Feed, (Session, Step) -> {
                if (Step == 0) Session.SetDemoMode(true);
            }));
        }
        Shots Serve = Shots.ByName("Serve");
        All.add(new Scenario("Serve / sweep with brush", Serve, new SweepingHand()));
        All.add(new Scenario("Serve / demo toggled", Serve, GoldenTrace::ToggleDemo));
        All.add(new Scenario("Serve / auto-replay toggled", Serve, GoldenTrace::ToggleAutoReplay));
        return All;
    }

    private static void PointAtTheBall(GameSession Session, int Step) {
        Vec3 Ball = Session.Ball().Position();
        Session.SetAim(PlayerReach.Clamp(new Vec3(Ball.X(), 0, Ball.Z())));
    }

    /** Demo for four seconds, a pointing hand for four, then demo again. */
    private static void ToggleDemo(GameSession Session, int Step) {
        if (Step == 0) Session.SetDemoMode(true);
        if (Step == 4 * StepsPerSecond) Session.SetDemoMode(false);
        if (Step == 8 * StepsPerSecond) Session.SetDemoMode(true);
        if (!Session.DemoMode()) PointAtTheBall(Session, Step);
    }

    private static void ToggleAutoReplay(GameSession Session, int Step) {
        if (Step == 3 * StepsPerSecond) Session.SetAutoReplay(false);
        if (Step == 7 * StepsPerSecond) Session.SetAutoReplay(true);
        PointAtTheBall(Session, Step);
    }

    /** A cursor sweeping the whole envelope at mouse rate, with two held-brush segments. */
    private static final class SweepingHand implements Hand {
        private boolean Brushing;
        private double HoldZ;

        @Override public void Apply(GameSession Session, int Step) {
            if (Step % MouseEventStride != 0) return;
            double Seconds = Step * Simulation.Step;
            boolean Brush = (Seconds >= 2 && Seconds < 4) || (Seconds >= 7 && Seconds < 8.5);
            if (Brush && !Brushing) HoldZ = Session.PlayerBlade().Centre().Z();
            Brushing = Brush;

            Vec3 RawAim = new Vec3(0.9 * Math.sin(2 * Math.PI * Seconds / 2.3), PlayerReach.HitY,
                                   1.3 + 0.9 * Math.sin(2 * Math.PI * Seconds / 3.1));
            double HeightFraction = 0.5 + 0.5 * Math.sin(2 * Math.PI * Seconds / 0.7);
            Session.SetAim(Brushing ? PlayerReach.ClampBrushed(RawAim, HeightFraction, HoldZ)
                                    : PlayerReach.Clamp(RawAim));
        }
    }

    private static List<String> Run(Scenario Each) {
        List<String> Lines = new ArrayList<>();
        Lines.add("scenario " + Each.Name());
        GameSession Session = new GameSession();
        Session.Launch(Each.Feed());
        StateHash Hash = new StateHash();

        for (int Step = 0; Step < ScenarioSteps; Step++) {
            Each.Input().Apply(Session, Step);
            GameSession.StepResult Result = Session.Step();
            Hash.Mix(Session.Ball());
            Hash.Mix(Session.PlayerBlade());
            Hash.Mix(Session.OpponentBlade());

            if (Result.HitBy() != null) Lines.add("@" + Step + " hit " + Token(Result.HitBy()));
            if (Result.PointTo() != null) Lines.add("@" + Step + " point " + Token(Result.PointTo()));
            if (Session.ReplayDue()) {
                Lines.add("@" + Step + " replay");
                Session.Launch(Each.Feed());
            }
            if ((Step + 1) % StepsPerSecond == 0) Lines.add("#" + (Step + 1) + " " + Hash.Hex());
        }
        Scoreboard.Snapshot Score = Session.Score();
        Lines.add(String.format("= %s points %d-%d games %d-%d", Hash.Hex(), Score.PlayerPoints(),
                                Score.OpponentPoints(), Score.PlayerGames(), Score.OpponentGames()));
        return Lines;
    }

    private static String Token(Side Hitter) {
        return Hitter == Side.Player ? "player" : "opponent";
    }

    /** 64-bit FNV-1a over the exact bits of every double, so a last-place difference shows. */
    private static final class StateHash {
        private long Value = 0xcbf29ce484222325L;

        void Mix(BallState Ball) {
            Mix(Ball.Position());
            Mix(Ball.Velocity());
            Mix(Ball.Spin());
            Mix(Ball.Orientation().W());
            Mix(Ball.Orientation().X());
            Mix(Ball.Orientation().Y());
            Mix(Ball.Orientation().Z());
        }

        void Mix(BladeCollider Blade) {
            Mix(Blade.Centre());
            Mix(Blade.Normal());
            Mix(Blade.Velocity());
            Mix(Blade.AngularVelocity());
        }

        void Mix(Vec3 Vector) {
            Mix(Vector.X());
            Mix(Vector.Y());
            Mix(Vector.Z());
        }

        void Mix(double Number) {
            long Bits = Double.doubleToLongBits(Number);
            for (int Shift = 0; Shift < 64; Shift += 8) {
                Value ^= (Bits >>> Shift) & 0xff;
                Value *= 0x100000001b3L;
            }
        }

        String Hex() { return String.format("%016x", Value); }
    }
}
