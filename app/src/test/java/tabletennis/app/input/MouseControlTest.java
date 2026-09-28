package tabletennis.app.input;

import org.junit.jupiter.api.Test;

import static tabletennis.testing.Claims.Check;

/** The brush mapping a player feels: pressing the right button must not move the bat. */
final class MouseControlTest {

    @Test
    void TheBrushStartsWhereTheBatIs() {
        double Height = 780, From = 600;
        double Pressed = MouseControl.BrushFraction(From, From, Height);
        double QuarterUp = MouseControl.BrushFraction(From, From - Height / 4, Height);
        double QuarterDown = MouseControl.BrushFraction(From, From + Height / 4, Height);
        double Beyond = MouseControl.BrushFraction(From, From - Height, Height);
        Check("pressing the right button leaves the bat at its normal height, and a quarter screen sweeps it to the end",
              Pressed == 0.5 && QuarterUp == 0.0 && QuarterDown == 1.0 && Beyond == 0.0,
              String.format("at the press %.2f (0.5 is the normal height); a quarter screen up %.2f, down %.2f; "
                            + "a whole screen up %.2f", Pressed, QuarterUp, QuarterDown, Beyond));
    }
}
