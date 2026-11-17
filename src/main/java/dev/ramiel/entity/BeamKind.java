package dev.ramiel.entity;

/** What kind of beam Ramiel is firing right now (synced to clients so they can draw it). */
public final class BeamKind {
    private BeamKind() {}
    public static final int NONE = 0;
    public static final int LANCE = 1;       // thin pink-white beam
    public static final int CROSS = 2;       // thick gold beam that blooms into a cross
    public static final int SUNBURST = 3;    // thin magenta sweeping beam
    public static final int SENTINEL = 4;    // thick white tracking beam
    public static final int STAR = 5;        // the mountain-breaker
    public static final int TELEGRAPH = 9;   // faint red aiming line shown before a beam fires
}
