# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

A 3D table tennis game in Java 21 and JavaFX: a validated ball-physics engine, an arcade game layer
on top of it, and a JavaFX front end. [AGENTS.md](AGENTS.md) is the operational contract (hard
invariants, coordinates, traps already hit, settled decisions, known defects, naming and comment
conventions); this file does not repeat it. The *why* lives in
[Architecture](docs/ARCHITECTURE.md), [Physics](docs/PHYSICS.md) and
[Game design](docs/GAME-DESIGN.md); read the relevant section before changing a physics model, a
control mapping or an invariant. [Development](docs/DEVELOPMENT.md) and
[Gameplay](docs/GAMEPLAY.md) cover setup and controls.

## Commands

Gradle wrapper only (Windows: `.\gradlew.bat`, elsewhere `bash ./gradlew`). There is no linter.

| Task | Command |
| --- | --- |
| Run the game (opens on the menu) | `.\gradlew.bat run` |
| Run in developer mode (straight onto the table, every key and overlay) | `.\gradlew.bat run -Pdev` |
| All checks, every module | `.\gradlew.bat check` |
| One module | `.\gradlew.bat :engine:test` or `:game:test` or `:app:test` |
| One test class / method | `.\gradlew.bat :game:test --tests tabletennis.game.rally.RefereeTest` (append `.MethodName` for one method) |
| Rewrite the golden trace | `.\gradlew.bat :game:writeGoldenTrace` (only after a deliberate behavior change) |
| Deterministic screenshot | `.\gradlew.bat run --args="--shot=Serve --at=0.45 --view=SIDE --out=frame.png"` |

Capture mode (`--out`) advances whole physics steps to `--at` simulated seconds, so the same
arguments give the same image (up to a few pixels of GPU noise). Other flags: `--demo=true`,
`--controldebug=true`, `--ball2x=true`, `--rallycam=true`, `--dev=true`, and `--screen=` (`menu`,
`practice`, `howtoplay`, `result` or `none`); `--view` takes `BEHIND`, `SIDE`, `HIGH`, `LOW` or
`TOP`. The full table is in [Development](docs/DEVELOPMENT.md#rendering-captures).

## Architecture

Three Gradle modules with one-way dependencies, enforced by the build: `app -> game -> engine`.

- **engine** (`tabletennis.engine`): pure physics, no dependencies. `PhysicsWorld.Step()` advances
  exactly `Simulation.Step` (1/480 s, RK4) and returns a `StepReport` of *facts*: the ball before
  and after, and every contact in order. It never decides what a contact means for a rally.
  `contact.ContactSolver` serves every surface. Landing questions go to `flight.TrialFlight`;
  future-reading goes to `world.FlightPredictor`. Every real-world number sits in a `*Spec`,
  `AeroData` or `Materials` class with its citation.
- **game** (`tabletennis.game`): `GameSession` is the one gameplay implementation that the app and
  the tests both drive. Each step: move the rackets (`control.CursorFollower` from the cursor, or
  `control.DemoHand`; `ai.Opponent` for the far end), hand the world only the rackets allowed to
  strike, step physics, turn notable contacts into rally facts (`rally.RallyFacts`), let
  `shot.ShotAssist` replace the raw bounce with an authored shot, then let `rally.Referee` judge
  and award points to `match.Scoreboard`. It hands out immutable `GameSnapshot` and `StepResult`
  values only. `shot.ShotAssist` is a pipeline (`SwingReading` -> `ShotPlanner` -> `ShotSearch`)
  driven by `ShotTuning`'s validated knob groups. Feeds (`Feeds.All`) state intent and
  `LaunchSolver` solves the launch.
- **app** (`tabletennis.app`): JavaFX composition root (`TableTennisApp`, which switches between
  menu, match, practice and demo), a fixed-step clock with render interpolation (`GameLoop`,
  `FixedStepClock`), `input` (mouse aim and brush; key bindings live in `Controls` next to the
  legend they draw), `scene`, `camera`, `hud` and `menu`. It reads snapshots and sends the session
  only commands (`Launch`, `NewMatch`, `SetAim`, `SetFaceTilt`, `SetDemoMode`, `SetAutoReplay`).

[Architecture](docs/ARCHITECTURE.md#one-step-end-to-end) walks one step end to end and maps every
package.

## Verifying a change

- **Golden trace** (`game`'s `GoldenTraceTest`): scripted scenarios through `GameSession`,
  compared bit for bit with `game/src/test/resources/tabletennis/game/golden-trace.txt`.
  A restructuring must reproduce it exactly.
- **Claims**: tests call `Claims.Check("falsifiable claim", Holds, "measured detail")` from
  `engine`'s test fixtures (`src/testFixtures`), used by all three modules. Each prints a
  `[PASS]`/`[FAIL]` line with the measured number; `check` shows test output.
- CI (`.github/workflows/ci.yml`) runs `check` on Ubuntu and Windows with Java 21.
- Rendering, mouse, brush, camera and overlays need captures and a person at the keyboard; say what
  you could not check.
