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
            String Legend = Each.Developer() ? Controls.DeveloperLegend : Controls.PracticeLegend;
            if (!Legend.contains(Each.LegendToken())) Missing.add(Each.Action() + " (" + Each.LegendToken() + ")");
        }
        if (!Controls.DeveloperLegend.contains(Controls.FeedDigitsToken)) Missing.add("feed digits");
        Check("every key binding is named in the legend of the people it is for", Missing.isEmpty(),
              Missing.isEmpty() ? Controls.Bindings.size() + " bindings and the feed digits" : "missing: " + Missing);
    }

    /** A player sees and reaches only player keys: debug overlays and time control need --dev. */
    @Test
    void DeveloperKeysNeedDeveloperMode() {
        List<String> Leaked = new ArrayList<>();
        for (Controls.Binding Each : Controls.Bindings) {
            if (!Each.Developer()) continue;
            for (KeyCode Key : Each.Keys()) {
                if (Controls.CommandFor(Key, false).isPresent()) Leaked.add(Key + " works without --dev");
                if (Controls.CommandFor(Key, true).isEmpty()) Leaked.add(Key + " dead even with --dev");
            }
            if (Controls.PracticeLegend.contains(Each.LegendToken())) Leaked.add(Each.LegendToken() + " in the player legend");
        }
        for (Controls.Command Each : Controls.PracticeOnly) {
            for (Controls.Binding Binding : Controls.Bindings) {
                if (Binding.Action() == Each && Controls.MatchLegend.contains(Binding.LegendToken())) {
                    Leaked.add(Binding.LegendToken() + " offered in a match");
                }
            }
        }
        Check("developer keys work only with --dev, and a match offers no practice-only key", Leaked.isEmpty(),
              Leaked.isEmpty() ? "player legend clean; every developer key answers only in developer mode" : String.join("; ", Leaked));
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
