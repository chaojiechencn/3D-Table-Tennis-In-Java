package tabletennis.game;

import tabletennis.engine.BallState;
import tabletennis.engine.contact.BladeCollider;
import tabletennis.game.match.ScoreSnapshot;
import tabletennis.game.shot.ShotDecision;

/**
 * The game at one instant, for anything that only reads it: the renderer, the HUD, the tests.
 * PreviousBall is the ball one step earlier, so a frame can interpolate between the two.
 */
public record GameSnapshot(BallState Ball, BallState PreviousBall, double Time,
                           BladeCollider PlayerBlade, BladeCollider OpponentBlade, ScoreSnapshot Score,
                           boolean PlayerMayHit, boolean OpponentMayHit, boolean PointOver,
                           boolean DemoMode, boolean AutoReplay, ShotDecision LastShot) {}
