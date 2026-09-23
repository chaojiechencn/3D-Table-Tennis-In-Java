package tabletennis.engine.aero;

/** A drag law passed as a parameter, so the closed-form checks can fly a constant coefficient. */
@FunctionalInterface
public interface DragModel {

    double Coefficient(double Speed, double SpinRatio);

    /** The measured law every flight in the game uses. */
    DragModel Measured = Aerodynamics::MeasuredDragCoefficient;

    static DragModel Constant(double Coefficient) {
        return (Speed, SpinRatio) -> Coefficient;
    }
}
