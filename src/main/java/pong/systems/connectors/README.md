# `systems/connectors/` — making systems interact

A connector owns no simulation and no rendering. It exists only to make two or more systems that
know nothing about each other work together, so each of them stays usable on its own.

The alternative is for every system to learn about the others, which is how a physics engine ends up
with a scoreboard in it.

**`RallyRules`** connects `World`, the two `Racket`s and the `Scoreboard`. It decides which racket is
allowed to hit (the ITTF one-bounce rule, enforced by handing `World` a null racket for whoever may
not hit yet) and who just won the point. None of the three systems it connects knows any of that.

It used to live inside the JavaFX `Application`, which cost two things. The rules ran only when a
window was open, so they could not be tested. And `RallyTest` had to *reimplement* the gating twice
to drive its own rallies, which meant the thing under test was a copy of the rules rather than the
rules. Both suites now drive this.
