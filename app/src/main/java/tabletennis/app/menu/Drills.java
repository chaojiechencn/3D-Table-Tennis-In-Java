package tabletennis.app.menu;

import tabletennis.game.feed.Feed;
import tabletennis.game.feed.Feeds;

import java.util.List;

/**
 * The feeds a player practises against, in the words a player uses. The two engine checks
 * ("Into the net", "ITTF drop test") are measurements, not drills, and are left out.
 */
public final class Drills {

    private Drills() {}

    /** One practice ball: the feed, its short title and what it asks of the player. */
    public record Drill(Feed Ball, String Title, String Blurb) {}

    public static final List<Drill> All = List.of(
            Of("Serve", "Warm-up", "A steady ball, corner to corner. Start here."),
            Of("Flat drive", "Flat drive", "Fast and flat. Meet it early and swing through."),
            Of("Topspin loop", "Topspin", "Dips and kicks off the table. Close the face (W) to keep it down."),
            Of("Heavy backspin push", "Backspin", "Slow and heavy. Lift it: brush up, or open the face (S)."),
            Of("Sidespin hook (left)", "Hook left", "Curves across the table, and the bounce kicks sideways."),
            Of("Sidespin hook (right)", "Hook right", "The same hook, bending the other way."),
            Of("Cross-court loop", "Wide loop", "Topspin to the far corner. Move early."),
            Of("Corkscrew serve", "Corkscrew", "Short and loaded with sidespin. Watch the bounce."),
            Of("Backspin lob", "Floater", "A high defensive ball. Attack it."),
            Of("Smash", "Smash", "Thirty metres a second. Just get the bat there."));

    /** The drill for a feed, or the warm-up if the feed is not one of them. */
    public static Drill For(Feed Ball) {
        for (Drill Each : All) if (Each.Ball().Name().equals(Ball.Name())) return Each;
        return All.get(0);
    }

    /** The drill after (or, with -1, before) this one, wrapping around. */
    public static Drill Next(Drill Current, int Delta) {
        int Index = All.indexOf(Current);
        return All.get(Math.floorMod(Index + Delta, All.size()));
    }

    private static Drill Of(String FeedName, String Title, String Blurb) {
        return new Drill(Feeds.ByName(FeedName), Title, Blurb);
    }
}
