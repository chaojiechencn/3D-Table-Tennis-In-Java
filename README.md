# 3D Table Tennis

A 3D table tennis game in Java 21 and JavaFX. Move the racket with your mouse, shape shots with
your swing, and play an AI opponent over a simulation with real spin, drag and spin-coupled bounces.
Inspired by *Ping Pong Fury*.

**In development:** rallies, assisted shots and match scoring work. Real serving, a predicting
opponent and menus are next.

## Run

Install a **JDK 21**. The included Gradle wrapper handles the build and JavaFX dependencies.

```powershell
# Windows PowerShell
.\gradlew.bat run
```

```bash
# macOS / Linux / Git Bash
bash ./gradlew run
```

For IntelliJ, VS Code, manual compilation with Liberica Full JDK, and troubleshooting, see
[Development](docs/DEVELOPMENT.md).

## Play

- Move the mouse left/right to move across the table, and up/down to reach forward or retreat.
- Swipe sideways to aim; drive forward for pace and topspin; pull back for a softer cut.
- Hold the right mouse button to brush up/down while depth stays fixed.
- Catch the ball near the racket centre for an assisted return. Rim contacts can miss.
- Use `Space` to pause, `R` to replay a feed and `Esc` to quit.

See [Gameplay and controls](docs/GAMEPLAY.md) for every control, current features and match rules.

## Validate

```powershell
.\gradlew.bat check
```

This runs both headless suites: `pong._tests.PhysicsTest` (101 checks) and `pong._tests.RallyTest`
(29 checks).
See [Validation](docs/DEVELOPMENT.md#validation) for individual suites and what they cover.

## Find your way around

Directories are named for **what the code is for**, and each one has a README saying what belongs
in it. Start at [`src/main/java/pong/README.md`](src/main/java/pong/README.md) for the full map.

| Path | Contents |
| --- | --- |
| `src/main/java/pong/core/` | Code with nothing table-tennis about it |
| `src/main/java/pong/config/` | Measured constants and tuned knobs |
| `src/main/java/pong/game_objects/` | The ball and the rackets, each with a `view/` |
| `src/main/java/pong/game_world/` | The simulated world, and the court drawn around it |
| `src/main/java/pong/systems/` | Collision, aim, control, opponent, scoring, shot-making |
| `src/main/java/pong/screens/` | `MatchScreen` — the entry point, loop and wiring |
| `src/main/java/pong/ui/`, `assets/`, `helpers/` | HUD, generated materials, the space conversion |
| `src/main/java/pong/_debug/` | Overlays; the game runs without them |
| `src/test/java/pong/_tests/` | The two headless validation suites |
| `docs/` | Gameplay, development instructions and design rationale |
| `_tools/` | Optional development utilities, not part of the game |
| `gradle/`, `gradlew`, `gradlew.bat` | Gradle wrapper |
| `build/`, `out/` | Generated output; ignored by Git |

An underscore prefix means the contents do not ship: `_debug/`, `_tests/`, `_tools/`.

[Documentation index](docs/README.md) · [Agent operating guide](AGENTS.md)
