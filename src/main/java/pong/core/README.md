# `core/` — reusable in any game

The rule for this directory, taken straight from the article the layout follows: **only code that
would transplant into a completely different game unchanged.** Nothing here knows what a ball is,
what a table is, or that this is a sport.

That makes it a small directory, and that is the point. The temptation is to put anything
"fundamental" here — the integrator, the contact solver — but both of those are written around a
40 mm hollow sphere and its measured aerodynamics, so they live with the ball and the collision
system instead. If a file here ever needs a constant from `config/`, it has stopped being core and
should move out.

| File | What it is |
| --- | --- |
| `math/Vec3` | Immutable 3-vector, right-handed |
| `math/Quat` | Unit quaternion, carrying orientation only |
| `math/Scalars` | `clamp` and `frac` — previously five identical private copies |
