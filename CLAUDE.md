# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A 3D table tennis game — mouse-controlled paddle, spin/physics simulation, AI opponent. CS Independent
Study, Term 1 (student: Chaojie Chen). `README.md` holds the scope, roadmap, and reference links; keep it
in sync when milestones land.

Milestones: physics demo (done, Aug 27) → playable demo (Sep 17: full point vs AI, mouse control, spin,
serving, scoring) → final demo (Oct 7: menus, multiple paddles).

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
Once a build tool exists, replace this section with the real build/run/test commands.

## Architecture notes

Nothing is implemented yet, so the constraints below come from the project's own goals rather than
existing code:

- **Fixed timestep.** The physics must behave identically on fast and slow machines — accumulate elapsed
  time in the `AnimationTimer` and step the simulation at a fixed dt, rendering with interpolation. See
  Gaffer On Games' "Fix Your Timestep", cited in the README.
- **Keep simulation and scene graph separate.** Physics state (position, velocity, spin) is plain math on
  vectors; JavaFX nodes are a view of that state updated once per frame. This is what keeps the
  simulation testable without a live `Stage`.
- **Spin is core, not a bonus.** Magnus force in flight, and spin transfer on table/paddle/net contact —
  it shapes the collision code, so design for it up front rather than bolting it on.
- **AI predicts, not follows.** The opponent should solve for where the ball will arrive, not chase its
  current position.
