package tabletennis.app.scene;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import tabletennis.engine.TableSpec;

/**
 * Small, deterministic material maps for the training hall, built once and never during a rally.
 * The fine grain is deliberately low contrast: it should reveal a surface when the camera is near
 * it without becoming another moving pattern behind the ball.
 *
 * JavaFX's bump input is a tangent-space NORMAL map, not a greyscale height image. The nearly
 * blue normals below only perturb the highlights; strong normals would make a regulation flat
 * table look dented. TUNED colours and grain amplitudes describe finishes, not physics.
 */
final class Textures {

    private Textures() {}

    private static final double BoardWidth = TableSpec.Width / 5;
    private static final double BoardLength = TableSpec.Length * 0.75;
    private static final int FloorResolution = 1024;

    /** A quiet satin finish: diffuse grain survives where the specular lobe is out of view. */
    static PhongMaterial Table() {
        return Grain(Color.web("#246c88"), 256, 0.025, 0.075, 48, false);
    }

    /** A woven tape needs a much softer highlight than the metal holding it up. */
    static PhongMaterial Tape() {
        PhongMaterial Finish = Grain(Color.web("#e2e5de"), 128, 0.025, 0.045, 12, true);
        // The narrow vertical tape faces away from the overhead key. A little baked room bounce
        // keeps the cord readable in the LOW view, where its lit top face is sub-pixel thin.
        WritableImage Bounce = new WritableImage(1, 1);
        Bounce.getPixelWriter().setColor(0, 0, Color.gray(0.26));
        Finish.setSelfIlluminationMap(Bounce);
        return Finish;
    }

    static PhongMaterial Rubber(Color Colour) {
        return Grain(Colour, 128, 0.035, 0.07, 28, false);
    }

    static PhongMaterial Solid(String Colour, double Shine, double Power) {
        PhongMaterial Finish = new PhongMaterial(Color.web(Colour));
        Finish.setSpecularColor(Color.gray(Shine));
        Finish.setSpecularPower(Power);
        return Finish;
    }

    private static PhongMaterial Grain(Color Base, int Size, double Contrast,
                                       double Shine, double Power, boolean Woven) {
        WritableImage Diffuse = new WritableImage(Size, Size);
        WritableImage Normal = new WritableImage(Size, Size);
        WritableImage Specular = new WritableImage(Size, Size);
        for (int Y = 0; Y < Size; Y++) {
            for (int X = 0; X < Size; X++) {
                double Grain = Noise(X, Y);
                double Weave = Woven ? Math.sin(X * Math.PI / 2) * Math.sin(Y * Math.PI / 2) : Grain;
                Diffuse.getPixelWriter().setColor(X, Y, Tint(Base, 1 + Contrast * Weave));
                Normal.getPixelWriter().setColor(X, Y, Color.color(
                        0.5 + 0.025 * (Noise((X + 1) % Size, Y) - Grain),
                        0.5 + 0.025 * (Noise(X, (Y + 1) % Size) - Grain), 1));
                Specular.getPixelWriter().setColor(X, Y, Color.gray(Shine * (0.85 + 0.15 * Grain)));
            }
        }
        return Mapped(Diffuse, Normal, Specular, Power);
    }

    /**
     * Muted timber boards give perspective lines without putting a bright grid under the ball.
     * The broad dark footprint is baked ambient occlusion: JavaFX lights cast no shadows, and
     * without this cue even correctly positioned legs appear to float. UVs cover the whole floor
     * once, so the footprint and the leg contacts are evaluated in metres against the same
     * dimensions the table is built from.
     */
    static PhongMaterial Floor(double Width, double Length, double LegX, double LegZ) {
        int Size = FloorResolution;
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
                double Shade = (0.95 + 0.065 * Noise(Board, 9) + 0.018 * Fibre + 0.018 * Noise(U, V)) * Joint;
                Shade = WithTableOcclusion(Shade, X, Z);
                Shade = WithLegContacts(Shade, X, Z, LegX, LegZ);
                Shade = WithRoomPool(Shade, X, Z);
                Colour.setColor(U, V, Tint(Timber, Shade));
                Normal.getPixelWriter().setColor(U, V, Color.color(0.5 + Fibre * 0.012, 0.5, 1));
                Specular.getPixelWriter().setColor(U, V, Color.gray(0.025 * Joint));
            }
        }
        return Mapped(Diffuse, Normal, Specular, 40);
    }

    private static double WithTableOcclusion(double Shade, double X, double Z) {
        double Dx = Math.max(0, Math.abs(X) - TableSpec.Width * 0.46);
        double Dz = Math.max(0, Math.abs(Z) - TableSpec.Length * 0.46);
        double Softness = TableSpec.Height * 0.52;
        return Shade * (1 - 0.48 * Math.exp(-(Dx * Dx + Dz * Dz) / (Softness * Softness)));
    }

    private static double WithLegContacts(double Shade, double X, double Z, double LegX, double LegZ) {
        double Spread = TableSpec.Height * 0.12;
        for (double FootX : new double[] { -LegX, LegX }) {
            for (double FootZ : new double[] { -LegZ, LegZ }) {
                double Dx = X - FootX;
                double Dz = Z - FootZ;
                Shade *= 1 - 0.42 * Math.exp(-(Dx * Dx + Dz * Dz) / (Spread * Spread));
            }
        }
        return Shade;
    }

    /** Room dressing, not a moving shadow: stable through every camera cut. */
    private static double WithRoomPool(double Shade, double X, double Z) {
        double Pool = Math.exp(-(X * X + Z * Z) / (TableSpec.Length * TableSpec.Length * 2));
        return Shade * (0.62 + 0.38 * Pool);
    }

    /**
     * A dark acoustic wall with a low dado and broad vertical bays. The strongest horizontal cue
     * stays below table height, so a ball near the net is silhouetted against quiet blue. The
     * modest emission keeps the far wall readable without adding more scene lights.
     */
    static PhongMaterial Wall() {
        int Width = 512, Height = 256;
        WritableImage Diffuse = new WritableImage(Width, Height);
        WritableImage Emission = new WritableImage(Width, Height);
        for (int Y = 0; Y < Height; Y++) {
            double Down = Y / (double) (Height - 1);
            for (int X = 0; X < Width; X++) {
                double Bay = Fract(X * 12.0 / Width);
                double Seam = Bay < 0.016 ? 0.65 : 1;
                double Light = 0.68 + 0.32 * Math.sin(Math.PI * Down);
                Color Base = Down > 0.86 ? Color.web("#16232e") : Color.web("#344654");
                Color Pixel = Tint(Base, Light * Seam * (1 + 0.012 * Noise(X, Y)));
                if (Down > 0.852 && Down < 0.86) Pixel = Color.web("#456774");
                Diffuse.getPixelWriter().setColor(X, Y, Pixel);
                Emission.getPixelWriter().setColor(X, Y, Tint(Pixel, 0.22));
            }
        }
        PhongMaterial Finish = new PhongMaterial(Color.WHITE);
        Finish.setDiffuseMap(Diffuse);
        Finish.setSelfIlluminationMap(Emission);
        Finish.setSpecularColor(Color.BLACK);
        return Finish;
    }

    private static PhongMaterial Mapped(WritableImage Diffuse, WritableImage Normal, WritableImage Specular,
                                        double Power) {
        PhongMaterial Finish = new PhongMaterial(Color.WHITE);
        Finish.setDiffuseMap(Diffuse);
        Finish.setBumpMap(Normal);
        Finish.setSpecularMap(Specular);
        Finish.setSpecularColor(Color.WHITE);
        Finish.setSpecularPower(Power);
        return Finish;
    }

    private static double Fract(double Value) { return Value - Math.floor(Value); }

    /** Integer hashing keeps startup repeatable without allocating a random generator per map. */
    private static double Noise(int X, int Y) {
        int Hash = X * 374761393 + Y * 668265263;
        Hash = (Hash ^ (Hash >>> 13)) * 1274126177;
        return ((Hash ^ (Hash >>> 16)) & 0xffff) / 32767.5 - 1;
    }

    private static Color Tint(Color Base, double Factor) {
        return Color.color(Math.min(1, Base.getRed() * Factor),
                           Math.min(1, Base.getGreen() * Factor), Math.min(1, Base.getBlue() * Factor));
    }
}
