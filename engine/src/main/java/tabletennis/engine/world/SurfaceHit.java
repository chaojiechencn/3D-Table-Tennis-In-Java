package tabletennis.engine.world;

import tabletennis.engine.contact.ContactSolver;
import tabletennis.engine.math.Vec3;

/** One contact resolved during a step: the surface struck and the solver's response. */
public record SurfaceHit(Surface Struck, ContactSolver.Response Response) {

    public SurfaceKind Kind()     { return Struck.Kind(); }
    public Racket Owner()         { return Struck.Owner(); }
    public Vec3 Point()           { return Response.Point(); }
    public double ImpactSpeed()   { return Response.ImpactSpeed(); }

    /** Hard enough to count as an event rather than a ball settling. */
    public boolean IsNotable()    { return Kind().IsNotable(ImpactSpeed()); }
}
