# `helpers/` — generalised helpers, each with a job

Not a junk drawer. Two files, each the single place a particular conversion is allowed to happen.

**`Xform`** is the ONE place physics space becomes scene space, in both directions. Physics is metres,
right-handed, `+Y` up. JavaFX is left-handed with `+Y` DOWN, at 300 scene units per metre — because
its default camera clip planes and its light attenuation both fall apart at metre scale. Scattering
that conversion across a renderer is how a project ends up with a ball curving the wrong way and a
"fix" that flips a sign in the physics to compensate. **Nothing else may multiply or divide by
`SPM`.**

**`MouseAim`** turns a cursor position into the point on the racket's hitting plane the player is
pointing at — pure ray geometry. No clamping and no envelope (`systems/control/PlayerReach` owns
those), and it has never seen the ball.

Anything added here has to earn it the same way: a single, named responsibility that genuinely has
no better home. "Miscellaneous" is not one.
