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
      **Part-built.** The physics half is done and validated: a moving-paddle contact solver, a
      `Paddle` collider, rubber with spin reversal, a charge-and-release `Stroke`, and an opponent
      that returns everything (`play.RallyTest`, 3 checks). **Not yet wired into `MrPong`** — there
      is no paddle on screen and the mouse does not drive one. Serving and scoring are not started
      and are deliberately the next piece of work, not this one.
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
    Stroke.java          the player's charge-and-release swing, advanced per physics step
    Opponent.java        interface: look at the ball, move the blade, swing
    Follower.java        the current opponent - tracks the ball, unbeatable, does NOT predict
    RallyTest.java       headless validation of the opponent (second main, own run config)
  render/
    Xform.java           the ONLY physics<->scene conversion, now both directions
    MouseAim.java        cursor -> ray -> point on the player's hitting plane
    Court.java           table, net (real mesh), floor, legs, ITTF markings
    BallView.java        ball + procedural chequer texture that makes spin visible
    PaddleView.java      blade, red/black rubber, handle
    Trail.java           fading dot trail; used for both the live path and the ghost
    BounceMarks.java     discs left where the ball landed
    CameraRig.java       orbit/zoom camera + preset views
    Hud.java             live readouts -- SLATED FOR REMOVAL, see below
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

What `SelfTest` anchors against (**97 checks**): terminal velocity vs. the closed form; free fall vs.
the exact `tanh`/`ln cosh` solution to 1 mm over 3 s; the ITTF drop test; RK4 convergence order;
`C_d` and `C_L` vs. measured values; the lift crisis; Magnus direction for top, back and side spin;
no contact ever adding energy; topspin kicking forward and backspin checking off the bounce; the net
killing a ball; in/out detection; every preset shot being legal; 10 simulated minutes of stability;
no tunnelling from 20 to 60 m/s; and the whole paddle group below.

`play.RallyTest` (3 checks) is separate and covers the opponent: it reaches every shot fed at it,
returns every one over the net, and never sends the ball out faster than the impulse allows.

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

## Where this is up to (as of Aug 30)

The physics for a playable game is **done and validated headlessly**; the wiring into the app is
**not**. Both test suites are green (97 + 3). Concretely:

**Done** — moving-surface contact solver, `Collider` seam, time-ordered resolution with sub-step
re-integration, measured drag and lift, airspeed-dependent spin decay, velocity-dependent table
restitution, `Paddle`/`Blade`, rubber with spin reversal, `Stroke`, `Opponent`/`Follower`,
`RallyTest`, `Xform.toPhysics`, `MouseAim`, `PaddleView`.

**Not done, in the order it was going to be tackled:**

1. **Wire the paddles into `MrPong`.** They exist but nothing constructs them, so there is still no
   paddle on screen. Needs: fields beside the other views; the nodes added to `world3d`; `Stroke` and
   `Follower` advanced inside `advanceOne()` (**per `DT`, never per frame** — sample the mouse into a
   field in the handler and consume it there); `PaddleView.update` in `render()` interpolated with the
   same `alpha` as the ball.
2. **Mouse buttons.** `CameraRig.attachControls` filters on *no* button, so today **any** drag orbits
   — which collides head-on with hold-right-click-to-charge. Left drag should orbit, right press /
   drag / release should charge and swing, bare movement should aim. `MOUSE_MOVED` is entirely free.
   Use `addEventHandler`, **not** `setOnMouseDragged`: those are single-slot properties and assigning
   one silently replaces the camera orbit.
3. **HUD.** Delete the readouts, the event log and the status line; keep the key legend and extend it
   with the new controls. Only six sites in `MrPong` reference `Hud` (`:80`, `:111`, `:150`,
   `:268-270`, `:345`); nothing in `render/` or `physics/` does.
4. **`RallyTest` run configuration** (see above), then `README.md`.

One consequence of the aero change worth knowing before it surprises someone: **shots curve less than
they used to.** Sidespin deflection dropped ~16% and backspin now floats more. That is the measured
behaviour, not a regression — the before/after numbers are in the git history for this change.

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
