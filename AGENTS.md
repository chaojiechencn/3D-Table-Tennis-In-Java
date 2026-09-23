# AGENTS.md

The operational contract for coding agents (Codex, Claude Code) in this repository: what you must
not break, and how to prove you did not. The reasoning lives in
[Architecture](docs/ARCHITECTURE.md), [Physics](docs/PHYSICS.md) and
[Game design](docs/GAME-DESIGN.md); read the relevant section before changing a physics model, a
control mapping or an invariant. Commands and layout are in [Development](docs/DEVELOPMENT.md).

## What this is

A 3D table tennis game in Java 21 and JavaFX, in three Gradle modules with one-way dependencies:

- **`engine`** (`tabletennis.engine`) is a validated ball-physics engine.
- **`game`** (`tabletennis.game`) is an arcade game layer on top of the engine.
- **`app`** (`tabletennis.app`) is a JavaFX front end on top of the game.

The build enforces `app -> game -> engine`.

## Build and validate

Use the Gradle wrapper (`.\gradlew.bat` on Windows, `bash ./gradlew` elsewhere). Do not add another
build system or a new dependency for a cleanup.

```powershell
.\gradlew.bat check                                   # every module, every check
.\gradlew.bat :game:test --tests tabletennis.game.rally.RefereeTest
.\gradlew.bat :game:writeGoldenTrace                  # only for a deliberate behavior change
```

**Every check must stay green.** A failing check is a broken deliverable, not a flaky test.

- **Never widen a threshold to fit a regression.** Re-deriving one because the model became more
  accurate is legitimate; say why in a comment, with the number the new model predicts. That
  comment is the only thing that tells the two apart later.
- **The golden trace** (`GoldenTraceTest`) must reproduce bit for bit after any restructuring. If a
  change is meant to alter behavior, regenerate it with `writeGoldenTrace`, review the diff, and
  state the reason in the commit message.
- **A new check** is `Claims.Check("falsifiable claim", Holds, "measured detail")` in the JUnit
  class for that component. The detail prints on a pass too, so it must state what was measured.
- Rendering changes cannot be checked headlessly. Compare `--out` captures before and after; say
  what you could not check (mouse, brush, camera and overlays need a person).

## Hard invariants

Breaking any of these is a defect even if it compiles and every check passes.
[Architecture](docs/ARCHITECTURE.md#hard-invariants) gives the reasons.

1. The engine imports neither JavaFX nor game code; the game imports no JavaFX.
2. `scene.Xform` is the only place metres become scene units, in both directions.
3. The app reads `GameSnapshot`s and `StepResult`s; it never writes game state.
4. One contact solver (`ContactSolver`) for table, net, floor and both blades; they differ only by
   `Material` and `Collider` shape.
5. The solver works in the surface's frame. In absolute velocity a swung blade does nothing.
6. `FlightPredictor` and `TrialFlight` never see a racket.
7. Landing questions go to `TrialFlight`, never to a world with a table in it.
8. The ball never moves the player's blade: `CursorFollower.Advance` takes no `BallState`.
9. Physics never sees a frame time; the session advances in whole steps.
10. Feeds state intent and `LaunchSolver` solves the angle; never hard-code a launch velocity.
11. One gameplay implementation: `GameSession`. The app and the tests drive it; neither
    re-implements a rule.

## Coordinates

Physics space is SI metres, right-handed, origin at the centre of the table surface: `+X` right,
`+Y` up, `+Z` toward the player (end lines at `z = ±1.37`, floor at `y = -0.76`). Scene space is
JavaFX's left-handed, `+Y`-down space, reached only through `Xform`. **Never fix a sign by flipping
an axis; fix the physics.**

## Traps that have already bitten this project

- **The dead axis.** In normal play the player's blade is pinned to `ReachEnvelope.HitY`, so its
  vertical velocity is identically zero; only the brush modifier makes it live. Twice an expression
  read that component and silently became a constant. Ask whose blade it is and whether the
  component can be non-zero.
- **Asking a world where a shot lands.** Its table bounces the ball away first, so the answer is
  the second descent. Found three separate times.
- **A variable named after a type.** Java resolves `Solution.State()` to a variable named `Solution`
  before the type `Solution`, so the code compiles against the wrong thing or fails far away. Never
  name a variable, field or record component after a type used in the same scope.
- **`Math.clamp` instead of `Numeric.Clamp`.** `Math.clamp` orders `-0.0` below `0.0` and throws on
  inverted bounds; either changes results bit for bit.
- **Probing the assist with a moved blade.** A probe that calls `ShotAssist.Assist` after
  `Racket.MoveTo` sideways leaves the ball behind the blade's centre, so a 10 m/s swipe registers
  as a rim hit. Place the ball at the blade's centre after the move. This produced a false "aim is
  asymmetric" report; measured correctly, aim is symmetric and monotone.

## Settled; do not undo

1. **Scoring** (`Scoreboard`): games to 11, win by 2, no deuce ceiling, service every 2 points and
   every 1 from 10-all, best of 5. The server is derived from the score, never stored.
2. **The point latches** (`Referee`): awarded once however many rules fire on a step, and both
   rackets are withdrawn.
3. **A net cord decides nothing** by itself. A cord that kills the ball decides the point later
   through the own-half or floor rule. Service lets belong with serving.
4. **The spin gesture works.** The brush comes from the drive axis plus the vertical axis while
   brushing (`SwingReading`), not from a dead velocity component.
5. **The player can lose a point.** Only the player's contacts are graded on where they struck the
   blade; the opponent is not, or it shanks ordinary feeds into the net.
6. **The brush modifier is modal.** Right mouse held: cursor Y moves the bat's height and depth
   freezes (`ReachEnvelope.ClampBrushed`). `ReachEnvelope.Clamp` never leaves the hitting plane.
7. **`TrackSpeed` stays at 13 m/s.** The reach envelope was the fix for unhittable balls, not blade
   speed; `ReachabilityTest` measures the margin.

## Known open defects

Do not rediscover these, and do not undo a fix for them.

1. **The opponent follows rather than predicts.** `BallFollower` is a placeholder behind the
   `Opponent` interface; a predicting opponent is a second implementation.
2. **The `Top` and `High` views cannot address the whole depth envelope.** Left alone
   deliberately: they are inspection views, not rally views.
3. **A missed legal return scores for the receiver.** When a return bounces legally on the
   receiver's half and then reaches the floor untouched, the floor rule awards the point against
   the last hitter. The fix belongs in `Referee`: once the hitter's shot has bounced on the
   receiver's half, a floor contact is the receiver's point lost.
4. **Candidate: a moving blade is carried a whole step on every contact pass.** After a swept
   contact has used part of the step, `PhysicsWorld.EarliestContact` still detects against a whole
   step of blade motion. Kept until deliberately changed.

## Conventions

- **Every identifier is PascalCase**: classes, methods, fields, locals, parameters, record
  components, enum constants and test methods. Lowercase only where Java or a library forces it
  (`main`, overrides such as `start`, `handle`, `equals`, `hashCode`, `toString`, and package
  names). No ALL_CAPS, including constants.
- Short methods with one responsibility and clear names.
- **Comments state why, in a sentence or two**: the constraint, the citation, or the `TUNED`
  reason. Derivations, measurement tables and rejected alternatives belong in the docs. No
  decorative divider comments.
- **Cite every real-world number** in the engine; a bare constant there is a bug. Anything tuned
  by eye is labelled `TUNED` and says what it stands in for.
- Every arcade knob lives in `ShotTuning`, validated when built, with each default and its reason on
  the `Builder`. Measured physical values stay single-sourced in the engine.
- Commit only the program itself: no IDE, editor or machine files, and no personal utilities.
