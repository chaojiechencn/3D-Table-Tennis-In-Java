package tabletennis.app;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;

import javax.imageio.ImageIO;
import java.io.File;

/**
 * Offline capture: one PNG of the scene, then exit. The only Swing use in the project, for PNG
 * encoding; the loop has already advanced whole physics steps to the capture time, so the same
 * arguments give the same image.
 */
final class FrameCapture {

    /** A few pulses let the scene lay out and upload its textures before the snapshot. */
    static final int PulsesBeforeCapture = 3;

    private FrameCapture() {}

    static void WriteAndExit(Scene Window, String Path, double SimulatedSeconds) {
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(Window.snapshot(null), null), "png", new File(Path));
            System.out.println("wrote " + Path + " at t=" + String.format("%.3f", SimulatedSeconds));
        } catch (Exception Failure) {
            System.err.println("screenshot failed: " + Failure);
        }
        Platform.exit();
    }
}
