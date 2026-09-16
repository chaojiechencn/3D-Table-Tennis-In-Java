# 3D Table Tennis

A 3D table tennis game in Java 21 and JavaFX. Move the paddle with your mouse, shape shots with
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
- Catch the ball near the paddle centre for an assisted return. Rim contacts can miss.
- Use `Space` to pause, `R` to replay a feed and `Esc` to quit.

See [Gameplay and controls](docs/GAMEPLAY.md) for every control, current features and match rules.

## Validate

```powershell
.\gradlew.bat check
```

This runs both headless suites: `physics.SelfTest` (101 checks) and `play.RallyTest` (29 checks).
See [Validation](docs/DEVELOPMENT.md#validation) for individual suites and what they cover.

## Find your way around

| Path | Contents |
| --- | --- |
| `src/main/java/` | Game entry point and the `physics`, `play`, `render` packages |
| `src/test/java/` | Headless validation suites, in their corresponding packages |
| `docs/` | Gameplay, development instructions and design rationale |
| `tools/` | Optional development utilities |
| `gradle/`, `gradlew`, `gradlew.bat` | Gradle wrapper |
| `build/`, `out/` | Generated output; ignored by Git |

[Documentation index](docs/README.md) · [Agent operating guide](AGENTS.md)
