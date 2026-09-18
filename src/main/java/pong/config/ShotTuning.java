package pong.config;

/**
 * Every knob the shot model has, and nothing else.
 *
 * This was a nested class inside {@code ShotAssist}, where 195 lines of tuning stood between the
 * reader and the 250 lines of algorithm that use them. They are configuration, so they live in
 * {@code config/} with the measured constants -- with one difference that matters: the numbers
 * in {@link Physical} are MEASURED and carry citations, and these are TUNED and carry reasons.
 * Changing a number here changes how the game feels. Changing one there makes the simulation
 * wrong.
 *
 * Bounce restitution and friction are deliberately NOT duplicated here. They are real measured
 * values, already single-sourced in {@code Physical.TABLE_MAT} and {@code RACKET_MAT}, and
 * {@code pong._tests.PhysicsTest} grades them.
 */
public final class ShotTuning {

    /** Shot strength, m/s. Swing speed maps onto this range and never beyond it. The ceiling
     *  is ASPIRATIONAL -- a shot still has to land, and geometry alone caps most contacts
     *  well under it, so raising this alone does nothing. */
    public double minShotSpeed = 5.0;
    public double maxShotSpeed = 17.0;

    /** Racket speed, m/s, that produces a full-strength shot. Faster adds nothing -- this is
     *  what stops repeated hits from compounding. */
    public double maxSwingSpeed = 16.0;

    /** How much a sideways or upward swipe counts toward shot STRENGTH, next to the forward
     *  drive. Low on purpose: driving through the ball is what makes it go, moving across is
     *  how you aim. */
    public double lateralEffort = 0.25;

    /** How much of the swing reaches the shot at all (0 = every shot the same strength). */
    public double swingInfluence = 1.0;

    /** Shape of swing -> strength. 1 = linear; below 1 = quick early response then
     *  diminishing returns, which is what makes a hard swing feel controlled. */
    public double swingCurve = 0.7;

    /** How far a sideways swipe moves the aim, as a fraction of the target box per m/s.
     *  TUNED: 0.20 puts an ordinary firm sweep on the edge of the box. */
    public double aimInfluence = 0.20;

    /** How much a forward drive deepens the target, per m/s. Pulls against `arcInfluence`
     *  below by design -- driving forward deepens the target through this term and raises
     *  `brush`, which shortens it through that one. See docs/DESIGN.md before retuning
     *  either. */
    public double depthInfluence = 0.100;

    /** How much an up/down swipe arcs the shot: up = shorter and higher, down = flatter and
     *  deeper. Fraction of the target depth range per m/s; see `depthInfluence`, which this
     *  partly cancels on purpose. */
    public double arcInfluence = 0.018;

    /** How much the racket's own tilt aims the shot, on top of where it is moving. */
    public double faceInfluence = 0.25;

    /** How much hitting off-centre on the blade shifts the aim. Deliberately small -- edge
     *  contacts should feel different, not random. */
    public double contactPointInfluence = 0.30;

    /** Fraction of the physical reflection blended into the authored shot -- there so a
     *  contact feels like an impact, not so it can steer the shot on its own. */
    public double physicalBlend = 0.15;

    // ---- contact quality -------------------------------------------------------------
    // How well the ball was struck, and how much help the shot earns for it. Without these
    // a shank could never be punished and a rally could not be won or lost on skill; see
    // docs/DESIGN.md.

    /**
     * The clean core of the blade, as a fraction of its radius: inside this a contact counts
     * as fully struck. FLOOR 0.50, measured against RallyTest's own competent-play probe;
     * TUNED up to 0.58 above that floor for an average player. See docs/DESIGN.md.
     */
    public double qualityCore = 0.58;

    /** How far past the core the quality falls from 1 to 0; core + this is the rim. TUNED
     *  wider (was 0.42) so the drop from clean to mishit is a slope, not a cliff. */
    public double qualityFalloff = 0.50;

    /** Incoming speed (m/s) at which the core starts shrinking, and the span over which it
     *  shrinks the whole way. A fast ball has to be met more precisely than a slow one. */
    public double qualityPaceFrom = 6.0;
    public double qualityPaceSpan = 12.0;

    /** How much of the core the fastest ball takes away, and the floor it cannot shrink
     *  below. TUNED down from 0.22 so a fast ball -- already the hardest thing to time --
     *  does not lose the forgiveness qualityCore just bought. */
    public double qualityPaceLoss = 0.15;
    public double qualityCoreMin = 0.26;

    /**
     * The assist a zero-quality contact still gets. Not zero -- the raw rim impulse lands on
     * the table only 11 times in 75 (see docs/DESIGN.md), which would make a shank fatal
     * every time. TUNED up to 0.35 so a shank stays clearly worse than a clean hit without
     * being close to an automatic loss.
     */
    public double assistFloor = 0.35;

    /** The quality below which the rescue search does not run at all -- it still exists for
     *  a ball met right at the net with no fast legal shot, but a contact off the rim no
     *  longer qualifies for it. */
    public double rescueQualityFloor = 0.60;

    /** How much forward drive counts as brushing over the ball; 0.8 is what lets a hard
     *  pull-back reach genuine backspin rather than merely less topspin. */
    public double driveBrush = 0.8;

    /** The reflection is capped at this speed before blending, so a violent impulse cannot
     *  leak through even at a small blend fraction. */
    public double reflectionCap = 6.0;

    /** Hard ceiling on the sideways component of the finished shot: a cone (degrees off
     *  straight) and an absolute m/s, whichever binds first. Widening either cannot make a
     *  shot illegal on its own -- every candidate is still flown and graded. */
    public double maxHorizontalDeviationDeg = 30.0;
    public double maxLateralVelocity = 4.5;

    /** Launch elevation band -- a SANITY GUARD, not a shaping tool. Aim owns the elevation; a
     *  real drive off a low ball near the baseline genuinely launches downward, so do not
     *  raise the floor above zero without re-deriving it (see docs/DESIGN.md). */
    public double maxVerticalLaunchAngleDeg = 45.0;
    public double minVerticalLaunchAngleDeg = -20.0;

    /** Shot speed and target depth are not independent, so the search tries a spread of
     *  speeds around the one the swing asked for and keeps the legal candidate closest to
     *  it. */
    public int speedCandidates = 5;
    public double speedSpread = 0.42;

    /** Penalty per m/s for not being the speed the swing asked for, and per correction pass
     *  for having had to give ground. Only ever separates candidates that are both already
     *  legal -- illegality outweighs both by two orders of magnitude. */
    public double speedPreference = 1.0;
    public double passPenalty = 2.0;

    /** Always at least this much pace toward the opponent. */
    public double minForwardVelocity = 4.5;

    /** The slowest shot the MAIN search may consider, m/s -- separate from minShotSpeed (the
     *  slowest the swing may ASK for) because some contacts have no legal fast answer and
     *  must fall back to a slow, legal one rather than the rescue. See docs/DESIGN.md. */
    public double minSearchSpeed = 3.0;

    /** The search may not slow a shot below this fraction of the pace the swing ASKED for,
     *  or the ladder could disguise any over-ambitious swing as a legal dink -- see
     *  docs/DESIGN.md for the measured case this guards against. */
    public double searchSpeedFloorFrac = 0.60;

    /** Above this much swing, the rescue does not run at all -- it is for a ball met right
     *  at the net with no fast legal answer, not for a hard swing that simply would not
     *  land. */
    public double rescueEffortCeiling = 0.75;

    /** The target box on the opponent's half, as fractions of half-width / half-length.
     *  Deliberately not the whole table -- `landingMargin` keeps the last few centimetres
     *  out of reach. See docs/DESIGN.md for the widening history. */
    public double targetHalfWidthFrac = 0.90;
    public double targetDepthMinFrac = 0.20;
    public double targetDepthMaxFrac = 0.92;

    /** Where the "safe" shot goes when a correction pass has to give ground. */
    public double safeDepthFrac = 0.55;

    /** How far each correction pass pulls the target toward safe, and how much it slows the
     *  shot. Kept small -- too much silently drags an over-hit shot back to the middle
     *  instead of letting the player see they overhit it. */
    public int maxCorrectionPasses = 2;
    public double targetAssist = 0.18;
    public double speedBackoffPerPass = 0.13;

    /** Clearance above the cord the validator insists on, metres. */
    public double netClearance = 0.055;

    /** Margin inside the sidelines / end line the landing must keep, metres. */
    public double landingMargin = 0.05;

    /** The rescue search, used only when the normal search finds nothing legal. Allowed to
     *  go slower than minShotSpeed and to re-aim, for a ball met right at the net that can
     *  only be lifted softly over. */
    public double rescueMinSpeed = 3.0;
    public int rescueSpeedSteps = 9;
    public double[] rescueDepthFracs = {0.55, 0.72, 0.88, 0.40};

    /** How much of the player's lateral aim the rescue keeps, tried in this order -- the
     *  full aim first, given up only if nothing there is legal, so a rescued shot keeps the
     *  player's aim instead of always centring. */
    public double[] rescueAimFracs = {1.0, 0.6, 0.3, 0.0};

    /** Spin, rev/s. Topspin comes from an upward swipe, sidespin from a sideways one. Capped
     *  so spin stays a secondary influence and never a source of chaos. */
    public double spinInfluence = 1.0;
    public double baseTopspin = 14.0;
    public double topspinPerLift = 2.6;

    /**
     * TUNED, standing in for how hard a player brushes ACROSS the back of the ball. This
     * knob SATURATES: every candidate shot is solved to a target and validated, so more spin
     * mostly makes the solver pick a different launch to the same legal landing rather than
     * visibly curving the ball more. See docs/DESIGN.md for the measured A/B before reaching
     * for this again. Note the sign: a swipe right AIMS the ball right but SPINS it to bend
     * back left, which is real -- brushing across curves the ball opposite the brush.
     */
    public double sidespinPerSwipe = 4.5;
    public double maxSpin = 55.0;
}
