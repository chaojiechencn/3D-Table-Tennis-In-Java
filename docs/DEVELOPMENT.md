# Development

[Project overview](../README.md) · [Architecture](ARCHITECTURE.md) · [Gameplay](GAMEPLAY.md)

## Requirements

The standard build uses **JDK 21** and the committed Gradle wrapper. Gradle resolves JavaFX and
JUnit and downloads its own distribution on first use, so the first build needs network access. A
global Gradle installation is unnecessary.

The optional manual build of the game uses **Liberica Full JDK 21**, which bundles JavaFX as system
modules. The tests need Gradle.

## Build, run and test

From the project root (Windows: `.\gradlew.bat`; macOS, Linux and Git Bash: `bash ./gradlew`):

| Action | Command |
| --- | --- |
| Run the game | `.\gradlew.bat run` |
| Compile everything | `.\gradlew.bat classes` |
| Every check in every module | `.\gradlew.bat check` |
| One module's checks | `.\gradlew.bat :engine:test` (or `:game:test`, `:app:test`) |
| One test class | `.\gradlew.bat :game:test --tests tabletennis.game.rally.RefereeTest` |
| One test method | `.\gradlew.bat :game:test --tests tabletennis.game.rally.RefereeTest.ANetTouchAloneDecidesNothing` |
| Rewrite the golden trace | `.\gradlew.bat :game:writeGoldenTrace` |
| Build and check | `.\gradlew.bat build` |

The application's main class is `tabletennis.app.TableTennisApp`.

## Validation

The tests are JUnit 5, one class per component, and every check is a falsifiable claim printed
with its measured number:

```text
  [PASS] ITTF drop test: 30.5 cm gives a 24-26 cm rebound  (rebound 24.4 cm, e = 0.931 at the 2.45 m/s impact)
```

`Claims.Check("claim", Holds, "measured detail")` lives in `engine`'s test fixtures, and `game`
and `app` use it too. The detail prints on a pass as well as a failure, so it must state what was
actually measured. The build shows test output, so `check` prints every line.

| Module | Checks | Covers |
| --- | --- | --- |
| engine | 54 | Flight, drag and Magnus against published measurements; RK4 order; the ITTF drop test; bounces, net and spin reversal; tunnelling; energy |
| game | 101 | Every feed's legality; the referee's rules; scoring; the reach envelope and cursor follower; the opponent; shot tuning; whole points through `GameSession`; the golden trace |
| app | 11 | The fixed-step clock; command-line parsing; key bindings against the legend; score formatting |

**The golden trace.** `GoldenTraceTest` plays 39 scripted scenarios through `GameSession` and
compares every step's ball and blade bits with `game/src/test/resources/tabletennis/game/golden-trace.txt`.
A restructuring must reproduce it exactly. When a behavior change is deliberate, rewrite the file
with `:game:writeGoldenTrace`, review the diff, and say why in the commit.

A failing check is a broken deliverable, not a flaky test. **Never widen a threshold to fit a
regression.** Re-deriving one because the model became more accurate is legitimate; say so in a
comment, with the number the new model predicts.

Mouse, brush, camera and overlay behavior cannot be checked headlessly; try them in the game.

## Source layout

```text
engine/   tabletennis.engine   physics; no dependencies
  src/main/java, src/test/java, src/testFixtures/java (Claims)
game/     tabletennis.game     rules, players, shot assist; depends on engine
  src/main/java, src/test/java, src/test/resources (golden-trace.txt)
app/      tabletennis.app      JavaFX front end; depends on game
  src/main/java, src/test/java
```

[Architecture](ARCHITECTURE.md) describes each package and the rules between them.

## IDE setup

### IntelliJ IDEA

1. Open the project root and import it as a Gradle project.
2. Select a JDK 21 for the project SDK and the Gradle JVM.
3. Reload the Gradle project after build changes.
4. Run the Gradle `run` task to play, or `check` to validate. JUnit classes also run from the
   gutter.

The `.idea/` directory and `.iml` files are generated and ignored. Change source sets in the
Gradle build, never in a generated module file.

### VS Code

Open the project root with the Java extension pack; it imports the Gradle build. Run the game and
the checks through the wrapper in a terminal.

## Manual build with Liberica Full JDK 21

The game compiles with one `javac` call, because a **Full** Liberica JDK 21
([BellSoft](https://bell-sw.com/pages/downloads/)) includes JavaFX. Adjust the path to your
installation.

```powershell
$JDK = "$env:USERPROFILE\.jdks\jdk-21.0.12.1-full\bin"
$Sources = (Get-ChildItem -Path engine/src/main/java,game/src/main/java,app/src/main/java -Recurse -Filter *.java).FullName
& "$JDK\javac" -d out/game $Sources
& "$JDK\java" -cp out/game tabletennis.app.TableTennisApp
```

```bash
JDK=~/.jdks/jdk-21.0.12.1-full/bin
"$JDK/javac" -d out/game $(find engine/src/main/java game/src/main/java app/src/main/java -name '*.java')
"$JDK/java" -cp out/game tabletennis.app.TableTennisApp
```

No `--module-path` or `--add-modules` is needed with the Full distribution. A missing `javafx`
package means the JDK does not include JavaFX: use the Full build or Gradle.

## Rendering captures

Capture mode writes one PNG and exits:

```powershell
.\gradlew.bat run --args="--shot=Serve --at=0.45 --view=SIDE --out=frame.png"
```

| Flag | Meaning |
| --- | --- |
| `--shot` | A feed name from `Feeds.All`; quote names with spaces (`"--shot=Topspin loop"`) |
| `--at` | Simulated seconds to advance before the capture |
| `--view` | `BEHIND`, `SIDE`, `HIGH`, `LOW` or `TOP` (case-insensitive) |
| `--out` | The PNG to write; also turns auto-replay off, so a late capture still completes |
| `--demo=true` | Let the demo hand play the player's side |
| `--controldebug=true` | Show the `D` control readout |
| `--ball2x=true` | Draw the ball at twice its size |
| `--rallycam=true` | Keep the rally-cam on during a capture |

The capture advances whole physics steps to `--at`, so the same arguments give the same image, up
to a few pixels of GPU noise. Captures compare well pixel by pixel after a rendering change.

## Files and generated output

- Commit only the program itself: code, tests, the golden trace, documentation and the wrapper.
  Personal utilities, IDE and editor state (`.idea/`, `.vscode/`, `.claude/`) and generated output
  (`build/`, `.gradle/`, `out/`, `bin/`) never go in the repository.
- Keep the root for entry points (`README.md`, `AGENTS.md`, `CLAUDE.md`, the build); longer
  documentation goes in `docs/`.

## Continuous integration

GitHub Actions runs `check` on Ubuntu and Windows with Java 21 for every push and pull request
(`.github/workflows/ci.yml`), using the committed wrapper.
