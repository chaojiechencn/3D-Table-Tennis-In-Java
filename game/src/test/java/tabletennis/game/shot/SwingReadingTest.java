package tabletennis.game.shot;

import org.junit.jupiter.api.Test;
import tabletennis.engine.BallState;
import tabletennis.engine.math.Vec3;

import static tabletennis.testing.Claims.Check;

/**
 * Contact grading. A simulated human's brush met the ball near the rim 48-87% of the time, because
 * a vertical sweep crosses the ball faster than a person can time it; graded like a drive, nearly
 * every brushed chop was a mishit. A brush now counts its offset along the brush for less.
 */
final class SwingReadingTest {

    private static final SwingReading Reading = new SwingReading(ShotTuning.Defaults());
    private static final BallState Arriving = BallState.At(Vec3.Zero, new Vec3(0, -1, 6), Vec3.Zero);

    @Test
    void ABrushIsGradedAlongTheBrushLeniently() {
        double Brushed = Quality(8.0, 0.0, 0.8), Driven = Quality(0.0, 0.0, 0.8);
        double Centred = Quality(0.0, 0.0, 0.0), SidewaysBrush = Quality(8.0, 0.8, 0.0);
        // On this slow ball the core is widest; a drive 0.8 off centre grades 0.56 here, less on a faster one.
        Check("a brush 0.8 of the radius above centre stays clean where the same contact without a brush is "
              + "clearly worse, and a brush is still graded fully across the face",
              Brushed == 1.0 && Driven < 0.7 && Centred == 1.0 && SidewaysBrush == Driven,
              String.format("brushed %.2f, driven %.2f, centred %.2f, brushed but 0.8 to the side %.2f",
                            Brushed, Driven, Centred, SidewaysBrush));
    }

    private static double Quality(double Lift, double OffX, double OffY) {
        return Reading.ContactQuality(Arriving, new SwingReading.Swing(8, 0, Lift, Lift, 0.5, OffX, OffY, 0));
    }
}
