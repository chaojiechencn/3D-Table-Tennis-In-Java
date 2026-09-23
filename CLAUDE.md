# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

A 3D table tennis game in Java 21 and JavaFX: a validated ball-physics engine, an arcade game layer
on top of it, and a JavaFX front end. Start with [AGENTS.md](AGENTS.md), the operational contract
(invariants, traps already hit, known defects). The *why* lives in
[Architecture](docs/ARCHITECTURE.md), [Physics](docs/PHYSICS.md) and
[Game design](docs/GAME-DESIGN.md); read the relevant section before changing a physics model, a
control mapping or an invariant. [Development](docs/DEVELOPMENT.md) and
[Gameplay](docs/GAMEPLAY.md) cover setup and controls.

## Commands

Gradle wrapper only (Windows: `.\gradlew.bat`, elsewhere `bash ./gradlew`). There is no linter.

| Task | Command |
| --- | --- |
| Run the game | `.\gradlew.bat run` |
| All checks, every module | `.\gradlew.bat check` |
| One module | `.\gradlew.bat :engine:test` or `:game:test` or `:app:test` |
| One test class / method | `.\gradlew.bat :game:test --tests tabletennis.game.rally.RefereeTest` (append `.MethodName` for one method) |
| Rewrite the golden trace | `.\gradlew.bat :game:writeGoldenTrace` (only after a deliberate behavior change) |
| Deterministic screenshot | `.\gradlew.bat run --args="--shot=Serve --at=0.45 --view=SIDE --out=frame.png"` |

Capture mode (`--out`) advances whole physics steps to `--at` simulated seconds, so the same
arguments give the same image. Other flags: `--demo=true`, `--controldebug=true`, `--ball2x=true`,
`--rallycam=true`; `--view` takes `BEHIND`, `SIDE`, `HIGH`, `LOW` or `TOP`.

## Architecture

Three Gradle modules with one-way dependencies, enforced by the build: `app -> game -> engine`.
The engine has no dependencies (it cannot import JavaFX or game code); the game cannot import JavaFX.

- **engine** (`tabletennis.engine`): pure physics. `PhysicsWorld.Step()` advances exactly
  `Simulation.Step` (1/480 s, RK4) and returns a `StepReport` of *facts*: the ball before and after,
  and every contact in order. It never decides what a contact means for a rally. One impulse solver
  (`contact.ContactSolver`) serves table, net, floor and both rackets, differing only by `Material`,
  and it works in the surface's frame. Landing questions go to `flight.TrialFlight` (contact-free);
  a world with a table in it bounces the ball away before the descent is seen. Future-reading goes
  to `world.FlightPredictor` (racket-free). Every real-world number sits in a `*Spec`, `AeroData`
  or `Materials` class with its citation.
- **game** (`tabletennis.game`): `GameSession` is the ONE gameplay implementation that the app and
  the tests both drive. Each step: move the rackets (`control.CursorFollower` from the cursor, or
  `control.DemoHand`; `ai.Opponent` for the far end), hand the world only the rackets allowed to
  strike, step physics, turn notable contacts into rally facts (`rally.RallyFacts`), let
  `shot.ShotAssist` replace the raw bounce with an authored shot, then let `rally.Referee` judge
  (one-bounce rule, out, floor, latch) and award points to `match.Scoreboard`. It hands out
  immutable `GameSnapshot` and `StepResult` values only. `shot.ShotAssist` is a pipeline
  (`SwingReading` -> `ShotPlanner` -> `ShotSearch`) driven by `ShotTuning`'s validated knob groups.
- **app** (`tabletennis.app`): JavaFX composition root, a fixed-step clock with render
  interpolation, input (mouse aim and brush, key bindings next to the legend), the 3D scene,
  camera and HUD. It reads snapshots and never writes game state. `scene.Xform` is the only place
  physics metres become scene units.

## Verifying a change

- **Golden trace** (`game`'s `GoldenTraceTest`): 39 scripted scenarios hashed bit for bit.
  A restructuring must reproduce it exactly. A deliberate behavior change regenerates it with
  `writeGoldenTrace`, and the commit says why.
- **Claims**: tests call `Claims.Check("falsifiable claim", Holds, "measured detail")`
  (`engine`'s test fixtures), which prints a `[PASS]`/`[FAIL]` line with the measured number on a
  pass too. Never widen a threshold to fit a regression. `check` shows these lines because test
  output is shown.
- Captures can be compared pixel by pixel; the GPU's run-to-run noise is a handful of pixels.
- Mouse, brush, camera and overlay behavior needs a person at the keyboard.

## Conventions that are easy to get wrong

- **Every identifier is PascalCase**: classes, methods, fields, locals, parameters, record
  components and enum constants. Lowercase only where Java or a library forces it (`main`,
  overrides such as `start`, `handle`, `toString`, and package names). Never name a variable after a
  type used in the same scope: Java resolves the variable first.
- `Numeric.Clamp`, not `Math.clamp`: the latter orders `-0.0` below `0.0` and would change results
  bit for bit.
- Physics never sees a frame time; anything sampled per step for display counts whole steps.
- Comments state the constraint, citation or `TUNED` reason in a sentence or two; derivations
  belong in the docs.
- Commit only the program itself: no IDE or machine files.
