# `game_world/` — the world the objects live in

`World` is the simulation: the ball, the surfaces it can hit, the fixed step, and the event log of
what happened. It is stepped by a fixed `DT` and never sees a frame time — that is what makes a
bounce identical on a fast machine and a slow one.

`view/` draws the room around it. `CourtView` builds the table, net, frame and hall; `BounceMarks`
leaves a disc where the ball landed, so a call can be compared with the painted line at leisure.
Nothing in `view/` is part of the collision world — the surfaces the ball actually hits are the three
boxes declared in `World`, and the legs, apron and netting are decoration.

`World.predict` flies a shot forward in a private, **racket-free** copy. A prediction that gets
intercepted is a prediction of nothing, so the copy having no rackets is the design, not an oversight.
