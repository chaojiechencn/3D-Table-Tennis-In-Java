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

The application entry point is `pong.screens.MatchScreen`.

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

Open the project root and run the Gradle wrapper from the integrated terminal — the commands in the
table above are the whole interface. `.vscode/` is ignored, so no task definitions are committed;
add your own locally if you want them on a keybinding.

## Validation

`pong._tests.PhysicsTest` and `pong._tests.RallyTest` are executable Java classes with `main`
methods. They do not require JUnit. See
[their README](../src/test/java/pong/_tests/README.md) for what each suite is responsible for.

- `pong._tests.PhysicsTest`: **101 checks** against analytic results, published measurements and ITTF
  rules. It covers flight, spin, energy, collision handling, preset shots and fast-moving rackets.
- `pong._tests.RallyTest`: **29 checks** covering opponent returns, shot assistance, racket reach
  and speed, cursor control, brushing and match scoring.

The Gradle `test`, `check` and `build` tasks all run both suites through the dedicated `selfTest`
and `rallyTest` tasks. Each suite prints PASS/FAIL and measured details, then exits non-zero if
a check fails.

Run the physics suite after physics changes, the rally suite after gameplay changes, and both
after changes to shared code, source layout or build configuration. Rendering changes must at
least compile. Both suites must stay at 100%; do not widen a threshold to fit a regression.

## Source layout

Directories are named for what the code is **for**, following
[How I structure my game projects](https://joshanthony.info/2021/12/06/how-i-structure-my-game-projects/).
Every directory has a README stating what belongs in it; start at
[`src/main/java/pong/README.md`](../src/main/java/pong/README.md).

```text
src/main/java/pong/
  core/math/        Vec3, Quat, Scalars -- nothing table-tennis about them
  config/           Physical (measured, cited), ShotTuning (tuned, reasoned)
  game_objects/     ball/, racket/ -- simulation, each with a JavaFX view/ leaf
  game_world/       World (the simulation) + view/ (court, bounce marks)
  systems/          collision, aim, control, opponent, scoring, shotmaking
    connectors/     RallyRules -- wires World + rackets + Scoreboard together
  assets/           Generated materials, shared by two views
  screens/          MatchScreen (entry point, loop, input, wiring), CameraRig
  ui/               Hud
  _debug/           ShotDebug (V), ControlOverlay (D)
  helpers/          Xform (the one space conversion), MouseAim (ray geometry)
src/test/java/pong/_tests/
  PhysicsTest.java  Simulation validation
  RallyTest.java    Game and control validation
```

An underscore prefix means the contents do not ship: `_debug/`, `_tests/`, `_tools/`.

**JavaFX may only be imported from `screens/`, `ui/`, `_debug/`, `assets/`, `helpers/` and the
`view/` leaves.** Everything else is headless, which is what lets both suites grade the whole
simulation and every game rule without opening a window. See
[Design rationale](DESIGN.md#architecture) for class responsibilities and the boundaries that must
remain intact.

## Manual build with Liberica Full JDK 21

This remains a plain `javac` project underneath Gradle. To compile without downloading dependencies,
use a **Full** Liberica JDK 21 from [BellSoft](https://bell-sw.com/pages/downloads/), which includes
JavaFX. Adjust the example path to your installation.

```powershell
$JDK = "$env:USERPROFILE\.jdks\jdk-21.0.12.1-full\bin"
$javaSources = (Get-ChildItem -Path src/main/java,src/test/java -Recurse -Filter *.java).FullName
& "$JDK\javac" -d out/production/3D-Table-Tennis-In-Java $javaSources
& "$JDK\java" -cp out/production/3D-Table-Tennis-In-Java pong.screens.MatchScreen
& "$JDK\java" -cp out/production/3D-Table-Tennis-In-Java pong._tests.PhysicsTest
& "$JDK\java" -cp out/production/3D-Table-Tennis-In-Java pong._tests.RallyTest
```

```bash
JDK=~/.jdks/jdk-21.0.12.1-full/bin
"$JDK/javac" -d out/production/3D-Table-Tennis-In-Java $(find src/main/java src/test/java -name '*.java')
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java pong.screens.MatchScreen
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java pong._tests.PhysicsTest
"$JDK/java" -cp out/production/3D-Table-Tennis-In-Java pong._tests.RallyTest
```

No `--module-path` or `--add-modules` is needed with the Full distribution. A missing
`javafx` package during manual compilation means the selected JDK does not include JavaFX.
Use the Full build, or use the standard Gradle workflow.

## Rendering captures

The application has an offline capture mode for examining a fixed shot and camera view.
After a manual compilation with the Full JDK:

```powershell
& "$JDK\java" -cp out/production/3D-Table-Tennis-In-Java pong.screens.MatchScreen "--shot=Topspin loop" --at=0.18 --view=SIDE --ball2x=true --out=out/frame.png
```

`--shot` accepts a name from `Shots.ALL`; quote names containing spaces. `--view` accepts a
`pong.screens.CameraRig.View` value (`BEHIND`, `SIDE`, `HIGH`, `LOW`, `TOP`). `--at` is in simulated
seconds. `--out` disables auto-replay so a capture past the end of a rally still completes.

## Files and generated output

- Commit application code, tests, documentation, development tools and wrapper files.
- Gradle writes generated output to `build/`; manual compilation uses the ignored `out/`.
- `.gradle/`, `bin/`, IDE state, compiled classes and Python bytecode are generated or local.
- Keep the repository root for project entry points. Put longer documentation in `docs/`
  and optional development utilities in `_tools/`.

The [IntelliJ MCP bridge](../_tools/intellij-mcp-bridge/README.md) is a development utility,
separate from the Java game. Its Python dependencies are vendored with the tool; they are not
application dependencies.
