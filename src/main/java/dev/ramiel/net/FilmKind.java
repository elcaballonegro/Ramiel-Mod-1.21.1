package dev.ramiel.net;

/** Which cutscene to play. Sent in {@link Payloads.Film}. */
public final class FilmKind {
    private FilmKind() {}

    public static final int INTRO = 0;        // falls out of space, lands above the water
    public static final int LANCE = 1;        // attack 1 transformation
    public static final int CROSS = 2;        // attack 2 transformation
    public static final int SUNBURST = 3;     // attack 3 transformation
    public static final int SENTINEL = 4;     // attack 4 transformation
    public static final int STUN = 5;         // spikes outward at 2/3 health
    public static final int UNFOLD = 6;       // star unfolding before each final-phase beam
    public static final int DEATH = 7;
    public static final int DRILL = 8;        // the lower corner stabs into the ground
    public static final int UNFOLD_FIRST = 9; // the very first unfolding (phase change)

    /** The lowest "cutscenes" setting that still plays this film (1 = key moments, 2 = everything). */
    public static int minCutsceneSetting(int kind) {
        switch (kind) {
            case INTRO: case STUN: case UNFOLD_FIRST: case DEATH: return 1;
            default: return 2;
        }
    }

    /** Only players within this many blocks (horizontally) get the cutscene. */
    public static double range(int kind) {
        switch (kind) {
            case INTRO: return 900;
            case DEATH: case STUN: case UNFOLD_FIRST: return 500;
            default: return 260;
        }
    }
}
