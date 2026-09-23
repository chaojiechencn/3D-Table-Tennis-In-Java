package tabletennis.app.input;

import javafx.geometry.Point2D;
import javafx.scene.SubScene;
import javafx.scene.input.MouseEvent;
import tabletennis.app.hud.ControlReadout;
import tabletennis.engine.math.Vec3;
import tabletennis.game.GameSession;
import tabletennis.game.control.ReachEnvelope;

/**
 * The mouse drives the player's racket, and only the mouse. Bare movement aims on the hitting
 * plane; holding the right button switches the cursor's Y to blade height for a brush, freezing
 * depth while held. The aim is handed to the session, which consumes it once per physics step.
 */
public final class MouseControl {

    private final SubScene Viewport;
    private final GameSession Session;

    private double CursorX = Double.NaN, CursorY = Double.NaN;
    private Vec3 RawAim;   // before clamping, so the D overlay can show a held blade
    private Vec3 Aim;
    private boolean Brushing;
    private double BrushHoldZ = ReachEnvelope.Neutral.Z();

    public MouseControl(SubScene Viewport, GameSession Session) {
        this.Viewport = Viewport;
        this.Session = Session;
    }

    /**
     * addEventHandler, because a setOnMouseMoved would unhook the camera's orbit handler.
     * MOUSE_MOVED stops while a button is down, so the brush also listens to MOUSE_DRAGGED.
     */
    public void Attach() {
        Viewport.addEventHandler(MouseEvent.MOUSE_MOVED, this::AimAt);
        Viewport.addEventHandler(MouseEvent.MOUSE_DRAGGED, Event -> { if (Brushing) AimAt(Event); });
        Viewport.addEventHandler(MouseEvent.MOUSE_PRESSED, Event -> {
            if (!Event.isSecondaryButtonDown()) return;
            Brushing = true;
            BrushHoldZ = Session.Snapshot().PlayerBlade().Centre().Z();
            AimAt(Event);
        });
        Viewport.addEventHandler(MouseEvent.MOUSE_RELEASED, Event -> {
            if (!Brushing || Event.isSecondaryButtonDown()) return;
            Brushing = false;
            AimAt(Event);
        });
    }

    /** Where the cursor is and what it aimed at, for the D overlay. */
    public ControlReadout.Cursor Cursor() {
        return new ControlReadout.Cursor(CursorX, CursorY, RawAim, Aim);
    }

    /** sceneToLocal, since events target the 3D nodes inside the viewport. */
    private void AimAt(MouseEvent Event) {
        Point2D Local = Viewport.sceneToLocal(Event.getSceneX(), Event.getSceneY());
        // Only for a degenerate ray; used as an input it would be a loop with gain.
        Vec3 Fallback = Aim != null ? Aim : Session.Snapshot().PlayerBlade().Centre();

        CursorX = Local.getX();
        CursorY = Local.getY();
        RawAim = CursorRay.OnPlane(Viewport, CursorX, CursorY, ReachEnvelope.HitY, Fallback);
        double HeightFraction = CursorY / Math.max(1, Viewport.getHeight());
        Aim = Brushing ? ReachEnvelope.ClampBrushed(RawAim, HeightFraction, BrushHoldZ)
                       : ReachEnvelope.Clamp(RawAim);
        Session.SetAim(Aim);
    }
}
