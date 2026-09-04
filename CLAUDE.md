# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A 3D table tennis game — mouse-controlled paddle, spin/physics simulation, AI opponent. `README.md` is
the player-facing README: what the game is, controls, how to run it. Keep it free of coursework framing —
the assignment contract, milestones, and dates live here instead.

## Coursework context

CS Independent Study, Term 1. Student: Chaojie Chen. This is graded schoolwork with a submitted contract,
so the milestones below are commitments with dates, not a wishlist.

**Deliverable:** a finished 3D ping pong game with good graphics and player control.

**Milestones**

- [x] **Physics demo** — *Aug 27*. Ball flying with spin, curving in the air, bouncing off table and net.
      Not a game yet, just physics on screen. **Delivered and in the repo**: `src/MrPong.java`,
      `src/physics/`, `src/render/`, validated by `physics.SelfTest` (68 checks).
- [ ] **Playable demo** — *Sep 17*. A full point against the AI: mouse control, spin, serving, scoring.
      **Part-built.** The physics is done and validated (`SelfTest` 101, `RallyTest` 7). Both
      rackets are wired into `MrPong`, the near one follows the mouse and *only* the mouse
      (see the Sep 4 entry below), and the game opens on a gentle serve with a two-view
      rally-cam at 0.45x. What holds a rally together is `play/ShotAssist` (below). Scoring is
      not started.

      *Design change (Sep 2):* two of them, and both move away from the contract's realistic
      model. (1) The charge-and-release stroke was pulled — the paddle just follows the cursor.
      (2) After any racket contact the raw impulse result is run through `ShotAssist`, which
      constrains the outgoing trajectory to a playable area (arcade "assist", à la Ping Pong
      Fury) — otherwise a fast brush sends the ball off the end of the room and no rally
      survives. **The realistic solver in `physics/` is unchanged and still what `SelfTest`
      grades; `ShotAssist` is a `play/` layer on top and can be switched off.** Which one to
      demo depends on what the grade weights — the physics work, or a game you can play.
- [ ] **Final demo** — *Oct 7*. Menus, AI opponent, and multiple paddles that play differently.

**Scope — core:** mouse-controlled paddle with the ball reflecting off it; real physics (spin, shot speed
from power applied); collision and rules (table, net, out of bounds); a basic AI good enough to play a
full point against; serving and scoring.

**Scope — stretch:** multiple paddles with different characteristics (more spin vs. more power); currency
earned by beating the AI, spent in a shop.

The contract lists the extras (paddle shop, currency, multiple paddles) as *additional* features for
Oct 7. Do not build them early at the cost of a checkpoint. Physics correctness is the graded core —
everything else is presentation.

**Learning goals** (what the student is here to learn — prefer explaining the reasoning over handing over
finished code):

| Area | Specifically |
| --- | --- |
| **Physics** | How a spinning ball moves through air, what happens to spin on table/paddle contact, and keeping the simulation stable over long runs. |
| **JavaFX** | The 3D scene graph — meshes, materials, camera, lighting — and a game loop on `AnimationTimer` that runs the same on fast and slow machines. |
| **AI** | Getting the opponent to predict where the ball is going rather than following it. |

## Stack

- **Java 21** — the IntelliJ project JDK is `liberica-21` (`.idea/misc.xml`), which resolves to
  `~/jdk/jdk-21.0.7-full` (literally `C:\Users\776471\jdk\jdk-21.0.7-full`). The `java` on PATH is
  22.0.1 and has **no JavaFX**, so a command-line build must name the Liberica JDK explicitly.
- **JavaFX 21 is bundled in that JDK.** It is the BellSoft Liberica **Full** distribution, which ships
  `javafx.base`, `.controls`, `.fxml`, `.graphics`, `.media`, `.swing` and `.web` as system modules. So
  there is **no `--module-path`, no `--add-modules`, and no Maven/Gradle dependency** — plain
  `javac`/`java` on the classpath works. The plain (non-Full) Liberica JDK has no JavaFX and fails at
  launch; if the project SDK ever gets switched to one, that is the cause.
- **JavaFX for all rendering and input** — the 3D scene graph (`Group`, `Box`/`Sphere`/`Cylinder`,
  `PhongMaterial`, `PerspectiveCamera`, lights), `AnimationTimer` for the game loop, `Scene` mouse events
  for paddle control. Do not reach for Swing/AWT or Java2D. (`javafx.swing` appears in exactly one place:
  `SwingFXUtils`, for PNG encoding in `MrPong`'s offline capture mode.)
- **No external dependencies.** Everything must build with stock `javac`. Do not introduce Maven or
  Gradle without asking — the grader runs it from IntelliJ.

## Build & run

There is deliberately no build system: the Full JDK removes the JavaFX module-path problem that would
otherwise have forced one. IntelliJ has two committed run configurations, **MrPong** and
**Physics SelfTest**. The project SDK must stay `liberica-21`.

> **`play.RallyTest` has no run configuration yet.** It is a second headless main and it needs a
> third `.idea/runConfigurations/` entry, same shape as the other two, or it is invisible from the
> IDE — which is the only place this project actually gets run. Write it with IntelliJ **closed**.

### The IntelliJ project files — `$MODULE_DIR$` is not what it looks like

Four files under `.idea/` are load-bearing and are all committed: `modules.xml` (registers the module —
if it goes missing there is no module at all and every Run button greys out), the module file
`3D-Table-Tennis-In-Java.iml`, `misc.xml` (pins the SDK and the `out/` output dir), and
`runConfigurations/`. Only `workspace.xml` is local and ignored.

For a module file stored inside `.idea/`, **IntelliJ resolves `$MODULE_DIR$` to the PROJECT directory,
not to `.idea/`.** So the content root is written as plain `$MODULE_DIR$` and the source root as
`$MODULE_DIR$/src`:

```xml
<content url="file://$MODULE_DIR$">
  <sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false" />
</content>
```

Do not "correct" those to `$MODULE_DIR$/..`. That resolves to the *parent* of this repo, which makes
every sibling project a part of this one and points the source root at a `src` that does not exist. With
no source root IntelliJ cannot see `MrPong` as a main class and **the Run button goes grey** — that is
the symptom to recognise if this file is ever edited.

The module name comes from the `.iml` filename and must stay `3D-Table-Tennis-In-Java`, because both run
configurations name it in their `<module>` element.

Note that a running IntelliJ rewrites `.idea/` underneath you. Edit these files with the IDE closed, and
check `git status` afterwards.

From a shell — PowerShell:

```powershell
$JDK = "$env:USERPROFILE\jdk\jdk-21.0.7-full\bin"
& "$JDK\javac" -d out\production\3D-Table-Tennis-In-Java (Get-ChildItem -Recurse src -Filter *.java).FullName
& "$JDK\java" -cp out\production\3D-Table-Tennis-In-Java MrPong
& "$JDK\java" -cp out\production\3D-Table-Tennis-In-Java physics.SelfTest
& "$JDK\java" -cp out\production\3D-Table-Tennis-In-Java play.RallyTest
```

bash (Git Bash) — note the different glob and the `;` → newline:

```bash
JDK=~/jdk/jdk-21.0.7-full/bin
"$JDK/javac" -d out/production/3D-Table-Tennis-In-Java $(find src -name '*.java')
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java MrPong
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java physics.SelfTest
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java play.RallyTest
```

**Always run `physics.SelfTest` after touching anything under `src/physics/`,** and
**`play.RallyTest` after touching anything under `src/play/`.** Both are headless, print PASS/FAIL per
check, and exit 0/1. They anchor the model against published and ITTF numbers rather than against
itself, so they catch the changes that still look plausible on screen. They must stay at 100% — a
failing check is a broken deliverable, not a flaky test.

There is no framework: a check is one `check("prose claim", boolean, "the measured number")` call and
one line added to `main`. Two house rules worth keeping. The claim is written as a **falsifiable
sentence**, not a method name. And the detail string prints **on pass as well as fail**, so it has to
state what was actually measured — a PASS reading "(it clipped the cord)" contradicts its own result.

**When a threshold has to move, say why in a comment, with the number the new model predicts.**
Re-deriving a threshold because the model got more accurate is legitimate; widening one until a
regression fits is not, and the comment is the only thing that tells those two apart later.

`MrPong` also has an offline capture mode, for checking the rendering without a human watching:

```
java -cp out/... MrPong "--shot=Topspin loop" --at=0.18 --view=SIDE --ball2x=true --out=frame.png
```

`--shot` takes any name from `Shots.ALL` (quote it — they contain spaces), `--view` any name from
`render.CameraRig.View` (`BEHIND`, `SIDE`, `HIGH`, `LOW`, `TOP`). `--at` is in simulated seconds.
Passing `--out` disables auto-replay, so an `--at` past the end of a rally still resolves instead of
hanging.

## Coordinate conventions — get these wrong and everything looks haunted

**Physics space** — SI units, metres, **right-handed**:

- origin = the centre of the table **surface**
- `+X` right (across the table), `+Y` up, `+Z` toward the near end / the camera
- near end (player) `z = +1.37`, far end (AI) `z = -1.37`, floor `y = -0.76`

Right-handedness matters: every cross product (Magnus `ω × v`, the contact impulses) assumes it. Do not
"fix" a sign by flipping an axis — fix the physics.

**Scene space** — JavaFX, left-handed, `+Y` is *down*. The conversion lives in exactly one place,
`render.Xform`:

```
sceneX = +x * SPM      SPM = 300 scene units per metre
sceneY = -y * SPM      (the Y flip)
sceneZ = -z * SPM      (JavaFX +Z goes into the screen)
```

`SPM` exists because JavaFX's default camera clip planes and its light attenuation both fall apart at
metre-scale coordinates. **Never do physics in scene units, and never do a cross product in scene
space.** Convert at the boundary and nowhere else.

## Architecture

```
src/
  MrPong.java            entry point; fixed-timestep loop, input, wiring, capture mode
  physics/                 no javafx imports, ever
    Vec3.java            immutable 3D vector (record)
    Quat.java            unit quaternion; carries the ball's VISUAL orientation only
    Constants.java       every real-world number, each with its source cited
    BallState.java       position, velocity, spin, orientation
    Aero.java            gravity + drag + Magnus + spin decay -> derivative
    Integrator.java      RK4 over Aero, fixed dt
    Collider.java        the 4 questions the solver asks a surface (sealed: Box, Blade)
    Contacts.java        ONE impulse solver, for every surface including a moving paddle
    Paddle.java          kinematic racket + its disc-slab Blade collider
    World.java           owns the state, steps it, emits events, predicts trajectories
    Aim.java             solves launch angle for a target; also builds spin vectors
    Shots.java           named launch presets, defined by intent and solved by Aim
    SelfTest.java        headless validation vs. published numbers
  play/                    game logic; plain Java, no javafx, so it can be tested headlessly
    Stroke.java          the player's paddle: follows the cursor, and nothing else
    ShotAssist.java      arcade shot model: turns any racket contact into a playable shot
    Opponent.java        interface: look at the ball, move the blade, swing
    Follower.java        the current opponent - tracks the ball, unbeatable, does NOT predict
    RallyTest.java       headless validation of the opponent (second main, own run config)
  render/
    Xform.java           the ONLY physics<->scene conversion, now both directions
    MouseAim.java        cursor -> ray -> point on the player's REACH SURFACE (depth included)
    Court.java           table, net (real mesh), floor, legs, ITTF markings
    BallView.java        ball + procedural chequer texture that makes spin visible
    PaddleView.java      blade, red/black rubber, handle
    Trail.java           fading dot trail; used for both the live path and the ghost
    BounceMarks.java     discs left where the ball landed
    CameraRig.java       two-view rally-cam (default) + orbit/zoom + preset views
    ShotDebug.java       V-toggled overlay: racket + ball velocity, raw / intended / final
                         shot, target, predicted landing, legal target box
    Hud.java             key legend, feed name, and the shot-assist readout while V is on
```

**Two packages, one dependency direction.** `play` depends on `physics`; `physics` depends on
nothing. That is why the opponent's checks live in `play/RallyTest.java` and not in
`physics/SelfTest.java` — importing `play.Follower` from `physics` would invert it. If a new test
needs both, it belongs in `play`.

Rules that keep this from rotting:

- **`src/physics/` must not import anything from `javafx.*`.** It is plain Java so the simulation can be
  checked headlessly and so it can never accidentally depend on the frame rate or on the renderer.
  `SelfTest` enforces this by existing.
- **`src/render/` reads physics state; it never writes it.** Physics state is plain math on vectors;
  JavaFX nodes are a *view* of that state, updated once per frame.
- **Shot presets state intent** (speed, spin, target) and let `Aim` solve the launch angle. Do not
  hard-code launch velocities: it was tried, and with drag this strong almost every hand-picked shot
  sailed off the end of the table.
- **One collision solver, still.** Table, net, floor and *both paddles* differ only by a `Material`
  and a shape — do not add a second bespoke bounce path. The net has to steal spin by the same rules
  the table uses to make it, and the paddle has to put spin on by them too.
  - Shape is behind `Collider`: `closestPoint`, `escapeNormal`, `sweep`, `velocityAt`. Those are the
    only four questions `Contacts` asks. A new shape answers them; it does not touch the impulse.
  - **The solver works in the SURFACE's frame, not the world's.** Every velocity in `applyImpulse`
    is relative to `velocityAt(contact)`. This is not a refinement — written in absolute velocity, a
    blade swung into a ball reads as "already separating" and does nothing at all, and a brushing
    stroke generates exactly zero spin. If you ever see a paddle that passes through the ball
    harmlessly, this is what broke.
  - Contacts resolve **earliest-first**, not in list order. With a paddle that can be over the table,
    "first in the list" and "the one it actually hit first" stop being the same answer.
  - A swept contact **flies the rest of the step** afterwards, and bounces the velocity the ball had
    *at impact*, not at the end of the step. Skipping either hands the ball free energy every bounce.
- **The ball never moves the player's paddle.** `Stroke` is handed a cursor point and a
  timestep, and that is all — deliberately not a `BallState`, so the rule is enforced by the
  signature rather than by discipline. Ball position, velocity and `World.predict` are for the
  opponent, the renderer and the assist; none of them may reach the near blade. The blade DOES
  move in depth -- it reaches in over the table -- but that depth comes from the cursor's aim
  ray in `render/MouseAim`, which is geometry the player drives. That is the only shape a reach
  may ever take here.
- **Spin is core, not a bonus.** Magnus in flight, and spin transfer on table/paddle/net contact. It
  shapes the collision code, so design for it up front rather than bolting it on.
- **The AI predicts, it does not follow — but the one in the repo right now follows.**
  `play/Follower.java` tracks the ball's *current* position and is unbeatable by moving faster than a
  person can. That is a deliberate, requested placeholder, not an oversight, and it is behind the
  `Opponent` interface so the predicting version is a second class rather than a rewrite. The real
  one still owes `World.predict`, which stays headless and side-effect free for exactly that.
  Note the limitation: a follower **cannot** be made beatable by slowing it down, it just gets
  erratic. Difficulty has to come from prediction quality — which is the argument for Oct 7.
- **`World.predict` must never see a paddle.** It builds a private `World` to fly a trajectory
  forward; a prediction that gets intercepted is a prediction of nothing. `setPaddles` is opt-in and
  `predict` leaves them null.
- **Nothing outside `Xform` may divide by `SPM`.** The conversion now runs both ways (`toPhysics`
  exists so the mouse can drive a paddle), which makes it *more* tempting to do it inline, not less.

## The game loop

Gaffer On Games, *Fix Your Timestep!*: an accumulator with a fixed `DT = 1/480 s`, RK4 per step, and
rendering that interpolates between the last two states. Consequences to respect:

- **Physics never sees the frame time.** If you find yourself passing a JavaFX `dt` into anything under
  `physics/`, you have broken determinism.
- The accumulator is clamped (`MAX_FRAME`) so a stall cannot spiral into a death loop.
- Rendering lerps position and slerps orientation. Do not snap to the raw state.
- `DT` is 1/480 s, far smaller than a display frame, so a 30 m/s smash moves 6 cm per step and cannot
  tunnel through the 2.5 cm table slab.
- Anything sampled per-step for display (the trail) counts **whole physics steps**, not seconds. A
  duration that is not an exact multiple of `DT` gets rounded differently by different callers.

## Physics model (and where the numbers came from)

All constants live in `physics/Constants.java` with per-value citations. Summary:

- **Ball** 40 mm, 2.7 g, hollow shell → `I = (2/3)mr²`. The 2/3 (not the solid sphere's 2/5) is why the
  grip impulse is `-(2/5)m·v_contact` and not `-(2/7)m·v_contact` — it changes how much spin a bounce
  can generate by about 40%.
- **Drag** `a = -½ρA·C_d·|v|v / m`, with `C_d` **measured, not constant** — a table over speed and spin
  ratio, 0.47–0.55 across the playing range. At 10 m/s that is 13.7 m/s², *more* than gravity: air,
  not gravity, dominates a table tennis trajectory. The old flat `C_d = 0.40` was below every
  published table-tennis figure and under-dragged the ball by ~20%.
- **Magnus** `a = ½ρA·C_L·|v|² · unit(ω × v) / m`. `C_L` comes from a **measured** piecewise fit,
  converted from the literature's volume convention at one boundary by `C_L = (8/3)·C_M·S`. Drag and
  lift still share one `½ρA/m` factor; keep it that way.
  - The old `C_L = S/(2S+1)` was replaced because it is **monotonic and reality is not.** Real `C_L`
    has a *valley* near `S ≈ 0.5–0.8` (the "lift crisis", Miyazaki et al. 2017), and normal play spans
    `S ≈ 0.1–1.4`, so a rally crosses it constantly. The old curve was ~15% too weak below S = 0.5 and
    up to **1.9× too strong** at S ≈ 0.8. Do not "simplify" this back to a formula: no monotonic
    function can represent a valley, and `SelfTest` asserts the valley exists.
- **Spin decay** `dω/dt = -k·ω·|v|`, per metre rather than per second. Still `TUNED` — no
  table-tennis-specific time constant exists in the literature, and sources disagree on whether spin
  decays in flight at all. The value is pinned so it reproduces the previous 5%/s at 12 m/s, so the
  fix is to the *shape*, not the magnitude.
- **Bounces** velocity-dependent normal restitution (`e = 0.98 − 0.02·|v_n|`, clamped — thin-shell cap
  buckling above ~5 m/s is real and measured), then either grip or a Coulomb slide, whichever the
  friction cone allows. This is what turns topspin into a low fast kick and backspin into a checked,
  dead ball — it is the whole point, and none of it is scripted.
- **Rubber** adds a *tangential* restitution `e_t ≈ 0.82`: the topsheet stores tangential energy and
  springs it back. That, and only that, is what **reverses** incoming spin rather than merely
  absorbing it. A grip-or-slide model provably cannot do it, and `SelfTest` has the negative control
  proving so. Perfect grip is just the `e_t = 0` case, so this is still one solver.
- **Collisions are swept**, not overlap-only. At 60 m/s the ball moves 12.5 cm per step against a 6.5 cm
  crossing, so an overlap test alone drops the hardest shots straight through the table.

What `SelfTest` anchors against (**101 checks**, +4 for the Serve preset): terminal velocity vs. the closed form; free fall vs.
the exact `tanh`/`ln cosh` solution to 1 mm over 3 s; the ITTF drop test; RK4 convergence order;
`C_d` and `C_L` vs. measured values; the lift crisis; Magnus direction for top, back and side spin;
no contact ever adding energy; topspin kicking forward and backspin checking off the bounce; the net
killing a ball; in/out detection; every preset shot being legal; 10 simulated minutes of stability;
no tunnelling from 20 to 60 m/s; and the whole paddle group below.

`play.RallyTest` (**7 checks**) is separate and covers the game logic: the opponent reaches every
shot fed at it, returns every one over the net, **lands every one on the table**, never sends the
ball out faster than the impulse allows, and never launches it out of the hall; and a thrown mouse
cannot move the player's blade faster than `Stroke.TRACK_SPEED`, which itself sits below a real
swing.

The landing check is new, and the reason it can exist now is worth keeping straight. It used to
be a printout with a long comment explaining why it could not be a check: the follower's RAW
return cleared the net every time and put ONE of ten on the table, and a sweep of its face angle,
swing speed and lift proved no fixed stroke could do both — settings that land three of ten
cannot return all ten. That is still true of the raw stroke. What changed is that `MrPong` runs
every contact, the follower's included, through `ShotAssist`, so **grading the raw return was
grading a code path the game no longer takes.** `RallyTest` now feeds contacts through the assist
exactly as the game does, and the answer went from 1 of 10 to **10 of 10**. The one number still
taken from before the assist is the raw outgoing speed, because that is what the "does not cheat"
check is actually about. This makes the follower *legal*, not intelligent — it still tracks the
ball rather than reading it, and October still owes a predicting opponent.

**Three things in `SelfTest` are load-bearing and easy to wreck by accident:**

- **The two closed-form checks are pinned to `Aero.DragModel.constant(C_DRAG)`.** `v_t = √(g/kC_d)`
  and the `tanh`/`ln cosh` solution *only exist for constant `C_d`*, and the free-fall one is the
  only check in the suite testing RK4 against real analysis. `C_DRAG = 0.40` still exists **solely**
  for them — it is not what the game flies with. Do not "tidy it away".
- **Interpolation between table entries is smootherstep, not linear, for a numerical reason.** Linear
  interpolation puts a kink in the force field at every node, and RK4 only reaches 4th order on a
  smooth right-hand side. With linear interpolation the convergence check measured 30× error
  reduction for a 4× smaller step; with smootherstep it measures 255× against a theoretical 256×.
- **The paddle checks fail against any solver that works in absolute velocity.** That is their point.

**Two published claims genuinely conflict, and the code records both rather than picking quietly.**
A terminal velocity of 9.0–9.6 m/s implies `C_d ≈ 0.40`; the measured `C_d ≈ 0.47–0.55` implies
8.3–8.5 m/s. They cannot both hold. The measured coefficient wins (table-tennis-specific; experiment,
CFD and a 277-match fit agree), and the check says so in its own failure text.

**One published number did not survive checking and must not be copied back in.** The racket paper's
tangential stiffness `k_p ≈ 0.019` implies a tangential restitution of 16.6 — the contact patch
leaving sixteen times faster than it arrived. Working back from the same paper's own `e_t = 0.819`
gives `k_p ≈ 0.0019`: a factor of ten. The model uses `e_t`, which is dimensionless and cannot hide
a units error like that. The arithmetic is written out in `Constants.RACKET_MAT`.

## Where this is up to (as of Sep 2) — the game turned into assisted arcade

The validated physics engine is **still there and still green** (SelfTest 101 + RallyTest 6 —
the +4 SelfTest checks are the new `Serve` preset's own legality). But the game on top of it
is now deliberately **arcade, not realistic**, because a rally you can actually keep needs it.

**The pivot: `play/ShotAssist.java`.** After the impulse solver resolves a paddle contact,
`MrPong.advanceOne()` hands the raw result to `ShotAssist`, which turns it into an authored
shot in one direction only: **solve → constrain → validate → correct.** Nothing is mutated
after its last check, which is the rule the first version broke.

1. **Intent.** Split the racket's motion into a **forward drive** (toward the opponent — a
   still blade dinks, a driving blade hits), a **sideways swipe** (across the table; for the
   player this is only the cursor, so *swipe right, ball goes right*) and a **lift**. Read the
   blade's tilt and where on the face the ball struck as smaller aim contributions.
2. **Target.** Build a point inside the opponent's court *by construction* — lateral from the
   swipe, depth from the drive and lift — so the aim can never be absurd. `targetHalfWidth()`
   / `targetNearDepth()` / `targetFarDepth()` expose the box, and the `V` overlay outlines it.
3. **Strength.** Map the drive onto a fixed 7–13 m/s band through a saturating curve. It is
   never `incoming + racket`, which is what stops a rally compounding.
4. **Solve.** Ask `Aim` — the same solver the presets use — for the launch that LANDS there.
5. **Constrain.** Minimum forward pace, a lateral cone *and* an absolute lateral cap, an
   elevation band, a speed cap. About 6% of a hard-capped raw bounce is blended in for feel.
6. **Validate.** Fly the finished velocity forward, contact-free, and measure its net height
   and landing point. If it clears the cord and lands in, done.
7. **Correct.** Otherwise try again: a spread of speeds around the one the swing asked for,
   then targets pulled toward the middle. The winner is the *legal* candidate closest to the
   player's intent. If nothing legal is found at all, a wider **rescue** search re-aims down
   the middle at anything from 3 m/s up — because some contacts have no fast answer, and a
   ball met right at the net can only be lifted softly over. That path is flagged in the
   overlay so it is visible rather than mysterious.

Then `world.setState(...)` swaps it in. **Both rackets** go through it, so the follower's returns
land too. `World.setState` is the only new `physics/` code — one setter, never called by SelfTest
or `predict`, so the model underneath is untouched. The player's blade is still moved by the raw
solver at contact; the *outgoing* trajectory is what gets civilised.

**Every tuning number lives in `ShotAssist.Tuning`**, a public nested class with a field per
knob — speeds, influences, clamps, the target box, the correction and rescue searches, spin.
Nothing is hardcoded below it. (Restitution and friction are deliberately NOT duplicated there:
they are measured values with citations, single-sourced in `physics/Constants`, and SelfTest
grades them.)

**What it measurably fixed.** `Sweep` (scratch) fires a realistic incoming ball at the blade
over a 75-point grid of racket velocities and flies each result to its landing:

| | lands on the table | worst sideways landing | fastest launch |
| --- | --- | --- | --- |
| raw impulse alone | 11 / 75 | 2.37 m (half-width is 0.76) | 24.5 m/s |
| through `ShotAssist` | **75 / 75** | 0.38 m | 12.4 m/s |

and the controls now separate: drive 0 / 3 / 6 / 10 / 14 m/s gives 6.9 / 7.9 / 8.9 / 9.9 /
12.4 m/s of shot and depth from 0.66 m to 1.07 m; an 8 m/s swipe moves the landing 0.38 m to
that side; twenty hard drives in a row stay between 9.9 and 10.0 m/s. Before the rewrite the
racket's vertical motion did nothing at all and drives of 10 and 14 were identical.

**Three measurement bugs were found and fixed while doing this, and they matter more than the
tuning did.** All three came from asking a `World` — which has a table in it — where a shot
lands. The table *bounces the ball out of the way* before the descent can be detected, so the
first crossing reported is the SECOND descent, out past the end line. It made `Sweep` read
0/75 when the real answer was 75/75, and made `RallyTest` call six good returns "long". The
landing question has to be asked of a contact-free flight (`Aim.landingPoint`), and the code
now says so in three places.

**The one-bounce rule.** `MrPong` enforces ITTF's "return only after it has bounced on your
side" by handing `World` a null racket for whoever may not hit yet — the blade still tracks the
ball on screen, it just phases through. A table bounce opens the receiver's racket; a *second*
bounce on the same side, a ball back on the hitter's own half, the net, or a ball past the end
line calls `endPoint()`, which cuts to the next serve after `POINT_END_DELAY` (0.9 s) rather
than waiting for the ball to trickle to a stop. There is no score counter yet — a point just
resets the rally.

One subtlety in there, `CONTACT_BOUNCE_WINDOW`: a ball may legally be struck while it is still
touching the table (a push dug out at surface height is a real shot, and the blade reaches in
over the table for it). The table contact then fires on the *same physics step* as the racket
contact, and the rule above reads it as "your own shot bounced on your own half" and ends the
point on a perfectly legal stroke. It killed every four-hit rally in the headless trace. A
bounce within two steps of a racket hit is that contact's own table touch and is not a rally
event — but the serial still has to advance, or the next real bounce is compared to a stale one.

This is a real departure from the contract's "real physics (spin, shot speed from power applied)".
The realistic solver is intact and could be switched back to (drop the `ShotAssist` call). Keeping
both is the point — the graded physics work stands on its own in `physics/` + `SelfTest`, and the
playable game is a `play/` layer on top. **If the grade cares more about the realistic model, that
is the thing to demo; if it cares about a playable game, this is.**

Tuning lives entirely in `ShotAssist.Tuning`. `play.Trace` (scratch) rallies a brain-dead autoplay
bot — it lunges to every ball and parks, the worst case — against the follower. It used to manage
about 3 exchanges per point; it now runs to the 40 s timeout on **every** feed (96 exchanges a
point on Serve, Flat drive, Topspin loop and Heavy backspin push alike). A human who lets the ball
come and drives through it does better still.

**`V` toggles `ShotDebug`**, and it draws the whole decision now: the racket's own velocity
(yellow), the incoming ball's (white), the raw physical reflection (grey), the intended shot
(cyan), the final constrained shot (orange, length = speed), the target (magenta dot), the
predicted landing (green dot) and the legal target box (blue outline), with speed / spin /
correction passes / legal-or-rescued printed on the HUD. Reading it: magenta and green apart
means the solve did not land where it aimed; cyan and orange apart means a clamp is fighting the
solve; a green dot off the table means the rescue is in play.

The `Follower` also picked up a fix this pass, and it is the one that unblocked the rally. It
waited on `PLANE_Z` for every ball, so a soft return that landed short simply fell below the
blade before it arrived — measured: a 4.3 m/s push bouncing at z = −0.62 was still descending
through the blade's plane and hit the floor at z = −1.88, untouched, ending the rally at two
hits. It now steps in to meet a LOW ball, over the last 55 cm only. Both guards are load-bearing:
written without the range guard, RallyTest fell from 10 of 10 shots reached to 3 of 10, because
the blade parked mid-table and was out of position for everything.

**Also this pass:**

- **`CameraRig` rally-cam** — no longer follows the ball. Two FIXED views (close / wide), and it
  cuts between them on who last hit: IN on a player hit (or the feed), OUT on an opponent hit.
  `F` toggles, `C` drops to the presets. "The camera is the most important tool for hitting the
  ball", so it always sits behind the near end looking down-table.
- **`Stroke` reach** — the blade tracks the ball's depth within a generous band (`REACH_FWD`
  1.2 m out over the table, `REACH_BACK` 0.8 m behind the baseline). `MouseAim` bounds widened
  (`MAX_X` +1 m, `MAX_Y` 1.4 m) and it reads the cursor at the blade's current z. `MIN_Y` is
  **`BLADE_R`**, not the ball's 0.02: it bounds the blade's CENTRE, and a disc of radius
  BLADE_R centred at 0.02 hangs 5.5 cm through the table top — which is the bug this constant
  was previously changed to fix and did not. It costs no reach, since the disc still spans 0 to
  15 cm and its lower half covers a ball scraping the surface.
  The `gone` cutoff in `advanceOne` is y < -0.6 (near the floor), not -0.25, so a wide/long ball
  stays chaseable.
- **Face eased toward the ball** (`FACE_TAU`), **`Stroke.TRACK_SPEED` 8 → 13**, **`timeScale`
  0.45**, **default feed = `Serve`** (gentle no-spin corner to corner).

## Sep 4 — the player's paddle no longer follows the ball

Reported as a gameplay bug and fixed at the source: with the mouse completely still, the near
blade was still moving, because `Stroke.advance` read the ball twice.

- **Depth.** `wantZ` tracked the ball's own z within a reach band (`REACH_FWD` 1.2 m /
  `REACH_BACK` 0.8 m) whenever the ball was on our side and approaching. The blade walked out
  over the table to meet a short ball with no input at all.
- **Face.** `faceToward` aimed the blade's normal at the ball, falling back to down-table only
  once the ball was behind it. That is auto-aim: the face turned to track a ball the player had
  not reacted to.

Both are gone. `advance(Paddle, double)` no longer takes a `BallState` at all, so the rule is
enforced by the signature. Measured: over every preset and a 30 m/s ball driven straight at it,
with the cursor set once and then still, blade drift, face turn and blade speed are **exactly
0**.

### The reach came back as geometry — `render/MouseAim`

Pinning the blade to a plane cost the two things the ball-driven reach had been paying for: the
player could not dig out a short ball, and ShotAssist reads forward drive off the blade's z, so
there was no pace axis left either. Both are back, off the cursor, in `MouseAim.onReachSurface`.

The blade stands **where the cursor's own ray meets the table**, and falls back to the rest plane
only when the ray is not pointing at the near half at all. Point at your own end line and the
blade is where the fixed plane had it; slide the cursor up-table and it walks out over the table
riding at blade height; keep going and it comes back, rising, for a high or deep ball. Measured
through the real camera (`ReachProbe`, scratch), sweeping the cursor down the wide rally view:

| cursor, top → bottom | blade |
| --- | --- |
| 0.02 – 0.40 | rest plane, height 1.40 m falling to 0.90 m |
| 0.42 – 0.52 | reaching in: z 1.45 → 0.70, height 0.82 → 0.29 |
| 0.60 – 0.72 | skimming the table at y = 0.075, z 0.76 → 1.43 |
| 0.80 – 0.98 | rest plane, low — exactly the old home position |

Three properties make this legitimate rather than a second auto-follow:

- **It never reads the ball.** It is the cursor ray and the camera, and nothing else.
- **Every point of it is under the cursor**, because the depth is chosen along the aim ray. That
  is what makes any depth choice honest — the blade always appears where you point, so the only
  question the depth rule answers is *how far along the ray*, which the player cannot see anyway.
- **It is stateless.** Depth is solved from the ray, then x and y are read on the plane that
  solve chose. Reading the cursor on the plane the blade currently occupies — which is what the
  old code did — is a loop WITH GAIN once depth is cursor-derived: the blade creeps forward, the
  ray reads lower on the plane it just moved to, and it creeps further, to full stretch. Do not
  reintroduce that by "simplifying" the two intersections into one.

Reaching in is an UP-screen gesture, and that is geometry, not a choice: from a camera behind the
near end, a blade reaching in is further away and therefore higher on screen. The gesture is
"point at the ball", and a short ball you have to reach for is up-screen.

**Coverage, measured** (`CoverageProbe`, scratch — the real camera's surface against the
follower's actual returns): all **10 of 10** returns pass within 3 mm of a point the cursor can
put the blade on, met at z = +0.69 to +1.57. Under the pinned plane, `Serve` and `Cross-court
loop` arrived below the table top and could not be played at all.

### The shot goes where the player aims it — `play/ShotAssist`

The other half of the same report: the ball came back to the middle of the table however you
swiped. It was not a weak aim. It was that **the rescue path had quietly become the normal
path**, and the rescue re-aims down the middle by construction.

`minShotSpeed` (7 m/s) was the floor for the whole search, and a contact low over the table or
behind the end line off a dropping ball has *no* legal answer at 7 m/s — the shot has to be
lifted, and a lifted shot is slow. So the main search failed, and every such shot was rescued
and centred. Measured before: `passes = 4` (rescued) on seven of eight test swings, target x
= 0.00 on every one of them, landing 0.09 m off centre *against* the swipe.

Three changes, all in `Tuning` except the last:

- **`minSearchSpeed` (new, 3.0 m/s)** — the slowest the SEARCH may consider, as against
  `minShotSpeed`, the slowest the swing may ASK for. They were the same number and should never
  have been: the score still prefers the speed the swing asked for, so a slow candidate only
  wins when the fast ones are illegal, which is exactly when it should.
- **The main ladder caps each candidate at its own speed** (`constrain(v, toOpp, speed)`), the
  way the rescue always has. Without it, `minForwardVelocity` re-inflates a slow shot and undoes
  the solve that just found it.
- **The rescue carries the player's aim** (`rescueAimFracs = {1.0, 0.6, 0.3, 0.0}`) instead of
  hard-centring. It tries the full aim first and gives it up only if nothing there is legal, so
  the guarantee is unchanged and the aim survives.
- Aim authority raised to match: `aimInfluence` 0.115 → 0.16, `targetHalfWidthFrac` 0.60 → 0.75
  (the box's corners were so far inside the table that a committed swipe still landed mid-court),
  `maxHorizontalDeviationDeg` 15 → 20 (at 15 the cone overruled the aim before the validator saw
  it; widening it cannot make a shot illegal — every candidate is still flown and graded).

Measured after, same swings: **`passes = 0`** — the normal search wins. Swipe right lands at
x = +0.36, swipe left at −0.36, a hard swipe at +0.44, out of a 0.76 m half-table. Driving in
deepens the shot from z = −0.56 to −0.77 and adds pace. The follower gained from the same fix
without being touched: its returns went from 4.3–7.4 m/s landing at z = +0.34…+1.00 to
**5.5–7.4 m/s landing at +0.70…+1.00**, which is most of the way through the first "not done"
item below.

### The assist was costing more than a frame

Found while measuring the above, and worth keeping because the shape of it is not obvious: one
`assist()` call took **25.8 ms** — longer than the 16.7 ms frame it happens inside, on the frame
a contact lands. It was never the flights. It was `Aim.atTarget`: 60 bisection halvings, each
flying a whole trajectory, called once per candidate shot, a dozen-plus times per contact.

- **`Aim.ITERATIONS` 60 → 28** (`physics/`). 60 halvings of an 80-degree bracket is the last bit
  of a double; 28 is 5e-9 rad, which is 14 nanometres of landing position on a 2.7 m shot. Free
  when it solved twelve presets at startup, not free once the assist calls it per contact.
  SelfTest grades every preset through this solver and is unchanged at 101/101.
- **The search stops at the first legal candidate** rather than finishing the pass. The speed
  ladder already tries the asked-for pace first and alternates outward, so the first legal
  candidate is the one that would have won on score anyway.
- **`ShotAssist.VALIDATE_DT` = 1/120**, four times the game's step, for validation flights only.
  RK4 error is O(h⁴), so that is 256x an error SelfTest measures in tenths of a millimetre over
  three seconds — millimetres, against the 5 cm landing margin it feeds. Not to be taken coarser
  without redoing that arithmetic.

**25.8 → 11.9 ms**, and an ordinary contact is now 2–3 ms; the worst case measured (a cord-high
ball at the net, which does go through the rescue) is 12.4 ms. Note that this is the second
change to `physics/` since the assist landed — `World.setState` is no longer the only one, though
`Aim.ITERATIONS` is a cost knob with no effect on any answer the model gives.

**Not done, next:**

1. **Make the follower's returns less passive.** Better than it was — the search floor fix
   pulled its returns out of the rescue path too — but it still meets the ball low near its own
   baseline and picks its stroke from nothing. The real answer is still the October opponent
   choosing its stroke from the ball.
2. **Scoring.** The point-ending events already fire and reset the rally; nothing counts points
   or tracks serve/receive turns.
3. **Serving for real** — the player serving off their own blade, not a `launchShot` feed.
4. **`RallyTest` run configuration** (headless `main`, no `.idea/runConfigurations/` entry).

One consequence of the aero change worth knowing before it surprises someone: **shots curve less than
they used to.** Sidespin deflection dropped ~16% and backspin now floats more. That is the measured
behaviour, not a regression — the before/after numbers are in the git history for this change.

One consequence of `timeScale = 0.45`: the game opens in slow motion. `[` and `]` still change it
live (down to 0.02, up to 2.0). It is a pure display-rate knob — fewer fixed steps run per wall
second, each identical to a full-speed step — so nothing under `physics/` sees it.

## Conventions

- Java 21, 4-space indent.
- Comments explain *why*, and cite a source for every real-world number. **A bare constant with no
  citation in `physics/` is a bug.**
- Anything tuned by eye is labelled `TUNED` and says what it is standing in for.
- `out/` is git-ignored; never commit build output.

## References

Sources named in the project contract:

| Source | For |
| --- | --- |
| [Gaffer On Games](https://gafferongames.com/) | Game loops and physics timing — see [Fix Your Timestep](https://gafferongames.com/post/fix_your_timestep/). |
| [Red Blob Games](https://www.redblobgames.com/) | Vector math and AI. |
| [Scratchapixel](https://www.scratchapixel.com/) | 3D rendering. |
| [OpenJFX](https://openjfx.io/) and the [JavaFX API docs](https://openjfx.io/javadoc/21/) | Graphics — the 3D scene graph, camera, and materials. |
| Research papers on table tennis ball trajectories | Real numbers to check the simulation against, instead of guessing. |
| AI tools like Claude or ChatGPT | Last resort. |
