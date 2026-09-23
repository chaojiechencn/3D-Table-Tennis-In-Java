package tabletennis.engine.world;

import org.junit.jupiter.api.Test;
import tabletennis.engine.BallSpec;
import tabletennis.engine.BallState;
import tabletennis.engine.Environment;
import tabletennis.engine.Simulation;
import tabletennis.engine.TableSpec;
import tabletennis.engine.contact.Materials;
import tabletennis.engine.flight.Integrator;
import tabletennis.engine.flight.LaunchSolver;
import tabletennis.engine.math.Vec3;

import static tabletennis.testing.Claims.Check;

/** The ball against the table: the ITTF drop test, spin off the bounce, energy and tunnelling. */
final class TableBounceTest {

    private static final int StepsPerSecond = Simulation.StepsPerSecond;

    /** The ITTF bounce test: dropped from 30.5 cm, a ball must rebound to 24-26 cm. */
    @Test
    void IttfDropTest() {
        PhysicsWorld World = new PhysicsWorld();
        World.Launch(BallState.At(new Vec3(0, 0.305 + BallSpec.Radius, -0.7), Vec3.Zero, Vec3.Zero));

        boolean Bounced = false;
        double Peak = 0;
        int Bounces = 0;
        for (int Step = 0; Step < StepsPerSecond * 3; Step++) {
            Bounces += TableBounces(World.Step());
            if (Bounces > 0) {
                if (!Bounced) { Bounced = true; Peak = 0; }
                Peak = Math.max(Peak, World.Ball().Position().Y() - BallSpec.Radius);
                if (Bounces > 1) break;
            }
        }

        // Report the restitution ACTUALLY used, not the intercept of the fit.
        double DropSpeed = Math.sqrt(2 * Environment.Gravity * 0.305);
        double RestitutionUsed = Materials.Table.RestitutionAt(DropSpeed);

        Check("ball bounced off the table at all", Bounced, "");
        Check("ITTF drop test: 30.5 cm gives a 24-26 cm rebound",
              Peak >= 0.24 && Peak <= 0.26,
              String.format("rebound %.1f cm, e = %.3f at the %.2f m/s impact", Peak * 100, RestitutionUsed, DropSpeed));

        // Why e is not the textbook sqrt(25/30.5) = 0.905: that ignores air, and with drag it undershoots.
        double Naive = Math.sqrt(0.25 / 0.305);
        double NaiveRebound = ReboundWithRestitution(Naive);
        Check("the drag-free estimate of e would MISS the ITTF band (this is why e is higher)",
              NaiveRebound < 0.24,
              String.format("e = %.3f gives only %.1f cm", Naive, NaiveRebound * 100));
    }

    /**
     * The spin coupling as a falsifiable claim: topspin must leave the bounce FASTER along its
     * travel than it arrived, and heavy backspin slower, with its spin knocked down or reversed.
     */
    @Test
    void TopspinKicksForwardBackspinChecks() {
        double Spin = 110 * 2 * Math.PI;
        Vec3 Position = new Vec3(0, 0.25, 0.5), Velocity = new Vec3(0, -3.0, -10);

        double[] Top = BounceChange(BallState.At(Position, Velocity, new Vec3(-Spin, 0, 0)));
        double[] Back = BounceChange(BallState.At(Position, Velocity, new Vec3(Spin, 0, 0)));

        Check("topspin gains forward speed off the bounce", Top[0] > 0.2, String.format("forward speed %+.2f m/s", Top[0]));
        Check("backspin loses forward speed off the bounce", Back[0] < -0.2, String.format("forward speed %+.2f m/s", Back[0]));
        Check("the bounce reduces backspin (friction fights it)", Back[1] < -1.0, String.format("spin change %+.0f rad/s", Back[1]));
        Check("topspin and backspin behave oppositely off the same table", Top[0] * Back[0] < 0, "");
    }

    /** No contact may ever add kinetic energy: this is what stops a simulation exploding. */
    @Test
    void BounceNeverAddsEnergy() {
        PhysicsWorld World = new PhysicsWorld();
        World.Launch(TopspinLoop());

        double Worst = 0;
        double Previous = World.Ball().KineticEnergy();
        for (int Step = 0; Step < StepsPerSecond * 20; Step++) {
            World.Step();
            double Now = World.Ball().KineticEnergy();
            // Gravity legitimately adds energy in free fall, so only a sudden jump counts.
            double Gain = Now - Previous;
            double GravityBudget = BallSpec.Mass * Environment.Gravity * Math.abs(World.Ball().Velocity().Y()) * Simulation.Step * 1.5 + 1e-9;
            if (Gain > GravityBudget) Worst = Math.max(Worst, Gain - GravityBudget);
            Previous = Now;
        }
        Check("no contact adds kinetic energy over a 20 s rally",
              Worst < 1e-6, String.format("worst unexplained gain %.3e J", Worst));
    }

    /** The hardest shot in the game must not fall through the table. */
    @Test
    void NoTunnellingAtSmashSpeed() {
        int Tested = 0, Caught = 0;
        for (double Speed = 20; Speed <= 60; Speed += 2.5) {
            Tested++;
            PhysicsWorld World = new PhysicsWorld();
            World.Launch(BallState.At(new Vec3(0, 0.30, -0.5), new Vec3(0, -Speed, 0), Vec3.Zero));
            for (int Step = 0; Step < StepsPerSecond; Step++) {
                if (TableBounces(World.Step()) > 0) { Caught++; break; }
                if (World.Ball().Position().Y() < -0.3) break;   // it went through
            }
        }
        Check("no tunnelling straight down from 20 to 60 m/s (swept collision working)",
              Caught == Tested, Caught + " of " + Tested + " speeds bounced");

        // Not passing for trivial reasons: at this step an overlap-only test WOULD miss the fast ones.
        double PerStep = 60 * Simulation.Step;
        Check("the fast cases really do outrun a static overlap test",
              PerStep > TableSpec.TopThickness + 2 * BallSpec.Radius,
              String.format("%.1f cm per step vs a %.1f cm crossing",
                            PerStep * 100, (TableSpec.TopThickness + 2 * BallSpec.Radius) * 100));
    }

    /** {change in forward speed, change in x-spin} across the first table bounce. */
    private static double[] BounceChange(BallState Start) {
        PhysicsWorld World = new PhysicsWorld();
        World.Launch(Start);
        BallState Before = Start;
        for (int Step = 0; Step < StepsPerSecond * 3; Step++) {
            BallState Previous = World.Ball();
            if (TableBounces(World.Step()) > 0) { Before = Previous; break; }
        }
        for (int Step = 0; Step < 6; Step++) World.Step();   // let it clear the surface
        BallState After = World.Ball();
        return new double[] { -After.Velocity().Z() - (-Before.Velocity().Z()),
                              After.Spin().X() - Before.Spin().X() };
    }

    /** Rebound height from the ITTF drop, for an arbitrary restitution. */
    private static double ReboundWithRestitution(double Restitution) {
        BallState Ball = BallState.At(new Vec3(0, 0.305 + BallSpec.Radius, -0.7), Vec3.Zero, Vec3.Zero);
        while (Ball.Position().Y() > BallSpec.Radius) Ball = Integrator.Step(Ball, Simulation.Step);
        Ball = Ball.WithVelocity(new Vec3(0, -Ball.Velocity().Y() * Restitution, 0))
                   .WithPosition(new Vec3(0, BallSpec.Radius, -0.7));

        double Peak = 0;
        while (Ball.Velocity().Y() > 0) {
            Ball = Integrator.Step(Ball, Simulation.Step);
            Peak = Math.max(Peak, Ball.Position().Y() - BallSpec.Radius);
        }
        return Peak;
    }

    static int TableBounces(StepReport Report) {
        return (int) Report.NotableHits().stream().filter(Hit -> Hit.Kind() == SurfaceKind.Table).count();
    }

    static BallState TopspinLoop() {
        return LaunchSolver.AtTarget(new Vec3(0, 0.30, 1.52), new Vec3(0, 0, -1.05), 15.0, 110, 0).State();
    }
}
