# 3D Table Tennis

A 3D table tennis game in Java. You move the paddle with your mouse, the ball carries real spin, and
an AI opponent plays you for the point. Inspired by the mobile game *Ping Pong Fury*.

> **In development.** The physics simulation runs and can be watched; the game around it is being built.

---

## Gameplay

The paddle follows your mouse, and only your mouse. Point at the table and the paddle goes
there — including out over the table, low, for a short ball that has died in front of you; point
higher up the table and it comes back to meet a deep or high one. How you move it through the ball
aims the shot: **swipe it sideways** to send the ball that way (swipe right, ball goes right),
**push it up-table through the ball** for pace and depth, **lift it** to loop the ball higher with
more topspin, **drag it down** to cut under the ball; a still paddle just blocks it back soft. The game then keeps that shot playable — the ball is aimed at a real spot
on the other side, and the shot is checked all the way to the bounce before it is played, so it
clears the net and lands in rather than flying off the end. It is assisted, arcade-style, more
*Ping Pong Fury* than a physics sim (though a full physics simulation runs underneath — see
below).

What that buys, measured over 75 different ways of swinging the paddle at the same ball: the raw
physics puts 11 of them on the table and throws the ball up to 2.4 m wide at 24 m/s; through the
assist all 75 land, none more than 0.38 m off centre, none faster than 12.4 m/s. Hitting harder
always does a little more and never a lot more, so a long rally cannot spiral into a rocket.

You can only return the ball **after it has bounced on your side** (real table-tennis rule). A ball
that bounces twice, hits the net, or sails past the end line ends the point and the next serve is
fed in. There's no scoreboard yet — the rally just restarts.

The camera is your main tool for reading the ball: it cuts between a close view and a wide one
depending on who last hit — close after your shot, wide after the opponent's.

Under the arcade assist there is a full physics simulation: in flight the ball is simulated rather
than scripted — spin curves it in the air, and it changes the bounce when the ball lands (heavy
topspin kicks forward, backspin sits up). The assist shapes the ball only at the moment a racket
hits it; between hits it flies for real.

## Features

**Playable now**

- Mouse-controlled paddle that reaches in over the table when YOU point it there
- Assisted arcade shots — drive for pace, swipe to aim; the game keeps the ball in play
- Rallies against the AI, with the one-bounce rule and points that end on a net / long / double bounce
- A two-view rally-cam that cuts on who last hit, plus five preset views, slow motion, single-step
- Full ball-flight simulation between hits: spin, air drag, Magnus curve, spin-coupled bounces
- A menu of shots to feed in, a grey no-spin "ghost" trail, and a `V` debug overlay that shows
  exactly how a shot was chosen

**In progress**

- Serving off your own blade (right now a ball is fed in each rally)
- Scoring and rules
- An AI opponent that reads where the ball is going rather than tracking it

**Planned**

- Multiple paddles that play differently — one built for spin, one for power
- Earn currency by beating the AI and spend it in a shop

## Controls

Once the game exists:

| Input | Action |
| --- | --- |
| Mouse | Move the paddle — across, up, and in over the table |
| Point further up the table | Reaches in for a short ball; point lower to come back |
| Swipe the paddle sideways | Sends the ball that way — swipe right, ball goes right |
| Drive the paddle through the ball | Pace and depth |
| Lift the paddle up through the ball | Higher, with more topspin |
| Drag the paddle down through the ball | Cuts under it for backspin |
| Still paddle | Soft block back |

There is no button to press — the shot is entirely in the mouse movement.

Other keys (carried over from the physics demo):

| Key | Action |
| --- | --- |
| `1`–`9`, `0` | Pick the feed shot |
| `N` / `P`, `←` `→` | Next / previous feed |
| `R` | Replay the current feed |
| `Space` | Pause |
| `.` | Single physics step |
| `[` `]` | Slow down / speed up (starts at 0.45×) |
| `F` | Rally-cam on / off |
| `C` | Cycle the preset camera views (turns the rally-cam off) |
| `V` | Shot-assist debug overlay (paddle and ball velocity, raw / intended / final shot, target, predicted landing) |
| `G` | Toggle the no-spin ghost trail |
| `T` | Toggle the flight trail |
| `B` | Draw the ball at 2× (physics still uses 40 mm) |
| `A` | Toggle auto-replay |
| `H` | Toggle the on-screen legend |
| `Esc` | Quit |
| Left-drag / scroll | Orbit / zoom the camera (turns the follow-cam off) |

## Requirements

**Liberica "Full" JDK 21** — <https://bell-sw.com/pages/downloads/>. The Full build bundles JavaFX 21 as
system modules, so there is nothing else to install and no module path to configure. The plain JDK 21
does *not* include JavaFX and will fail at launch.

There are no other dependencies and no build script — this compiles with stock `javac`.

## Running

> **I have only ever built and run this from IntelliJ.** That is the only setup I have actually
> tested. The terminal commands below are provided for reference and are not the path I use, so if
> something there does not work for you, try it from the IDE first.

In IntelliJ: open the project, set the project SDK to the Full JDK 21, and use the committed
**MrPong** run configuration. **Physics SelfTest** runs the headless validation suite.

From a terminal (untested by me):

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

`physics.SelfTest` is a headless suite of 101 checks that compares the simulation against numbers that
did not come from this program — closed-form solutions of the same equations, published measurements,
and the ITTF Laws. It checks terminal velocity against the analytic result, free fall against the exact
`tanh` solution, the ITTF drop test (30.5 cm in, 24–26 cm out), that RK4 really is fourth-order, that
spin curves the ball the correct way, that no bounce ever adds energy, and that nothing tunnels through
the table at smash speed.

```bash
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java physics.SelfTest
```

It prints PASS/FAIL per check and exits non-zero if anything fails. I run it from the
**Physics SelfTest** configuration in IntelliJ.

`play.RallyTest` is a second headless suite, 7 checks, covering the game rather than the physics:
that the AI reaches every shot fed at it, puts every one back over the net, lands every one on the
table, never returns the ball faster than the impulse could have sent it, never launches it out of
the hall, and that flinging the mouse cannot move the paddle faster than a person carries a bat.

## Built with

Java 21 and JavaFX — the 3D scene graph for rendering, `AnimationTimer` for the game loop. No libraries.
