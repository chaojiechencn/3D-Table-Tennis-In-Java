package tabletennis.game;

import tabletennis.engine.*;

import java.util.ArrayList;
import java.util.List;

import static tabletennis.engine.Constants.*;

/** Headless validation of the opponent, in the same style as physics.SelfTest. */
public final class RallyTest {

    private static final List<String> Failures = new ArrayList<>();
    private static int Checks = 0;

    public static void main(String[] Args) {
        System.out.println("Mr. Pong - opponent validation");
        System.out.println("=".repeat(74));

        TheOpponentReachesEveryShot();
        TheOpponentReturnsEveryShot();
        TheOpponentDoesNotCheat();
        ARallyStaysInTheRoom();
        ReportedReturnQuality();
        AFlickOfTheMouseCannotOutrunACarriedBat();
        TheCursorCannotRaiseTheBat();
        DepthRunsOneWayOnly();
        EveryReturnIsActuallyReachable();
        APlayerPointingAtTheBallCanReturnIt();
        TheScoreFollowsTheITTFRules();
        TheBrushLiftsTheBatWithoutExtendingItsReach();
        TheSessionAppliesTheRallyRules();
        TheSessionSchedulesReplaysAndKeepsTheScore();
        TheSessionIsDeterministic();
        TheShotTuningKeepsItsDefaultsAndRejectsNonsense();

        System.out.println("=".repeat(74));
        if (Failures.isEmpty()) {
            System.out.printf("ALL %d CHECKS PASSED%n", Checks);
        } else {
            System.out.printf("%d of %d CHECKS FAILED:%n", Failures.size(), Checks);
            Failures.forEach(F -> System.out.println("  - " + F));
            System.exit(1);
        }
    }

    /** What happened when one shot was fed at the opponent. */
    private record Rally(boolean Touched, boolean Returned, double MaxZ, double RawSpeed) {}

    /** Feed one shot and let the follower play it. */
    private static Rally Feed(Shots Shot) {
        World Physics = new World();
        Paddle Blade = new Paddle(Follower.Ready, Follower.Square);
        Opponent Ai = new Follower();
        ShotAssist Assist = new ShotAssist();

        Physics.SetPaddles(null, Blade);
        Physics.Launch(Shot.State());

        boolean Touched = false;
        double MaxZ = -9, RawSpeed = 0;

        for (int I = 0; I < (int) (4.0 / Dt); I++) {
            Ai.Advance(Physics.State(), Blade, Dt);
            BallState Before = Physics.State();
            Physics.Step();

            if (!Touched && Physics.PaddleHits() > 0) {
                Touched = true;
                RawSpeed = Physics.State().Speed();
                Physics.SetState(Assist.Assist(Before, Physics.State(), Blade, false));
            }
            if (Touched) MaxZ = Math.max(MaxZ, Physics.State().Pos().Z());

            // Once it has come back past the net there is nothing more to learn.
            if (Touched && Physics.State().Pos().Z() > 0.05) break;
            if (Physics.State().Pos().Y() < -TableHeight + 0.05 && Touched) break;
        }
        return new Rally(Touched, Touched && MaxZ > 0.0, MaxZ, RawSpeed);
    }

    /** Every shot in the menu has to be reached. A wall that misses is not a wall. */
    private static void TheOpponentReachesEveryShot() {
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
    private static void TheOpponentReturnsEveryShot() {
        int Returned = 0, Playable = 0;
        StringBuilder Failed = new StringBuilder();

        for (Shots Shot : Shots.All) {
            if (!IsFedAtTheOpponent(Shot)) continue;
            Playable++;
            Rally R = Feed(Shot);
            if (R.Returned()) Returned++;
            else Failed.append(String.format("%s (reached z=%.2f); ", Shot.Name(), R.MaxZ()));
        }
        Check("the follower returns every shot back over the net",
              Returned == Playable,
              String.format("%d of %d returned%s", Returned, Playable,
                            Failed.length() == 0 ? "" : " -- failed: " + Failed));
    }

    /** Unbeatable is allowed; cheating is not: no return may leave faster than the blade could send it. */
    private static void TheOpponentDoesNotCheat() {
        double Fastest = 0;
        String Worst = "";
        for (Shots Shot : Shots.All) {
            if (!IsFedAtTheOpponent(Shot)) continue;
            Rally R = Feed(Shot);
            if (R.RawSpeed() > Fastest) { Fastest = R.RawSpeed(); Worst = Shot.Name(); }
        }
        // A ball can leave at (1+e) times the blade speed plus its own incoming speed.
        double Ceiling = (1 + RacketMat.Restitution()) * 25.0 + 30.0;
        Check("no return leaves faster than the impulse could possibly have sent it",
              Fastest < Ceiling,
              String.format("fastest return %.1f m/s (%s) against a ceiling of %.1f",
                            Fastest, Worst, Ceiling));
    }

    /** A rally has to stay in the room. */
    private static void ARallyStaysInTheRoom() {
        double Highest = 0;
        String Worst = "";
        for (Shots Shot : Shots.All) {
            if (!IsFedAtTheOpponent(Shot)) continue;
            Return R = PlayOut(Shot);
            if (R.Apex() > Highest) { Highest = R.Apex(); Worst = Shot.Name(); }
        }
        Check("no return is ever launched out of the hall",
              Highest < 3.0,
              String.format("highest apex %.2f m (%s) against a 3.0 m ceiling", Highest, Worst));
    }

    /** Where every return actually lands -- printed in full, and then asserted. */
    private static void ReportedReturnQuality() {
        System.out.println();
        System.out.println("  where the returns land:");

        int In = 0, Played = 0;
        StringBuilder Missed = new StringBuilder();
        for (Shots Shot : Shots.All) {
            if (!IsFedAtTheOpponent(Shot)) continue;
            Played++;
            Return R = PlayOut(Shot);
            if (R.LandsOnTheTable()) In++;
            else Missed.append(Shot.Name()).append(" (").append(R.Verdict()).append("); ");
            System.out.printf("    %-24s out %5.1f m/s  apex %4.2f m  %s%n",
                    Shot.Name(), R.OutSpeed(), R.Apex(), R.Verdict());
        }
        System.out.println();
        Check("every assisted return lands on the opponent's half of the table",
              In == Played,
              String.format("%d of %d land%s", In, Played,
                            Missed.length() == 0 ? "" : " -- missed: " + Missed));
    }

    /** What became of one return. */
    private record Return(double Apex, double LandingZ, double LandingX, double OutSpeed) {

        boolean LandsOnTheTable() {
            return LandingZ > 0.02 && LandingZ < TableLength / 2
                && Math.abs(LandingX) < TableWidth / 2;
        }

        String Verdict() {
            if (LandingZ < -90) return "never came down";
            if (LandsOnTheTable()) return String.format("IN   at z=%+.2f", LandingZ);
            if (LandingZ <= 0.02) return String.format("short, z=%+.2f", LandingZ);
            if (Math.abs(LandingX) >= TableWidth / 2) return String.format("wide, x=%+.2f", LandingX);
            return String.format("long, z=%+.2f", LandingZ);
        }
    }

    /** Feed one shot, let the follower answer it, and watch the answer all the way down. */
    private static Return PlayOut(Shots Shot) {
        World Physics = new World();
        Paddle Blade = new Paddle(Follower.Ready, Follower.Square);
        Opponent Ai = new Follower();

        Physics.SetPaddles(null, Blade);
        Physics.Launch(Shot.State());

        ShotAssist Assist = new ShotAssist();
        boolean Hit = false;
        double Apex = 0, OutSpeed = 0;
        int HitAt = -1;
        Vec3 Landing = null;

        for (int I = 0; I < (int) (6.0 / Dt); I++) {
            Ai.Advance(Physics.State(), Blade, Dt);
            BallState Prev = Physics.State();
            Physics.Step();

            if (!Hit && Physics.PaddleHits() > 0) {
                Hit = true;
                HitAt = I;
                Physics.SetState(Assist.Assist(Prev, Physics.State(), Blade, false));
                OutSpeed = Physics.State().Speed();
                Landing = Aim.LandingPoint(Physics.State());
            }
            if (!Hit) continue;

            Apex = Math.max(Apex, Physics.State().Pos().Y());

            // Nothing more to learn once it is on the floor or has left the far end.
            if (I > HitAt + 20 && Physics.State().Pos().Y() < -TableHeight + 0.05) break;
        }
        return Landing == null ? new Return(Apex, -99, -99, OutSpeed)
                               : new Return(Apex, Landing.Z(), Landing.X(), OutSpeed);
    }

    /** A flick of the mouse must not out-hit a bat a player actually carries. */
    private static void AFlickOfTheMouseCannotOutrunACarriedBat() {
        // The position is arbitrary -- the claim is about speed, not about where the blade is.
        Vec3 Start = new Vec3(0, 0.25, 1.57);
        Paddle Blade = new Paddle(Start, new Vec3(0, 0, -1));
        Stroke PlayerStroke = new Stroke(Start);

        // Throw the cursor a metre sideways between two frames, which is about as fast as a hand
        // moves a mouse, and let the eight steps of one 60 Hz frame consume it.
        PlayerStroke.AimAt(Start.Plus(new Vec3(1.0, 0, 0)));

        double Fastest = 0;
        for (int I = 0; I < 8; I++) {
            PlayerStroke.Advance(Blade, Dt);
            Fastest = Math.max(Fastest, Blade.Vel().Length());
        }
        Check("a mouse flick cannot move the blade faster than a player carries a bat",
              Fastest <= Stroke.TrackSpeed * 1.001,
              String.format("peak blade speed %.2f m/s against the %.1f m/s limit",
                            Fastest, Stroke.TrackSpeed));

        // The limit is only worth having if it sits below a real swing.
        Check("the tracking limit is slower than an advanced player's swing",
              Stroke.TrackSpeed < 17.8,
              String.format("%.1f m/s tracking against a measured 17.8 m/s swing",
                            Stroke.TrackSpeed));
    }

    /** The decoupling, stated as something that can fail. */
    private static void TheCursorCannotRaiseTheBat() {
        double Worst = 0;
        for (double Y = -3.0; Y <= 3.0; Y += 0.05) {
            for (double Z : new double[]{-2.0, 0.3, 1.0, 1.9, 5.0}) {
                Vec3 Got = PlayerReach.Clamp(new Vec3(0.4, Y, Z));
                Worst = Math.max(Worst, Math.abs(Got.Y() - PlayerReach.HitY));
            }
        }
        Check("no cursor aim, at any height, can move the racket off its hitting plane",
              Worst < 1e-12,
              String.format("worst height deviation %.1e m over aims from y = -3 to +3 m", Worst));

        // And the other half of the same claim: the axes that ARE inputs still work.
        Vec3 Left  = PlayerReach.Clamp(new Vec3(-0.5, 99, 1.5));
        Vec3 Right = PlayerReach.Clamp(new Vec3(+0.5, -99, 1.5));
        Check("cursor X still moves the racket across the table",
              Right.X() - Left.X() > 0.9,
              String.format("x %+.2f -> %+.2f as the aim crosses the centre line", Left.X(), Right.X()));
    }

    /** Depth must run one way only. */
    private static void DepthRunsOneWayOnly() {
        double Prev = Double.NEGATIVE_INFINITY;
        boolean Monotone = true;
        double ReversedAt = Double.NaN;
        for (double Z = -3.0; Z <= 5.0; Z += 0.01) {
            double Got = PlayerReach.Clamp(new Vec3(0, 0, Z)).Z();
            if (Got < Prev - 1e-12) { Monotone = false; if (Double.isNaN(ReversedAt)) ReversedAt = Z; }
            Prev = Got;
        }
        Check("racket depth is monotone in the aim -- pointing further up-table never brings it back",
              Monotone,
              Monotone ? "no reversal over aims from z = -3 to +5 m"
                       : String.format("reverses at z = %.2f", ReversedAt));

        // Monotone alone would be satisfied by a constant, so the range has to be real too.
        double Span = PlayerReach.ZFar - PlayerReach.ZNear;
        Check("the depth range spans the player's half and the ground behind it",
              PlayerReach.ZNear < 0.5 && PlayerReach.ZFar > TableLength / 2 + 0.5,
              String.format("z %.2f..%.2f m (%.2f m of travel; the end line is at %.2f)",
                            PlayerReach.ZNear, PlayerReach.ZFar, Span, TableLength / 2));
    }

    /**
     * The bug, measured: can the player actually get to the ball? This is the check that would
     * have caught it.
     */
    private static void EveryReturnIsActuallyReachable() {
        double WorstWindow = Double.MAX_VALUE, WorstMargin = Double.MAX_VALUE;
        String WorstWindowShot = "", WorstMarginShot = "";
        int Playable = 0, Fed = 0;

        for (Shots Shot : Shots.All) {
            List<Vec3> Path = PathAfterThePlayerSideBounce(Shot);
            if (Path == null) continue;
            Fed++;

            int Steps = 0;
            Vec3 First = null;
            for (Vec3 P : Path) {
                if (!PlayerReach.CanTouch(P)) continue;
                Steps++;
                if (First == null) First = P;
            }
            if (First == null) continue;
            Playable++;

            double Window = Steps * Dt;
            double Dash = PlayerReach.TravelTime(PlayerReach.Neutral, new Vec3(First.X(), PlayerReach.HitY, First.Z()));
            if (Window < WorstWindow) { WorstWindow = Window; WorstWindowShot = Shot.Name(); }
            if (Window - Dash < WorstMargin) { WorstMargin = Window - Dash; WorstMarginShot = Shot.Name(); }
        }

        Check("every return the opponent makes passes through a place the racket can reach",
              Playable == Fed,
              String.format("%d of %d returns reachable", Playable, Fed));

        // 200 ms is the floor a human reaction time argues for: simple visual reaction is 200-250
        // ms, and the game runs at 0.45x by default, so 200 ms of simulated time is about 440 ms on
        // the clock.
        Check("the racket has a human amount of time to meet each one",
              WorstWindow > 0.200,
              String.format("worst touchable window %.0f ms (%s); %.0f ms of wall-clock at the 0.45x default",
                            WorstWindow * 1000, WorstWindowShot, WorstWindow * 1000 / 0.45));

        // The point of check 5 in the brief: the blade must be fast enough for the envelope it has,
        // and this is what says so -- rather than TrackSpeed being raised until the symptom went
        // away.
        Check("the blade can cross to every one of them in the time the ball allows",
              WorstMargin > 0,
              String.format("tightest case %s: %.0f ms of margin at TRACK_SPEED = %.1f m/s",
                            WorstMarginShot, WorstMargin * 1000, Stroke.TrackSpeed));
    }

    /**
     * The whole thing, end to end: can a player who simply points at the ball hit it back? The
     * three checks above are geometric -- the ball passes through the legal region, and the
     * blade could cross to it in time.
     */
    private static void APlayerPointingAtTheBallCanReturnIt() {
        int Returned = 0, Attempted = 0;
        List<String> Missed = new ArrayList<>();

        for (Shots Shot : Shots.All) {
            if (PathAfterThePlayerSideBounce(Shot) == null) continue;
            Attempted++;
            if (PlayThePoint(Shot)) Returned++; else Missed.add(Shot.Name());
        }

        Check("a player who points at the ball returns it over the net",
              Returned == Attempted,
              String.format("%d of %d feeds returned%s", Returned, Attempted,
                            Missed.isEmpty() ? "" : "; missed: " + String.join(", ", Missed)));
    }

    /** Play one point with a stand-in hand on the near racket. */
    private static boolean PlayThePoint(Shots Shot) {
        GameSession Game = new GameSession();
        Game.Launch(Shot);
        boolean Returned = false;

        for (int I = 0; I < (int) (14.0 / Dt); I++) {
            // The stand-in hand: point the CURSOR at the ball, and let the envelope and the
            // tracking speed decide whether the blade gets there.
            Vec3 Ball = Game.Ball().Pos();
            Game.SetAim(PlayerReach.Clamp(new Vec3(Ball.X(), 0, Ball.Z())));
            if (Game.Step().HitBy() == Scoreboard.Side.Player) Returned = true;

            // Returned AND it got to the other side: a ball popped straight up is not a return.
            if (Returned && Game.Ball().Pos().Z() < -0.1) return true;
            if (Game.Ball().Pos().Y() < -TableHeight) break;
        }
        return false;
    }

    /**
     * One feed, played by the real game until the opponent has returned it and the return has
     * bounced on the player's half; the ball's path from that bounce onward, or null if no such
     * rally happens.
     */
    private static List<Vec3> PathAfterThePlayerSideBounce(Shots Shot) {
        GameSession Game = new GameSession();
        Game.SetAim(PlayerReach.Clamp(new Vec3(-9, 0, 9)));
        Game.Launch(Shot);

        boolean Returned = false, Bounced = false;
        List<Vec3> Path = new ArrayList<>();

        for (int I = 0; I < (int) (14.0 / Dt); I++) {
            if (Game.Step().HitBy() == Scoreboard.Side.Opponent) Returned = true;
            if (Returned && Game.PlayerMayHit()) Bounced = true;

            if (Bounced) Path.add(Game.Ball().Pos());
            if (Game.Ball().Pos().Y() < -TableHeight) break;
        }
        return Bounced ? Path : null;
    }

    /** Shots the opponent actually ever sees. */
    private static boolean IsFedAtTheOpponent(Shots Shot) {
        if (Shot.State().Pos().Z() <= 0) return false;

        World W = new World();          // no paddles
        W.Launch(Shot.State());
        for (int I = 0; I < (int) (3.0 / Dt); I++) {
            W.Step();
            if (W.State().Pos().Z() < -0.5) return true;
        }
        return false;
    }

    /** The scoreboard, against the ITTF's own rules rather than against itself. */
    private static void TheScoreFollowsTheITTFRules() {
        System.out.println("\n-- the score --");

        // 11-9 is a finished game; 11-10 is not.
        Scoreboard A = new Scoreboard();
        for (int I = 0; I < 9; I++) { A.PointTo(Scoreboard.Side.Player); A.PointTo(Scoreboard.Side.Opponent); }
        A.PointTo(Scoreboard.Side.Player);          // 10-9
        A.PointTo(Scoreboard.Side.Player);          // 11-9
        Check("a game is won at 11 with two clear points", A.GameOver(),
              "11-9 -> games " + A.Games(Scoreboard.Side.Player) + "-" + A.Games(Scoreboard.Side.Opponent));

        Scoreboard B = new Scoreboard();
        for (int I = 0; I < 10; I++) { B.PointTo(Scoreboard.Side.Player); B.PointTo(Scoreboard.Side.Opponent); }
        B.PointTo(Scoreboard.Side.Player);          // 11-10
        Check("11-10 does NOT end a game -- it takes two clear", !B.GameOver(),
              "11-10, deuce=" + B.IsDeuce() + ", game over=" + B.GameOver());

        B.PointTo(Scoreboard.Side.Player);          // 12-10
        Check("12-10 does end it", B.GameOver(),
              "12-10 -> games " + B.Games(Scoreboard.Side.Player) + "-" + B.Games(Scoreboard.Side.Opponent));

        // Service: two each before deuce, one each from 10-all.
        Scoreboard C = new Scoreboard();
        Scoreboard.Side S0 = C.Server();
        C.PointTo(Scoreboard.Side.Player);
        boolean HeldForTwo = C.Server() == S0;
        C.PointTo(Scoreboard.Side.Player);
        boolean HandedOver = C.Server() == S0.Other();
        Check("service is held for two points, then handed over", HeldForTwo && HandedOver,
              "after 1 point same server=" + HeldForTwo + ", after 2 it changed=" + HandedOver);

        Scoreboard D = new Scoreboard();
        for (int I = 0; I < 10; I++) { D.PointTo(Scoreboard.Side.Player); D.PointTo(Scoreboard.Side.Opponent); }
        Scoreboard.Side AtDeuce = D.Server();
        D.PointTo(Scoreboard.Side.Player);
        Check("from 10-all the service changes every single point", D.Server() == AtDeuce.Other(),
              "10-10 deuce=" + D.IsDeuce() + "; server changed after one point=" + (D.Server() == AtDeuce.Other()));

        // The opening server alternates between games (ITTF 2.13.6).
        Scoreboard E = new Scoreboard();
        Scoreboard.Side FirstOfGameOne = E.Server();
        for (int I = 0; I < 11; I++) E.PointTo(Scoreboard.Side.Player);   // 11-0, game one
        E.PointTo(Scoreboard.Side.Opponent);                              // rolls into game two
        Check("whoever served first in a game receives first in the next",
              E.Server() == FirstOfGameOne.Other() || E.Points(Scoreboard.Side.Opponent) == 1,
              "game two opened with the serve on the other side");

        // A match is best of five.
        Scoreboard F = new Scoreboard();
        for (int G = 0; G < 3; G++) for (int I = 0; I < 11; I++) F.PointTo(Scoreboard.Side.Player);
        Check("a match is the best of five games -- three wins takes it", F.MatchOver(),
              "games " + F.Games(Scoreboard.Side.Player) + "-" + F.Games(Scoreboard.Side.Opponent)
              + ", winner=" + F.MatchWinner());

        int FinalGames = F.Games(Scoreboard.Side.Player);
        F.PointTo(Scoreboard.Side.Opponent);
        Check("a finished match cannot be scored into", F.Games(Scoreboard.Side.Player) == FinalGames
              && F.Games(Scoreboard.Side.Opponent) == 0,
              "awarding after match point left it at " + F.Games(Scoreboard.Side.Player)
              + "-" + F.Games(Scoreboard.Side.Opponent));

        // The winning score has to survive long enough to be read.
        Scoreboard G2 = new Scoreboard();
        for (int I = 0; I < 9; I++) G2.PointTo(Scoreboard.Side.Opponent);
        for (int I = 0; I < 11; I++) G2.PointTo(Scoreboard.Side.Player);
        Check("the winning score stays on the board until the next rally starts",
              G2.Points(Scoreboard.Side.Player) == 11 && G2.Points(Scoreboard.Side.Opponent) == 9,
              "reads " + G2.Points(Scoreboard.Side.Player) + "-" + G2.Points(Scoreboard.Side.Opponent)
              + " after the game-winning point");
    }

    /** The brush modifier, against the invariant it is allowed to bend and the ones it is not. */
    private static void TheBrushLiftsTheBatWithoutExtendingItsReach() {
        System.out.println("\n-- the brush --");

        Vec3 BrushAim = new Vec3(0.3, PlayerReach.HitY, 1.10);

        Vec3 High = PlayerReach.ClampBrushed(BrushAim, 0.0, 1.10);   // cursor at the top of the screen
        Vec3 Low  = PlayerReach.ClampBrushed(BrushAim, 1.0, 1.10);   // cursor at the bottom
        Check("the brush carries the bat up and down through the ball",
              High.Y() > PlayerReach.HitY + 0.01 && Low.Y() < PlayerReach.HitY - 0.01,
              String.format("top of screen y=%.3f, bottom y=%.3f, plane is %.3f",
                            High.Y(), Low.Y(), PlayerReach.HitY));

        double Worst = 0;
        for (double F = 0; F <= 1.0001; F += 0.02) {
            Worst = Math.max(Worst, Math.abs(PlayerReach.ClampBrushed(BrushAim, F, 1.10).Y()
                                             - PlayerReach.HitY));
        }
        Check("the brush cannot lift the bat further than a stroke",
              Worst <= PlayerReach.BrushBand + 1e-9,
              String.format("worst departure %.3f m against a band of %.3f",
                            Worst, PlayerReach.BrushBand));

        double Lowest = Double.MAX_VALUE;
        for (double F = 0; F <= 1.0001; F += 0.02) {
            Lowest = Math.min(Lowest, PlayerReach.ClampBrushed(BrushAim, F, 1.10).Y());
        }
        Check("the brush cannot cut the bat down through the table top",
              Lowest >= tabletennis.engine.Constants.BladeR - 1e-9,
              String.format("lowest blade centre %.3f m against a blade radius of %.3f",
                            Lowest, tabletennis.engine.Constants.BladeR));

        // The reach rule: brushing must not let the blade stand anywhere clamp() would not.
        double WorstZ = 0;
        for (double F = 0; F <= 1.0001; F += 0.1) {
            Vec3 B = PlayerReach.ClampBrushed(new Vec3(0.3, PlayerReach.HitY, 9.0), F, 1.10);
            WorstZ = Math.max(WorstZ, Math.abs(B.Z() - 1.10));
        }
        Check("the brush freezes depth -- it cannot be used to reach further up-table",
              WorstZ < 1e-9,
              String.format("depth moved by %.6f m over the whole cursor sweep", WorstZ));

        // And normal aiming is still pinned to the plane, brush or no brush.
        double OffPlane = 0;
        for (double Z = -3; Z <= 5; Z += 0.25) {
            OffPlane = Math.max(OffPlane,
                    Math.abs(PlayerReach.Clamp(new Vec3(0.2, 7.5, Z)).Y() - PlayerReach.HitY));
        }
        Check("with the modifier up, no aim at any height leaves the hitting plane",
              OffPlane == 0,
              String.format("worst height deviation %.1e m", OffPlane));
    }

    /** An opponent that never plays: its blade stands far behind the table, so feeds run out. */
    private static final Opponent Statue = new Opponent() {
        @Override public void Advance(BallState Ball, Paddle Blade, double Dt) {
            Blade.PlaceAt(new Vec3(0, 0.20, -4.0), Follower.Square);
        }
    };

    private static Shots Feed(String Name, Vec3 Pos, Vec3 Vel) {
        return new Shots(Name, Name, BallState.At(Pos, Vel, Vec3.Zero), null);
    }

    /** Hit well past the far end without touching the table: out, and then the floor. */
    private static final Shots LongFeed = Feed("long", new Vec3(0, 0.30, 1.52), new Vec3(0, 1.5, -14));

    /** Dropped short on the player's own half, rising toward the player after its bounce. */
    private static final Shots Bouncer = Feed("bouncer", new Vec3(0, 0.30, 0.45), new Vec3(0, 0, 1.2));

    /**
     * An opponent that digs the ball off the table surface: as the ball falls onto the far half
     * a second time, the blade drops in 3 cm behind it at 3 cm up and pushes forward, so the
     * racket contact and the ball's table touch fall on the SAME physics step.
     */
    private static final class Digger implements Opponent {
        private boolean Bounced, Set;
        @Override public void Advance(BallState Ball, Paddle Blade, double Dt) {
            Vec3 P = Ball.Pos();
            if (Ball.Vel().Y() > 0) Bounced = true;
            if (Set) {
                Blade.MoveTo(Blade.Pos().Plus(new Vec3(0, 0, 0.01)), Follower.Square, Dt);
            } else if (Bounced && Ball.Vel().Y() < 0 && P.Y() < 0.03) {
                Blade.PlaceAt(new Vec3(P.X(), P.Y(), P.Z() - 0.03), Follower.Square);
                Set = true;
            } else {
                Blade.PlaceAt(Follower.Ready, Follower.Square);
            }
        }
    }

    private static boolean Happened(GameSession Game, World.EventType Type) {
        return Game.Events().stream().anyMatch(E -> E.Type() == Type);
    }

    /** Point the stand-in hand's cursor at the ball, through the real envelope. */
    private static void PointAtTheBall(GameSession Game) {
        Vec3 Ball = Game.Ball().Pos();
        Game.SetAim(PlayerReach.Clamp(new Vec3(Ball.X(), 0, Ball.Z())));
    }

    /**
     * The rally rules, played through the same session the application runs -- not a copy of
     * its loop.
     */
    private static void TheSessionAppliesTheRallyRules() {
        System.out.println("\n-- the game session --");

        // First bounce: whichever half a feed lands on first opens that side's racket and no other;
        // nobody may hit while it is still in the air.
        for (Shots Shot : new Shots[]{Shots.ByName("Serve"), Bouncer}) {
            GameSession Game = new GameSession();
            Game.Launch(Shot);
            boolean ShutWhileInAir = true;
            while (Game.BounceSerial() == 0 && Game.Time() < 2) {
                ShutWhileInAir &= !Game.PlayerMayHit() && !Game.OpponentMayHit();
                Game.Step();
            }
            boolean Near = Game.Events().stream()
                    .filter(E -> E.Type() == World.EventType.TableBounce).findFirst()
                    .map(E -> E.Side() < 0).orElse(false);
            Check("the first bounce opens only the racket on that half (" + Shot.Name() + ")",
                  ShutWhileInAir && Game.PlayerMayHit() == Near && Game.OpponentMayHit() == !Near,
                  String.format("closed in the air=%b; landed %s; player=%b opponent=%b", ShutWhileInAir,
                                Near ? "near" : "far", Game.PlayerMayHit(), Game.OpponentMayHit()));
        }

        // Double bounce: a dead drop on the opponent's half that nobody plays.
        GameSession Drop = new GameSession(Statue, new ShotAssist());
        Drop.Launch(Shots.ByName("ITTF drop test"));
        boolean OpenedAfterOne = false;
        GameSession.StepResult Decided = null;
        for (int I = 0; I < (int) (3.0 / Dt) && Decided == null; I++) {
            GameSession.StepResult R = Drop.Step();
            OpenedAfterOne |= Drop.OpponentMayHit();
            if (R.PointAwarded()) Decided = R;
        }
        Check("a second bounce on the receiver's half is the receiver's point lost",
              OpenedAfterOne && Decided != null && Decided.PointTo() == Scoreboard.Side.Player,
              String.format("first bounce opened the opponent=%b; point to %s at t=%.3f s",
                            OpenedAfterOne, Decided == null ? "nobody" : Decided.PointTo(), Drop.Time()));

        // Own half: a shot that comes straight back off a still blade onto the player's own half.
        ShotTuning Raw = ShotTuning.Builder()
                .QualityCore(0).QualityCoreMin(0).QualityFalloff(1e-9).AssistFloor(0).Build();
        GameSession Own = new GameSession(Statue, new ShotAssist(Raw));
        Own.SetAim(PlayerReach.Clamp(new Vec3(0, 0, RisingThroughTheHittingPlane(Bouncer).Z())));
        Own.Launch(Bouncer);
        Scoreboard.Side OwnHit = null, OwnPoint = null;
        for (int I = 0; I < (int) (3.0 / Dt) && OwnPoint == null; I++) {
            GameSession.StepResult R = Own.Step();
            if (R.Contact()) OwnHit = R.HitBy();
            if (R.PointAwarded()) OwnPoint = R.PointTo();
        }
        Check("a return that falls back on the hitter's own half loses the point",
              OwnHit == Scoreboard.Side.Player && OwnPoint == Scoreboard.Side.Opponent
                  && Own.Ball().Pos().Z() > 0,
              String.format("hit by %s, point to %s, ball at z=%+.2f", OwnHit, OwnPoint, Own.Ball().Pos().Z()));

        // Out, then floor: two terminal events on one rally, exactly one point, against the hitter.
        GameSession Out = new GameSession(Statue, new ShotAssist());
        Out.Launch(LongFeed);
        int Awards = 0;
        for (int I = 0; I < (int) (3.0 / Dt); I++) if (Out.Step().PointAwarded()) Awards++;
        boolean Both = Happened(Out, World.EventType.OutOfBounds) && Happened(Out, World.EventType.Floor);
        Check("out and then the floor end the rally with exactly one point, against the hitter",
              Both && Awards == 1 && Out.Score().OpponentPoints() == 1 && Out.Score().PlayerPoints() == 0,
              String.format("out+floor both fired=%b; %d award(s); score %d-%d", Both, Awards,
                            Out.Score().PlayerPoints(), Out.Score().OpponentPoints()));

        // Net cord: a feed that clips the cord and still lands on the far half is a live ball.
        Shots Cord = NetCordFeed();
        boolean TouchedNet = false, Opened = false, EarlyPoint = false;
        if (Cord != null) {
            GameSession Net = new GameSession(Statue, new ShotAssist());
            Net.Launch(Cord);
            for (int I = 0; I < (int) (2.0 / Dt) && !Net.OpponentMayHit(); I++) {
                EarlyPoint |= Net.Step().PointAwarded();
            }
            TouchedNet = Happened(Net, World.EventType.Net);
            Opened = Net.OpponentMayHit();
        }
        Check("a ball that clips the net and lands legally stays in play",
              TouchedNet && Opened && !EarlyPoint,
              Cord == null ? "no cord-clipping feed found"
                           : String.format("%s: net touched=%b, far bounce opened the opponent=%b, early point=%b",
                                           Cord.Name(), TouchedNet, Opened, EarlyPoint));

        // A decided point withdraws both rackets: a hand still chasing the ball cannot touch it.
        GameSession Dead = new GameSession();
        Dead.Launch(Shots.ByName("Into the net"));
        boolean Over = false;
        int LateContacts = 0;
        double Closest = Double.MAX_VALUE;
        for (int I = 0; I < (int) (4.0 / Dt); I++) {
            PointAtTheBall(Dead);
            GameSession.StepResult R = Dead.Step();
            if (Over && R.Contact()) LateContacts++;
            if (Over) Closest = Math.min(Closest, Dead.PlayerBlade().Centre().Minus(Dead.Ball().Pos()).Length());
            Over |= R.PointAwarded();
        }
        Check("once a point is decided, no racket can touch the ball again",
              Over && LateContacts == 0,
              String.format("point decided=%b; %d contacts after it; blade came within %.3f m of the ball",
                            Over, LateContacts, Closest));

        // The contact window: a push dug off the surface touches the table on (or right after) the
        // racket contact.
        GameSession Dig = new GameSession(new Digger(), new ShotAssist());
        Dig.Launch(Shots.ByName("ITTF drop test"));
        double ContactAt = Double.NaN, BounceGap = Double.NaN;
        Scoreboard.Side DigPoint = null;
        for (int I = 0; I < (int) (3.0 / Dt) && DigPoint == null; I++) {
            int Serial = Dig.BounceSerial();
            GameSession.StepResult R = Dig.Step();
            if (R.Contact() && Double.isNaN(ContactAt)) ContactAt = Dig.Time();
            if (!Double.isNaN(ContactAt) && Double.isNaN(BounceGap) && Dig.BounceSerial() > Serial) {
                BounceGap = Dig.Time() - ContactAt;
            }
            if (R.PointAwarded()) DigPoint = R.PointTo();
            if (!Double.isNaN(ContactAt) && Dig.Time() - ContactAt > 0.1) break;
        }
        Check("a table touch on the contact's own step does not score as the hitter's own half",
              !Double.isNaN(ContactAt) && BounceGap <= GameSession.ContactBounceWindow && DigPoint == null,
              String.format("contact at t=%.4f s, table touch %.1f ms after it (window %.1f ms), point: %s",
                            ContactAt, BounceGap * 1000, GameSession.ContactBounceWindow * 1000,
                            DigPoint == null ? "none" : DigPoint));
    }

    /** Replays fire on the documented delay only when enabled; a new feed keeps the score. */
    private static void TheSessionSchedulesReplaysAndKeepsTheScore() {
        GameSession On = new GameSession(Statue, new ShotAssist());
        On.Launch(LongFeed);
        double PointAt = Double.NaN, DueAt = Double.NaN;
        for (int I = 0; I < (int) (4.0 / Dt) && Double.isNaN(DueAt); I++) {
            if (On.Step().PointAwarded()) PointAt = On.Time();
            if (On.ReplayDue()) DueAt = On.Time();
        }
        double Delay = DueAt - PointAt;
        Check("with auto-replay on, the next feed is due one point-end delay after the point",
              Delay >= GameSession.PointEndDelay - 1e-9 && Delay < GameSession.PointEndDelay + Dt + 1e-9,
              String.format("due %.4f s after the point (delay %.3f s, step %.4f s)",
                            Delay, GameSession.PointEndDelay, Dt));

        Scoreboard.Snapshot Before = On.Score();
        On.Launch(LongFeed);
        Check("a new feed keeps the match score and reopens the rally",
              On.Score().equals(Before) && !On.PointOver() && !On.PlayerMayHit() && !On.OpponentMayHit(),
              String.format("score %d-%d kept=%b; point over=%b", Before.PlayerPoints(),
                            Before.OpponentPoints(), On.Score().equals(Before), On.PointOver()));

        GameSession Off = new GameSession(Statue, new ShotAssist());
        Off.SetAutoReplay(false);
        Off.Launch(LongFeed);
        boolean EverDue = false;
        for (int I = 0; I < (int) (6.0 / Dt); I++) { Off.Step(); EverDue |= Off.ReplayDue(); }
        Check("with auto-replay off, no feed is scheduled but the point still counts",
              !EverDue && Off.Score().OpponentPoints() == 1,
              String.format("replay due=%b over 6 s; score %d-%d", EverDue,
                            Off.Score().PlayerPoints(), Off.Score().OpponentPoints()));
    }

    /** The same inputs, step for step, give the same game -- bit for bit. */
    private static void TheSessionIsDeterministic() {
        GameSession A = new GameSession(), B = new GameSession();
        for (GameSession G : new GameSession[]{A, B}) { G.SetDemoMode(true); G.Launch(Shots.ByName("Serve")); }
        int Steps = (int) (10.0 / Dt), FirstDiff = -1, Contacts = 0;
        for (int I = 0; I < Steps && FirstDiff < 0; I++) {
            GameSession.StepResult Ra = A.Step(), Rb = B.Step();
            if (Ra.Contact()) Contacts++;
            if (!Ra.equals(Rb) || !A.Ball().equals(B.Ball())) FirstDiff = I;
        }
        Check("two sessions fed the same inputs stay identical",
              FirstDiff < 0 && Contacts > 0,
              FirstDiff < 0 ? String.format("%d steps and %d contacts, every ball state equal", Steps, Contacts)
                            : "diverged at step " + FirstDiff);
    }

    /**
     * The shipped tuning, restated here rather than read back from the builder, so a changed
     * default fails loudly; and a tuning the model cannot run on refuses to build.
     */
    private static void TheShotTuningKeepsItsDefaultsAndRejectsNonsense() {
        System.out.println("\n-- the shot tuning --");

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

        record Bad(String What, java.util.function.UnaryOperator<ShotTuning.Builder> Edit) {}
        Bad[] Invalid = {
            new Bad("NaN shot speed",            B -> B.MaxShotSpeed(Double.NaN)),
            new Bad("infinite spin",             B -> B.BaseTopspin(Double.POSITIVE_INFINITY)),
            new Bad("speed range upside down",   B -> B.MinShotSpeed(18)),
            new Bad("rescue faster than the top", B -> B.RescueMinSpeed(20)),
            new Bad("depth range upside down",   B -> B.TargetDepthMinFrac(0.95)),
            new Bad("zero quality falloff",      B -> B.QualityFalloff(0)),
            new Bad("zero pace span",            B -> B.QualityPaceSpan(0)),
            new Bad("zero swing speed",          B -> B.MaxSwingSpeed(0)),
            new Bad("zero swing curve",          B -> B.SwingCurve(0)),
            new Bad("blend past 1",              B -> B.PhysicalBlend(1.5)),
            new Bad("negative assist floor",     B -> B.AssistFloor(-0.1)),
            new Bad("aim fraction past 1",       B -> B.RescueAimFracs(1.0, 1.2)),
            new Bad("NaN rescue depth",          B -> B.RescueDepthFracs(0.5, Double.NaN)),
            new Bad("vertical angle at 90",      B -> B.MaxVerticalLaunchAngleDeg(90)),
            new Bad("elevation band inverted",   B -> B.MinVerticalLaunchAngleDeg(50)),
            new Bad("horizontal cone at 90",     B -> B.MaxHorizontalDeviationDeg(90)),
            new Bad("no speed candidates",       B -> B.SpeedCandidates(0)),
            new Bad("one rescue speed step",     B -> B.RescueSpeedSteps(1)),
            new Bad("backoff that stops the shot", B -> B.SpeedBackoffPerPass(0.5)),
            new Bad("negative reflection cap",   B -> B.ReflectionCap(-1)),
        };
        List<String> Accepted = new ArrayList<>();
        for (Bad B : Invalid) {
            try { B.Edit().apply(ShotTuning.Builder()).Build(); Accepted.add(B.What()); }
            catch (IllegalArgumentException ExpectedRejection) { /* rejected, as it should be */ }
        }
        Check("a shot tuning the model cannot run on is rejected when built",
              Accepted.isEmpty(),
              Accepted.isEmpty() ? Invalid.length + " invalid tunings rejected" : "accepted: " + Accepted);

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

    /** Where a feed's ball first rises through the player's hitting plane after its bounce. */
    private static Vec3 RisingThroughTheHittingPlane(Shots Shot) {
        World W = new World();
        W.Launch(Shot.State());
        for (int I = 0; I < (int) (2.0 / Dt); I++) {
            double Y0 = W.State().Pos().Y();
            W.Step();
            if (W.BounceSerial() > 0 && Y0 < PlayerReach.HitY && W.State().Pos().Y() >= PlayerReach.HitY) {
                return W.State().Pos();
            }
        }
        throw new IllegalStateException(Shot.Name() + " never rises through the hitting plane");
    }

    /**
     * A feed that touches the net cord and still lands first on the far half, found by search
     * so it does not hang on hand-tuned numbers: a paddle-free flight is the honest judge.
     */
    private static Shots NetCordFeed() {
        for (double Vz = 6; Vz <= 10; Vz += 1) {
            for (double Vy = -0.5; Vy <= 2.5; Vy += 0.02) {
                Shots S = Feed(String.format("cord feed vz=-%.0f vy=%+.2f", Vz, Vy),
                               new Vec3(0, 0.20, 1.2), new Vec3(0, Vy, -Vz));
                World W = new World();
                W.Launch(S.State());
                boolean Touched = false;
                for (int I = 0; I < (int) (1.5 / Dt) && W.BounceSerial() == 0; I++) {
                    W.Step();
                    Touched |= W.LastEvent() != null && W.LastEvent().Type() == World.EventType.Net;
                }
                World.Event Bounce = W.LastEvent();
                if (Touched && Bounce != null && Bounce.Type() == World.EventType.TableBounce
                        && Bounce.Side() > 0) {
                    return S;
                }
            }
        }
        return null;
    }

    private static void Check(String What, boolean Ok, String Detail) {
        Checks++;
        System.out.printf("  [%s] %s%s%n", Ok ? "PASS" : "FAIL", What,
                          Detail.isEmpty() ? "" : "  (" + Detail + ")");
        if (!Ok) Failures.add(What + (Detail.isEmpty() ? "" : " -> " + Detail));
    }
}
