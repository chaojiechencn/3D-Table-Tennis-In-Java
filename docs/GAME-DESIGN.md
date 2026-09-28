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
outgoing launch. Only the launch is authored; the flight after it is the real simulation, except
that a player's shot curves under `CurveGain` (below). `PhysicsWorld.ReplaceBall` and
`PhysicsWorld.SetSideLift` are the engine's only concessions to this; both default to the measured
physics. Remove the `ShotAssist` call and the side-lift setting and the realistic game is back,
untouched. Keeping the realistic model separable is worth more
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

Every arcade knob lives in `ShotTuning`, in nine groups: `StrengthKnobs`, `AimKnobs`,
`TargetKnobs`, `QualityKnobs`, `LimitKnobs`, `SearchKnobs`, `RescueKnobs`, `SpinKnobs` and
`ContactKnobs` (the contact model, below). Each
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
- `ShotTuningTest` pins all 71 defaults, so a retune is always a visible, deliberate diff.
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

### The contact model: the racket sets the spin

A racket-driven shot is being built to replace the authored one. Today it sets the **spin** of every
player shot, mouse or demo hand: `ShotAssist` plans with the spin the racket's contact made (capped
at `MaxSpin`, blended by `RacketSpinShare`, 1 by default) and the search solves a launch that lands
*with* that spin. Speed and direction are still the assist's. An opponent's shot keeps the arcade
spin plan. The HUD names the spin of the player's last shot and which way it curves.

**Swipes curve like a banana, by arcade gain.** The measured lift alone saturates: a mid-court shot
bends 14-28 cm at 4 to 6 m/s whatever the spin past about 40 rev/s, too little to see. Above about
90 rev/s at the slow end of play the spin ratio leaves the lift fit's data (normal play spans
S = 0.1-1.4), its quadratic branch goes negative, and the ball curves the WRONG way. So:

- `SidespinGain` (5) multiplies the contact's sidespin, capped at `MaxSidespin` (50 rev/s), below
  the reversal.
- `CurveGain` (5.625) multiplies the sideways Magnus force on the player's shot in flight: the 10
  first tried was too much in play, and it was cut by three quarters twice (to 7.5, then 5.625). The session sets it on the world
  after a player hit (`PhysicsWorld.SetSideLift`) and resets it to 1 after an opponent hit or a
  feed, so feeds and the opponent fly the measured physics. At 1 the engine is bit-identical, and
  every scenario with no player hit in the golden trace is unchanged.
- The search flies candidates under the same gain and `LaunchSolver.Curving` aims them off target so
  the curve carries them back on. A firm swipe launches up to ~25 degrees wide and bends 76 cm.
- Under the gain a hard deep drive can bend further than the table is wide. `MaxCurve` (0.225 m,
  scaled with the gain from 0.4) scales the sidespin down, from one trial flight, until the planned bend
  fits; if the search still fails, one straight search follows. At 0.6-0.8 m up to half the first
  searches failed at ~40 ms each.
- **Cost.** A curved player contact takes ~15 ms median and ~26 ms worst (measured at 10x), against
  2-3 ms before: the curving solve re-solves each candidate two or three times.

When the racket spin landed, the golden trace changed only in scenarios with player hits. Curving
shots beat the following opponent more often.

- **The swing is averaged.** `SwingHistory` records the player's blade once per step, and the contact
  reads its velocity over `VelocityWindow` (0.040 s, 19 whole steps). The mouse reports more coarsely
  than the step, so one step's velocity is a sawtooth.
- **The contact is the engine's own.** `ContactModel` hands `ContactSolver` a blade moving at the
  averaged velocity, facing the way `CursorFollower` leaned it, with a `Material` built from
  `ContactKnobs`. There is still one contact solver. A square hit (relative velocity along the normal)
  keeps its pace and gets no spin; a glancing one trades pace for spin. An upward brush makes topspin,
  a chop backspin, a swipe to +X spin about +Y.
- **The knobs start at the measured rubber** (`Materials.Rubber`): restitution and its clamps, `Grip`
  (the friction limit) and `SpinTransfer` (the tangential restitution). `SwipeToDirection` moves a
  share of the sidespin's energy into sideways speed; at 0, the default, the ball's inertia alone
  splits a swipe.
- **It can never add energy** in the blade's frame, whatever the knobs; `ContactModelTest` sweeps
  17,000 contacts.
- **The log.** The full racket-driven shot (speed and direction too) still runs in shadow. Each player hit yields a `ShotComparison` in its `StepResult`, which the app prints as
  two lines: speed, elevation, aim, top and side spin, and whether the shot clears the cord and lands
  in by the rules, plus the far-bounce time and the net clearance of the shot played. The flights are
  `TrialFlight`s at the game step.

## The rally rules

`Referee` is a pure state machine fed facts per step. It is unit-tested without physics
(`RefereeTest`) and end to end through `GameSession` (`GameSessionTest`).

- **One bounce.** A racket may strike only after the ball has bounced on its own half; until then
  it is left out of the world, so the blade still tracks on screen but the ball passes through it.
  A second bounce on the receiver's half, a return onto the hitter's own half, a shot out, or a
  floor contact decides the point.
- **The floor scores against whoever the ball belongs to.** Before a shot bounces legally, a floor
  contact is the hitter's fault. After the bounce the ball is the receiver's to return, so it is the
  receiver's point lost; a feed counts as the player's shot. Until this was fixed, an idle player
  won every point on the `Serve` feed.
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

### The brush, made playable

A simulated human (200 ms reaction, a 125 Hz mouse, about 13 ms of timing error) brushed loops and
chops against every drill. As first built, 48-87% of its brushes met the ball near the rim and a chop
landed 27% of the time. The vertical sweep crosses the ball faster than a person can time it, the
bat cannot wait lower than the table top, and a tilted face slides away from the ball. Slowing the
sweep (13 down to 4.5 m/s) or shortening it (18 cm down to 8 cm) made it worse, not better.

- **A brush is graded as a brush.** When the blade rises or falls faster than `BrushLift` (1 m/s;
  only a brush moves it vertically), its offset along the brush counts at `BrushAlongWeight` (0.35).
  A brushed contact glances off the face; the spin comes from the contact physics wherever it
  touched. Across the face it is graded in full, and no other contact changes. Measured: the loop
  lands 64% of tries (88% of contacts, +27 rev/s), the chop 67% (97% of contacts, -14 rev/s).
- **The brush is relative** (`MouseControl.BrushFraction`): pressing the right button no longer
  moves the bat; a quarter of the screen up or down sweeps the whole band. Before, the bat jumped to
  the height of the cursor's place on the screen the moment the button went down.
- The reliable spin is still the face keys on a forward swing (W +52 rev/s); the brush is the
  harder, showier stroke.

### The face keys

`W`/`S` close and open the face, `A`/`D` tilt it right and left, while held
(`GameSession.SetFaceTilt`). A key tilts its axis by `FaceTilt` (0.6, about 31 degrees) and the face
eases there at `FaceTau`. Where the automatic lean agrees with the key it adds on (W while driving
forward closes the face to 49 degrees and tops the drive, +52 rev/s); where it disagrees it is dropped
(W while moving back or sideways gives 31 degrees closed, S while driving forward 31 open). The first
version only added the tilt, so W while backing off to reach a ball merely cancelled the lean that
opens the face: a square face, a rim contact, and the ball died on the player's own half (1 of 9 feeds
returned; `ReachabilityTest` and `CursorFollowerTest` now guard it). A bat moving back with the ball
still cannot hit hard: with W it returns a soft, high topspin ball. The tilt applies only while a key
is held, so untouched play is bit-for-bit unchanged. `A` and `D` were auto-replay and the control
readout; those moved to `U` and `I`.

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
- **`I`, the control readout.** It exists because "I could not get there" and "that ball was
  unplayable" look identical on screen and have opposite fixes. It shows the cursor, the raw and
  clamped aim, the blade, the ball and whether the blade could reach it in time.
