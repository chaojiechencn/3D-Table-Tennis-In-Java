package tabletennis.app.render;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import tabletennis.engine.math.Vec3;

/**
 * The V overlay for tuning play.ShotAssist, drawn from the last contact: racket velocity (yellow),
 * incoming ball (white), raw reflection (grey), intended shot (cyan), final shot (orange, length =
 * speed), target (magenta), predicted landing (green) and the legal target box (blue).
 */
public final class ShotDebug {

    /** Metres of arrow per m/s, shared so the velocity arrows compare. */
    private static final double VelScale = 0.045;

    private final Group Root = new Group();
    private final Cylinder RacketLine   = Line("#f5d442", 2.0);
    private final Cylinder IncomingLine = Line("#e8eef6", 2.0);
    private final Cylinder ReflectLine  = Line("#8a8f99", 2.5);
    private final Cylinder IntendLine   = Line("#39c6d6", 2.5);
    private final Cylinder FinalLine    = Line("#ff9a3c", 3.5);
    private final Sphere TargetDot  = Dot(7, "#e05cc8");
    private final Sphere LandingDot = Dot(6, "#5ce07a");
    private final Group Box = new Group();
    private String Readout = "";

    public ShotDebug() {
        Root.getChildren().addAll(Box, RacketLine, IncomingLine, ReflectLine,
                                  IntendLine, FinalLine, TargetDot, LandingDot);
        Root.setVisible(false);
    }

    public Group Node() { return Root; }
    public void SetShown(boolean S) { Root.setVisible(S); }
    public boolean IsShown() { return Root.isVisible(); }

    /** The numbers that do not draw as arrows, for the HUD. */
    public String Readout() { return Readout; }

    public void Set(Vec3 Contact, Vec3 RacketVel, Vec3 IncomingVel, Vec3 ReflectDir,
                    Vec3 IntendDir, Vec3 FinalDir, Vec3 Target, Vec3 Landing,
                    double Speed, Vec3 Spin, int Passes, boolean Legal) {
        Orient(RacketLine,   Contact, RacketVel,   RacketVel.Length() * VelScale);
        Orient(IncomingLine, Contact, IncomingVel, IncomingVel.Length() * VelScale);
        Orient(ReflectLine,  Contact, ReflectDir,  0.45);
        Orient(IntendLine,   Contact, IntendDir,   0.55);
        Orient(FinalLine,    Contact, FinalDir,    Speed * VelScale);

        Xform.Place(TargetDot, Target.X(), 0.02, Target.Z());
        Xform.Place(LandingDot, Landing.X(), 0.03, Landing.Z());

        double Rev = 2 * Math.PI;
        Readout = String.format(
                "shot  %.1f m/s   spin (%+.0f %+.0f %+.0f) rev/s   passes %d   %s",
                Speed, Spin.X() / Rev, Spin.Y() / Rev, Spin.Z() / Rev, Passes,
                Legal ? "LEGAL" : "fallback");
    }

    /** Drawn from ShotAssist's own accessors, so the outline cannot drift from the code. */
    public void SetTargetArea(double HalfWidth, double NearZ, double FarZ, boolean TowardPlayer) {
        double S = TowardPlayer ? 1 : -1;
        double Z0 = S * NearZ, Z1 = S * FarZ;
        Box.getChildren().setAll(
                Edge(-HalfWidth, Z0,  HalfWidth, Z0),
                Edge(-HalfWidth, Z1,  HalfWidth, Z1),
                Edge(-HalfWidth, Z0, -HalfWidth, Z1),
                Edge( HalfWidth, Z0,  HalfWidth, Z1));
    }

    private static Cylinder Line(String Web, double Radius) {
        Cylinder C = new Cylinder(Radius, 100);
        C.setMaterial(new PhongMaterial(Color.web(Web)));
        return C;
    }

    private static Sphere Dot(double R, String Web) {
        Sphere S = new Sphere(R);
        S.setMaterial(new PhongMaterial(Color.web(Web)));
        return S;
    }

    private static Cylinder Edge(double X0, double Z0, double X1, double Z1) {
        Cylinder C = Line("#4a7fd6", 1.6);
        Vec3 Dir = new Vec3(X1 - X0, 0, Z1 - Z0);
        Orient(C, new Vec3(X0, 0.01, Z0), Dir, Dir.Length());
        return C;
    }

    /** Lay a +Y-aligned cylinder from fromM along dirM for lenM metres. */
    private static void Orient(Cylinder C, Vec3 FromM, Vec3 DirM, double LenM) {
        Vec3 D = DirM.Normalized();
        if (D.LengthSquared() < 1e-9 || LenM < 1e-4) { C.setVisible(false); return; }
        C.setVisible(true);

        double Len = Xform.Length(LenM);
        C.setHeight(Len);
        Point3D Direction = Xform.ToScene(D).normalize();
        double Dx = Direction.getX(), Dy = Direction.getY(), Dz = Direction.getZ();
        Point3D Start = Xform.ToScene(FromM);
        C.setTranslateX(Start.getX() + Dx * Len / 2);
        C.setTranslateY(Start.getY() + Dy * Len / 2);
        C.setTranslateZ(Start.getZ() + Dz * Len / 2);

        double Angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, Dy))));
        boolean Vertical = Math.abs(Dx) < 1e-6 && Math.abs(Dz) < 1e-6;
        Point3D Axis = Vertical ? Rotate.X_AXIS : new Point3D(Dz, 0, -Dx);   // (0,1,0) x d
        C.getTransforms().setAll(new Rotate(Angle, Axis));
    }
}
