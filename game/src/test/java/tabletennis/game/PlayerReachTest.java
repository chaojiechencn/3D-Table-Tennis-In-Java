package tabletennis.game;

import org.junit.jupiter.api.Test;
import tabletennis.engine.RacketSpec;
import tabletennis.engine.TableSpec;
import tabletennis.engine.math.Vec3;

import static tabletennis.testing.Claims.Check;

/** The control envelope: the cursor means one thing at a time, and the brush bends only what it may. */
final class PlayerReachTest {

    /** The decoupling, stated as something that can fail. */
    @Test
    void TheCursorCannotRaiseTheBat() {
        double Worst = 0;
        for (double Y = -3.0; Y <= 3.0; Y += 0.05) {
            for (double Z : new double[]{-2.0, 0.3, 1.0, 1.9, 5.0}) {
                Vec3 Got = PlayerReach.Clamp(new Vec3(0.4, Y, Z));
                Worst = Math.max(Worst, Math.abs(Got.Y() - PlayerReach.HitY));
            }
        }
        Check("no cursor aim, at any height, can move the racket off its hitting plane",
              Worst < 1e-12,
              String.format("worst height deviation %.1e m over aims from y = -3 to +3 m", Worst));

        // And the other half of the same claim: the axes that ARE inputs still work.
        Vec3 Left = PlayerReach.Clamp(new Vec3(-0.5, 99, 1.5));
        Vec3 Right = PlayerReach.Clamp(new Vec3(+0.5, -99, 1.5));
        Check("cursor X still moves the racket across the table",
              Right.X() - Left.X() > 0.9,
              String.format("x %+.2f -> %+.2f as the aim crosses the centre line", Left.X(), Right.X()));
    }

    @Test
    void DepthRunsOneWayOnly() {
        double Previous = Double.NEGATIVE_INFINITY;
        boolean Monotone = true;
        double ReversedAt = Double.NaN;
        for (double Z = -3.0; Z <= 5.0; Z += 0.01) {
            double Got = PlayerReach.Clamp(new Vec3(0, 0, Z)).Z();
            if (Got < Previous - 1e-12) {
                Monotone = false;
                if (Double.isNaN(ReversedAt)) ReversedAt = Z;
            }
            Previous = Got;
        }
        Check("racket depth is monotone in the aim -- pointing further up-table never brings it back",
              Monotone,
              Monotone ? "no reversal over aims from z = -3 to +5 m" : String.format("reverses at z = %.2f", ReversedAt));

        // Monotone alone would be satisfied by a constant, so the range has to be real too.
        double Span = PlayerReach.ZFar - PlayerReach.ZNear;
        Check("the depth range spans the player's half and the ground behind it",
              PlayerReach.ZNear < 0.5 && PlayerReach.ZFar > TableSpec.Length / 2 + 0.5,
              String.format("z %.2f..%.2f m (%.2f m of travel; the end line is at %.2f)",
                            PlayerReach.ZNear, PlayerReach.ZFar, Span, TableSpec.Length / 2));
    }

    /** The brush modifier, against the invariant it is allowed to bend and the ones it is not. */
    @Test
    void TheBrushLiftsTheBatWithoutExtendingItsReach() {
        Vec3 BrushAim = new Vec3(0.3, PlayerReach.HitY, 1.10);

        Vec3 High = PlayerReach.ClampBrushed(BrushAim, 0.0, 1.10);   // cursor at the top of the screen
        Vec3 Low = PlayerReach.ClampBrushed(BrushAim, 1.0, 1.10);    // cursor at the bottom
        Check("the brush carries the bat up and down through the ball",
              High.Y() > PlayerReach.HitY + 0.01 && Low.Y() < PlayerReach.HitY - 0.01,
              String.format("top of screen y=%.3f, bottom y=%.3f, plane is %.3f", High.Y(), Low.Y(), PlayerReach.HitY));

        double Worst = 0;
        for (double Fraction = 0; Fraction <= 1.0001; Fraction += 0.02) {
            Worst = Math.max(Worst, Math.abs(PlayerReach.ClampBrushed(BrushAim, Fraction, 1.10).Y() - PlayerReach.HitY));
        }
        Check("the brush cannot lift the bat further than a stroke",
              Worst <= PlayerReach.BrushBand + 1e-9,
              String.format("worst departure %.3f m against a band of %.3f", Worst, PlayerReach.BrushBand));

        double Lowest = Double.MAX_VALUE;
        for (double Fraction = 0; Fraction <= 1.0001; Fraction += 0.02) {
            Lowest = Math.min(Lowest, PlayerReach.ClampBrushed(BrushAim, Fraction, 1.10).Y());
        }
        Check("the brush cannot cut the bat down through the table top",
              Lowest >= RacketSpec.BladeRadius - 1e-9,
              String.format("lowest blade centre %.3f m against a blade radius of %.3f", Lowest, RacketSpec.BladeRadius));

        // The reach rule: brushing must not let the blade stand anywhere Clamp would not.
        double WorstDepthChange = 0;
        for (double Fraction = 0; Fraction <= 1.0001; Fraction += 0.1) {
            Vec3 Brushed = PlayerReach.ClampBrushed(new Vec3(0.3, PlayerReach.HitY, 9.0), Fraction, 1.10);
            WorstDepthChange = Math.max(WorstDepthChange, Math.abs(Brushed.Z() - 1.10));
        }
        Check("the brush freezes depth -- it cannot be used to reach further up-table",
              WorstDepthChange < 1e-9,
              String.format("depth moved by %.6f m over the whole cursor sweep", WorstDepthChange));

        // And normal aiming is still pinned to the plane, brush or no brush.
        double OffPlane = 0;
        for (double Z = -3; Z <= 5; Z += 0.25) {
            OffPlane = Math.max(OffPlane, Math.abs(PlayerReach.Clamp(new Vec3(0.2, 7.5, Z)).Y() - PlayerReach.HitY));
        }
        Check("with the modifier up, no aim at any height leaves the hitting plane",
              OffPlane == 0,
              String.format("worst height deviation %.1e m", OffPlane));
    }
}
