package tabletennis.testing;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A falsifiable claim checked against a number that did not come from this program. The measured
 * detail prints on a pass as well as a failure, so it must state what was actually measured.
 */
public final class Claims {

    private Claims() {}

    public static void Check(String Claim, boolean Holds, String Measured) {
        System.out.printf("  [%s] %s%s%n", Holds ? "PASS" : "FAIL", Claim,
                          Measured.isEmpty() ? "" : "  (" + Measured + ")");
        assertTrue(Holds, Claim + (Measured.isEmpty() ? "" : " -> " + Measured));
    }
}
