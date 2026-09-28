package tabletennis.app.input;

import javafx.scene.input.KeyCode;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Every key the game answers to, and the legends that tell the player about them, kept side by
 * side so they cannot drift apart. Each binding names the token its legend shows for it. Player
 * keys always work; developer keys (debug overlays, time control, camera presets) only with --dev.
 */
public final class Controls {

    private Controls() {}

    /** Things a key can ask for. Digits pick a feed directly and are handled apart from these. */
    public enum Command {
        NextFeed, PreviousFeed, Replay, ToggleAutoReplay,
        Pause, SingleStep, Slower, Faster,
        ToggleRallyCam, NextView, ToggleShotOverlay, ToggleControlReadout,
        ToggleGhost, ToggleTrail, ToggleBallSize, ToggleHud, Menu, ToggleDemo
    }

    /** One command, the keys that trigger it, how the legend names them, and who it is for. */
    public record Binding(Command Action, String LegendToken, List<KeyCode> Keys, boolean Developer) {}

    public static final List<Binding> Bindings = List.of(
            new Binding(Command.Pause, "SPACE pause", List.of(KeyCode.SPACE), false),
            new Binding(Command.ToggleHud, "H hide help", List.of(KeyCode.H), false),
            new Binding(Command.Menu, "ESC menu", List.of(KeyCode.ESCAPE), false),
            new Binding(Command.NextFeed, "N/P", List.of(KeyCode.N, KeyCode.RIGHT), false),
            new Binding(Command.PreviousFeed, "N/P", List.of(KeyCode.P, KeyCode.LEFT), false),
            new Binding(Command.Replay, "R replay", List.of(KeyCode.R), false),
            new Binding(Command.ToggleDemo, "M let the game play your side", List.of(KeyCode.M), false),
            new Binding(Command.ToggleAutoReplay, "U auto-replay", List.of(KeyCode.U), true),
            new Binding(Command.SingleStep, ". step", List.of(KeyCode.PERIOD), true),
            new Binding(Command.Slower, "[ ]", List.of(KeyCode.OPEN_BRACKET), true),
            new Binding(Command.Faster, "[ ]", List.of(KeyCode.CLOSE_BRACKET), true),
            new Binding(Command.ToggleRallyCam, "F rally-cam", List.of(KeyCode.F), true),
            new Binding(Command.NextView, "C preset view", List.of(KeyCode.C), true),
            new Binding(Command.ToggleShotOverlay, "V shot debug", List.of(KeyCode.V), true),
            new Binding(Command.ToggleControlReadout, "I control debug", List.of(KeyCode.I), true),
            new Binding(Command.ToggleGhost, "G ghost", List.of(KeyCode.G), true),
            new Binding(Command.ToggleTrail, "T trail", List.of(KeyCode.T), true),
            new Binding(Command.ToggleBallSize, "B ball x2", List.of(KeyCode.B), true));

    /** Commands that would let a match be skipped or played by the computer. */
    public static final List<Command> PracticeOnly =
            List.of(Command.NextFeed, Command.PreviousFeed, Command.Replay, Command.ToggleDemo);

    /** The token the developer legend uses for the digit keys that pick a feed. */
    public static final String FeedDigitsToken = "1-9,0 pick";

    private static final String PlayLines = """
            MOUSE   move the bat - swing THROUGH the ball to hit it
                    RIGHT hold, flick up/down as the ball arrives = loop / chop (add W / S)
            FACE    W/S close,open the face   A/D tilt it right,left   (hold)
                    SPACE pause   H hide help   ESC menu""";

    /** In a match: playing only. */
    public static final String MatchLegend = PlayLines;

    /** In practice: playing, plus choosing and repeating the drill. */
    public static final String PracticeLegend = PlayLines + """

            DRILL   N/P next,prev drill   R replay   M let the game play your side""";

    /** With --dev: everything. */
    public static final String DeveloperLegend = PracticeLegend + """

            DEV     1-9,0 pick   U auto-replay   . step   [ ] slower,faster
                    F rally-cam   C preset view   V shot debug   I control debug
                    G ghost   T trail   B ball x2""";

    /** The command a key asks for; developer keys answer only in developer mode. */
    public static Optional<Command> CommandFor(KeyCode Key, boolean DeveloperMode) {
        for (Binding Each : Bindings) {
            if (Each.Keys().contains(Key) && (DeveloperMode || !Each.Developer())) return Optional.of(Each.Action());
        }
        return Optional.empty();
    }

    /** The top-row digits 1-9 then 0 pick the first ten feeds. Numpad digits never did. */
    public static OptionalInt FeedIndexFor(KeyCode Key) {
        if (!Key.name().startsWith("DIGIT")) return OptionalInt.empty();
        int Digit = Key.getChar().charAt(0) - '0';
        return OptionalInt.of((Digit + 9) % 10);
    }
}
