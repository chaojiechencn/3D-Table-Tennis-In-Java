# Physics

[Project overview](../README.md) · [Architecture](ARCHITECTURE.md) · [Game design](GAME-DESIGN.md) ·
[Agent contract](../AGENTS.md)

The model in the `engine` module, where each number came from, what the tests anchor it against,
and the alternatives that were tried and rejected. Each alternative is cheap to propose again and
expensive to disprove again; search this file before re-deriving something.

## The model

Every real-world number lives in a `*Spec` class, `Environment`, `AeroData` or `Materials`, next
to its citation. A bare constant with no citation in the engine is a bug. Anything tuned by eye is
labelled `TUNED` and says what it stands in for.

- **Ball** (`BallSpec`): 40 mm, 2.7 g, a hollow shell, so `I = (2/3)mr²`. The 2/3 (not a solid
  sphere's 2/5) makes the grip impulse `-(2/5)m·v_contact` rather than `-(2/7)m·v_contact`. It
  changes how much spin a bounce can generate by about 40%.
- **Drag**: `a = -½ρA·C_d·|v|v / m`, with `C_d` measured, not constant. It comes from a table
  over speed and spin ratio: 0.47 to 0.55 across the playing range. At 10 m/s drag is 13.7 m/s²,
  more than gravity, so air dominates a table tennis trajectory.
- **Magnus**: `a = ½ρA·C_L·|v|²·unit(ω × v) / m`, with `C_L` from a measured piecewise fit. The fit
  is converted from the literature's volume convention at one boundary by `C_L = (8/3)·C_M·S`.
  Drag and lift share one `½ρA/m` factor (`Aerodynamics.DragLiftFactor`); keep it that way.
- **Spin decay**: `dω/dt = -k·ω·|v|`, per metre rather than per second (`AeroData.SpinDecayPerMetre`).
  It is `TUNED`: no table-tennis time constant exists in the literature, and sources disagree on
  whether spin decays in flight at all. The value reproduces the earlier 5%/s at 12 m/s, so only
  the shape changed, not the magnitude.
- **Bounces** (`ContactSolver`, `Materials.Table`): velocity-dependent normal restitution,
  `e = 0.98 − 0.02·|v_n|` clamped to [0.75, 0.94]. Thin-shell buckling above about 5 m/s is real
  and measured. Then either grip or a Coulomb slide, whichever the friction cone allows. That is
  what turns topspin into a low, fast kick and backspin into a checked, dead ball. None of it is
  scripted.
- **Rubber** (`Materials.Rubber`) adds a tangential restitution `e_t = 0.819`. The topsheet stores
  tangential energy and springs it back. That, and only that, reverses incoming spin rather than
  merely absorbing it. Perfect grip is the `e_t = 0` case, so it is still one solver.
- **Collisions are swept** (`Collider.Sweep`), not overlap-only. At 60 m/s the ball moves 12.5 cm
  per step against a 6.5 cm crossing; an overlap test alone drops the hardest shots straight
  through the table.
- **Contacts resolve earliest-first**, not in list order. With a blade that can be over the table,
  "first in the list" and "the one it hit first" stop being the same answer.
- **A swept contact flies the rest of the step** after the bounce. It bounces the velocity the ball
  had at impact, not at the end of the step. Skipping either gives the ball free energy on every
  bounce.
- **Integration**: RK4 on a fixed 1/480 s step (`Simulation.Step`, `flight.Integrator`). An impulse
  is a discontinuity, so contacts resolve between flight steps and RK4 never straddles one.
- **Orientation**: `BallState.Orientation` is a unit quaternion carried for the visual spin only.

## What the tests anchor against

The engine's JUnit classes (`AerodynamicsTest`, `IntegratorTest`, `MagnusFlightTest`,
`TableBounceTest`, `NetTest`, `RacketContactTest`), and the feed checks in `game`'s `FeedsTest`,
are anchored against analysis and published numbers, not against the code's own output:

- terminal velocity against the closed form, and free fall against the exact `tanh`/`ln cosh`
  solution to 1 mm over 3 s;
- RK4 convergence order: the error shrinks about 255× for a 4× smaller step, against a
  theoretical 256×;
- the ITTF drop test: 30.5 cm must rebound 24 to 26 cm;
- `C_d` and `C_L` against the measured tables, including the lift crisis;
- Magnus direction for topspin, backspin and sidespin;
- no contact ever adds energy; topspin kicks forward and backspin checks up off the bounce; the net
  kills a ball;
- the rubber: a swung blade hits, spin reverses, the loop reaches measured speeds and spin, and
  the grip-only negative control makes strictly less spin;
- no tunnelling from 20 to 60 m/s, against the table and against a swung blade;
- 10 simulated minutes stay finite, and every feed is legal: it converges, clears the net and
  lands in.

### Three load-bearing details that are easy to wreck

- **The two closed-form checks use `DragModel.Constant(AeroData.ConstantDragCoefficient)`.**
  `v_t = √(g/kC_d)` and the `tanh`/`ln cosh` solution only exist for a constant `C_d`. The free-fall
  check is the only one that tests RK4 against real analysis. `ConstantDragCoefficient = 0.40`
  exists solely for these checks. It is not what the game flies with; do not tidy it away.
- **Interpolation between table entries is smootherstep (`Numeric.SmoothLerp`), not linear, for a
  numerical reason.** Linear interpolation puts a kink in the force field at every node, and RK4
  only reaches 4th order on a smooth right-hand side. With linear interpolation the convergence
  check measured a 30× error reduction for a 4× smaller step; with smootherstep it measures 255×.
- **The racket checks fail against any solver that works in absolute velocity.** That is their
  point.

## Conflicts in the literature, recorded rather than resolved quietly

**Terminal velocity.** A terminal velocity of 9.0 to 9.6 m/s implies `C_d ≈ 0.40`; the measured
`C_d` of 0.47 to 0.55 implies 8.3 to 8.5 m/s. They cannot both hold. The measured coefficient wins:
it is table-tennis-specific, and experiment, CFD and a fit to 277 recorded matches agree. The test
says so in its own detail line.

**The racket paper's tangential stiffness.** Its `k_p ≈ 0.019` implies a tangential restitution of
16.6: the contact patch would leave sixteen times faster than it arrived. Working back from the
same paper's own `e_t = 0.819` gives `k_p ≈ 0.0019`, a factor of ten. The model uses `e_t`, which
is dimensionless and cannot hide a units error like that. Do not copy `k_p = 0.019` back in.

## Rejected alternatives

| Tried | Why it was replaced |
| --- | --- |
| A flat `C_d = 0.40` | Below every published table tennis figure; the ball was under-dragged by about 20%. |
| `C_L = S/(2S+1)` | It is monotonic, and reality is not. Real `C_L` has a valley near `S ≈ 0.5–0.8` (the lift crisis, Miyazaki et al. 2017), and normal play spans `S ≈ 0.1–1.4`, so a rally crosses it constantly. The old curve was about 15% too weak below `S = 0.5` and up to 1.9× too strong near `S = 0.8`. No monotonic formula can represent a valley, and a test asserts the valley exists. |
| Linear interpolation of the aero tables | Kinks the force field and costs RK4 its order (30× instead of 255×). |
| A grip-or-slide bounce for rubber | Provably cannot reverse spin; the grip-only negative control shows it. |
| A solver in absolute velocity | A swung blade reads as separating and does nothing. |
| An overlap-only collision test | Drops the fastest shots through the table. |
| Hand-picked feed launch velocities | With drag this strong, almost all of them sailed off the end. `LaunchSolver` solves the angle from intent instead. |
| Asking a `PhysicsWorld` where a shot lands | The table bounces the ball away first, so the answer is the second descent. `TrialFlight` is contact-free for this reason. |

## Costs worth knowing

- **`LaunchSolver.Halvings = 28`.** It was 60, the last bit of a double over an 80° bracket. 28
  halvings is 5e-9 rad, or 14 nm of landing position on a 2.7 m shot. The count matters because the
  shot assist calls the solver per candidate, a dozen or more times per contact. With 60 halvings
  one contact cost 25.8 ms, longer than a 16.7 ms frame.
- **Shots curve less than they did under the old aero model.** Sidespin deflection dropped about
  16%, and backspin floats more. That is the measured behavior, not a regression.
