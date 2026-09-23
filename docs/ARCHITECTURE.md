# Architecture

[Project overview](../README.md) · [Physics](PHYSICS.md) · [Game design](GAME-DESIGN.md) ·
[Development](DEVELOPMENT.md) · [Agent contract](../AGENTS.md)

How the program is put together, the rules that keep it that way, and where the unbuilt features
plug in. The reasoning behind the physics numbers is in [Physics](PHYSICS.md); the reasoning
behind the arcade layer and the controls is in [Game design](GAME-DESIGN.md).

## Three modules, one direction

```text
engine   tabletennis.engine   pure physics: no game words, no JavaFX, no dependencies
  ^
game     tabletennis.game     rules, players and the arcade layer; no JavaFX
  ^
app      tabletennis.app      JavaFX: loop, input, scene, camera, HUD
```

Gradle enforces the direction: `game` declares `api project(':engine')`, `app` declares
`implementation project(':game')`, and only `app` applies the JavaFX plugin. A physics class that
imports JavaFX or a game class does not compile, so these are build errors rather than code-review
rules.

| Module | Packages | Holds |
| --- | --- | --- |
| engine | `math` | `Vec3`, `Quat`, `Numeric` (the one `Clamp`, `Fraction`, `SmoothLerp`) |
| | (root) | `BallState` and every real-world number: `BallSpec`, `TableSpec`, `NetSpec`, `RacketSpec`, `Environment`, `Simulation` |
| | `aero` | `Aerodynamics` (gravity, drag, Magnus, spin decay), `DragModel`, `AeroData` (the cited fit tables) |
| | `flight` | `Integrator` (RK4), `TrialFlight` (the one contact-free flight), `LaunchSolver`, `SpinVector` |
| | `contact` | `Collider` (sealed: `BoxCollider`, `BladeCollider`), `Material`, `Materials`, `ContactSolver` |
| | `world` | `PhysicsWorld`, `Racket`, `Arena`, `Surface`, `SurfaceKind`, `SurfaceHit`, `StepReport`, `FlightPredictor` |
| game | (root) | `GameSession` (the facade), `GameSnapshot`, `StepResult` |
| | `rally` | `Referee`, `RallyFacts`, `RallyEvent`, `EventType`, `Side` |
| | `match` | `Scoreboard`, `ScoreSnapshot` |
| | `control` | `ReachEnvelope`, `CursorFollower`, `DemoHand`, `ReachTiming` |
| | `ai` | `Opponent`, `BallFollower`, `MeetingPoint`, `HittingZone` |
| | `shot` | `ShotAssist`, `SwingReading`, `ShotPlanner`, `ShotSearch`, `ShotDecision`, `ShotTuning` |
| | `feed` | `Feed`, `Feeds` |
| app | (root) | `TableTennisApp` (composition root), `GameLoop`, `FixedStepClock`, `LaunchOptions`, `FrameCapture` |
| | `input` | `Controls` (key bindings and the legend), `MouseControl`, `CursorRay` |
| | `scene` | `Xform`, `TableScene`, `ArenaView`, `Meshes`, `Textures`, `BallView`, `BallShadow`, `RacketView`, `Trail`, `BounceMarks`, `ShotOverlay` |
| | `camera` | `CameraRig`, `CameraView`, `CameraPose` |
| | `hud` | `Hud`, `ScoreLine`, `ShotLine`, `ControlReadout` |

## One step, end to end

`GameSession.Step()` advances exactly one `Simulation.Step` (1/480 s). The order matters, and the
golden trace pins it:

1. **Move the rackets.** The player's blade follows the cursor (`CursorFollower`), or the demo
   hand's cursor (`DemoHand`) when demo mode is on. The opponent's blade is moved by the
   `Opponent` (today `BallFollower`). Both blades are posed before physics runs, so a contact uses
   this step's swing.
2. **Hand the world only the rackets allowed to strike** (`PhysicsWorld.SetRackets`). A racket
   that may not hit yet is simply absent, so the ball passes through it. When both are
   present, the player's is listed first, and that is the order equal times of impact resolve in.
3. **Step physics.** `PhysicsWorld.Step()` flies the ball (RK4), then resolves contacts
   earliest-first, and returns a `StepReport`: the ball before and after, and every `SurfaceHit`
   in order. The engine reports what was touched; it never decides what a touch means.
4. **Turn contacts into rally facts.** `RallyFacts.Of` keeps the notable hits (hard enough to
   count, per `SurfaceKind`) and names the hitter by which racket was struck.
5. **Author the shot.** After a racket contact, `ShotAssist` replaces the raw outgoing ball with an
   authored shot, through `PhysicsWorld.ReplaceBall`, the engine's one hook for the arcade layer.
6. **Judge.** `Referee.Judge` applies the one-bounce rule, out, floor, own half and the point
   latch, and returns a `Ruling`: the events and the point's winner, if any.
7. **Score and schedule.** A point goes to `Scoreboard`. A replay is scheduled
   `PointEndDelay` (0.9 s) after a point, or `ReplayDelay` (1.8 s) after a ball dies undecided.

The session hands out only immutable values: a `GameSnapshot` (ball, previous ball, time, both
blades, score, who may hit, the last `ShotDecision`) and a `StepResult` per step. Nothing outside
`game` can reach its world, rackets, referee or scoreboard.

## Hard invariants

Breaking one of these is a defect even if everything compiles and every check passes.

1. **The engine imports neither JavaFX nor game code; the game imports no JavaFX.** The build
   enforces it. The engine stays headless and frame-rate independent.
2. **`scene.Xform` is the only place metres become scene units**, in both directions. Nothing
   else may use `Xform.ScenePerMetre`.
3. **The app reads snapshots; it never writes game state.** It sends the session commands
   (`Launch`, `SetAim`, `SetDemoMode`, `SetAutoReplay`) and nothing else.
4. **One contact solver.** Table, net, floor and both blades differ only by a `Material` and a
   `Collider` shape. The solver asks a shape four questions (`ClosestPoint`, `EscapeNormal`,
   `Sweep`, `VelocityAt`); a new shape answers them and never touches the impulse.
5. **The solver works in the surface's frame.** Every velocity is taken relative to
   `VelocityAt(contact)`. In absolute velocity, a blade swung into a ball reads as already
   separating and does nothing, and a brushing stroke makes no spin. A paddle passing harmlessly
   through the ball means this broke.
6. **Predictions never see a racket.** `FlightPredictor` and `TrialFlight` fly the ball with no
   blade in the world. A prediction that gets intercepted predicts nothing.
7. **Landing questions go to `TrialFlight`, never to a world with a table in it.** The table
   bounces the ball away before the descent can be seen, so the first crossing found is the
   second descent, past the end line. This bug was found three times.
8. **The ball never moves the player's blade.** `CursorFollower.Advance(Racket, Seconds)` takes no
   `BallState`, so the signature enforces it. Anything that reads the ball before the blade's
   target is chosen is auto-follow in disguise. `ReachTiming` and the `D` readout read the ball
   only after the target is set, to answer "could the player have got there".
9. **Physics never sees a frame time.** The session advances in whole steps only. Anything
   sampled per step for display counts whole steps, not seconds.
10. **Feeds state intent** (speed, spin, target) and `LaunchSolver` solves the launch angle. With
    drag this strong, hand-picked launch velocities almost all sail off the end.
11. **One gameplay implementation.** Eligibility, contact handling, point decisions and replay
    scheduling live in `GameSession` and its parts. The app and the tests both drive it; neither
    re-implements a rule.

## Coordinates

**Physics space** is SI metres and right-handed, with the origin at the centre of the table
surface: `+X` right, `+Y` up, `+Z` toward the player. The near end line is at `z = +1.37`, the
far one at `z = -1.37`, and the floor at `y = -0.76`. Every cross product (Magnus `ω × v`, the
contact impulses) assumes right-handedness. **Never fix a sign by flipping an axis; fix the
physics.**

**Scene space** is JavaFX's: left-handed, with `+Y` down and `+Z` into the screen.

```text
SceneX = +X * ScenePerMetre        ScenePerMetre = 300
SceneY = -Y * ScenePerMetre
SceneZ = -Z * ScenePerMetre
```

The scale exists because JavaFX's default clip planes and light attenuation fall apart at metre
scale. Never do physics in scene units, and never take a cross product in scene space.

## The loop

Gaffer On Games, [*Fix Your Timestep!*](https://gafferongames.com/post/fix_your_timestep/):

- `FixedStepClock` pays wall-clock time out as whole 1/480 s steps and keeps the remainder as an
  interpolation fraction. It owns pause, single-step and the time scale (0.45 by default, 0.02 to
  2.0). A frame is clamped to `MaxFrameSeconds` (0.25 s), so a stall cannot spiral.
- `GameLoop.Frame` steps the session while the clock owes steps, feeds each `StepResult` to the
  scene, camera and HUD, then renders. Rendering interpolates position and slerps the orientation
  between the last two states; it never snaps to the raw state.
- A due replay is launched at the loop boundary, and the frame that launches it drops the rest of
  its owed time.
- Capture mode (`--out`) advances whole steps to `--at`, lets the scene settle for three pulses and
  writes one PNG. The same arguments always give the same image.

The step is small enough that a 30 m/s smash moves 6 cm per step, and contacts are swept besides,
so nothing tunnels through the 2.5 cm table top.

## Where the next features plug in

The architecture has a place for each unbuilt feature; none of them needs a rewrite.

- **Serving.** A rally still opens on a feed, a ball that appears in the air. A serve becomes a new
  phase in `Referee` (toss, strike, first bounce on the server's own half), and `Scoreboard`
  already derives whose serve it is. `Feed.IsServe()` and the `Corkscrew serve` feed already model
  the own-half-first bounce.
- **A predicting opponent.** `BallFollower` tracks the ball's current position. A predicting
  opponent is a second `Opponent` implementation, built on `FlightPredictor` and `MeetingPoint`,
  which the demo hand already uses. Difficulty has to come from prediction quality: a follower
  cannot be made beatable by slowing it down; it only becomes erratic.
- **Menus.** A new front in `app`, choosing what `TableTennisApp` launches. The session needs
  nothing new.
- **Paddle profiles.** A paddle is a set of `ShotTuning` values ("the defaults plus these
  changes", via `ShotTuning.Builder`). Physical rubber differences belong in a new `Material`.
- **Currency and the shop** come last, after the core features.

## How behavior is pinned

- **Golden trace.** `GoldenTraceTest` drives `GameSession` through 39 scripted scenarios: every
  feed with no input, with a hand pointing at the ball and with the demo hand, plus a cursor sweep
  with brush segments and toggles mid-rally. It hashes every step's ball and both blades bit for
  bit and logs every contact, point and replay. A restructuring must reproduce
  `golden-trace.txt` exactly; a deliberate behavior change regenerates it
  (`:game:writeGoldenTrace`), and the commit says why.
- **Claims.** Each check is `Claims.Check("falsifiable claim", Holds, "measured detail")`, from
  `engine`'s test fixtures. It prints `[PASS]` or `[FAIL]` with the measured number either way.
  The anchors are published measurements and ITTF rules, not the code's own output.
