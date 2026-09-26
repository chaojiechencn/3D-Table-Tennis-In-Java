package tabletennis.game.shot;

import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;
import tabletennis.engine.NetSpec;
import tabletennis.engine.Simulation;
import tabletennis.engine.TableSpec;
import tabletennis.engine.flight.TrialFlight;
import tabletennis.engine.flight.TrialFlight.Flight;
import tabletennis.engine.math.Vec3;

/**
 * A player's launch as a player would describe it, and where it goes. Angles and spin are relative
 * to the heading: AimDeg is positive to the player's right, TopRevs negative for backspin, SideRevs
 * about +Y. The flight is contact-free at the game's own step, so until something clips the net it
 * is the flight the game will play. Legality is the rules' (clear the cord, land on the far half),
 * not the assist's safety margins.
 */
public record ShotReport(double Speed, double ElevationDeg, double AimDeg, double TopRevs, double SideRevs,
                         boolean ClearsNet, boolean LandsIn, double FlightSeconds, double NetClearance) {

    private static final int MaxFlightSteps = 3 * Simulation.StepsPerSecond;

    /** For a player's shot, heading toward -Z, under the measured physics. */
    public static ShotReport Of(BallState Launch) {
        return Of(Launch, 1.0);
    }

    /** For a player's shot, heading toward -Z, flown under a sideways-lift gain. */
    public static ShotReport Of(BallState Launch, double CurveGain) {
        Vec3 V = Launch.Velocity();
        SpinPlan Revs = SpinPlan.Of(V, Launch.Spin());

        Flight Path = TrialFlight.Fly(Launch, TrialFlight.Heading.TowardNegativeZ, Simulation.Step, MaxFlightSteps,
                                      CurveGain);
        double Clearance = Path.NetHeight() - BallSpec.Radius - NetSpec.Height;   // NaN if it never crossed
        boolean ClearsNet = Clearance > 0;
        Vec3 Landing = Path.Landing();
        boolean LandsIn = ClearsNet && Landing.Z() < 0 && Landing.Z() >= -TableSpec.HalfLength
                       && Math.abs(Landing.X()) <= TableSpec.HalfWidth;

        return new ShotReport(V.Length(),
                              Math.toDegrees(Math.atan2(V.Y(), Math.hypot(V.X(), V.Z()))),
                              Math.toDegrees(Math.atan2(V.X(), -V.Z())),
                              Revs.Top(), Revs.Side(), ClearsNet, LandsIn, Path.Seconds(), Clearance);
    }

    /** One log line; the flight time and net height only where asked for. */
    String Describe(boolean WithFlight) {
        String Line = String.format("%5.2f m/s  elev %+5.1f deg  aim %+5.1f deg  top %+6.1f  side %+6.1f rev/s  %-3s  %-3s",
                                    Speed, ElevationDeg, AimDeg, TopRevs, SideRevs,
                                    ClearsNet ? "net" : "NET", LandsIn ? "IN" : "OUT");
        if (!WithFlight) return Line;
        String Bounce = LandsIn ? String.format("far bounce %.3f s", FlightSeconds) : "no far bounce";
        String Over = Double.isNaN(NetClearance) ? "never reached the net"
                                                 : String.format("%+.1f cm over the net", NetClearance * 100);
        return Line + "  " + Bounce + "  " + Over;
    }
}
