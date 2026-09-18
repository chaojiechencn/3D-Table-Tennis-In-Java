# `pong/` — the source map

Directories are named for **what the code is for**, not what it is. The layout follows
[How I structure my game projects](https://joshanthony.info/2021/12/06/how-i-structure-my-game-projects/),
adapted to Java: a `core/` that would transplant into another game unchanged, `game_objects/` and
`game_world/` for the things in the world and the world they live in, `systems/` for the rules,
`connectors/` for the wiring between systems, and a leading underscore on anything that does not
ship.

Every directory has a README saying what belongs in it. If a new file has no obvious home, that is
a signal about the file, not about the layout.

| Directory | Holds | JavaFX? |
| --- | --- | --- |
| [`core/`](core/README.md) | Nothing that knows what a ball is | no |
| [`config/`](config/README.md) | Measured constants and tuned knobs | no |
| [`game_objects/`](game_objects/README.md) | The ball, the rackets — and a `view/` each | in `view/` only |
| [`game_world/`](game_world/README.md) | The simulated world, and the court drawn around it | in `view/` only |
| [`systems/`](systems/README.md) | Collision, aim, control, opponent, scoring, shot-making | no |
| [`systems/connectors/`](systems/connectors/README.md) | Making independent systems interact | no |
| [`assets/`](assets/README.md) | Generated textures and materials | yes |
| [`screens/`](screens/README.md) | What the player is looking at: the match, the camera | yes |
| [`ui/`](ui/README.md) | The HUD | yes |
| [`_debug/`](_debug/README.md) | Overlays that exist to answer a question | yes |
| [`helpers/`](helpers/README.md) | Ray-casting and the physics↔scene boundary | yes |

## The one rule the tests depend on

**JavaFX may only be imported from `screens/`, `ui/`, `_debug/`, `assets/`, `helpers/` and the
`view/` leaves.** Everything else — the whole simulation and every game rule — stays headless, which
is what lets `pong._tests.PhysicsTest` and `pong._tests.RallyTest` grade it without opening a window.

Dependencies run one way: a `view/` leaf depends on the package above it, never the reverse.

```
screens ─→ ui, _debug, systems, game_objects, game_world
systems ─→ game_objects, game_world, core, config
game_world ─→ game_objects, systems/collision, core, config
game_objects ─→ core, config
core ─→ nothing
```

Verify it:

```bash
grep -rl --include=*.java "import javafx" src/main/java/pong \
  | grep -vE "/(view|screens|ui|_debug|assets|helpers)/"   # must print nothing
```

See [`docs/DESIGN.md`](../../../../docs/DESIGN.md) for why each boundary exists, and
[`AGENTS.md`](../../../../AGENTS.md) for the invariants that must not be broken.
