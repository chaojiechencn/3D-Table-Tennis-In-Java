package tabletennis.game;

import org.junit.jupiter.api.Test;
import tabletennis.engine.Simulation;
import tabletennis.engine.TableSpec;
import tabletennis.engine.math.Vec3;
import tabletennis.game.control.CursorFollower;
import tabletennis.game.control.ReachEnvelope;
import tabletennis.game.control.ReachTiming;
import tabletennis.game.feed.Feed;
import tabletennis.game.feed.Feeds;
import tabletennis.game.rally.Side;

import java.util.ArrayList;
import java.util.List;

import static tabletennis.testing.Claims.Check;

/** Can the player actually get to the ball? This is the check that would have caught "0.3 s later it's unhittable". */
final class ReachabilityTest {

    @Test
    void EveryReturnIsActuallyReachable() {
        double WorstWindow = Double.MAX_VALUE, WorstMargin = Double.MAX_VALUE;
        String WorstWindowShot = "", WorstMarginShot = "";
        int Playable = 0, Fed = 0;

        for (Feed Shot : Feeds.All) {
            List<Vec3> Path = PathAfterThePlayerSideBounce(Shot);
            if (Path == null) continue;
            Fed++;

            int Steps = 0;
            Vec3 First = null;
            for (Vec3 Point : Path) {
                if (!ReachTiming.CanTouch(Point)) continue;
                Steps++;
                if (First == null) First = Point;
            }
            if (First == null) continue;
            Playable++;

            double Window = Steps * Simulation.Step;
            double Dash = ReachTiming.TravelTime(ReachEnvelope.Neutral, new Vec3(First.X(), ReachEnvelope.HitY, First.Z()));
            if (Window < WorstWindow) { WorstWindow = Window; WorstWindowShot = Shot.Name(); }
            if (Window - Dash < WorstMargin) { WorstMargin = Window - Dash; WorstMarginShot = Shot.Name(); }
        }

        Check("every return the opponent makes passes through a place the racket can reach",
              Playable == Fed,
              String.format("%d of %d returns reachable", Playable, Fed));

        // 200 ms is the floor a human reaction time argues for: simple visual reaction is 200-250 ms,
        // and the game runs at 0.45x by default, so 200 ms simulated is about 440 ms on the clock.
        Check("the racket has a human amount of time to meet each one",
              WorstWindow > 0.200,
              String.format("worst touchable window %.0f ms (%s); %.0f ms of wall-clock at the 0.45x default",
                            WorstWindow * 1000, WorstWindowShot, WorstWindow * 1000 / 0.45));

        // The blade must be fast enough for the envelope it has, rather than TrackSpeed being raised
        // until the symptom went away.
        Check("the blade can cross to every one of them in the time the ball allows",
              WorstMargin > 0,
              String.format("tightest case %s: %.0f ms of margin at TRACK_SPEED = %.1f m/s",
                            WorstMarginShot, WorstMargin * 1000, CursorFollower.TrackSpeed));
    }

    /** End to end: can a player who simply points at the ball hit it back? */
    @Test
    void APlayerPointingAtTheBallCanReturnIt() {
        int Returned = 0, Attempted = 0;
        List<String> Missed = new ArrayList<>();
        for (Feed Shot : Feeds.All) {
            if (PathAfterThePlayerSideBounce(Shot) == null) continue;
            Attempted++;
            if (PlayThePoint(Shot)) Returned++; else Missed.add(Shot.Name());
        }
        Check("a player who points at the ball returns it over the net",
              Returned == Attempted,
              String.format("%d of %d feeds returned%s", Returned, Attempted,
                            Missed.isEmpty() ? "" : "; missed: " + String.join(", ", Missed)));
    }

    /** Play one point with a stand-in hand: point the CURSOR at the ball, let envelope and speed decide. */
    private static boolean PlayThePoint(Feed Shot) {
        GameSession Game = new GameSession();
        Game.Launch(Shot);
        boolean Returned = false;
        for (int Step = 0; Step < (int) (14.0 / Simulation.Step); Step++) {
            Vec3 Ball = Game.Snapshot().Ball().Position();
            Game.SetAim(ReachEnvelope.Clamp(new Vec3(Ball.X(), 0, Ball.Z())));
            if (Game.Step().HitBy() == Side.Player) Returned = true;

            // Returned AND over to the other side: a ball popped straight up is not a return.
            if (Returned && Game.Snapshot().Ball().Position().Z() < -0.1) return true;
            if (Game.Snapshot().Ball().Position().Y() < -TableSpec.Height) break;
        }
        return false;
    }

    /**
     * One feed, played by the real game until the opponent has returned it and the return has
     * bounced on the player's half: the ball's path from that bounce on, or null if no such rally.
     */
    private static List<Vec3> PathAfterThePlayerSideBounce(Feed Shot) {
        GameSession Game = new GameSession();
        Game.SetAim(ReachEnvelope.Clamp(new Vec3(-9, 0, 9)));
        Game.Launch(Shot);

        boolean Returned = false, Bounced = false;
        List<Vec3> Path = new ArrayList<>();
        for (int Step = 0; Step < (int) (14.0 / Simulation.Step); Step++) {
            if (Game.Step().HitBy() == Side.Opponent) Returned = true;
            if (Returned && Game.Snapshot().PlayerMayHit()) Bounced = true;
            if (Bounced) Path.add(Game.Snapshot().Ball().Position());
            if (Game.Snapshot().Ball().Position().Y() < -TableSpec.Height) break;
        }
        return Bounced ? Path : null;
    }
}
