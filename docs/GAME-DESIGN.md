# Game design

[Project overview](../README.md) · [Architecture](ARCHITECTURE.md) · [Physics](PHYSICS.md) ·
[Gameplay](GAMEPLAY.md) · [Agent contract](../AGENTS.md)

The `game` module's decisions and the measurements behind them: the shot assist, the rally rules,
the control mapping, the opponent and the demo hand. Most of the numbers below came from a sweep,
not from judgement by eye. A number marked *at the time* was measured when the decision was made;
the tuning has moved since, and the current checks print today's values.

## Why the shot is authored

The physics underneath is real and graded on its own. The game on top is deliberately arcade:
a rally a person can keep needs it. Fired at the blade over a 75-point grid of racket velocities
(*at the time*):

| | lands on the table | worst sideways landing | fastest launch |
| --- | --- | --- | --- |
| raw impulse alone | 11 / 75 | 2.37 m (the half-width is 0.76) | 24.5 m/s |
| through the assist | **75 / 75** | 0.38 m | 12.4 m/s |

So after a racket contact, `GameSession` hands the raw result to `ShotAssist`, which authors the
outgoing launch. Only the launch is authored; the flight after it is the real simulation.
`PhysicsWorld.ReplaceBall` is the engine's only concession to this. Remove the `ShotAssist` call
and the realistic game is back, untouched. Keeping the realistic model separable is worth more
than any single tuning win.

## The shot pipeline

`ShotAssist.Assist` runs in one direction only (read, plan, search, blend), and nothing is changed
after its last check:

1. **`SwingReading`** splits the racket's motion into a forward **drive**, a sideways **swipe**
   and a **lift** (live only while brushing). It combines them into a **brush** for spin and an
   effort `Amount` on a saturating 0..1 curve. It also grades **contact quality**: 1 mid-blade,
   falling to 0 at the rim, with a clean core that shrinks as the ball arrives faster.
2. **`ShotPlanner`** builds the plan:
   - a target inside the opponent's court by construction: lateral from the swipe, face and
     contact point; depth from the drive, brush and contact height. `TargetArea` is the box.
   - a pace from a fixed band. The incoming ball only nudges it and is never added, which is what
     stops a rally compounding into a rocket.
   - spin from the brush and the swipe (`SpinPlan`).
3. **`ShotSearch`** finds the legal launch closest to the plan:
   - each candidate is solved by `LaunchSolver`, constrained, flown contact-free by `TrialFlight`
     and graded. The constraints are minimum forward pace, a lateral cone and cap, an elevation
     band and the candidate's own top speed.
   - a speed ladder starts at the asked-for pace and alternates slower and faster. Correction
     passes pull the target toward safe and back the pace off.
   - the first legal candidate ends the search.
   - if nothing is legal, a **rescue** re-aims down the middle at anything from a soft lift up. It
     keeps as much of the player's aim as still works (`RescueAimFracs = 1.0, 0.6, 0.3, 0.0`).
4. **`ShotAssist`** blends the capped raw reflection with the found launch. The opponent always
   gets the full authored shot. The player gets `AssistFloor + (1 − AssistFloor) × quality` of it,
   so a clean contact lands where aimed and a shank keeps mostly raw physics and usually dies. A
   rim contact or an over-hard swing does not qualify for the rescue. The whole decision is kept
   as a `ShotDecision` for the `V` overlay.

**Only the player is graded.** Grading the opponent made it shank ordinary feeds into the net,
which reads as broken rather than beatable.

### Tuning

Every arcade knob lives in `ShotTuning`, in eight groups: `StrengthKnobs`, `AimKnobs`,
`TargetKnobs`, `QualityKnobs`, `LimitKnobs`, `SearchKnobs`, `RescueKnobs` and `SpinKnobs`. Each
pipeline stage receives only the groups it uses.

- Each group validates itself when built, following each knob's mathematical use:
  - every value is finite;
  - divisors and the swing-curve exponent are positive;
  - caps and clamp half-widths are non-negative;
  - ranges are ordered;
  - lerp weights and table fractions sit in [0, 1];
  - `RescueSpeedSteps >= 2`, because the ladder divides by `steps − 1`;
  - `SpeedBackoffPerPass × MaxCorrectionPasses < 1`, so the last pass still moves forward.
- `ShotTuning.Builder` keeps each default beside its reason. A future paddle profile is "the
  defaults plus these changes".
- `ShotTuningTest` pins all 57 defaults, so a retune is always a visible, deliberate diff.
- Restitution and friction are not duplicated here. They are measured values, single-sourced in
  `Materials` and graded by the engine's tests.

### Decisions inside the assist

- **Depth pulls against arc, by design.** A drive deepens the target through `DepthInfluence`
  (0.100 per m/s). It also raises the brush (`DriveBrush` 0.8), which shortens the target through
  `ArcInfluence` (0.018). The net effect is 0.0856 of the depth range per m/s of drive. Retune the
  two together.
- **The brush gain.** `Brush = Lift + Drive × DriveBrush` has to reach genuine backspin, not just
  less topspin. A still blade authors `BaseTopspin` (14 rev/s). A hard pull-back (about −8 m/s of
  drive) at 0.8 gives a brush of −6.4, and 14 − 6.4 × 2.6 (`TopspinPerLift`) ≈ −2.6 rev/s, just
  into a chop. Below about 0.7, the whole backspin half of the gesture is unreachable.
- **The rescue had quietly become the normal path**, and it centres by construction, so every
  swipe came back down the middle. Three changes fixed it (*at the time*: seven of eight test
  swings went from rescued to `passes = 0`; swipes then landed at ±0.36 m):
  - `MinSearchSpeed` (3 m/s) is kept separate from `MinShotSpeed`, the slowest pace a swing may
    ask for. The search may go slower than a swing asks, because the score still prefers the asked
    pace.
  - The ladder caps each candidate at its own speed. Otherwise `MinForwardVelocity` re-inflates a
    slow shot and undoes the solve that just found it.
  - The rescue carries the player's aim instead of hard-centring.
- **Cost per contact.** One assist once cost 25.8 ms, more than a frame. The fix was three
  changes, bringing an ordinary contact to 2–3 ms and the worst case (a cord-high ball at the net)
  to 12.4 ms:
  - fewer `LaunchSolver` halvings (see [Physics](PHYSICS.md#costs-worth-knowing));
  - stopping at the first legal candidate;
  - validation flights at `TrialStep = 1/120`, four times the game step. RK4 error is O(h⁴), so
    that is 256× an error the tests measure in tenths of a millimetre over 3 s: millimetres,
    against a 5 cm landing margin. Do not coarsen it without redoing that arithmetic.
- **Ball-control ease, for an average player.** The clean core was widened (`QualityCore`
  0.50 → 0.58, `QualityFalloff` 0.42 → 0.50), and a fast ball shrinks it less
  (`QualityPaceLoss` 0.22 → 0.15). A mishit earns more assist (`AssistFloor` 0.25 → 0.35). Before
  this change the tolerance before a ball went out was 0.6 of the blade radius against a 5 m/s
  ball, 0.4 at 12 m/s and 0.3 at 18 m/s. A mishit is still clearly worse than a clean hit; it no
  longer reads as an automatic loss.

## The rally rules

`Referee` is a pure state machine fed facts per step. It is unit-tested without physics
(`RefereeTest`) and end to end through `GameSession` (`GameSessionTest`).

- **One bounce.** A racket may strike only after the ball has bounced on its own half; until then
  it is left out of the world, so the blade still tracks on screen but the ball passes through it.
  A second bounce on the receiver's half, a return onto the hitter's own half, a shot out, or a
  floor contact decides the point.
- **Out is judged per shot.** Every racket hit starts the in/out question again.
- **A net cord decides nothing.** Under ITTF a rally ball that clips the cord and lands legally is
  good. A cord that kills the ball still decides the point a moment later, through the own-half or
  floor rule. Service lets belong with serving, which is not built.
- **The point latches.** It is awarded once however many rules fire on the same step, and both
  rackets are withdrawn so a decided ball stops being playable.
- **`ContactBounceWindow` (two steps).** A ball may be struck while it still touches the table (a
  push dug off the surface), so the table contact fires on the racket contact's own step. Read as
  a bounce, it ends a legal stroke as "your own half"; it once killed every four-hit rally in the
  trace. A table touch within the window is the contact's own and is not a rally event.

The match (`Scoreboard`) keeps ITTF rules: games to 11, win by 2, no ceiling at deuce, service
every 2 points and every point from 10-all, best of 5. The server is derived from the score, never
stored.

## The control mapping

### The ball never moves the player's blade

It was once reported as a bug: with the mouse still, the blade still moved, because it read the
ball twice. Depth tracked the ball within a reach band, and the face turned to the ball. Both are
gone. `CursorFollower.Advance(Racket, Seconds)` takes no `BallState`, so the signature enforces the
rule. Measured over every feed, with the cursor set once and left still: blade drift, face turn and
blade speed are exactly 0.

### The cursor means one thing at a time

```text
cursor X -> racket X        cursor Y -> racket Z (depth)        racket Y = ReachEnvelope.HitY
```

`CursorRay.OnPlane` intersects the cursor's ray with one horizontal plane and never reads the
ray's own height. `ReachEnvelope.Clamp` bounds X and Z independently. The ball still flies in full
3D; only the control is two-dimensional.

**Rejected: the reach surface.** An earlier mapping put the blade where the cursor's ray met the
table, so cursor Y set depth and height together. Neither could be moved alone, and its depth
curve doubled back at full stretch: one continuous hand motion reversed the blade halfway. Its
three good properties survive in the replacement: it never reads the ball, the blade is under the
cursor, and the mapping is stateless. Reading the cursor on the plane the blade currently occupies
is a loop with gain once depth comes from the cursor, so the blade creeps to full stretch; do not
reintroduce it.

**The envelope was read off sweeps.** "About 0.3 s after the bounce the ball becomes nearly
impossible to hit" was a reachability bug. Depth was clamped to [0.47, 1.57] with the end line at
1.37, so a ball still playable past the end line was out of reach. The sweep over the feeds that
bounce on the player's side, using the worst feed's touchable window (*at the time*):

| back limit | worst window | | hitting height | worst window |
| --- | --- | --- | --- | --- |
| 1.57 (old) | **98 ms** | | 0.14 | 258 ms |
| 1.80 | 154 ms | | 0.16 | **302 ms** |
| 2.00 | 206 ms | | 0.18 | 281 ms |
| 2.20 | 260 ms | | 0.22 | 221 ms |
| 2.40 | **281 ms** | | 0.26 | 135 ms |
| 2.60 | 281 ms (no gain) | | | |

Hence `ZNear = 0.30`, `ZFar = 2.40` (where the curve flattens) and `HitY = 0.16`. Today
`ReachabilityTest` measures the worst window at 260 ms (`Smash`), 579 ms of wall-clock at the 0.45×
default.

**`TrackSpeed` stayed at 13 m/s.** Raising it was the tempting fix and the wrong one: the ball was
unhittable because the envelope was wrong, not because the blade was slow. It stays below a
measured advanced swing of 17.8 m/s. `ReachabilityTest` measures the crossing margin every run
(tightest today: 162 ms, `Smash`), so a change that eats it fails a check instead of quietly
needing a faster mouse.

### The brush modifier

Holding the right mouse button switches cursor Y from depth to blade height
(`ReachEnvelope.ClampBrushed`) and freezes depth while held. It is modal, not simultaneous, which
is what separates it from the two-meanings-on-one-axis bug. `ReachEnvelope.Clamp` is untouched, so
in normal play no aim at any height leaves the hitting plane. The band is `BrushBand` (0.18 m),
and its lower half stops at `BladeRadius`, so the bat cannot cut through the table top.

**The dead-axis trap.** In normal play the player's blade is pinned to `HitY`, so its vertical
velocity is identically zero. Only the brush makes it live. Twice an expression read that
component and silently evaluated to a constant: once in the face easing, once in the assist's
spin. Before reading a velocity component, ask whose blade it is and whether that component can
ever be non-zero.

### The face settles to square

`CursorFollower` leans the face with the stroke and relaxes it to square (`FaceTau`) once the blade
stops. Without that, the same ball came off at a face lean of ±0.480 depending on whether the
player last walked in or backed up: a 57° difference from a movement that had ended hundreds of
milliseconds earlier.

**The wobble fix (`AimStillDelay`, 0.05 s).** The blade advances at 480 Hz, but the mouse reports
at its own coarser rate. Between reports every step read as "not moving", so the face relaxed and
was pulled back on the next report: a sawtooth, felt as a wobbling racket. The relax now waits
until the aim has been unchanged for 0.05 s.

## The opponent

`BallFollower` tracks the ball's current position; it does not predict. It waits at `PlaneZ`
behind its end line. It steps in to meet a low ball, but only over the last 55 cm
(`ReachForward`) and below `StepInHeight`. Without stepping in, a 4.3 m/s push bouncing at
z = −0.62 fell through the blade's plane untouched. Without the range guard, the tests fell from
10 of 10 shots reached to 3 of 10, because the blade parked mid-table. Today it reaches, returns and
lands 10 of 10 fed shots (`BallFollowerTest`).

A follower cannot be made beatable by slowing it down; it only becomes erratic. The next AI work
is prediction (see [where features plug in](ARCHITECTURE.md#where-the-next-features-plug-in)).

## The demo hand

`DemoHand` is a stand-in hand, not a shortcut: it produces a cursor point that goes through the
same `CursorFollower` and `ReachEnvelope` path as a mouse.

- **Where it aims.** It predicts where the ball is going (`MeetingPoint` over `FlightPredictor`)
  and aims at the point on that path closest to `HitY`. The first reachable point would be at the
  rim, where contact is a graze.
- **How it swings.** It sets up `Setback` (0.20 m) behind and `PrepAcross` (0.18 m) to one side,
  so the bat is still travelling when the ball arrives. `SwingLead` is derived from
  `Setback / TrackSpeed`, so it stays right if either is retuned.
- **When it stops re-predicting.** `Commit` (0.30 s) stops it re-predicting into a receding
  target, and `Stale` (0.12 s) stops it freezing on a meeting point that has already passed.

*At the time* it managed 71–79 exchanges a point against the follower on every feed, against 1.9
for a hand that merely points at the ball.

## Camera and overlays

- **Rally-cam.** Two fixed views, close and wide, cut on who last hit: in on a player hit or a
  feed, out on an opponent hit. It always sits behind the near end looking down-table. `F` toggles
  it, `C` cycles the presets. `Top` and `High` cannot address the whole depth envelope; they are
  inspection views, not ones a rally is played from.
- **`V`, the shot overlay.** It shows:

  | Colour | Meaning |
  | --- | --- |
  | Yellow | The racket's velocity |
  | White | The incoming ball's velocity |
  | Grey | The raw reflection |
  | Cyan | The intended shot |
  | Orange | The final shot, with length equal to speed |
  | Magenta | The target |
  | Green | The predicted landing |
  | Blue | The legal target box |

  To read it:
  - magenta and green apart: the solve missed its aim;
  - cyan and orange apart: a constraint is fighting the solve;
  - a green dot off the table: the rescue is in play.
- **`D`, the control readout.** It exists because "I could not get there" and "that ball was
  unplayable" look identical on screen and have opposite fixes. It shows the cursor, the raw and
  clamped aim, the blade, the ball and whether the blade could reach it in time.
