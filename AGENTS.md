# AGENTS.md

Operating guide for coding agents (Codex, Claude Code) working in this repository.

[docs/DESIGN.md](docs/DESIGN.md) holds the plan, methodology and full rationale; root
[CLAUDE.md](CLAUDE.md) is its entry point. **This file is the short operational contract**:
what you must not break and how to prove you did not break it. See
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for current builds and source layout. Read the design
notes when you need the *why* behind a rule.

---

## What this is

A 3D table tennis game in Java 21 + JavaFX. Mouse-controlled racket, a ball carrying real spin,
and an AI opponent. A validated simulation with an arcade game layer on top of it. Directories
are named for what the code is FOR -- see `src/main/java/pong/README.md` for the map, and the
README in each directory for what belongs in it.

---

## Build and run

**Java 21 + the existing Gradle wrapper** is the standard build. It resolves JavaFX dependencies.
The project also remains compilable with stock `javac` when using **Liberica Full JDK 21**, which
bundles JavaFX. Do not introduce another build system or application dependency for a cleanup.

```powershell
.\gradlew.bat classes
.\gradlew.bat run
```

On macOS/Linux/Git Bash, use `bash ./gradlew`. Full manual compilation commands and IDE setup live in
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). Manual compilation includes both `src/main/java` and
`src/test/java`, and writes to `out/production/3D-Table-Tennis-In-Java`.

Gradle uses `build/`; manual builds use `out/`. Both are ignored. Never commit generated output.

---

## Validation — not optional

Two headless suites. Both print PASS/FAIL per check and exit 0/1. **They must stay at 100%.**

```powershell
.\gradlew.bat check       # both suites
.\gradlew.bat selfTest    # pong._tests.PhysicsTest: 101 checks
.\gradlew.bat rallyTest   # pong._tests.RallyTest: 29 checks
```

The suites remain plain Java `main` classes under `src/test/java/pong/_tests/`. Gradle's `test`, `check` and
`build` tasks invoke both dedicated suites.

- Touched the simulation (`game_objects/`, `game_world/`, `systems/collision/`, `config/Physical`)
  or its tests → run `pong._tests.PhysicsTest`.
- Touched game logic (`systems/`, `config/ShotTuning`) or its tests → run `pong._tests.RallyTest`.
- Touched a `view/`, `ui/` or `_debug/` only → still compile, and run both if you changed anything shared.
- Changed source layout or build configuration → compile and run both suites.

A failing check is a broken deliverable, not a flaky test. **Never widen a threshold to make a
regression fit.** Re-deriving a threshold because the model got more accurate is legitimate — say
why in a comment, with the number the new model predicts. That comment is the only thing that
tells those two cases apart later.

Adding a check is one `check("falsifiable claim", boolean, "the measured number")` call plus one
line in `main`. The detail string prints on **pass as well as fail**, so it must state what was
actually measured.

---

## Architecture and the rules that keep it from rotting

```text
src/main/java/pong/
  core/math/        Vec3, Quat, Scalars. Knows nothing about table tennis.
  config/           Physical (MEASURED, cited) and ShotTuning (TUNED, reasoned).
  game_objects/     ball/ and racket/ -- simulation at the top, JavaFX in a view/ leaf.
  game_world/       World (the simulation) + view/ (the court, bounce marks).
  systems/          collision, aim, control, opponent, scoring, shotmaking.
    connectors/     RallyRules -- wires World + rackets + Scoreboard together.
  assets/           Generated materials, shared by two views.
  screens/          MatchScreen: entry point, fixed-timestep loop, input, wiring.
  ui/               Hud.
  _debug/           ShotDebug (V), ControlOverlay (D). The game runs without them.
  helpers/          Xform (the ONE space conversion), MouseAim (ray geometry).
src/test/java/pong/_tests/
  PhysicsTest.java  simulation validation
  RallyTest.java    game validation
```

**Dependency direction is one-way**, and a `view/` depends on the package above it, never the
reverse:

```
screens → ui, _debug, systems, game_objects, game_world
systems → game_objects, game_world, core, config
game_world → game_objects, systems/collision, core, config
game_objects → core, config
core → nothing
```

Hard invariants — breaking any of these is a defect even if it compiles and the suites pass:

1. **JavaFX may only be imported from `screens/`, `ui/`, `_debug/`, `assets/`, `helpers/` and the
   `view/` leaves.** Everything else stays headless and frame-rate independent, which is the only
   reason both suites can grade it without opening a window. Check it:
   `grep -rl --include=*.java "import javafx" src/main/java/pong | grep -vE "/(view|screens|ui|_debug|assets|helpers)/"`
   must print nothing.
2. **`helpers.Xform` is the ONLY place physics space becomes scene space**, in both directions.
   Nothing else may multiply or divide by `SPM`.
3. **A `view/` reads simulation state, never writes it.**
4. **One collision solver.** Table, net, floor and both rackets differ only by a `Material` and a
   shape. Do not add a second bespoke bounce path. Shape hides behind `Collider`'s four questions.
5. **The solver works in the SURFACE's frame, not the world's.** Written in absolute velocity, a
   blade swung into a ball reads as "already separating" and does nothing. If a racket ever passes
   through a ball harmlessly, this is what broke.
6. **`World.predict` must never see a racket.** A prediction that gets intercepted predicts nothing.
7. **The ball never moves the player's racket.** `Stroke.advance` does not take a `Ball` — the
   rule is enforced by the signature. Anything that reads the ball *before* the blade's target is
   chosen is auto-follow wearing a different hat.
8. **The simulation never sees the frame time.** Passing a JavaFX `dt` into `World` or
   `Integrator` breaks determinism.
9. **Shot presets state intent** (speed, spin, target) and let `Aim` solve the launch angle. Do not
   hard-code launch velocities.

---

## Coordinate conventions — get these wrong and everything looks haunted

**Physics space** — SI metres, right-handed, origin at the centre of the table surface:
`+X` right, `+Y` up, `+Z` toward the near end (the player). Near end `z = +1.37`, far end
`z = -1.37`, floor `y = -0.76`.

Right-handedness is load-bearing: every cross product (Magnus `ω × v`, the contact impulses)
assumes it. **Do not fix a sign by flipping an axis — fix the physics.**

**Scene space** — JavaFX, left-handed, `+Y` is *down*:

```
sceneX = +x * SPM        SPM = 300 scene units per metre
sceneY = -y * SPM
sceneZ = -z * SPM
```

`SPM` exists because JavaFX's default camera clip planes and light attenuation both fall apart at
metre scale. Convert at the boundary and nowhere else.

---

## Traps that have already bitten this project

**The dead-axis trap.** The player's blade is pinned to one horizontal plane
(`PlayerReach.HIT_Y`) — height is a gameplay parameter, not an input axis. So for the player,
**`racket.vel().y()` is identically zero.** Any expression reading it is dead code that silently
evaluates to a constant. This has already happened twice: once in `Stroke.faceToward` (fixed), and
once in `ShotAssist` (see open defects below). Before reading a velocity component, ask which
actor's blade it belongs to and whether that component can ever be non-zero.

**Asking a `World` where a shot lands.** A `World` has a table in it, and the table bounces the
ball out of the way before the descent can be detected — so the first crossing you see is the
SECOND descent, out past the end line. **Always ask the landing question of a contact-free flight
(`Aim.landingPoint`).** This bug has been found three separate times.

**IDE configuration is generated.** Import the existing Gradle project so `src/main/java` and
`src/test/java` are marked correctly. `.idea/` and `.iml` files are local and ignored; a running
IntelliJ rewrites them. Change source roots in the Gradle project rather than moving or hand-editing
generated module files. Keep the `pong.screens.MatchScreen`, `pong._tests.PhysicsTest` and
`pong._tests.RallyTest` entry points stable.

---

## Settled — do not undo these

1. **Scoring exists.** `systems/scoring/Scoreboard.java` keeps the ITTF rules (11, win by 2, no ceiling at
   deuce, service every 2 points and every 1 from 10-all, best of 5). It is derived from the
   score rather than toggled, deliberately — see the class javadoc. 9 checks in `RallyTest`.
2. **`endPoint()` latches.** It now takes the winning side, awards exactly once however many
   rules fire on the same step, and withdraws both rackets so a decided ball stops being
   playable.
3. **A net cord no longer loses the point.** `NET` was removed from the immediate point-enders:
   `World` emits it for *any* cord contact above 0.05 m/s, and under ITTF a rally ball that
   clips the net and lands legally is a good shot. A cord that genuinely kills the ball still
   decides the point, via the own-half or floor rule a moment later. Service lets are a separate
   rule and belong with serving, which is not built.
4. **The player's spin gesture works.** `ShotAssist` now computes a `brush` term from the depth
   axis plus the (newly live) vertical one, instead of reading the always-zero `swing.y()`.
   Measured: a hard pull-back gives 2.6 rev/s of backspin, a still blade 14, a hard drive 43 —
   it was a flat 14 in every case before.
5. **The player can lose a point.** `ShotAssist` grades each contact on where it landed on the
   blade (`quality`) and blends the authored shot against the raw bounce in proportion. A
   centred contact still lands where aimed; a contact off the rim gets mostly real physics and
   usually dies. The rescue no longer runs below `rescueQualityFloor`. Measured tolerance before
   the ball goes out: 0.6 of the blade radius against a 5 m/s ball, 0.4 at 12 m/s, 0.3 at 18.
   **Only the player is graded** — grading `Follower` made it shank ordinary feeds into the net,
   which reads as broken rather than beatable. See the comment at the `assist` computation.
6. **A brush modifier exists.** Holding the right mouse button switches the cursor's Y axis from
   depth to blade height (`PlayerReach.clampBrushed`), freezing depth while held. This is modal,
   not simultaneous — which is the distinction that makes it legitimate rather than a return of
   the two-meanings-on-one-axis bug. `PlayerReach.clamp` is untouched, so the "no aim at
   any height leaves the hitting plane" invariant still holds literally for normal play, and is
   still checked. The downward half of the band stops at `BLADE_R` so the bat cannot cut through
   the table top.

## Known open defects

Do not "rediscover" these, and do not undo a fix for them.

1. **The opponent still follows rather than predicts.** It is now losable *to*, because the
   player can miss, but it never chooses a shot. Prediction is the next piece of AI work.
2. **`TOP` and `HIGH` cannot address the whole depth envelope** (z ≈ 1.51 and 1.16 against
   `Z_FAR = 2.40`). Left alone deliberately — they are inspection views, not ones a rally is
   played from. Both rally views now reach 2.400, measured through the real rig.

### Retracted — do NOT re-report these

**"Aim is asymmetric, right swipes have 4× the authority of left."** Not real. It was an
artefact of the probe that found it, and the artefact is worth knowing because it is easy to
reproduce by accident: if a probe calls `ShotAssist.assist` directly with a blade that has been
`moveTo`'d sideways, the blade centre travels `swipe × dt` while the ball stays put, so a 10 m/s
swipe puts the contact 8.3 cm off a 7.5 cm blade radius and registers as a rim hit. Place the
ball at `racket.pos()` *after* the move. Measured correctly, the aim is exactly symmetric and
monotone: ±2.5 m/s of swipe lands at ±0.098 m, ±5 at ±0.220, ±10 at ±0.398.

---

## Conventions

- Java 21, 4-space indent.
- **Comments stay short and explain *why*, not what.** One or two lines: the constraint, the
  citation or `TUNED` reasoning, the thing that breaks if this is changed carelessly. The full
  derivation, measurement tables and rejected alternatives belong in `docs/DESIGN.md`, not in the
  source — check there before re-deriving something that already has a measured answer.
- **Cite a source for every real-world number.** A bare constant with no citation in
  `config/Physical` is a bug.
- Anything tuned by eye is labelled `TUNED` and says what it stands in for.
- Every tuning knob for the arcade layer lives in `config/ShotTuning`. Nothing below it is
  hardcoded. Measured physical values stay single-sourced in `config/Physical`.
