# 3D Table Tennis

A 3D table tennis game in Java — mouse-controlled paddle, real ball physics, AI opponent.
Heavily inspired by the mobile game *Ping Pong Fury*.

**CS Independent Study · Term 1** · Student: Chaojie Chen
**Built with:** Java 21 · JavaFX (3D scene graph)
**Status:** physics demo done. Next milestone: playable demo (*Sep 17*).

---

## Concept

The paddle is controlled by the mouse: wherever the mouse is, that is where the paddle is. Once the
ball touches the paddle, the physics gets simulated and the ball reflects back off it.

**Deliverable:** a finished 3D ping pong game with good graphics and player control.

---

## Scope

### Core

- Mouse-controlled paddle, with the ball reflecting off it on contact.
- Real physics: spin on the ball, and a shot speed based on how much power went into it.
- Collision and rules: does the ball hit the table, hit the net, or go out of bounds.
- A basic AI opponent — good enough to play a full point against.
- Serving and scoring.

### Stretch

- Multiple paddles that play differently — one with more spin, one with more power.
- Money and a shop: earn by beating the AI, spend it on paddles.

---

## Roadmap

- [x] **Physics demo** — *Aug 27*
  The ball flying with spin, curving in the air, and bouncing right off the table and net.
  Not a game yet — just the physics running on screen.
- [ ] **Playable demo** — *Sep 17*
  A full point against the AI, with mouse control, spin, serving, and scoring.
- [ ] **Final demo** — *Oct 7*
  The finished game with menus, an AI opponent, and multiple paddles that play differently.

---

## What I need to learn

| Area | What specifically |
| --- | --- |
| **Physics** | How a spinning ball moves through the air, what happens to the spin when the ball hits the table or a paddle, and how to keep the simulation from breaking after running a while. |
| **JavaFX** | The JavaFX 3D scene graph — meshes, materials, camera, lighting — and writing a game loop on `AnimationTimer` so the physics runs the same on a fast or slow computer. |
| **AI** | Getting the opponent to figure out where the ball is going instead of just following it. |

---

## References

| Source | For |
| --- | --- |
| [Gaffer On Games](https://gafferongames.com/) | Game loops and physics timing — see [Fix Your Timestep](https://gafferongames.com/post/fix_your_timestep/). |
| [Red Blob Games](https://www.redblobgames.com/) | Vector math and AI. |
| [Scratchapixel](https://www.scratchapixel.com/) | 3D rendering. |
| [OpenJFX](https://openjfx.io/) and the [JavaFX API docs](https://openjfx.io/javadoc/21/) | Graphics — the 3D scene graph, camera, and materials. |
| Research papers on table tennis ball trajectories | Real numbers to check the simulation against, instead of guessing. |
| AI tools like Claude or ChatGPT | Last resort. |
