package tabletennis.game.control;

import org.junit.jupiter.api.Test;
import tabletennis.engine.Simulation;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.Racket;

import static tabletennis.testing.Claims.Check;

/** The player's blade follows the cursor at a speed a person can actually carry a bat. */
final class CursorFollowerTest {

    @Test
    void AFlickOfTheMouseCannotOutrunACarriedBat() {
        // The position is arbitrary: the claim is about speed, not about where the blade is.
        Vec3 Start = new Vec3(0, 0.25, 1.57);
        Racket Blade = new Racket(Start, new Vec3(0, 0, -1));
        CursorFollower PlayerHand = new CursorFollower(Start);

        // Throw the cursor a metre sideways between two frames, about as fast as a hand moves a
        // mouse, and let the eight steps of one 60 Hz frame consume it.
        PlayerHand.AimAt(Start.Plus(new Vec3(1.0, 0, 0)));

        double Fastest = 0;
        for (int Step = 0; Step < 8; Step++) {
            PlayerHand.Advance(Blade, Simulation.Step);
            Fastest = Math.max(Fastest, Blade.Velocity().Length());
        }
        Check("a mouse flick cannot move the blade faster than a player carries a bat",
              Fastest <= CursorFollower.TrackSpeed * 1.001,
              String.format("peak blade speed %.2f m/s against the %.1f m/s limit", Fastest, CursorFollower.TrackSpeed));

        // The limit is only worth having if it sits below a real swing.
        Check("the tracking limit is slower than an advanced player's swing",
              CursorFollower.TrackSpeed < 17.8,
              String.format("%.1f m/s tracking against a measured 17.8 m/s swing", CursorFollower.TrackSpeed));
    }
}
