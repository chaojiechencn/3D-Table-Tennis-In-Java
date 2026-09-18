# `assets/` — resources other parts of the game draw with

Generated, not loaded. There are no asset files to lose, and each map is tuned for the one thing it
has to do.

`SurfaceMaterials` builds the diffuse, normal and specular maps for the table, the net tape, the
rubber on both rackets, the floor and the walls. Every map is built ONCE at startup, never during a
rally.

It lives here rather than inside a view because two different views draw with it — the court and the
racket. Note the direction that implies: **an asset must never reach back into a view.** When the
floor's baked contact shadows needed the table's leg positions, those constants moved to `config/`;
they were not imported from `game_world/view/`.
