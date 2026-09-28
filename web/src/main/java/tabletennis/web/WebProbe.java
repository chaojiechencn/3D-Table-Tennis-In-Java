package tabletennis.web;

import tabletennis.engine.BallState;
import tabletennis.engine.math.Vec3;
import tabletennis.game.GameSession;
import tabletennis.game.GameSnapshot;
import tabletennis.game.feed.Feeds;

/** Plays a demo match headlessly and prints a running hash of the ball, to compare JVM and browser. */
public final class WebProbe {
    public static void main(String[] Args) {
        GameSession Game = new GameSession();
        Game.SetDemoMode(true);
        Game.SetAutoReplay(true);
        Game.Launch(Feeds.Default());
        long Hash = 1125899906842597L;
        for (int Second = 1; Second <= 60; Second++) {
            for (int Each = 0; Each < 480; Each++) {
                Game.Step();
                if (Game.ReplayDue()) Game.Launch(Feeds.Default());
                BallState Ball = Game.Snapshot().Ball();
                Hash = Fold(Fold(Fold(Hash, Ball.Position()), Ball.Velocity()), Ball.Spin());
            }
            GameSnapshot Now = Game.Snapshot();
            System.out.println(Second + "s " + Long.toHexString(Hash) + " ball " + Now.Ball().Position()
                               + " score " + Now.Score().PlayerPoints() + "-" + Now.Score().OpponentPoints());
        }
    }

    private static long Fold(long Hash, Vec3 Value) {
        Hash = 31 * Hash + Double.doubleToLongBits(Value.X());
        Hash = 31 * Hash + Double.doubleToLongBits(Value.Y());
        return 31 * Hash + Double.doubleToLongBits(Value.Z());
    }
}
