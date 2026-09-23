package tabletennis.engine.contact;

import org.junit.jupiter.api.Test;
import tabletennis.engine.BallState;
import tabletennis.engine.Simulation;
import tabletennis.engine.flight.Integrator;
import tabletennis.engine.math.Vec3;
import tabletennis.engine.world.Racket;

import static tabletennis.testing.Claims.Check;

/**
 * A swung blade against the ball. Every check here fails against a solver that works in absolute
 * velocity rather than in the surface's frame; that is their point.
 */
final class RacketContactTest {

    private static final Material Rubber = Materials.Rubber;

    /** A blade swung into a STATIONARY ball sends it away at (1 + e) times the blade's own speed. */
    @Test
    void PaddleImpartsItsOwnVelocity() {
        double Swing = 10.0;
        Vec3 Normal = new Vec3(0, 0, -1);
        BallState Ball = BallState.At(new Vec3(0, 0.30, 0), Vec3.Zero, Vec3.Zero);

        BallState After = Strike(Ball, new Vec3(0, 0.30, 0.025), new Vec3(0, 0, -Swing), Normal, Rubber);
        Check("a swung blade actually hits a stationary ball", After != null, "");
        if (After == null) return;

        double E = Rubber.RestitutionAt(Swing);
        double Expected = (1 + E) * Swing;
        Check("a stationary ball leaves at (1+e) times the blade speed",
              Math.abs(-After.Velocity().Z() - Expected) < 0.05,
              String.format("%.2f m/s from a %.0f m/s swing, expected %.2f at e = %.3f",
                            -After.Velocity().Z(), Swing, Expected, E));
    }

    /** The pace-versus-spin trade-off, against measured players. */
    @Test
    void SwingSpeedSplitsIntoPaceAndSpin() {
        double Swing = 17.8;
        BallState Arriving = BallState.At(new Vec3(0, 0.30, 0), new Vec3(0, 0, 10), Vec3.Zero);

        BallState Loop = Brush(Arriving, Swing, 30);    // 30 degrees up: a looping stroke
        BallState Drive = Brush(Arriving, Swing, 0);    // straight through: a drive

        Check("a looping brush at a measured swing speed gives the measured loop ball speed",
              Loop != null && Loop.Velocity().Length() > 18 && Loop.Velocity().Length() < 25,
              Loop == null ? "no contact"
                           : String.format("%.1f m/s against a measured forehand loop of ~21 m/s", Loop.Velocity().Length()));

        double LoopRevs = Loop == null ? 0 : -Loop.Spin().X() / (2 * Math.PI);
        Check("and it carries the spin a real loop carries",
              LoopRevs > 88 && LoopRevs < 150,
              String.format("%.0f rev/s against a measured 117 +/- 29", LoopRevs));

        double DriveRevs = Drive == null ? 0 : -Drive.Spin().X() / (2 * Math.PI);
        Check("the same swing driven flat trades that spin for pace",
              Drive != null && Drive.Velocity().Length() > Loop.Velocity().Length() + 4 && DriveRevs < LoopRevs - 30,
              Drive == null ? "no contact"
                            : String.format("flat: %.1f m/s / %.0f rev/s   vs   loop: %.1f m/s / %.0f rev/s",
                                            Drive.Velocity().Length(), DriveRevs, Loop.Velocity().Length(), LoopRevs));
    }

    /** Brushing UP the back of the ball must generate topspin, at a rate a real player reaches. */
    @Test
    void BrushingContactGeneratesTopspin() {
        BallState Ball = BallState.At(new Vec3(0, 0.30, 0), new Vec3(0, 0, 4), Vec3.Zero);

        BallState After = Strike(Ball, new Vec3(0, 0.245, 0.0263), new Vec3(0, 12, -9), ClosedFace(0.45), Rubber);
        Check("an upward brush makes contact", After != null, "");
        if (After == null) return;

        // The ball now heads toward -Z, so topspin is rotation about -X.
        double TopRevs = -After.Spin().X() / (2 * Math.PI);
        Check("brushing up the back of the ball generates TOPSPIN, not backspin",
              TopRevs > 0, String.format("%+.0f rev/s", TopRevs));

        Check("the spin generated is in the range a real player produces",
              TopRevs > 15 && TopRevs < 150,
              String.format("%.0f rev/s; skilled topspin forehands measure 117 +/- 29 rev/s "
                          + "and the peer-reviewed ceiling is 150", TopRevs));

        Check("the brush also sends the ball back down the table",
              After.Velocity().Z() < 0, String.format("%.1f m/s in Z", After.Velocity().Z()));
    }

    /** Heavy BACKSPIN into a brushing blade comes back as TOPSPIN; a rigid surface cannot do that. */
    @Test
    void PaddleReversesIncomingBackspin() {
        Vec3 Backspin = new Vec3(-90 * 2 * Math.PI, 0, 0);
        BallState Chop = BallState.At(new Vec3(0, 0.30, 0), new Vec3(0, 0, 5), Backspin);

        Vec3 Face = ClosedFace(0.45);
        Vec3 End = new Vec3(0, 0.245, 0.0263);
        Vec3 Swing = new Vec3(0, 14, -10);

        BallState OffRubber = Strike(Chop, End, Swing, Face, Rubber);
        Check("the blade reaches the chopped ball", OffRubber != null, "");
        if (OffRubber == null) return;

        // Measured about the OUTGOING ball's axis, which now travels toward -Z: positive is topspin.
        double InRevs = Chop.Spin().X() / (2 * Math.PI);
        double OutRevs = -OffRubber.Spin().X() / (2 * Math.PI);
        Check("heavy backspin comes off an inverted rubber as topspin (spin REVERSAL)",
              OutRevs > 0,
              String.format("%.0f rev/s of backspin in -> %+.0f rev/s of topspin out", Math.abs(InRevs), OutRevs));

        // The same stroke on a rigid surface can strip spin but cannot reverse it.
        Material NoSpringBack = Material.Rigid(Rubber.RestitutionAt(0), Rubber.Friction(), 1.0, 1.0);
        BallState OffRigid = Strike(Chop, End, Swing, Face, NoSpringBack);
        double RigidRevs = OffRigid == null ? 0 : -OffRigid.Spin().X() / (2 * Math.PI);
        Check("a grip-only surface generates strictly less spin (this is why rubber needs e_t)",
              RigidRevs < OutRevs,
              String.format("e_t=0 gives %+.0f rev/s where rubber gives %+.0f", RigidRevs, OutRevs));
    }

    /** A swing may add energy -- that is what it is for -- but only as much as it physically could. */
    @Test
    void PaddleContactAddsNoFreeEnergy() {
        Vec3 Normal = new Vec3(0, 0, -1);
        BallState Incoming = BallState.At(new Vec3(0, 0.30, 0), new Vec3(0, 0, 8), new Vec3(-300, 0, 0));

        // A blade held perfectly still is just a wall.
        BallState Off = Strike(Incoming, new Vec3(0, 0.30, 0.025), Vec3.Zero, Normal, Rubber);
        Check("a ball into a STATIONARY blade never gains energy",
              Off != null && Off.KineticEnergy() <= Incoming.KineticEnergy() + 1e-12,
              Off == null ? "no contact"
                          : String.format("%.6f J -> %.6f J", Incoming.KineticEnergy(), Off.KineticEnergy()));

        // A swung blade may add energy, but not more than (1+e)*u + |v_in| allows.
        double Swing = 15.0, Arriving = 6.0;
        BallState Ball = BallState.At(new Vec3(0, 0.30, 0), new Vec3(0, 0, Arriving), Vec3.Zero);
        BallState Hit = Strike(Ball, new Vec3(0, 0.30, 0.025), new Vec3(0, 0, -Swing), Normal, Rubber);
        double Limit = (1 + Rubber.RestitutionAt(Swing + Arriving)) * Swing + Arriving;
        Check("a swung blade cannot send the ball faster than its own swing allows",
              Hit != null && Hit.Velocity().Length() <= Limit + 1e-9,
              Hit == null ? "no contact"
                          : String.format("%.2f m/s against a limit of %.2f", Hit.Velocity().Length(), Limit));
    }

    /** The paddle equivalent of the table tunnelling check, and a harder problem than the table was. */
    @Test
    void NoTunnellingThroughASwungPaddle() {
        int Caught = 0, Tried = 0;
        double BladeSpeed = 20.0;

        for (double Speed = 20; Speed <= 60.01; Speed += 2.5) {
            Tried++;
            BallState Ball = BallState.At(new Vec3(0, 0.30, 0), new Vec3(0, 0, Speed), Vec3.Zero);
            BallState Flown = Integrator.Step(Ball, Simulation.Step);

            // Half way along the RELATIVE sweep, so the crossing is mid-step at every speed.
            double Meet = (Speed * Simulation.Step - BladeSpeed * Simulation.Step) / 2;
            Racket Blade = new Racket(new Vec3(0, 0.30, Meet + BladeSpeed * Simulation.Step), new Vec3(0, 0, -1));
            Blade.MoveTo(new Vec3(0, 0.30, Meet), new Vec3(0, 0, -1), Simulation.Step);

            if (ContactSolver.Detect(Ball, Flown, Blade.Blade(), Simulation.Step) != null) Caught++;
        }
        Check("no tunnelling through a swung paddle from 20 to 60 m/s",
              Caught == Tried,
              String.format("%d of %d speeds caught, closing at up to %.0f m/s", Caught, Tried, 60 + BladeSpeed));
    }

    /** A blade with a closed face, as used for a topspin stroke. */
    private static Vec3 ClosedFace(double Tilt) {
        return new Vec3(0, -Tilt, -1).Normalized();
    }

    /** A brushing stroke of the given speed, angled UpDegrees above the horizontal. */
    private static BallState Brush(BallState Ball, double Speed, double UpDegrees) {
        double Angle = Math.toRadians(UpDegrees);
        Vec3 Swing = new Vec3(0, Speed * Math.sin(Angle), -Speed * Math.cos(Angle));
        return Strike(Ball, new Vec3(0, 0.245, 0.0263), Swing, ClosedFace(0.45), Rubber);
    }

    /** Strike the ball with a blade that moves through Swing over one step, finishing at End. */
    private static BallState Strike(BallState Ball, Vec3 End, Vec3 Swing, Vec3 Normal, Material Finish) {
        Racket Blade = new Racket(End.Minus(Swing.Scale(Simulation.Step)), Normal);
        Blade.MoveTo(End, Normal, Simulation.Step);
        BladeCollider Shape = Blade.Blade();

        ContactSolver.Contact Touch = ContactSolver.Detect(Ball, Ball, Shape, Simulation.Step);
        if (Touch == null) return null;
        return ContactSolver.Respond(Ball, Shape, Touch, Finish, Simulation.Step).State();
    }
}
