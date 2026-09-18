# Documentation

| Document | Use it for |
| --- | --- |
| [Gameplay and controls](GAMEPLAY.md) | Playing the game, controls, features and match rules |
| [Development](DEVELOPMENT.md) | Building, running, IDE setup, validation and source layout |
| [Design rationale](DESIGN.md) | Architecture, physics decisions, measurements and development history |
| [Source map](../src/main/java/pong/README.md) | What each directory is for, and the one rule the tests depend on |
| [Agent operating guide](../AGENTS.md) | Required checks, hard invariants and known defects |

Directories are named for **what the code is for** and each one carries its own README, so the
answer to "where does this new file go?" lives next to the files rather than only here. The layout
follows [How I structure my game
projects](https://joshanthony.info/2021/12/06/how-i-structure-my-game-projects/).

Start with the [project overview](../README.md). `AGENTS.md` and `CLAUDE.md` remain at the
repository root so coding agents can discover the project instructions.
