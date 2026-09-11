# AGENTS.md

Operating guide for coding agents (Codex, Claude Code) working in this repository.

`CLAUDE.md` is the full project history and rationale — ~51 KB, and the authority when the two
disagree. **This file is the short operational contract**: what you must not break, how to build,
and how to prove you did not break it. Read `CLAUDE.md` when you need the *why* behind a rule.

---

## What this is

A 3D table tennis game in Java 21 + JavaFX. Mouse-controlled paddle, a ball carrying real spin,
and an AI opponent. Graded coursework — a validated physics engine (`physics/`) with an arcade
game layer (`play/`) on top of it.

---

## Build and run

There is **no build system** and no dependencies. Stock `javac` only. Do not introduce Maven or
Gradle — the grader runs this from IntelliJ.

The JDK must be **Liberica "Full" JDK 21**, which ships JavaFX as system modules. With it there is
no `--module-path` and no `--add-modules`. A plain JDK has no JavaFX and fails at launch.

```bash
# Git Bash — the path that actually exists on this machine
JDK=~/.jdks/jdk-21.0.12.1-full/bin
"$JDK/javac" -d out/production/3D-Table-Tennis-In-Java $(find src -name '*.java')
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java Table_Tennis_In_3D
```

```powershell
# PowerShell
$JDK = "$env:USERPROFILE\.jdks\jdk-21.0.12.1-full\bin"
& "$JDK\javac" -d out\production\3D-Table-Tennis-In-Java (Get-ChildItem -Recurse src -Filter *.java).FullName
```

> **Note:** `CLAUDE.md` cites `~/jdk/jdk-21.0.7-full`. That path **no longer exists**. The real JDK
> is `~/.jdks/jdk-21.0.12.1-full`, which is what the committed IntelliJ run configuration uses.

Never commit build output. `out/` is git-ignored; do not add new build directories to the repo.

---

## Validation — not optional

Two headless suites. Both print PASS/FAIL per check and exit 0/1. **They must stay at 100%.**

```bash
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java physics.SelfTest   # 101 checks
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java play.RallyTest     #  15 checks
```

- Touched anything in `src/physics/` → run `physics.SelfTest`.
- Touched anything in `src/play/` → run `play.RallyTest`.
- Touched rendering only → still compile, and run both if you changed anything shared.

A failing check is a broken deliverable, not a flaky test. **Never widen a threshold to make a
regression fit.** Re-deriving a threshold because the model got more accurate is legitimate — say
why in a comment, with the number the new model predicts. That comment is the only thing that
tells those two cases apart later.

Adding a check is one `check("falsifiable claim", boolean, "the measured number")` call plus one
line in `main`. The detail string prints on **pass as well as fail**, so it must state what was
actually measured.

---

## Architecture and the rules that keep it from rotting

```
src/
  Table_Tennis_In_3D.java   entry point; fixed-timestep loop, input, wiring, capture mode
  physics/                  plain Java. NO javafx imports, ever.
  play/                     game logic. Plain Java, so it tests headlessly.
  render/                   JavaFX views. Reads physics state; never writes it.
```

**Dependency direction is one-way:** `play` → `physics`; `physics` → nothing. A test needing both
belongs in `play`.

Hard invariants — breaking any of these is a defect even if it compiles and the suites pass:

1. **`src/physics/` must never import `javafx.*`.** It stays headless and frame-rate independent.
2. **`render.Xform` is the ONLY place physics space becomes scene space**, in both directions.
   Nothing else may multiply or divide by `SPM`.
3. **`render/` reads physics state, never writes it.**
4. **One collision solver.** Table, net, floor and both paddles differ only by a `Material` and a
   shape. Do not add a second bespoke bounce path. Shape hides behind `Collider`'s four questions.
5. **The solver works in the SURFACE's frame, not the world's.** Written in absolute velocity, a
   blade swung into a ball reads as "already separating" and does nothing. If a paddle ever passes
   through a ball harmlessly, this is what broke.
6. **`World.predict` must never see a paddle.** A prediction that gets intercepted predicts nothing.
7. **The ball never moves the player's paddle.** `Stroke.advance` does not take a `BallState` — the
   rule is enforced by the signature. Anything that reads the ball *before* the blade's target is
   chosen is auto-follow wearing a different hat.
8. **Physics never sees the frame time.** Passing a JavaFX `dt` into `physics/` breaks determinism.
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

**Editing `.idea/` with IntelliJ open.** A running IDE rewrites those files underneath you. Edit
them with IntelliJ closed and check `git status` afterwards. `modules.xml`, the `.iml`, `misc.xml`
and `runConfigurations/` are all committed and load-bearing; `workspace.xml` is local and ignored.
For a module file stored in `.idea/`, `$MODULE_DIR$` resolves to the PROJECT directory — do not
"correct" it to `$MODULE_DIR$/..` or every Run button greys out.

---

## Fixed on 2026-09-11 — do not undo these

1. **Scoring exists.** `play/Scoreboard.java` keeps the ITTF rules (11, win by 2, no ceiling at
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
   the Sep 4 two-meanings-on-one-axis bug. `PlayerReach.clamp` is untouched, so the "no aim at
   any height leaves the hitting plane" invariant still holds literally for normal play, and is
   still checked. The downward half of the band stops at `BLADE_R` so the bat cannot cut through
   the table top.

## Known open defects (verified 2026-09-11, not yet fixed)

Do not "rediscover" these, and do not undo a fix for them.

1. **Aim is strongly asymmetric.** A +8 m/s swipe lands the ball at x = +0.511; an identical −8 m/s
   swipe reaches only x = −0.103. Right swipes have roughly 4× the authority of left ones.
2. **The camera, not `PlayerReach`, sets the usable envelope.** `RallyTest` never calls `MouseAim`;
   it feeds world coordinates straight into `PlayerReach.clamp`. Swept through the real rig, the
   `RALLY_IN` and `TOP` views can only reach z ≈ 1.49 against `Z_FAR = 2.40`, giving 90–94 ms
   touch windows — worse than the original reported bug.
3. **`MouseAim` freezes when the camera eye drops below the hitting plane.** `MouseAim.java:106`
   returns the caller's fallback for every descending ray, so the blade stops responding across
   roughly half the viewport until the player orbits back up.
4. **`Stroke`'s face never decays.** The blade keeps whatever lean its last positioning move gave
   it, indefinitely — measured, a 0.96 swing in face normal Y from the approach direction alone.
   Retreating to cover a deep ball locks the face open into a chop.
5. **The opponent still follows rather than predicts,** and its returns are near-identical
   (4.6–7.4 m/s, landing z +0.70…+1.00). It is now losable *to*, because the player can miss,
   but it never attacks. Difficulty has to come from prediction and shot selection — the
   October milestone.

---

## Conventions

- Java 21, 4-space indent.
- **Comments explain *why*, not what.** Match the density of the surrounding code — this codebase
  documents the reasoning behind a choice and the alternative that was rejected.
- **Cite a source for every real-world number.** A bare constant with no citation in `physics/` is
  a bug.
- Anything tuned by eye is labelled `TUNED` and says what it stands in for.
- Every tuning knob for the arcade layer lives in `ShotAssist.Tuning`. Nothing below it is
  hardcoded. Measured physical values stay single-sourced in `physics/Constants`.
