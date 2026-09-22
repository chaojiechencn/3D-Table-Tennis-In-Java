package tabletennis.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/** The game, replayed from the recorded scenarios, must reproduce the fixture bit for bit. */
final class GoldenTraceTest {

    @Test
    void TheGameReproducesTheGoldenTrace() throws IOException {
        List<String> Expected = Fixture();
        List<String> Actual = GoldenTrace.Record();

        int Limit = Math.min(Expected.size(), Actual.size());
        String Scenario = "(none)";
        for (int Line = 0; Line < Limit; Line++) {
            if (Expected.get(Line).startsWith("scenario ")) Scenario = Expected.get(Line);
            if (!Expected.get(Line).equals(Actual.get(Line))) {
                fail(String.format("%s diverged at trace line %d: expected '%s' but got '%s'",
                                   Scenario, Line + 1, Expected.get(Line), Actual.get(Line)));
            }
        }
        if (Expected.size() != Actual.size()) {
            fail("trace length changed: expected " + Expected.size() + " lines, got " + Actual.size());
        }
        System.out.printf("  [PASS] the game reproduces the golden trace  (%d lines)%n", Actual.size());
    }

    private static List<String> Fixture() throws IOException {
        try (InputStream In = GoldenTraceTest.class.getResourceAsStream(GoldenTrace.FixtureResource)) {
            assertNotNull(In, "missing fixture " + GoldenTrace.FixtureResource);
            return new String(In.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}
