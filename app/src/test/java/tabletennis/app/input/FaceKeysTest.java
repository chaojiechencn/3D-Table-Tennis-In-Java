package tabletennis.app.input;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static tabletennis.testing.Claims.Check;

/** The held face keys: they take no key from another command, and they read as a player means. */
final class FaceKeysTest {

    @Test
    void TheFaceKeysCollideWithNoCommand() {
        List<String> Taken = new ArrayList<>();
        for (KeyCode Key : FaceKeys.All) {
            Controls.CommandFor(Key, true).ifPresent(Command -> Taken.add(Key + " -> " + Command));
        }
        Check("W, A, S and D steer the face and nothing else",
              Taken.isEmpty() && Controls.MatchLegend.contains(FaceKeys.LegendToken),
              Taken.isEmpty() ? "no clash; the legend names them" : "also bound: " + Taken);
    }

    @Test
    void HeldKeysReadAsTiltAndOppositesCancel() {
        FaceKeys Keys = new FaceKeys();
        Keys.Press(KeyCode.W);
        Keys.Press(KeyCode.A);
        double Close = Keys.CloseTilt(), Side = Keys.SideTilt();
        Keys.Press(KeyCode.S);
        double Cancelled = Keys.CloseTilt();
        Keys.ReleaseAll();
        boolean LetGo = Keys.CloseTilt() == 0 && Keys.SideTilt() == 0;
        boolean NotOurs = !Keys.Press(KeyCode.R);
        Check("W closes, A tilts right, W with S cancels, losing focus lets go, other keys pass through",
              Close == 1 && Side == 1 && Cancelled == 0 && LetGo && NotOurs,
              String.format("W+A -> close %.0f side %.0f; +S -> close %.0f; released=%b; R passed=%b",
                            Close, Side, Cancelled, LetGo, NotOurs));
    }
}
