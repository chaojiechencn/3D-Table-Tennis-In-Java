# `config/` — the numbers

Two kinds of number live here, and the distinction is the reason they sit in one place where it can
be seen.

**`Physical`** is MEASURED. Every constant carries a citation — ITTF laws, published drag and lift
fits, contact studies. Changing one of these does not change how the game feels, it makes the
simulation *wrong*, and `pong._tests.PhysicsTest` grades them against the sources they came from.
Anything tuned by eye is labelled `TUNED` and says what it stands in for.

**`ShotTuning`** is TUNED. Every knob the arcade shot model has, each carrying the reasoning behind
its value rather than a citation. Changing one of these changes how the game feels. Nothing below it
is hardcoded: to change the feel, change these.

Bounce restitution and friction are deliberately NOT duplicated into `ShotTuning`. They are measured
values, single-sourced in `Physical`, and they stay there.
