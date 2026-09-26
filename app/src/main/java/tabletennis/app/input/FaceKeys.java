package tabletennis.app.input;

import javafx.scene.input.KeyCode;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The racket-face keys, held rather than pressed: W closes the face, S opens it, A and D tilt it
 * left and right. Opposite keys held together cancel. Input only; the session owns the face.
 */
public final class FaceKeys {

    public static final KeyCode Close = KeyCode.W, Open = KeyCode.S, Left = KeyCode.A, Right = KeyCode.D;

    public static final List<KeyCode> All = List.of(Close, Open, Left, Right);

    /** The token the legend uses for them. */
    public static final String LegendToken = "W/S close,open the face";

    private final Set<KeyCode> Held = EnumSet.noneOf(KeyCode.class);

    /** True if the key is a face key, which is then held; any other key is not this class's. */
    public boolean Press(KeyCode Key) {
        if (!All.contains(Key)) return false;
        Held.add(Key);
        return true;
    }

    public boolean Release(KeyCode Key) {
        if (!All.contains(Key)) return false;
        Held.remove(Key);
        return true;
    }

    /** A window that loses focus never hears the key come up, so everything is let go. */
    public void ReleaseAll() { Held.clear(); }

    /** +1 closed (W), -1 open (S), 0 neither or both. */
    public double CloseTilt() { return Axis(Close, Open); }

    /** +1 toward the player's right (D), -1 left (A), 0 neither or both. */
    public double SideTilt() { return Axis(Right, Left); }

    private double Axis(KeyCode Positive, KeyCode Negative) {
        return (Held.contains(Positive) ? 1 : 0) - (Held.contains(Negative) ? 1 : 0);
    }
}
