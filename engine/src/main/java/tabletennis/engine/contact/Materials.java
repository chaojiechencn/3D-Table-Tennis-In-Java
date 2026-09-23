package tabletennis.engine.contact;

/**
 * The surfaces the ball meets, each with its source:
 *   [ITTF] Laws of Table Tennis 2.01: the table's drop-test rebound and friction band.
 *   [CONT] Ball-table contact studies: restitution ~0.89-0.93, sliding friction ~0.2-0.3.
 *   [FIT]  arXiv:2606.28805, coefficients fitted to 277 recorded competitive matches.
 */
public final class Materials {

    private Materials() {}

    /**
     * Inverted offensive rubber. e_n = 0.878 - 0.020|v_n| [FIT Table IV], corroborated by
     * arXiv:2604.11349. e_t springs the patch back, which is what reverses spin. Friction is TUNED
     * high so the elastic branch governs; drill damping applies to spin about the normal only.
     */
    public static final Material Rubber = new Material(
            0.878, 0.020, 0.45, 0.90,
            1.20,
            0.819, 0.010,
            0.805,
            1.00, 1.00);

    /**
     * Ball on table: e_n = 0.98 - 0.02|v_n| clamped to [0.75, 0.94] [FIT][CONT]; 0.931 at the ITTF
     * drop speed gives the 24-26 cm rebound. Friction 0.25 is centre of ITTF's 0.150-0.350 band.
     */
    public static final Material Table =
            new Material(0.98, 0.02, 0.75, 0.94, 0.25, 0.0, 0.0, 1.0, 1.00, 1.00);

    /** A hard indoor floor, slightly deader and grippier than the table. */
    public static final Material Floor = Material.Rigid(0.80, 0.40, 1.00, 1.00);

    /** TUNED: no standard COR exists for netting; a ball into the net drops on the near side. */
    public static final Material Net = Material.Rigid(0.12, 0.50, 0.55, 0.35);
}
