# `_debug/` — not part of the game

The underscore means what it means in a variable name: private, not part of the public thing. Delete
this directory and the game still plays; it just gets harder to work on.

Each overlay exists to answer one question that is genuinely ambiguous on screen:

- **`ShotDebug` (`V`)** — "what did the shot model actually decide?" It draws the racket's velocity,
  the incoming ball, the raw physical reflection, the intended shot, the final shot, the target and
  the predicted landing. Magenta and green apart means the solve did not land where it aimed; cyan
  and orange apart means a clamp is fighting the solve; a green dot off the table means the
  validator gave up and the fallback shot is in play.
- **`ControlOverlay` (`D`)** — "was that the control mapping failing to express where I wanted the
  bat, or was that ball genuinely unplayable?" Those two look identical while playing and have
  opposite fixes.

Both are strictly read-only and strictly downstream of everything. `ControlOverlay` takes the ball's
position as an argument and has no way to write anything, which is part of how "the ball never moves
the player's racket" stays true even though a debug view gets to see both.
