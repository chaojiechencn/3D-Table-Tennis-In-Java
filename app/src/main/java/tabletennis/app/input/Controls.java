package tabletennis.app.input;

import javafx.scene.input.KeyCode;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Every key the game answers to, and the legend that tells the player about them, kept side by
 * side so they cannot drift apart. Each binding names the token the legend shows for it.
 */
public final class Controls {

    private Controls() {}

    /** Things a key can ask for. Digits pick a feed directly and are handled apart from these. */
    public enum Command {
        NextFeed, PreviousFeed, Replay, ToggleAutoReplay,
        Pause, SingleStep, Slower, Faster,
        ToggleRallyCam, NextView, ToggleShotOverlay, ToggleControlReadout,
        ToggleGhost, ToggleTrail, ToggleBallSize, ToggleHud, Quit, ToggleDemo
    }

    /** One command, the keys that trigger it, and how the legend names those keys. */
    public record Binding(Command Action, String LegendToken, List<KeyCode> Keys) {}

    public static final List<Binding> Bindings = List.of(
            new Binding(Command.NextFeed, "N/P", List.of(KeyCode.N, KeyCode.RIGHT)),
            new Binding(Command.PreviousFeed, "N/P", List.of(KeyCode.P, KeyCode.LEFT)),
            new Binding(Command.Replay, "R replay", List.of(KeyCode.R)),
            new Binding(Command.ToggleAutoReplay, "A auto-replay", List.of(KeyCode.A)),
            new Binding(Command.Pause, "SPACE pause", List.of(KeyCode.SPACE)),
            new Binding(Command.SingleStep, ". step", List.of(KeyCode.PERIOD)),
            new Binding(Command.Slower, "[ ]", List.of(KeyCode.OPEN_BRACKET)),
            new Binding(Command.Faster, "[ ]", List.of(KeyCode.CLOSE_BRACKET)),
            new Binding(Command.ToggleRallyCam, "F rally-cam", List.of(KeyCode.F)),
            new Binding(Command.NextView, "C preset view", List.of(KeyCode.C)),
            new Binding(Command.ToggleShotOverlay, "V shot debug", List.of(KeyCode.V)),
            new Binding(Command.ToggleControlReadout, "D control debug", List.of(KeyCode.D)),
            new Binding(Command.ToggleGhost, "G ghost", List.of(KeyCode.G)),
            new Binding(Command.ToggleTrail, "T trail", List.of(KeyCode.T)),
            new Binding(Command.ToggleBallSize, "B ball x2", List.of(KeyCode.B)),
            new Binding(Command.ToggleHud, "H hud", List.of(KeyCode.H)),
            new Binding(Command.Quit, "ESC quit", List.of(KeyCode.ESCAPE)),
            new Binding(Command.ToggleDemo, "M watch it play itself", List.of(KeyCode.M)));

    /** The token the legend uses for the digit keys that pick a feed. */
    public static final String FeedDigitsToken = "1-9,0 pick";

    public static final String Legend = """
            MOUSE   move to move the paddle -- swing through the ball to hit
                    (how you move through the ball aims the shot; hit it CLEAN or it goes out)
                    RIGHT hold = brush: mouse up/down lifts/cuts the bat for spin
                    LEFT drag orbits the camera      scroll zooms
            FEED    1-9,0 pick   N/P next,prev   R replay   A auto-replay
            TIME    SPACE pause   . step   [ ] slower,faster
            VIEW    F rally-cam on/off   C preset view
                    V shot debug   D control debug
                    G ghost   T trail   B ball x2   H hud   ESC quit
            DEMO    M watch it play itself""";

    public static Optional<Command> CommandFor(KeyCode Key) {
        for (Binding Each : Bindings) {
            if (Each.Keys().contains(Key)) return Optional.of(Each.Action());
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
