package tabletennis.engine.contact;

import static tabletennis.engine.math.Numeric.Clamp;

/**
 * Contact parameters for one surface. Restitution falls with approach speed because the shell
 * buckles above ~5 m/s [CONT]. Tangential restitution is the fraction of patch slip sprung back:
 * zero for rigid surfaces (perfect grip), nonzero only for rubber.
 */
public record Material(double Restitution, double RestitutionFade,
                       double MinRestitution, double MaxRestitution,
                       double Friction,
                       double TangentialRestitution, double TangentialRestitutionFade,
                       double DrillSpinDamping,
                       double VelocityDamping, double SpinDamping) {

    public static Material Rigid(double Restitution, double Friction,
                                 double VelocityDamping, double SpinDamping) {
        return new Material(Restitution, 0, Restitution, Restitution, Friction, 0, 0, 1,
                            VelocityDamping, SpinDamping);
    }

    public double RestitutionAt(double ApproachSpeed) {
        return Clamp(Restitution - RestitutionFade * ApproachSpeed, MinRestitution, MaxRestitution);
    }

    public double TangentialRestitutionAt(double SlipSpeed) {
        return Clamp(TangentialRestitution - TangentialRestitutionFade * SlipSpeed, 0, 1);
    }
}
