# `systems/` — the rules

The gameplay mechanics: how a contact resolves, where a shot is aimed, what the player's racket may
do, how the opponent plays, and how the score is kept. All plain Java — a rule that can only be
observed by looking at the screen is a rule that does not get tested.

| Directory | System |
| --- | --- |
| `collision/` | ONE impulse solver for every surface. Table, net, floor and both rackets differ by a `Material` and a shape, nothing else. Do not add a second bespoke bounce path. |
| `aim/` | `Aim` solves the launch angle that lands a shot on a chosen spot; `Shots` is the preset menu, each preset stating its intent rather than a hard-coded velocity. |
| `control/` | `PlayerReach` (where the racket may be) and `Stroke` (the blade following the cursor). `Stroke.advance` takes no ball — the rule that the ball never moves the player's racket is enforced by the signature. |
| `opponent/` | `Opponent` and the `Follower` that plays today; `DemoPlayer` is a stand-in hand that drives the CURSOR, never the blade. |
| `scoring/` | ITTF scoring. The server is derived from the score, never stored as a flag that can drift. |
| `shotmaking/` | `ShotAssist` reads a contact as intent, builds a legal shot, and validates it by flying it. |
| `connectors/` | See [its own README](connectors/README.md). |
