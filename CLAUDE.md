# CLAUDE.md

Start with [AGENTS.md](AGENTS.md), the operational contract for coding agents in this repository.

- [Development](docs/DEVELOPMENT.md): current build commands, validation, IDE setup and source layout.
- [Design rationale](docs/DESIGN.md): the full plan, methodology, measured decisions and history.
- [Gameplay and controls](docs/GAMEPLAY.md): player-facing behavior.
- [Source map](src/main/java/pong/README.md): what each directory is for.

Directories are named for **what the code is for**, and every directory has a README saying what
belongs in it. Read that README before adding a file there. If a new file has no obvious home, that
is a signal about the file, not about the layout.

The detailed rationale formerly stored in this file lives in `docs/DESIGN.md`. Read the relevant
section before changing a physics model, control mapping or architectural invariant. Its reasoning
remains authoritative when a short summary omits an important constraint; current build paths and
commands live in `docs/DEVELOPMENT.md`.
