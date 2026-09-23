package tabletennis.app.render;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;

import static tabletennis.engine.Constants.*;

/**
 * Small, deterministic material maps for the training hall. All maps are built once, never
 * during a rally. The fine grain is deliberately low contrast: it should reveal a surface
 * when the camera is near it, without becoming another moving pattern behind the ball.
 *
 * JavaFX's bump input is a tangent-space NORMAL map, not a greyscale height image. The nearly
 * blue normals below only perturb the highlights; strong normals would make a regulation
 * flat table look dented. TUNED colours and grain amplitudes describe finishes, not physics.
 */
final class SurfaceMaterials {

    private SurfaceMaterials() {}

    /** A quiet satin finish: diffuse grain survives where the specular lobe is out of view. */
    static PhongMaterial Table() {
        return Grain(Color.web("#246c88"), 256, 0.025, 0.075, 48, false);
    }

    /** A woven tape needs a much softer highlight than the metal holding it up. */
    static PhongMaterial Tape() {
        PhongMaterial Material = Grain(Color.web("#e2e5de"), 128, 0.025, 0.045, 12, true);
        // The narrow vertical tape faces away from the overhead key. A little baked room
        // bounce keeps the cord readable in LOW, where its lit top face is sub-pixel thin.
        WritableImage Bounce = new WritableImage(1, 1);
        Bounce.getPixelWriter().setColor(0, 0, Color.gray(0.26));
        Material.setSelfIlluminationMap(Bounce);
        return Material;
    }

    static PhongMaterial Rubber(Color Colour) {
        return Grain(Colour, 128, 0.035, 0.07, 28, false);
    }

    static PhongMaterial Solid(String Colour, double Shine, double Power) {
        PhongMaterial Material = new PhongMaterial(Color.web(Colour));
        Material.setSpecularColor(Color.gray(Shine));
        Material.setSpecularPower(Power);
        return Material;
    }

    private static PhongMaterial Grain(Color Base, int Size, double Contrast,
                                       double Shine, double Power, boolean Woven) {
        WritableImage Diffuse = new WritableImage(Size, Size);
        WritableImage Normal = new WritableImage(Size, Size);
        WritableImage Specular = new WritableImage(Size, Size);
        for (int Y = 0; Y < Size; Y++) {
            for (int X = 0; X < Size; X++) {
                double N = Noise(X, Y);
                double Weave = Woven ? Math.sin(X * Math.PI / 2) * Math.sin(Y * Math.PI / 2) : N;
                Diffuse.getPixelWriter().setColor(X, Y, Tint(Base, 1 + Contrast * Weave));
                Normal.getPixelWriter().setColor(X, Y, Color.color(
                        0.5 + 0.025 * (Noise((X + 1) % Size, Y) - N),
                        0.5 + 0.025 * (Noise(X, (Y + 1) % Size) - N), 1));
                Specular.getPixelWriter().setColor(X, Y, Color.gray(Shine * (0.85 + 0.15 * N)));
            }
        }
        PhongMaterial Material = new PhongMaterial(Color.WHITE);
        Material.setDiffuseMap(Diffuse);
        Material.setBumpMap(Normal);
        Material.setSpecularMap(Specular);
        Material.setSpecularColor(Color.WHITE);
        Material.setSpecularPower(Power);
        return Material;
    }

    /**
     * Muted timber boards give perspective lines without putting a bright grid under the
     * ball. The broad dark footprint is baked ambient occlusion: JavaFX lights do not cast
     * shadows, and without this cue even correctly positioned legs appear to float.
     *
     * UVs cover the whole floor once, so its footprint and the four leg contacts are evaluated
     * in metres against the SAME Constants-derived dimensions as Court. No transparent floor
     * layers, per-board nodes or per-frame texture uploads are needed.
     */
    static PhongMaterial Floor(double Width, double Length) {
        int Size = 1024;
        WritableImage Diffuse = new WritableImage(Size, Size);
        WritableImage Normal = new WritableImage(Size, Size);
        WritableImage Specular = new WritableImage(Size, Size);
        PixelWriter Colour = Diffuse.getPixelWriter();
        Color Timber = Color.web("#796959");
        for (int V = 0; V < Size; V++) {
            double Z = (0.5 - V / (double) (Size - 1)) * Length;
            for (int U = 0; U < Size; U++) {
                double X = (U / (double) (Size - 1) - 0.5) * Width;
                int Board = (int) Math.floor(X / BoardWidth);
                double Across = Fract(X / BoardWidth);
                double Along = Fract(Z / BoardLength + Noise(Board, 4));
                double Joint = Across < 0.025 || Along < 0.009 ? 0.82 : 1;
                double Fibre = Math.sin(X / BoardWidth * 95 + Math.sin(Z * 7) * 0.7);
                double Shade = (0.95 + 0.065 * Noise(Board, 9) + 0.018 * Fibre
                        + 0.018 * Noise(U, V)) * Joint;
                Shade = WithTableOcclusion(Shade, X, Z);
                Shade = WithLegContacts(Shade, X, Z);
                Shade = WithRoomPool(Shade, X, Z);
                Colour.setColor(U, V, Tint(Timber, Shade));
                Normal.getPixelWriter().setColor(U, V, Color.color(0.5 + Fibre * 0.012, 0.5, 1));
                Specular.getPixelWriter().setColor(U, V, Color.gray(0.025 * Joint));
            }
        }
        PhongMaterial Material = new PhongMaterial(Color.WHITE);
        Material.setDiffuseMap(Diffuse);
        Material.setBumpMap(Normal);
        Material.setSpecularMap(Specular);
        Material.setSpecularColor(Color.WHITE);
        Material.setSpecularPower(40);
        return Material;
    }

    private static final double BoardWidth = TableWidth / 5;
    private static final double BoardLength = TableLength * 0.75;

    private static double WithTableOcclusion(double Shade, double X, double Z) {
        double Dx = Math.max(0, Math.abs(X) - TableWidth * 0.46);
        double Dz = Math.max(0, Math.abs(Z) - TableLength * 0.46);
        double Softness = TableHeight * 0.52;
        return Shade * (1 - 0.48 * Math.exp(-(Dx * Dx + Dz * Dz) / (Softness * Softness)));
    }

    private static double WithLegContacts(double Shade, double X, double Z) {
        double Spread = TableHeight * 0.12;
        for (double LegX : new double[] { -Court.LegX, Court.LegX }) {
            for (double LegZ : new double[] { -Court.LegZ, Court.LegZ }) {
                double Lx = X - LegX;
                double Lz = Z - LegZ;
                Shade *= 1 - 0.42 * Math.exp(-(Lx * Lx + Lz * Lz) / (Spread * Spread));
            }
        }
        return Shade;
    }

    /** Room dressing, not a moving shadow: stable through every camera cut. */
    private static double WithRoomPool(double Shade, double X, double Z) {
        double Pool = Math.exp(-(X * X + Z * Z) / (TableLength * TableLength * 2));
        return Shade * (0.62 + 0.38 * Pool);
    }

    /**
     * A dark acoustic wall with a low dado and broad vertical bays. The strongest horizontal
     * cue stays below table height, so a ball near the net is silhouetted against quiet blue.
     * The modest emission keeps the far wall readable without adding more scene lights.
     */
    static PhongMaterial Wall() {
        int W = 512, H = 256;
        WritableImage Diffuse = new WritableImage(W, H);
        WritableImage Emission = new WritableImage(W, H);
        for (int Y = 0; Y < H; Y++) {
            double Down = Y / (double) (H - 1);
            for (int X = 0; X < W; X++) {
                double Bay = Fract(X * 12.0 / W);
                double Seam = Bay < 0.016 ? 0.65 : 1;
                double Light = 0.68 + 0.32 * Math.sin(Math.PI * Down);
                Color Base = Down > 0.86 ? Color.web("#16232e") : Color.web("#344654");
                Color C = Tint(Base, Light * Seam * (1 + 0.012 * Noise(X, Y)));
                if (Down > 0.852 && Down < 0.86) C = Color.web("#456774");
                Diffuse.getPixelWriter().setColor(X, Y, C);
                Emission.getPixelWriter().setColor(X, Y, Tint(C, 0.22));
            }
        }
        PhongMaterial Material = new PhongMaterial(Color.WHITE);
        Material.setDiffuseMap(Diffuse);
        Material.setSelfIlluminationMap(Emission);
        Material.setSpecularColor(Color.BLACK);
        return Material;
    }

    private static double Fract(double Value) { return Value - Math.floor(Value); }

    /** Integer hashing keeps startup repeatable without allocating a random generator per map. */
    private static double Noise(int X, int Y) {
        int N = X * 374761393 + Y * 668265263;
        N = (N ^ (N >>> 13)) * 1274126177;
        return ((N ^ (N >>> 16)) & 0xffff) / 32767.5 - 1;
    }

    private static Color Tint(Color C, double Factor) {
        return Color.color(Math.min(1, C.getRed() * Factor),
                Math.min(1, C.getGreen() * Factor), Math.min(1, C.getBlue() * Factor));
    }
}
