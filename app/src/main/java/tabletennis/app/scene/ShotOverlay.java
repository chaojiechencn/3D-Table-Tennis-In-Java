package tabletennis.app.scene;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import tabletennis.engine.math.Vec3;
import tabletennis.game.shot.ShotDecision;
import tabletennis.game.shot.TargetArea;

/**
 * The V overlay for tuning the shot assist, drawn from the last contact: racket velocity (yellow),
 * incoming ball (white), raw reflection (grey), intended shot (cyan), final shot (orange, length =
 * speed), target (magenta), predicted landing (green) and the legal target box (blue).
 */
final class ShotOverlay {

    /** Metres of arrow per m/s, shared so the velocity arrows compare. */
    private static final double VelocityScale = 0.045;
    private static final double ReflectLength = 0.45, IntendedLength = 0.55;

    private final Group Root = new Group();
    private final Cylinder RacketLine = Line("#f5d442", 2.0);
    private final Cylinder IncomingLine = Line("#e8eef6", 2.0);
    private final Cylinder ReflectLine = Line("#8a8f99", 2.5);
    private final Cylinder IntendedLine = Line("#39c6d6", 2.5);
    private final Cylinder FinalLine = Line("#ff9a3c", 3.5);
    private final Sphere TargetDot = Dot(7, "#e05cc8");
    private final Sphere LandingDot = Dot(6, "#5ce07a");
    private final Group Box = new Group();

    ShotOverlay() {
        Root.getChildren().addAll(Box, RacketLine, IncomingLine, ReflectLine, IntendedLine, FinalLine,
                                  TargetDot, LandingDot);
        Root.setVisible(false);
    }

    Group Node() { return Root; }
    void SetShown(boolean Shown) { Root.setVisible(Shown); }
    boolean IsShown() { return Root.isVisible(); }

    /** TowardPlayer puts the target box on the player's half, where the opponent's shots aim. */
    void Show(ShotDecision Shot, boolean TowardPlayer) {
        Vec3 Contact = Shot.Contact();
        Orient(RacketLine, Contact, Shot.RacketVelocity(), Shot.RacketVelocity().Length() * VelocityScale);
        Orient(IncomingLine, Contact, Shot.IncomingVelocity(), Shot.IncomingVelocity().Length() * VelocityScale);
        Orient(ReflectLine, Contact, Shot.ReflectDirection(), ReflectLength);
        Orient(IntendedLine, Contact, Shot.IntendedDirection(), IntendedLength);
        Orient(FinalLine, Contact, Shot.FinalDirection(), Shot.Speed() * VelocityScale);

        Xform.Place(TargetDot, Shot.Target().X(), 0.02, Shot.Target().Z());
        Xform.Place(LandingDot, Shot.Landing().X(), 0.03, Shot.Landing().Z());
        ShowTargetArea(Shot.Area(), TowardPlayer);
    }

    /** Drawn from the assist's own target area, so the outline cannot drift from the code. */
    private void ShowTargetArea(TargetArea Area, boolean TowardPlayer) {
        double Toward = TowardPlayer ? 1 : -1;
        double Near = Toward * Area.NearDepth(), Far = Toward * Area.FarDepth();
        double Half = Area.HalfWidth();
        Box.getChildren().setAll(
                Edge(-Half, Near, Half, Near),
                Edge(-Half, Far, Half, Far),
                Edge(-Half, Near, -Half, Far),
                Edge(Half, Near, Half, Far));
    }

    private static Cylinder Line(String Colour, double Radius) {
        Cylinder Shape = new Cylinder(Radius, 100);
        Shape.setMaterial(new PhongMaterial(Color.web(Colour)));
        return Shape;
    }

    private static Sphere Dot(double Radius, String Colour) {
        Sphere Shape = new Sphere(Radius);
        Shape.setMaterial(new PhongMaterial(Color.web(Colour)));
        return Shape;
    }

    private static Cylinder Edge(double X0, double Z0, double X1, double Z1) {
        Cylinder Shape = Line("#4a7fd6", 1.6);
        Vec3 Direction = new Vec3(X1 - X0, 0, Z1 - Z0);
        Orient(Shape, new Vec3(X0, 0.01, Z0), Direction, Direction.Length());
        return Shape;
    }

    /** Lay a +Y-aligned cylinder from From along Direction for LengthMetres. */
    private static void Orient(Cylinder Shape, Vec3 From, Vec3 Direction, double LengthMetres) {
        Vec3 Unit = Direction.Normalized();
        if (Unit.LengthSquared() < 1e-9 || LengthMetres < 1e-4) { Shape.setVisible(false); return; }
        Shape.setVisible(true);

        double Length = Xform.Length(LengthMetres);
        Shape.setHeight(Length);
        Point3D Along = Xform.ToScene(Unit).normalize();
        double Dx = Along.getX(), Dy = Along.getY(), Dz = Along.getZ();
        Point3D Start = Xform.ToScene(From);
        Shape.setTranslateX(Start.getX() + Dx * Length / 2);
        Shape.setTranslateY(Start.getY() + Dy * Length / 2);
        Shape.setTranslateZ(Start.getZ() + Dz * Length / 2);

        double Angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, Dy))));
        boolean Vertical = Math.abs(Dx) < 1e-6 && Math.abs(Dz) < 1e-6;
        Point3D Axis = Vertical ? Rotate.X_AXIS : new Point3D(Dz, 0, -Dx);   // (0,1,0) x d
        Shape.getTransforms().setAll(new Rotate(Angle, Axis));
    }
}
