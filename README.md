# 3D Table Tennis

A 3D table tennis game in Java. You move the paddle with your mouse, the ball carries real spin, and
an AI opponent plays you for the point. Inspired by the mobile game *Ping Pong Fury*.

> **In development.** The physics simulation runs and can be watched; the game around it is being built.

---

## Gameplay

The paddle follows your mouse — wherever the cursor is, that is where the paddle is. How you move
through the ball is the shot: swing speed sets the power, and the direction you cut across it sets the
spin. Hit it flat and it drives; brush up the back of it and it loops over the net and dips.

The ball is simulated rather than scripted. Spin curves it in the air, and it changes the bounce when
the ball lands — a heavy topspin kicks forward off the table, backspin sits up and slows down.

## Features

**Playable now — the physics demo**

- Full ball flight simulation: spin, air drag, and Magnus curve
- Bounces that couple spin and speed — topspin kicks forward, backspin checks up
- A net that kills a ball instead of bouncing it back, and in/out calls against real ITTF dimensions
- A menu of eleven shots that differ mainly in their spin, so what changes on screen is the spin
- A grey "ghost" trail: the same shot with the spin deleted, flown alongside, so the curve is a
  visible gap rather than a claim
- Five camera views, slow motion, single-step, and live readouts of spin ratio, lift and drag

**In progress**

- Mouse-controlled paddle with spin on contact
- Serving, scoring, and out-of-bounds calls
- An AI opponent that reads where the ball is going and moves to meet it

**Planned**

- Multiple paddles that play differently — one built for spin, one for power
- Earn currency by beating the AI and spend it in a shop

## Controls

Once the game exists:

| Input | Action |
| --- | --- |
| Mouse | Move the paddle |
| Mouse movement through the ball | Sets shot power and spin |

In the physics demo as it stands:

| Key | Action |
| --- | --- |
| `1`–`9`, `0` | Pick a shot |
| `N` / `P`, `←` `→` | Next / previous shot |
| `R` | Replay the current shot |
| `Space` | Pause |
| `.` | Single physics step |
| `[` `]` | Slow down / speed up |
| `G` | Toggle the no-spin ghost trail |
| `T` | Toggle the flight trail |
| `B` | Draw the ball at 2× (physics still uses 40 mm) |
| `C` | Cycle camera views |
| `A` | Toggle auto-replay |
| `H` | Toggle the readouts |
| `Esc` | Quit |
| Drag / scroll | Orbit / zoom the camera |

## Requirements

**Liberica "Full" JDK 21** — <https://bell-sw.com/pages/downloads/>. The Full build bundles JavaFX 21 as
system modules, so there is nothing else to install and no module path to configure. The plain JDK 21
does *not* include JavaFX and will fail at launch.

There are no other dependencies and no build script — this compiles with stock `javac`.

## Running

In IntelliJ: open the project, set the project SDK to the Full JDK 21, and use the committed
**MrPong** run configuration. **Physics SelfTest** runs the headless validation suite.

From a terminal:

```powershell
# PowerShell
$JDK = "$env:USERPROFILE\jdk\jdk-21.0.7-full\bin"
& "$JDK\javac" -d out\production\3D-Table-Tennis-In-Java (Get-ChildItem -Recurse src -Filter *.java).FullName
& "$JDK\java" -cp out\production\3D-Table-Tennis-In-Java MrPong
```

```bash
# bash
JDK=~/jdk/jdk-21.0.7-full/bin
"$JDK/javac" -d out/production/3D-Table-Tennis-In-Java $(find src -name '*.java')
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java MrPong
```

Adjust the JDK path to wherever you installed it.

## Is the physics actually right?

`physics.SelfTest` is a headless suite of 68 checks that compares the simulation against numbers that
did not come from this program — closed-form solutions of the same equations, published measurements,
and the ITTF Laws. It checks terminal velocity against the analytic result, free fall against the exact
`tanh` solution, the ITTF drop test (30.5 cm in, 24–26 cm out), that RK4 really is fourth-order, that
spin curves the ball the correct way, that no bounce ever adds energy, and that nothing tunnels through
the table at smash speed.

```bash
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java physics.SelfTest
```

It prints PASS/FAIL per check and exits non-zero if anything fails.

## Built with

Java 21 and JavaFX — the 3D scene graph for rendering, `AnimationTimer` for the game loop. No libraries.
