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
```

bash (Git Bash) — note the different glob and the `;` → newline:

```bash
JDK=~/jdk/jdk-21.0.7-full/bin
"$JDK/javac" -d out/production/3D-Table-Tennis-In-Java $(find src -name '*.java')
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java MrPong
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java physics.SelfTest
```

**Always run `physics.SelfTest` after touching anything under `src/physics/`.** It is headless, prints
PASS/FAIL per check, exits 0/1, and takes about 1.5 s. It anchors the model against published and ITTF
numbers rather than against itself, so it catches the changes that still look plausible on screen. It
must stay at 100% — a failing check is a broken deliverable, not a flaky test.

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
    Contacts.java        one sphere-vs-box impulse solver for table, net and floor
    World.java           owns the state, steps it, emits events, predicts trajectories
    Aim.java             solves launch angle for a target; also builds spin vectors
    Shots.java           named launch presets, defined by intent and solved by Aim
    SelfTest.java        headless validation vs. published numbers
  render/
    Xform.java           the ONLY physics->scene conversion
    Court.java           table, net (real mesh), floor, legs, ITTF markings
    BallView.java        ball + procedural chequer texture that makes spin visible
    Trail.java           fading dot trail; used for both the live path and the ghost
    BounceMarks.java     discs left where the ball landed
    CameraRig.java       orbit/zoom camera + preset views
    Hud.java             live readouts
```

Rules that keep this from rotting:

- **`src/physics/` must not import anything from `javafx.*`.** It is plain Java so the simulation can be
  checked headlessly and so it can never accidentally depend on the frame rate or on the renderer.
  `SelfTest` enforces this by existing.
- **`src/render/` reads physics state; it never writes it.** Physics state is plain math on vectors;
  JavaFX nodes are a *view* of that state, updated once per frame.
- **Shot presets state intent** (speed, spin, target) and let `Aim` solve the launch angle. Do not
  hard-code launch velocities: it was tried, and with drag this strong almost every hand-picked shot
  sailed off the end of the table.
- **One collision solver.** Table, net and floor differ only by a `Material` record (restitution,
  friction, extra damping) — do not add a second bespoke bounce path. The net has to steal spin by the
  same rules the table uses to make it.
- **Spin is core, not a bonus.** Magnus in flight, and spin transfer on table/paddle/net contact. It
  shapes the collision code, so design for it up front rather than bolting it on.
- **The AI predicts, it does not follow.** When it arrives (Sep 17), it should solve for where the ball
  will be, using `World.predict` — which is already headless and side-effect free for exactly this.

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
- **Drag** `a = -½ρA·C_d·|v|v / m`, `C_d = 0.40`. At 10 m/s that is 11.4 m/s², *more* than gravity —
  air, not gravity, dominates a table tennis trajectory.
- **Magnus** `a = ½ρA·C_L·|v|² · unit(ω × v) / m`, with a saturating `C_L = S / (2S + 1)` for spin ratio
  `S = rω/|v|` — 0.17 to 0.33 over realistic spin, matching measurements. A linear `C_L = kS` blows up
  on serves. Drag and lift share one `½ρA/m` factor; keep it that way.
- **Bounces** normal restitution, then either grip (`v_contact → 0`) or a Coulomb slide, whichever the
  friction cone allows. This is what turns topspin into a low fast kick and backspin into a checked,
  dead ball — it is the whole point, and none of it is scripted.
- **Collisions are swept**, not overlap-only. At 60 m/s the ball moves 12.5 cm per step against a 6.5 cm
  crossing, so an overlap test alone drops the hardest shots straight through the table.

What `SelfTest` anchors against (68 checks): terminal velocity vs. the closed form and the published
9.0–9.6 m/s range; free fall vs. the exact `tanh`/`ln cosh` solution to 1 mm over 3 s; the ITTF drop
test; RK4 convergence order; Magnus direction for top, back and side spin; no contact ever adding
energy; topspin kicking forward and backspin checking off the bounce; the net killing a ball; in/out
detection; every preset shot being legal; 10 simulated minutes of stability; and no tunnelling from
20 to 60 m/s.

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
