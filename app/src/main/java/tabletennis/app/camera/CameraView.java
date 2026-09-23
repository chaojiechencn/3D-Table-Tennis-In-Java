package tabletennis.app.camera;

/**
 * The preset views, cycled with C: yaw and pitch in degrees, distance and pivot height in metres.
 * The side view matters: from behind, perspective hides a Magnus dip almost entirely.
 */
public enum CameraView {
    Behind(0, 16, 3.85, 0.12),
    Side(90, 9, 3.35, 0.18),
    High(34, 42, 3.60, 0.05),
    Low(8, 1.5, 2.30, 0.16),
    Top(0, 86, 4.60, 0.0);

    final double Yaw, Pitch, Distance, Height;

    CameraView(double Yaw, double Pitch, double Distance, double Height) {
        this.Yaw = Yaw;
        this.Pitch = Pitch;
        this.Distance = Distance;
        this.Height = Height;
    }

    /** Case-insensitive, so the documented --view=SIDE spelling keeps working. */
    public static CameraView Named(String Name) {
        for (CameraView Each : values()) if (Each.name().equalsIgnoreCase(Name)) return Each;
        throw new IllegalArgumentException("no view named " + Name);
    }

    CameraView Next() {
        CameraView[] All = values();
        return All[(ordinal() + 1) % All.length];
    }
}
