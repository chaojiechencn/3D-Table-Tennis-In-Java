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
      Not a game yet, just physics on screen.
- [ ] **Playable demo** — *Sep 17*. A full point against the AI: mouse control, spin, serving, scoring.
- [ ] **Final demo** — *Oct 7*. Menus, AI opponent, and multiple paddles that play differently.

**Scope — core:** mouse-controlled paddle with the ball reflecting off it; real physics (spin, shot speed
from power applied); collision and rules (table, net, out of bounds); a basic AI good enough to play a
full point against; serving and scoring.

**Scope — stretch:** multiple paddles with different characteristics (more spin vs. more power); currency
earned by beating the AI, spent in a shop.

**Learning goals** (what the student is here to learn — prefer explaining the reasoning over handing over
finished code):

| Area | Specifically |
| --- | --- |
| **Physics** | How a spinning ball moves through air, what happens to spin on table/paddle contact, and keeping the simulation stable over long runs. |
| **JavaFX** | The 3D scene graph — meshes, materials, camera, lighting — and a game loop on `AnimationTimer` that runs the same on fast and slow machines. |
| **AI** | Getting the opponent to predict where the ball is going rather than following it. |

## Stack

- **Java 21** — the IntelliJ project JDK is `liberica-21` (`.idea/misc.xml`). Note the `java` on PATH is
  22.0.1, so a command-line run can differ from an IDE run; prefer the 21 toolchain.
- **JavaFX** for all rendering and input — the 3D scene graph (`Group`, `MeshView`/`Box`/`Sphere`,
  `PhongMaterial`, `PerspectiveCamera`, lights), `AnimationTimer` for the game loop, `Scene` mouse events
  for paddle control. Do not reach for Swing/AWT or Java2D.
- JavaFX is **not bundled** with modern JDKs. It has to come in either as a build dependency
  (`org.openjfx:javafx-controls`/`javafx-graphics`) or as the standalone OpenJFX SDK passed via
  `--module-path <jfx>/lib --add-modules javafx.controls,javafx.fxml`.

## Build & run

There is no build system yet — the repo is a plain IntelliJ module (`.idea/`, output to `out/`) with no
`pom.xml` or `build.gradle`, and no source tree or tests. Adding Maven or Gradle is an open decision;
either one solves the JavaFX module-path problem, so raise it before hand-rolling `javac` invocations.
Once a build tool exists, replace this section with the real build/run/test commands, and replace the
"Running" section of `README.md` too — it currently tells the reader to run from IntelliJ.

Note: the Aug 27 physics demo is not in this repo. Only docs and IntelliJ project files are committed.

## Architecture notes

Nothing is implemented yet, so the constraints below come from the project's own goals rather than
existing code:

- **Fixed timestep.** The physics must behave identically on fast and slow machines — accumulate elapsed
  time in the `AnimationTimer` and step the simulation at a fixed dt, rendering with interpolation. See
  Gaffer On Games' "Fix Your Timestep" below.
- **Keep simulation and scene graph separate.** Physics state (position, velocity, spin) is plain math on
  vectors; JavaFX nodes are a view of that state updated once per frame. This is what keeps the
  simulation testable without a live `Stage`.
- **Spin is core, not a bonus.** Magnus force in flight, and spin transfer on table/paddle/net contact —
  it shapes the collision code, so design for it up front rather than bolting it on.
- **AI predicts, not follows.** The opponent should solve for where the ball will arrive, not chase its
  current position.

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
