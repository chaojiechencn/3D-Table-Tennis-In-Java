package pong.assets;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;

import static pong.config.Physical.*;

/**
 * Small, deterministic material maps for the training hall. All maps are built once, never
 * during a rally. The fine grain is deliberately low contrast: it should reveal a surface
 * when the camera is near it, without becoming another moving pattern behind the ball.
 *
 * JavaFX's bump input is a tangent-space NORMAL map, not a greyscale height image. The nearly
 * blue normals below only perturb the highlights; strong normals would make a regulation
 * flat table look dented. TUNED colours and grain amplitudes describe finishes, not pong.
 */
public final class SurfaceMaterials {

    private SurfaceMaterials() {}

    /** A quiet satin finish: diffuse grain survives where the specular lobe is out of view. */
    public static PhongMaterial table() {
        return grain(Color.web("#246c88"), 256, 0.025, 0.075, 48, false);
    }

    /** A woven tape needs a much softer highlight than the metal holding it up. */
    public static PhongMaterial tape() {
        PhongMaterial material = grain(Color.web("#e2e5de"), 128, 0.025, 0.045, 12, true);
        // The narrow vertical tape faces away from the overhead key. A little baked room
        // bounce keeps the cord readable in LOW, where its lit top face is sub-pixel thin.
        WritableImage bounce = new WritableImage(1, 1);
        bounce.getPixelWriter().setColor(0, 0, Color.gray(0.26));
        material.setSelfIlluminationMap(bounce);
        return material;
    }

    public static PhongMaterial rubber(Color colour) {
        return grain(colour, 128, 0.035, 0.07, 28, false);
    }

    public static PhongMaterial solid(String colour, double shine, double power) {
        PhongMaterial material = new PhongMaterial(Color.web(colour));
        material.setSpecularColor(Color.gray(shine));
        material.setSpecularPower(power);
        return material;
    }

    private static PhongMaterial grain(Color base, int size, double contrast,
                                       double shine, double power, boolean woven) {
        WritableImage diffuse = new WritableImage(size, size);
        WritableImage normal = new WritableImage(size, size);
        WritableImage specular = new WritableImage(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double n = noise(x, y);
                double weave = woven ? Math.sin(x * Math.PI / 2) * Math.sin(y * Math.PI / 2) : n;
                diffuse.getPixelWriter().setColor(x, y, tint(base, 1 + contrast * weave));
                normal.getPixelWriter().setColor(x, y, Color.color(
                        0.5 + 0.025 * (noise((x + 1) % size, y) - n),
                        0.5 + 0.025 * (noise(x, (y + 1) % size) - n), 1));
                specular.getPixelWriter().setColor(x, y, Color.gray(shine * (0.85 + 0.15 * n)));
            }
        }
        PhongMaterial material = new PhongMaterial(Color.WHITE);
        material.setDiffuseMap(diffuse);
        material.setBumpMap(normal);
        material.setSpecularMap(specular);
        material.setSpecularColor(Color.WHITE);
        material.setSpecularPower(power);
        return material;
    }

    /**
     * Muted timber boards give perspective lines without putting a bright grid under the
     * ball. The broad dark footprint is baked ambient occlusion: JavaFX lights do not cast
     * shadows, and without this cue even correctly positioned legs appear to float.
     *
     * UVs cover the whole floor once, so its footprint and the four leg contacts are evaluated
     * in metres against the SAME Physical-derived dimensions as the court. No transparent floor
     * layers, per-board nodes or per-frame texture uploads are needed.
     */
    public static PhongMaterial floor(double width, double length) {
        int size = 1024;
        WritableImage diffuse = new WritableImage(size, size);
        WritableImage normal = new WritableImage(size, size);
        WritableImage specular = new WritableImage(size, size);
        PixelWriter colour = diffuse.getPixelWriter();
        double boardWidth = TABLE_WIDTH / 5;
        double boardLength = TABLE_LENGTH * 0.75;
        double[] legXs = { -LEG_X, LEG_X };
        double[] legZs = { -LEG_Z, LEG_Z };
        double contactSpread = TABLE_HEIGHT * 0.12;
        Color timber = Color.web("#796959");
        for (int v = 0; v < size; v++) {
            double z = (0.5 - v / (double) (size - 1)) * length;
            for (int u = 0; u < size; u++) {
                double x = (u / (double) (size - 1) - 0.5) * width;
                int board = (int) Math.floor(x / boardWidth);
                double across = fract(x / boardWidth);
                double along = fract(z / boardLength + noise(board, 4));
                double joint = across < 0.025 || along < 0.009 ? 0.82 : 1;
                double fibre = Math.sin(x / boardWidth * 95 + Math.sin(z * 7) * 0.7);
                double shade = (0.95 + 0.065 * noise(board, 9) + 0.018 * fibre
                        + 0.018 * noise(u, v)) * joint;

                double dx = Math.max(0, Math.abs(x) - TABLE_WIDTH * 0.46);
                double dz = Math.max(0, Math.abs(z) - TABLE_LENGTH * 0.46);
                double softness = TABLE_HEIGHT * 0.52;
                shade *= 1 - 0.48 * Math.exp(-(dx * dx + dz * dz) / (softness * softness));
                for (double legX : legXs) {
                    for (double legZ : legZs) {
                        double lx = x - legX;
                        double lz = z - legZ;
                        shade *= 1 - 0.42 * Math.exp(-(lx * lx + lz * lz)
                                / (contactSpread * contactSpread));
                    }
                }
                // A soft pool around the court falls off toward the walls. It is room
                // dressing, not a moving shadow, and stays stable through every camera cut.
                double pool = Math.exp(-(x * x + z * z) / (TABLE_LENGTH * TABLE_LENGTH * 2));
                shade *= 0.62 + 0.38 * pool;
                colour.setColor(u, v, tint(timber, shade));
                normal.getPixelWriter().setColor(u, v, Color.color(0.5 + fibre * 0.012, 0.5, 1));
                specular.getPixelWriter().setColor(u, v, Color.gray(0.025 * joint));
            }
        }
        PhongMaterial material = new PhongMaterial(Color.WHITE);
        material.setDiffuseMap(diffuse);
        material.setBumpMap(normal);
        material.setSpecularMap(specular);
        material.setSpecularColor(Color.WHITE);
        material.setSpecularPower(40);
        return material;
    }

    /**
     * A dark acoustic wall with a low dado and broad vertical bays. The strongest horizontal
     * cue stays below table height, so a ball near the net is silhouetted against quiet blue.
     * The modest emission keeps the far wall readable without adding more scene lights.
     */
    public static PhongMaterial wall() {
        int w = 512, h = 256;
        WritableImage diffuse = new WritableImage(w, h);
        WritableImage emission = new WritableImage(w, h);
        for (int y = 0; y < h; y++) {
            double down = y / (double) (h - 1);
            for (int x = 0; x < w; x++) {
                double bay = fract(x * 12.0 / w);
                double seam = bay < 0.016 ? 0.65 : 1;
                double light = 0.68 + 0.32 * Math.sin(Math.PI * down);
                Color base = down > 0.86 ? Color.web("#16232e") : Color.web("#344654");
                Color c = tint(base, light * seam * (1 + 0.012 * noise(x, y)));
                if (down > 0.852 && down < 0.86) c = Color.web("#456774");
                diffuse.getPixelWriter().setColor(x, y, c);
                emission.getPixelWriter().setColor(x, y, tint(c, 0.22));
            }
        }
        PhongMaterial material = new PhongMaterial(Color.WHITE);
        material.setDiffuseMap(diffuse);
        material.setSelfIlluminationMap(emission);
        material.setSpecularColor(Color.BLACK);
        return material;
    }

    private static double fract(double value) { return value - Math.floor(value); }

    /** Integer hashing keeps startup repeatable without allocating a random generator per map. */
    private static double noise(int x, int y) {
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0xffff) / 32767.5 - 1;
    }

    private static Color tint(Color c, double factor) {
        return Color.color(Math.min(1, c.getRed() * factor),
                Math.min(1, c.getGreen() * factor), Math.min(1, c.getBlue() * factor));
    }
}
