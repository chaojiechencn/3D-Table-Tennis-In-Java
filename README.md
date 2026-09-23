# 3D Table Tennis

A 3D table tennis game in Java 21 and JavaFX. Move the paddle with your mouse, shape shots with
your swing, and rally against an AI opponent. Underneath is a validated simulation: real spin,
measured drag, a Magnus lift curve and spin-coupled bounces. Inspired by *Ping Pong Fury*.

**In development:** rallies, assisted shots and match scoring work. Real serving, a predicting
opponent and menus are next.

## Run

Install a **JDK 21**. The included Gradle wrapper handles the build, JavaFX and JUnit.

```powershell
# Windows PowerShell
.\gradlew.bat run
```

```bash
# macOS / Linux / Git Bash
bash ./gradlew run
```

For IDE setup, a manual build with Liberica Full JDK and deterministic screenshots, see
[Development](docs/DEVELOPMENT.md).

## Play

- Move the mouse left/right to move across the table, and up/down to reach forward or retreat.
- Swipe sideways to aim; drive forward for pace and topspin; pull back for a softer cut.
- Hold the right mouse button to brush up or down while depth stays fixed.
- Catch the ball near the paddle's centre for an assisted return. Rim contacts can miss.
- `Space` pauses, `R` replays the feed, `M` lets the game play itself, and `Esc` quits.

[Gameplay and controls](docs/GAMEPLAY.md) lists every control, feature and rule.

## Validate

```powershell
.\gradlew.bat check
```

This runs every module's JUnit checks: 166 falsifiable claims, each printed with its measured
number. One of them replays a golden trace that pins the whole game bit for bit. See
[Development](docs/DEVELOPMENT.md#validation).

## Find your way around

| Path | Contents |
| --- | --- |
| `engine/` | `tabletennis.engine`: the physics, with no dependencies |
| `game/` | `tabletennis.game`: rules, players, shot assist and scoring; no JavaFX |
| `app/` | `tabletennis.app`: the JavaFX game |
| `docs/` | Gameplay, development, architecture, physics and game design |
| `gradle/`, `gradlew`, `gradlew.bat` | Gradle wrapper |

| Document | Use it for |
| --- | --- |
| [Gameplay and controls](docs/GAMEPLAY.md) | Playing the game |
| [Development](docs/DEVELOPMENT.md) | Building, testing, IDE setup and captures |
| [Architecture](docs/ARCHITECTURE.md) | Modules, the step data flow, invariants, coordinates, the loop |
| [Physics](docs/PHYSICS.md) | The model, its sources, its validation and rejected alternatives |
| [Game design](docs/GAME-DESIGN.md) | The shot assist, rally rules, controls and opponent, with measurements |
| [Agent contract](AGENTS.md) | What coding agents must not break, and known defects |
