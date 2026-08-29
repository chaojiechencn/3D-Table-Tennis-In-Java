# 3D Table Tennis

A 3D table tennis game in Java. You move the paddle with your mouse, the ball carries real spin, and
an AI opponent plays you for the point. Inspired by the mobile game *Ping Pong Fury*.

> **In development.** The physics simulation runs; the game around it is being built.

---

## Gameplay

The paddle follows your mouse — wherever the cursor is, that is where the paddle is. How you move
through the ball is the shot: swing speed sets the power, and the direction you cut across it sets the
spin. Hit it flat and it drives; brush up the back of it and it loops over the net and dips.

The ball is simulated rather than scripted. Spin curves it in the air, and it changes the bounce when
the ball lands — a heavy topspin kicks forward off the table, backspin sits up and slows down.

## Features

**Playable now**

- Full ball flight simulation — spin, air drag, and curve
- Bounces off the table and the net

**In progress**

- Mouse-controlled paddle with spin on contact
- Serving, scoring, and out-of-bounds calls
- An AI opponent that reads where the ball is going and moves to meet it

**Planned**

- Multiple paddles that play differently — one built for spin, one for power
- Earn currency by beating the AI and spend it in a shop

## Controls

| Input | Action |
| --- | --- |
| Mouse | Move the paddle |
| Mouse movement through the ball | Sets shot power and spin |

## Requirements

- **JDK 21**
- **JavaFX 21** — not bundled with the JDK; get the SDK from [openjfx.io](https://openjfx.io/)

## Running

There's no build script in the repo yet, so there is no one-line run command to give you. Open the
project in IntelliJ with a JDK 21 SDK selected, add the JavaFX SDK as a library, and run with:

```
--module-path <path-to-javafx-sdk>/lib --add-modules javafx.controls
```

This section gets a real command once Maven or Gradle is set up.

## Built with

Java 21 and JavaFX — the 3D scene graph for rendering, `AnimationTimer` for the game loop.
