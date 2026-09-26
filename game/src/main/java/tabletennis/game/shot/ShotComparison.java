package tabletennis.game.shot;

import tabletennis.engine.math.Vec3;

import java.util.List;

/**
 * One player hit, both ways: the shot the game played (ShotAssist) and the shot the racket's own
 * motion would have made (ContactModel). Contact is null when the averaged swing was separating
 * from the ball. For the shadow log only; nothing reads it back into the game.
 */
public record ShotComparison(double Time, Vec3 RacketVelocity, ShotReport Played, ShotReport Contact) {

    /** Two lines: the played shot with its flight, then the contact model's. */
    public List<String> Lines() {
        String Racket = String.format("racket %+.2f,%+.2f,%+.2f m/s", RacketVelocity.X(), RacketVelocity.Y(),
                                      RacketVelocity.Z());
        String Shadow = Contact == null ? "no approach: the averaged swing was already leaving the ball"
                                        : Contact.Describe(false);
        return List.of(String.format("t=%8.3f PLAYED  %s", Time, Played.Describe(true)),
                       String.format("t=%8.3f CONTACT %s  (%s)", Time, Shadow, Racket));
    }
}
