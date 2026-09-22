# Development

[Project overview](../README.md) · [Gameplay](GAMEPLAY.md) · [Design rationale](DESIGN.md)

## Requirements

The standard build uses **JDK 21** and the committed Gradle wrapper. Gradle resolves JavaFX and
downloads its own distribution on first use, so the first build needs network access. A global
Gradle installation is unnecessary.

The optional manual build uses **Liberica Full JDK 21**, which bundles JavaFX as system modules.
An ordinary JDK supports the Gradle build; it cannot compile or launch JavaFX directly without
additional modules.

## Build and run

From the project root:

| Action | Windows PowerShell | macOS / Linux / Git Bash |
| --- | --- | --- |
| Compile the game | `.\gradlew.bat classes` | `bash ./gradlew classes` |
| Run the game | `.\gradlew.bat run` | `bash ./gradlew run` |
| Run both validation suites | `.\gradlew.bat check` | `bash ./gradlew check` |
| Validate physics | `.\gradlew.bat selfTest` | `bash ./gradlew selfTest` |
| Validate gameplay | `.\gradlew.bat rallyTest` | `bash ./gradlew rallyTest` |
| Build and validate | `.\gradlew.bat build` | `bash ./gradlew build` |

The application entry point remains `Table_Tennis_In_3D`.

## IDE setup

### IntelliJ IDEA

1. Open the project root and import `build.gradle` as a Gradle project.
2. Select a JDK 21 for the project SDK and Gradle JVM.
3. Reload the Gradle project after source or build configuration changes.
4. Run the Gradle `run` task to play, or `check`, `selfTest` and `rallyTest` to validate.

Gradle marks `src/main/java` as production code and `src/test/java` as test code. IntelliJ's
generated `.idea/` files and `.iml` modules are local and ignored by Git. Avoid moving or
hand-editing a generated module to change the source layout; update the Gradle project instead.

### VS Code

Open the project root and use the committed tasks for running the game and its validation suites.
The tasks invoke the wrapper, so they use the same source layout and dependencies as terminal builds.

## Validation

`physics.SelfTest` and `play.RallyTest` are executable Java classes with `main` methods. They do
not require JUnit. Their package names remain unchanged even though they live under
`src/test/java`.

- `physics.SelfTest`: **101 checks** against analytic results, published measurements and ITTF
  rules. It covers flight, spin, energy, collision handling, preset shots and fast-moving paddles.
- `play.RallyTest`: **44 checks** covering opponent returns, shot assistance and its tuning,
  paddle reach and speed, cursor control, brushing, match scoring, and the rally rules played
  through `play.GameSession`.

The Gradle `test`, `check` and `build` tasks all run both suites through the dedicated `selfTest`
and `rallyTest` tasks. Each suite prints PASS/FAIL and measured details, then exits non-zero if
a check fails.

Run the physics suite after physics changes, the rally suite after gameplay changes, and both
after changes to shared code, source layout or build configuration. Rendering changes must at
least compile. Both suites must stay at 100%; do not widen a threshold to fit a regression.

## Source layout

```text
src/
  main/java/
    Table_Tennis_In_3D.java    JavaFX entry point: frame loop, input, views and capture
    physics/                  Headless simulation and measured physical constants
    play/                     Headless game logic: GameSession, shot assistance, scoring
    render/                   JavaFX views and input geometry
  test/java/
    physics/SelfTest.java      Physics validation
    play/RallyTest.java        Game and control validation
```

Tests retain their `physics` and `play` packages. Production code stays in the same packages and
keeps the same public entry points. `play.GameSession` is the one gameplay implementation: the
application drives it for play and `RallyTest` drives it for validation, and rendering reads its
immutable snapshots. The game layer depends on physics; physics does not depend
on gameplay or JavaFX. See [Design rationale](DESIGN.md#architecture) for class responsibilities
and the boundaries that must remain intact.

## Manual build with Liberica Full JDK 21

This remains a plain `javac` project underneath Gradle. To compile without downloading dependencies,
use a **Full** Liberica JDK 21 from [BellSoft](https://bell-sw.com/pages/downloads/), which includes
JavaFX. Adjust the example path to your installation.

```powershell
$JDK = "$env:USERPROFILE\.jdks\jdk-21.0.12.1-full\bin"
$javaSources = (Get-ChildItem -Path src/main/java,src/test/java -Recurse -Filter *.java).FullName
& "$JDK\javac" -d out/production/3D-Table-Tennis-In-Java $javaSources
& "$JDK\java" -cp out/production/3D-Table-Tennis-In-Java Table_Tennis_In_3D
& "$JDK\java" -cp out/production/3D-Table-Tennis-In-Java physics.SelfTest
& "$JDK\java" -cp out/production/3D-Table-Tennis-In-Java play.RallyTest
```

```bash
JDK=~/.jdks/jdk-21.0.12.1-full/bin
"$JDK/javac" -d out/production/3D-Table-Tennis-In-Java $(find src/main/java src/test/java -name '*.java')
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java Table_Tennis_In_3D
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java physics.SelfTest
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java play.RallyTest
```

No `--module-path` or `--add-modules` is needed with the Full distribution. A missing
`javafx` package during manual compilation means the selected JDK does not include JavaFX.
Use the Full build, or use the standard Gradle workflow.

## Rendering captures

The application has an offline capture mode for examining a fixed shot and camera view.
After a manual compilation with the Full JDK:

```powershell
& "$JDK\java" -cp out/production/3D-Table-Tennis-In-Java Table_Tennis_In_3D "--shot=Topspin loop" --at=0.18 --view=SIDE --ball2x=true --out=out/frame.png
```

`--shot` accepts a name from `Shots.ALL`; quote names containing spaces. `--view` accepts a
`render.CameraRig.View` value (`BEHIND`, `SIDE`, `HIGH`, `LOW`, `TOP`). `--at` is in simulated
seconds. `--out` disables auto-replay so a capture past the end of a rally still completes.

## Files and generated output

- Commit application code, tests, documentation, development tools and wrapper files.
- Gradle writes generated output to `build/`; manual compilation uses the ignored `out/`.
- `.gradle/`, `bin/`, IDE and editor state (`.idea/`, `.vscode/`, `.claude/`) and compiled
  classes are generated or local, and never committed.
- Keep the repository root for project entry points. Put longer documentation in `docs/`
  and optional development utilities in `tools/`.

The [IntelliJ MCP bridge](../tools/intellij-mcp-bridge/README.md) is an optional, dependency-free
single-file Java program, separate from the game's build. The project is Java only.

## Continuous integration

GitHub Actions runs `check` on Ubuntu and Windows with Java 21 for every push and pull request
(`.github/workflows/ci.yml`), using the committed wrapper. Both validation suites must pass.
