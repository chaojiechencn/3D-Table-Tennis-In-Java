package tabletennis.game.shot;

/** The box shots are aimed into, on whichever half they head for: metres from the centre line and the net. */
public record TargetArea(double HalfWidth, double NearDepth, double FarDepth) {}
