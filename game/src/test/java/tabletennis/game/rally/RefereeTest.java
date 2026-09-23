package tabletennis.game.rally;

import org.junit.jupiter.api.Test;
import tabletennis.engine.BallState;
import tabletennis.engine.math.Vec3;

import java.util.List;

import static tabletennis.testing.Claims.Check;

/** The rally rules on their own, fed facts directly: no physics, no rackets, no timing luck. */
final class RefereeTest {

    /** A ball well above the table, so no step judged with it can be called out. */
    private static final BallState InTheAir = BallState.At(new Vec3(0, 0.5, 0), new Vec3(0, 0, -5), Vec3.Zero);

    private static final Vec3 PlayerHalf = new Vec3(0, 0, 0.8);
    private static final Vec3 OpponentHalf = new Vec3(0, 0, -0.8);

    private static Side Judge(Referee Rules, double Time, RallyEvent... Facts) {
        return Rules.Judge(List.of(Facts), InTheAir, InTheAir, Time).PointTo();
    }

    private static RallyEvent Bounce(Vec3 On) { return RallyEvent.Of(EventType.TableBounce, On); }

    @Test
    void NoRacketMayStrikeBeforeTheBallBouncesOnItsHalf() {
        Referee Rules = new Referee();
        Judge(Rules, 0.1);
        boolean ClosedInTheAir = !Rules.MayHit(Side.Player) && !Rules.MayHit(Side.Opponent);
        Judge(Rules, 0.4, Bounce(OpponentHalf));
        Check("a racket may strike only once the ball has bounced on its own half",
              ClosedInTheAir && Rules.MayHit(Side.Opponent) && !Rules.MayHit(Side.Player),
              String.format("closed in the air=%b; after a far bounce player=%b opponent=%b",
                            ClosedInTheAir, Rules.MayHit(Side.Player), Rules.MayHit(Side.Opponent)));
    }

    @Test
    void ASecondBounceGivesThePointToTheHitter() {
        Referee Rules = new Referee();
        Judge(Rules, 0.1, RallyEvent.Hit(Side.Player, PlayerHalf));
        Side AfterFirst = Judge(Rules, 0.4, Bounce(OpponentHalf));
        Side AfterSecond = Judge(Rules, 0.7, Bounce(OpponentHalf));
        Check("a second bounce on the receiver's half is the hitter's point",
              AfterFirst == null && AfterSecond == Side.Player,
              "first bounce: " + AfterFirst + ", second bounce: " + AfterSecond);
    }

    @Test
    void AShotTheReceiverNeverTouchesIsTheReceiversPointLost() {
        Referee AfterAReturn = new Referee();
        Judge(AfterAReturn, 0.1, RallyEvent.Hit(Side.Opponent, OpponentHalf));
        Judge(AfterAReturn, 0.4, Bounce(PlayerHalf));
        Side ReturnPoint = Judge(AfterAReturn, 0.8, RallyEvent.Of(EventType.FloorTouch, new Vec3(0, -0.76, 2)));

        Referee AfterAFeed = new Referee();
        Judge(AfterAFeed, 0.4, Bounce(OpponentHalf));
        Side FeedPoint = Judge(AfterAFeed, 0.8, RallyEvent.Of(EventType.FloorTouch, new Vec3(0, -0.76, -2)));

        Check("a legal shot that reaches the floor untouched is the receiver's point lost",
              ReturnPoint == Side.Opponent && FeedPoint == Side.Player,
              "opponent's return past the player: point to " + ReturnPoint
              + "; feed past the opponent: point to " + FeedPoint);
    }

    @Test
    void AReturnOntoTheHittersOwnHalfLosesThePoint() {
        Referee Rules = new Referee();
        Judge(Rules, 0.1, RallyEvent.Hit(Side.Player, PlayerHalf));
        Side Point = Judge(Rules, 0.3, Bounce(PlayerHalf));
        Check("a shot that bounces on its hitter's own half loses the point", Point == Side.Opponent,
              "point to " + Point);
    }

    @Test
    void ABounceInsideTheContactWindowIsTheContactsOwnTouch() {
        Referee Rules = new Referee();
        Judge(Rules, 0.4, Bounce(OpponentHalf));
        Side Point = Judge(Rules, 0.5, RallyEvent.Hit(Side.Opponent, OpponentHalf), Bounce(OpponentHalf));
        Check("a table touch on the racket contact's own step is not a bounce",
              Point == null && !Rules.PointOver(), "point to " + Point);
    }

    @Test
    void AShotThatComesDownOffTheTableIsOut() {
        Referee Rules = new Referee();
        Judge(Rules, 0.1, RallyEvent.Hit(Side.Player, PlayerHalf));
        BallState Falling = BallState.At(new Vec3(1.2, 0.1, -0.5), new Vec3(0, -2, -5), Vec3.Zero);
        BallState Landed = BallState.At(new Vec3(1.2, 0.01, -0.51), new Vec3(0, -2, -5), Vec3.Zero);
        Referee.Ruling Verdict = Rules.Judge(List.of(), Falling, Landed, 0.3);
        boolean Called = Verdict.Events().stream().anyMatch(Event -> Event.Type() == EventType.OutOfBounds);
        Check("a shot that comes down wide of the table before it bounces is out, against its hitter",
              Called && Verdict.PointTo() == Side.Opponent,
              "out called=" + Called + ", point to " + Verdict.PointTo());
    }

    @Test
    void ADecidedPointIsAwardedOnceAndWithdrawsBothRackets() {
        Referee Rules = new Referee();
        Judge(Rules, 0.1, RallyEvent.Hit(Side.Opponent, OpponentHalf));
        Side First = Judge(Rules, 0.5, RallyEvent.Of(EventType.FloorTouch, new Vec3(0, -0.76, 2)));
        Side Second = Judge(Rules, 0.6, RallyEvent.Of(EventType.FloorTouch, new Vec3(0, -0.76, 2.5)));
        Check("a decided point is awarded once, and neither racket may strike after it",
              First == Side.Player && Second == null && !Rules.MayHit(Side.Player) && !Rules.MayHit(Side.Opponent),
              "first award " + First + ", second award " + Second);
    }

    @Test
    void ANetTouchAloneDecidesNothing() {
        Referee Rules = new Referee();
        Judge(Rules, 0.1, RallyEvent.Hit(Side.Player, PlayerHalf));
        Side Point = Judge(Rules, 0.2, RallyEvent.Of(EventType.NetTouch, new Vec3(0, 0.15, 0)));
        Check("clipping the net does not decide the point", Point == null && !Rules.PointOver(),
              "point to " + Point);
    }
}
