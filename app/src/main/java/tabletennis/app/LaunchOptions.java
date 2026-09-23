package tabletennis.app;

import tabletennis.app.camera.CameraView;
import tabletennis.game.feed.Feed;
import tabletennis.game.feed.Feeds;

import java.util.List;

/**
 * The command line, as --name=value pairs; anything else is ignored. A CapturePath switches to
 * offline capture: --shot="Topspin loop" --at=0.45 --view=SIDE --out=frame.png.
 */
record LaunchOptions(Feed FirstFeed, CameraView View, boolean BallMagnified, boolean ControlReadout,
                     boolean DemoMode, boolean RallyCamInCapture, String CapturePath, double CaptureAt) {

    static LaunchOptions Parse(List<String> Arguments) {
        Feed FirstFeed = Feeds.Default();
        CameraView View = null;
        boolean BallMagnified = false, ControlReadout = false, DemoMode = false, RallyCam = false;
        String CapturePath = null;
        double CaptureAt = 0;

        for (String Argument : Arguments) {
            String[] NameAndValue = Argument.split("=", 2);
            if (NameAndValue.length != 2) continue;
            String Value = NameAndValue[1];
            switch (NameAndValue[0]) {
                case "--shot" -> FirstFeed = Feeds.ByName(Value);
                case "--at" -> CaptureAt = Double.parseDouble(Value);
                case "--out" -> CapturePath = Value;
                case "--view" -> View = CameraView.Named(Value);
                case "--ball2x" -> BallMagnified = Boolean.parseBoolean(Value);
                case "--controldebug" -> ControlReadout = Boolean.parseBoolean(Value);
                case "--demo" -> DemoMode = Boolean.parseBoolean(Value);
                case "--rallycam" -> RallyCam = Boolean.parseBoolean(Value);
                default -> { }
            }
        }
        return new LaunchOptions(FirstFeed, View, BallMagnified, ControlReadout, DemoMode, RallyCam,
                                 CapturePath, CaptureAt);
    }

    boolean Capturing() { return CapturePath != null; }
}
