package tabletennis.engine.world;

import tabletennis.engine.contact.Collider;
import tabletennis.engine.contact.Material;

/** Something the ball can hit: a shape, what it is made of, what it is, and its racket if any. */
public record Surface(Collider Shape, Material Finish, SurfaceKind Kind, Racket Owner) {}
