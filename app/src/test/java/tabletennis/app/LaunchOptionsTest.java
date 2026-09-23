package tabletennis.app;

import org.junit.jupiter.api.Test;
import tabletennis.app.camera.CameraView;

import java.util.List;

import static tabletennis.testing.Claims.Check;

/** The documented command line keeps working. */
final class LaunchOptionsTest {

    private static String Describe(LaunchOptions Options) {
        return String.format("feed %s, at %.2f s, view %s, ball x2 %b, demo %b, out %s", Options.FirstFeed().Name(),
                             Options.CaptureAt(), Options.View(), Options.BallMagnified(), Options.DemoMode(),
                             Options.CapturePath());
    }

    @Test
    void TheDocumentedCaptureLineParses() {
        LaunchOptions Options = LaunchOptions.Parse(List.of(
                "--shot=Topspin loop", "--at=0.18", "--view=SIDE", "--ball2x=true", "--out=out/frame.png"));
        Check("the documented capture command line parses",
              Options.FirstFeed().Name().equals("Topspin loop") && Options.CaptureAt() == 0.18
                  && Options.View() == CameraView.Side && Options.BallMagnified() && Options.Capturing(),
              Describe(Options));
    }

    @Test
    void ANormalLaunchIsNotACapture() {
        LaunchOptions Options = LaunchOptions.Parse(List.of("--unknown=1", "stray", "--demo=true"));
        Check("no --out means normal play; unknown and malformed arguments are ignored",
              !Options.Capturing() && Options.DemoMode() && Options.FirstFeed().Name().equals("Serve")
                  && Options.View() == null,
              Describe(Options));
    }
}
