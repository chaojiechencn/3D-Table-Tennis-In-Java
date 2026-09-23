package tabletennis.game.ai;

/**
 * Where a blade can meet the ball: a plane at PlaneY, a band of depths into its own half from the
 * net, and the ball heights a blade on that plane can touch.
 */
public record HittingZone(double PlaneY, double NearDepth, double FarDepth, double VerticalCapture) {}
