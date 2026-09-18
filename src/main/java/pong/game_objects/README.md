# `game_objects/` — the things in the world

Objects that exist inside the game world and are acted upon by it: the ball and the two rackets. The
world itself is next door in `game_world/`.

Each object owns its simulation at the top level and its JavaFX node in a `view/` leaf beneath it.
The split is load-bearing, not tidiness: `ball/Ball` and `racket/Racket` are plain Java, so the
validation suites can play a whole rally with no window open, while `ball/view/BallView` and
`racket/view/RacketView` read that state and draw it. **A `view/` may never write the state it
draws.**

| | Simulation | View |
| --- | --- | --- |
| `ball/` | `Ball` (a state snapshot), `Aero` (the forces in flight), `Integrator` (RK4) | `BallView`, `BallShadow`, `Trail` |
| `racket/` | `Racket` and its `Blade` collider — kinematic, its velocity measured from its own motion rather than invented | `RacketView` |
