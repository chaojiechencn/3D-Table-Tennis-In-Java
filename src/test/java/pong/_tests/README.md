# `_tests/` — the validation suites

Two headless suites, both plain `main` classes rather than JUnit. Each prints PASS/FAIL with the
measured number for every check and exits non-zero if any fail. **They must stay at 100%.**

```bash
./gradlew check       # both
./gradlew selfTest    # pong._tests.PhysicsTest  -- 101 checks
./gradlew rallyTest   # pong._tests.RallyTest    --  29 checks
```

Two suites rather than one, because they grade different things:

- **`PhysicsTest`** grades the SIMULATION against numbers that did not come from this program —
  closed-form solutions of the same equations, published drag/lift/restitution fits, and ITTF
  measurements. A failure here means the physics is wrong.
- **`RallyTest`** grades the GAME built on top: the opponent, the shot model, the control envelope,
  the rally rules and the score. A failure here means the game is wrong.

The detail string prints on **pass as well as fail**, so it must state what was actually measured.
Adding a check is one `check("falsifiable claim", boolean, "the measured number")` call plus one line
in `main`.

**Never widen a threshold to make a regression fit.** Re-deriving a threshold because the model got
more accurate is legitimate — say why in a comment, with the number the new model predicts. That
comment is the only thing that tells the two cases apart later.

They live under `_tests/` rather than mirroring each package because they test across package
boundaries by design — `RallyTest` drives `World`, `RallyRules`, `Follower`, `ShotAssist` and
`PlayerReach` in one rally — and nothing they touch is package-private.
