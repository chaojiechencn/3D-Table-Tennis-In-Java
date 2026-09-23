package tabletennis.engine.world;

import tabletennis.engine.NetSpec;
import tabletennis.engine.TableSpec;
import tabletennis.engine.contact.BoxCollider;
import tabletennis.engine.contact.Materials;
import tabletennis.engine.math.Vec3;

import java.util.List;

/** The fixed surfaces: the table top, the net and the floor. */
public final class Arena {

    private Arena() {}

    /** Top face on y = 0, the physics origin. */
    public static final BoxCollider TableTop = BoxCollider.Centered(
            new Vec3(0, -TableSpec.TopThickness / 2, 0),
            new Vec3(TableSpec.Width, TableSpec.TopThickness, TableSpec.Length));

    public static final BoxCollider Net = BoxCollider.Centered(
            new Vec3(0, NetSpec.Height / 2, 0),
            new Vec3(NetSpec.Width, NetSpec.Height, NetSpec.Thickness));

    /** 120 m across because a missed ball rolls 17-20+ m; the visible floor is 14 x 16 m. */
    public static final BoxCollider Floor = BoxCollider.Centered(
            new Vec3(0, -TableSpec.Height - 0.5, 0),
            new Vec3(120, 1.0, 120));

    /** In the order contacts are tried: at equal times of impact the first listed wins. */
    static final List<Surface> FixedSurfaces = List.of(
            new Surface(Net, Materials.Net, SurfaceKind.Net, null),
            new Surface(TableTop, Materials.Table, SurfaceKind.Table, null),
            new Surface(Floor, Materials.Floor, SurfaceKind.Floor, null));
}
