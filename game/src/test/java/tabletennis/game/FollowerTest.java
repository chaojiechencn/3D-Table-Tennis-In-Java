package tabletennis.game;

import org.junit.jupiter.api.Test;
import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.TableSpec;
import tabletennis.engine.contact.Materials;
import tabletennis.engine.flight.TrialFlight;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.PhysicsWorld;
import tabletennis.engine.world.Racket;
import tabletennis.engine.world.SurfaceKind;

import java.util.List;

import static tabletennis.testing.Claims.Check;

/** The current opponent against every feed that reaches it: a wall that must not miss or cheat. */
final class FollowerTest {

    /** What happened when one shot was fed at the opponent. */
    private record Rally(boolean Touched, boolean Returned, double MaxZ, double RawSpeed) {}

    /** What became of one return, flown all the way down. */
    private record Return(double Apex, double LandingZ, double LandingX, double OutSpeed) {

        boolean LandsOnTheTable() {
            return LandingZ > 0.02 && LandingZ < TableSpec.Length / 2 && Math.abs(LandingX) < TableSpec.Width / 2;
        }

        String Verdict() {
            if (LandingZ < -90) return "never came down";
            if (LandsOnTheTable()) return String.format("IN   at z=%+.2f", LandingZ);
            if (LandingZ <= 0.02) return String.format("short, z=%+.2f", LandingZ);
            if (Math.abs(LandingX) >= TableSpec.Width / 2) return String.format("wide, x=%+.2f", LandingX);
            return String.format("long, z=%+.2f", LandingZ);
        }
    }

    /** Every shot in the menu has to be reached. A wall that misses is not a wall. */
    @Test
    void TheOpponentReachesEveryShot() {
        int Reached = 0, Playable = 0;
        StringBuilder Missed = new StringBuilder();
        for (Shots Shot : Shots.All) {
            if (!IsFedAtTheOpponent(Shot)) continue;
            Playable++;
            if (Feed(Shot).Touched()) Reached++;
            else Missed.append(Shot.Name()).append("; ");
        }
        Check("the follower reaches every shot fed at it",
              Reached == Playable,
              String.format("%d of %d shots reached%s", Reached, Playable,
                            Missed.length() == 0 ? "" : " -- missed: " + Missed));
    }

    /** And having reached them, it has to put them back over the net. */
    @Test
    void TheOpponentReturnsEveryShot() {
        int Returned = 0, Playable = 0;
        StringBuilder Failed = new StringBuilder();
        for (Shots Shot : Shots.All) {
            if (!IsFedAtTheOpponent(Shot)) continue;
            Playable++;
            Rally Result = Feed(Shot);
            if (Result.Returned()) Returned++;
            else Failed.append(String.format("%s (reached z=%.2f); ", Shot.Name(), Result.MaxZ()));
        }
        Check("the follower returns every shot back over the net",
              Returned == Playable,
              String.format("%d of %d returned%s", Returned, Playable,
                            Failed.length() == 0 ? "" : " -- failed: " + Failed));
    }

    /** Unbeatable is allowed; cheating is not: no return may leave faster than a blade could send it. */
    @Test
    void TheOpponentDoesNotCheat() {
        double Fastest = 0;
        String Worst = "";
        for (Shots Shot : Shots.All) {
            if (!IsFedAtTheOpponent(Shot)) continue;
            Rally Result = Feed(Shot);
            if (Result.RawSpeed() > Fastest) { Fastest = Result.RawSpeed(); Worst = Shot.Name(); }
        }
        // A ball can leave at (1+e) times the blade speed plus its own incoming speed.
        double Ceiling = (1 + Materials.Rubber.Restitution()) * 25.0 + 30.0;
        Check("no return leaves faster than the impulse could possibly have sent it",
              Fastest < Ceiling,
              String.format("fastest return %.1f m/s (%s) against a ceiling of %.1f", Fastest, Worst, Ceiling));
    }

    @Test
    void ARallyStaysInTheRoom() {
        double Highest = 0;
        String Worst = "";
        for (Shots Shot : Shots.All) {
            if (!IsFedAtTheOpponent(Shot)) continue;
            Return Result = PlayOut(Shot);
            if (Result.Apex() > Highest) { Highest = Result.Apex(); Worst = Shot.Name(); }
        }
        Check("no return is ever launched out of the hall",
              Highest < 3.0,
              String.format("highest apex %.2f m (%s) against a 3.0 m ceiling", Highest, Worst));
    }

    /** Where every return actually lands: printed in full, and then asserted. */
    @Test
    void EveryAssistedReturnLands() {
        System.out.println("  where the returns land:");
        int In = 0, Played = 0;
        StringBuilder Missed = new StringBuilder();
        for (Shots Shot : Shots.All) {
            if (!IsFedAtTheOpponent(Shot)) continue;
            Played++;
            Return Result = PlayOut(Shot);
            if (Result.LandsOnTheTable()) In++;
            else Missed.append(Shot.Name()).append(" (").append(Result.Verdict()).append("); ");
            System.out.printf("    %-24s out %5.1f m/s  apex %4.2f m  %s%n",
                              Shot.Name(), Result.OutSpeed(), Result.Apex(), Result.Verdict());
        }
        Check("every assisted return lands on the opponent's half of the table",
              In == Played,
              String.format("%d of %d land%s", In, Played, Missed.length() == 0 ? "" : " -- missed: " + Missed));
    }

    /** Feed one shot and let the follower play it, through the assist as the game does. */
    private static Rally Feed(Shots Shot) {
        PhysicsWorld Physics = new PhysicsWorld();
        Racket Blade = new Racket(Follower.Ready, Follower.Square);
        Opponent Ai = new Follower();
        ShotAssist Assist = new ShotAssist();
        Physics.SetRackets(List.of(Blade));
        Physics.Launch(Shot.State());

        boolean Touched = false;
        double MaxZ = -9, RawSpeed = 0;
        for (int Step = 0; Step < (int) (4.0 / Simulation.Step); Step++) {
            Ai.Advance(Physics.Ball(), Blade, Simulation.Step);
            BallState Before = Physics.Ball();
            boolean Struck = Physics.Step().Touched(SurfaceKind.Blade);

            if (!Touched && Struck) {
                Touched = true;
                RawSpeed = Physics.Ball().Speed();
                Physics.ReplaceBall(Assist.Assist(Before, Physics.Ball(), Blade, false));
            }
            if (Touched) MaxZ = Math.max(MaxZ, Physics.Ball().Position().Z());

            // Once it has come back past the net there is nothing more to learn.
            if (Touched && Physics.Ball().Position().Z() > 0.05) break;
            if (Physics.Ball().Position().Y() < -TableSpec.Height + 0.05 && Touched) break;
        }
        return new Rally(Touched, Touched && MaxZ > 0.0, MaxZ, RawSpeed);
    }

    /** Feed one shot, let the follower answer it, and watch the answer all the way down. */
    private static Return PlayOut(Shots Shot) {
        PhysicsWorld Physics = new PhysicsWorld();
        Racket Blade = new Racket(Follower.Ready, Follower.Square);
        Opponent Ai = new Follower();
        Physics.SetRackets(List.of(Blade));
        Physics.Launch(Shot.State());

        ShotAssist Assist = new ShotAssist();
        boolean Hit = false;
        double Apex = 0, OutSpeed = 0;
        int HitAt = -1;
        Vec3 Landing = null;

        for (int Step = 0; Step < (int) (6.0 / Simulation.Step); Step++) {
            Ai.Advance(Physics.Ball(), Blade, Simulation.Step);
            BallState Previous = Physics.Ball();
            boolean Struck = Physics.Step().Touched(SurfaceKind.Blade);

            if (!Hit && Struck) {
                Hit = true;
                HitAt = Step;
                Physics.ReplaceBall(Assist.Assist(Previous, Physics.Ball(), Blade, false));
                OutSpeed = Physics.Ball().Speed();
                Landing = TrialFlight.LandingPoint(Physics.Ball());
            }
            if (!Hit) continue;

            Apex = Math.max(Apex, Physics.Ball().Position().Y());
            // Nothing more to learn once it is on the floor or has left the far end.
            if (Step > HitAt + 20 && Physics.Ball().Position().Y() < -TableSpec.Height + 0.05) break;
        }
        return Landing == null ? new Return(Apex, -99, -99, OutSpeed)
                               : new Return(Apex, Landing.Z(), Landing.X(), OutSpeed);
    }

    /** Shots the opponent actually ever sees: launched from the near end and carried past the net. */
    private static boolean IsFedAtTheOpponent(Shots Shot) {
        if (Shot.State().Position().Z() <= 0) return false;
        PhysicsWorld Physics = new PhysicsWorld();
        Physics.Launch(Shot.State());
        for (int Step = 0; Step < (int) (3.0 / Simulation.Step); Step++) {
            Physics.Step();
            if (Physics.Ball().Position().Z() < -0.5) return true;
        }
        return false;
    }
}
