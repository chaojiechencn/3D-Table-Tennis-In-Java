# `screens/` — what the player is looking at

A screen composes systems, game objects and UI into something a person interacts with.

`MatchScreen` is the JavaFX `Application`: the fixed-timestep loop, mouse and key input, the wiring
between everything, and the offline capture mode. It owns the loop and the wiring and as little else
as possible — the rally rules live in `systems/connectors/RallyRules`, the shot model in
`systems/shotmaking`, and the `D` overlay's formatting in `_debug/ControlOverlay`.

`CameraRig` is here rather than in a `view/` because it is not a view *of* anything — it decides
where the player is looking from, which is a property of the screen. Its rally-cam distance and yaw
limits are a CONTROL constraint rather than a framing preference: the player aims by casting the
cursor's ray onto the hitting plane, so the camera decides how much of the reachable envelope is on
screen to aim into.

A second screen — a menu, a match-over card — belongs here beside them.
