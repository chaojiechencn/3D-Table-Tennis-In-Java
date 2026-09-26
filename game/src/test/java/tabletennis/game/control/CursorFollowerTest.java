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

    /**
     * The face keys, with the blade held still. The resting face is not square: a still blade
     * reads as driving forward, so the automatic lean rests it 28.8 degrees closed. Each key tilts
     * the face from there, its own way, by 20-32 degrees (W to 49, S to 2.9, A and D to 39 off
     * square), and letting go settles it back to rest.
     */
    @Test
    void TheFaceKeysTiltTheFaceAndLetGo() {
        record Tilt(String Key, double Close, double Side) {}
        Vec3 Rest = SettledFace(0, 0, null);
        StringBuilder Measured = new StringBuilder(String.format("rest %.1f deg closed; ", AngleFromSquare(Rest)));
        boolean AllRight = true;
        for (Tilt Each : new Tilt[]{new Tilt("W", 1, 0), new Tilt("S", -1, 0), new Tilt("D", 0, 1), new Tilt("A", 0, -1)}) {
            Vec3[] Released = new Vec3[1];
            Vec3 Held = SettledFace(Each.Close(), Each.Side(), Released);
            Vec3 Moved = Held.Minus(Rest);
            boolean RightWay = Each.Close() > 0 ? Moved.Y() < 0 : Each.Close() < 0 ? Moved.Y() > 0
                             : Each.Side() > 0 ? Moved.X() > 0 : Moved.X() < 0;
            double Turned = AngleBetween(Held, Rest), Back = AngleBetween(Released[0], Rest);
            AllRight &= RightWay && Turned >= 15 && Back < 0.5;
            Measured.append(String.format("%s %.1f deg from rest %s, %.2f deg after release; ", Each.Key(), Turned,
                                          RightWay ? "the right way" : "THE WRONG WAY", Back));
        }
        Check("each face key tilts the face at least 15 degrees its own way, and letting go returns it to rest",
              AllRight, Measured.toString());
    }

    /** The face after half a second holding the tilt, and, if asked, after half a second let go. */
    private static Vec3 SettledFace(double Close, double Side, Vec3[] AfterRelease) {
        Vec3 Start = new Vec3(0, 0.16, 1.6);
        Racket Blade = new Racket(Start, CursorFollower.Square);
        CursorFollower Hand = new CursorFollower(Start);
        Hand.SetFaceTilt(Close, Side);
        for (int Step = 0; Step < Simulation.StepsPerSecond / 2; Step++) Hand.Advance(Blade, Simulation.Step);
        Vec3 Held = Blade.Normal();
        if (AfterRelease != null) {
            Hand.SetFaceTilt(0, 0);
            for (int Step = 0; Step < Simulation.StepsPerSecond / 2; Step++) Hand.Advance(Blade, Simulation.Step);
            AfterRelease[0] = Blade.Normal();
        }
        return Held;
    }

    private static double AngleBetween(Vec3 A, Vec3 B) {
        return Math.toDegrees(Math.acos(Math.min(1, A.Normalized().Dot(B.Normalized()))));
    }

    private static double AngleFromSquare(Vec3 Normal) {
        return Math.toDegrees(Math.acos(Math.min(1, Normal.Normalized().Dot(CursorFollower.Square))));
    }
}
