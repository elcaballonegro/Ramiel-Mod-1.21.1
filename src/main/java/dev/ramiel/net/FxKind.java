package dev.ramiel.net;

/** Numbers that say WHAT an {@link Payloads.Fx} packet wants the client to draw. */
public final class FxKind {
    private FxKind() {}

    /** Anime-style impact frame. a = duration in ticks. */
    public static final int IMPACT_FRAME = 1;
    /** Expanding ring on the ground. a = radius, b = duration ticks. */
    public static final int SHOCKWAVE = 2;
    /** Cluster of glowing spheres + fireball (the big explosion look). a = radius. */
    public static final int BLAST = 3;
    /** A glowing, slowly cooling scar on the ground. a = radius, b = lifetime ticks. */
    public static final int SCAR = 4;
    /** Red blast-zone marker. a = radius, b = ticks left, f1 = zone id. */
    public static final int WARN_ZONE = 5;
    /** Hexagon ripple on the shield. (a,b,c) = outward direction, f1 = strength. */
    public static final int SHIELD_HIT = 6;
    /** The shield shatters. */
    public static final int SHIELD_BREAK = 7;
    /** Beam hitting something: sparks and flare. (a,b,c) = beam direction, f1 = size. */
    public static final int BEAM_IMPACT = 8;
    /** Pink/orange rings where the drill touches the ground. f1 = size. */
    public static final int DRILL_SPARK = 9;
    /** Camera shake for everyone nearby. a = strength, b = ticks. */
    public static final int SHAKE = 10;
    /** The railcannon beam. pos = start, (a,b,c) = end, f1 = width, f2 = ticks. */
    public static final int RAIL_BEAM = 11;
    /** Dust cloud / secondary explosion. a = radius. */
    public static final int POOF = 12;
}
