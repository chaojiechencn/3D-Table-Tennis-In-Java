[![Download count](https://img.shields.io/github/downloads/chaojiechencn/3D-Table-Tennis-In-Java/total)](https://github.com/chaojiechencn/3D-Table-Tennis-In-Java/releases)
[![Latest release](https://img.shields.io/github/v/release/chaojiechencn/3D-Table-Tennis-In-Java?include_prereleases&display_name=tag)](https://github.com/chaojiechencn/3D-Table-Tennis-In-Java/releases)

# 3D Table Tennis

3D Table Tennis is a table tennis game for Windows. The player controls a paddle with the mouse, and
the ball is simulated with real physics. The speed, direction and spin of a shot are not chosen from
a menu or a button. They come from how the paddle moves when it meets the ball.

> :warning: **Note:** 3D Table Tennis is in early development. Version 0.1.0 is an early build, and
> changes are to be expected.

## Downloads

Download (Windows, 64-bit)|All releases
----|----
<a href="https://github.com/chaojiechencn/3D-Table-Tennis-In-Java/releases/download/v0.1.0/TableTennis-0.1.0-win64.zip"><img src="https://img.shields.io/github/v/release/chaojiechencn/3D-Table-Tennis-In-Java?include_prereleases&display_name=tag&label=download&style=for-the-badge" height="65px" /></a>|[Releases page](https://github.com/chaojiechencn/3D-Table-Tennis-In-Java/releases)

### Installing

The download is a zip file. It contains `TableTennis.exe` and its own copy of Java, so nothing else
has to be installed.

1. Right-click the zip, choose **Properties**, tick **Unblock** and click **OK**.
2. Unzip it to any folder.
3. Double-click `TableTennis.exe`.

### System requirements

Part|Requirement
----|----
OS|64-bit Windows 10 or 11
Graphics|A graphics card with Direct3D support
Memory (RAM)|400 MB free (the game uses about 350 MB)
Disk (storage)|90 MB free (the unzipped game is 84 MB)

Version 0.1.0 was tested on Windows 11 with an AMD Radeon RX 6750 XT.

## How to play

### Modes

- **Play a match**: a match against the computer, best of 5 games of 11 points.
- **Practice**: the same kind of ball is played to you again and again. There are ten drills, from
  *Warm-up* to *Smash*.

### Controls

Action | Input
:-- | :--
Move the paddle | Move the mouse
Hit the ball | Move the paddle through the ball
Tilt the paddle face up or down | `W` / `S`
Tilt the paddle face left or right | `A` / `D`
Rotate the camera | Hold the left mouse button and drag the mouse
Zoom the camera | Scroll wheel
Pause | `Space`
Menu | `Esc`

### Rules and scoring

The game uses the official scoring of table tennis. A game is won by the first player to reach 11
points with a lead of at least 2 points.

During a rally, the ball has to cross the net and bounce once on the opponent's half. A player loses
the point if they miss the ball, or if their shot misses the table, hits the floor or bounces on
their own half. A ball that touches the net and still lands on the opponent's half stays in play.

At the moment, each point starts with the ball being played to you from the other side.

## What's coming

Feature | Description
:-- | :--
Serving | Double-click to toss the ball up, then hit it as it falls. Serves follow the official rules.
New graphics | Real shadows, a shadow directly under the ball and a short trail, which make the height of the ball easier to judge. Better lighting, paddles and hall.
Online matches | Games against another player over the internet, using a room code.
Better hits | The shot will depend even more on your swing, and the game will help less.

Sound, doubles (2v2) and tournaments will come after these.

## Building from source

The source code in this repository is older than the 0.1.0 build.

1. Install [Java 21 (JDK)](https://adoptium.net/temurin/releases/?version=21) and
   [Git](https://git-scm.com/downloads).
2. Get the source code, either by [downloading the zip](https://github.com/chaojiechencn/3D-Table-Tennis-In-Java/archive/main.zip)
   or with `git clone https://github.com/chaojiechencn/3D-Table-Tennis-In-Java`.
3. In the source folder, run `.\gradlew.bat run` (on Linux, `bash ./gradlew run`). The first start
   downloads the build tools and libraries, which takes a few minutes.

Developer documentation is in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Contributors

[![Contributors](https://contrib.rocks/image?repo=chaojiechencn/3D-Table-Tennis-In-Java)](https://github.com/chaojiechencn/3D-Table-Tennis-In-Java/graphs/contributors)

## FAQ

### Windows says the app may be unsafe?

The game is not code-signed, so Windows SmartScreen may warn about it. Click **More info** and then
**Run anyway**. Unblocking the zip before unzipping it (step 1 of [Installing](#installing)) prevents
the warning.

### Do I need to install Java?

No. The zip contains its own copy of Java.

### Does it run on macOS or Linux?

The download is for Windows only. The source code builds and runs on Linux. macOS has not been tested.

### Where is the full list of keys?

The controls legend inside the game and the **How to play** page in the menu list every key.

### How do I uninstall?

Delete the folder you unzipped. The game does not install anything or save files outside that folder.

### Where do I report a bug?

[Open an issue](https://github.com/chaojiechencn/3D-Table-Tennis-In-Java/issues) on GitHub.
