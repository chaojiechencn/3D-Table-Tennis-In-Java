# `ui/` — the HUD

What the player reads while playing: the score, the current feed, the key legend, and the two debug
lines that only appear while their overlay is on.

`Hud` takes formatted strings and places them. It does no formatting of its own beyond styling — the
score line is composed by `systems/scoring/Scoreboard.line()`, the shot line by
`_debug/ShotDebug.readout()`, and the control block by `_debug/ControlOverlay`. That keeps the thing
that KNOWS a number apart from the thing that DRAWS it, so a readout can be checked without opening
a window.
