package tabletennis.app.input;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static tabletennis.testing.Claims.Check;

/** The legend is the player's only manual, so it must describe the keys that actually work. */
final class ControlsTest {

    @Test
    void TheLegendNamesEveryBinding() {
        List<String> Missing = new ArrayList<>();
        for (Controls.Binding Each : Controls.Bindings) {
            if (!Controls.Legend.contains(Each.LegendToken())) Missing.add(Each.Action() + " (" + Each.LegendToken() + ")");
        }
        if (!Controls.Legend.contains(Controls.FeedDigitsToken)) Missing.add("feed digits");
        Check("every key binding is named in the on-screen legend", Missing.isEmpty(),
              Missing.isEmpty() ? Controls.Bindings.size() + " bindings and the feed digits" : "missing: " + Missing);
    }

    @Test
    void NoKeyMeansTwoThings() {
        Set<KeyCode> Seen = new HashSet<>();
        List<KeyCode> Twice = new ArrayList<>();
        for (Controls.Binding Each : Controls.Bindings) {
            for (KeyCode Key : Each.Keys()) if (!Seen.add(Key) || Controls.FeedIndexFor(Key).isPresent()) Twice.add(Key);
        }
        Check("no key is bound to two commands", Twice.isEmpty(), Twice.isEmpty() ? Seen.size() + " keys" : "twice: " + Twice);
    }

    @Test
    void TheTopRowDigitsPickFeedsOneToTen() {
        boolean OneIsFirst = Controls.FeedIndexFor(KeyCode.DIGIT1).orElse(-1) == 0;
        boolean ZeroIsTenth = Controls.FeedIndexFor(KeyCode.DIGIT0).orElse(-1) == 9;
        boolean NumpadIgnored = Controls.FeedIndexFor(KeyCode.NUMPAD1).isEmpty();
        Check("1 picks the first feed, 0 the tenth, and the numpad picks nothing",
              OneIsFirst && ZeroIsTenth && NumpadIgnored,
              "1 first=" + OneIsFirst + ", 0 tenth=" + ZeroIsTenth + ", numpad ignored=" + NumpadIgnored);
    }
}
