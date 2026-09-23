package tabletennis.app.camera;

/** Where the rally-cam sits: pitch in degrees, distance from its pivot and the pivot's height in metres. */
record CameraPose(double Pitch, double Distance, double Height) {

    /** This pose carried Fraction of the way toward Target, one easing step. */
    CameraPose EasedToward(CameraPose Target, double Fraction) {
        return new CameraPose(Pitch + (Target.Pitch - Pitch) * Fraction,
                              Distance + (Target.Distance - Distance) * Fraction,
                              Height + (Target.Height - Height) * Fraction);
    }
}
