package dev.ramiel;

/**
 * Every shape Ramiel can take. The renderer (client/model/FormPoses) knows how to build each
 * one out of the same pool of bricks, and morphs between two of them by interpolating bricks.
 *
 * The numbers in the comments are the reference frames this was modelled on.
 */
public enum Form {
    /** The calm octahedron. */
    DORMANT,
    /** Octahedron with its lower corner stretched into a thin needle (frames 340-379). */
    DRILL,
    /** Attack 1: the body splits into stepped slabs around a spine of beads (frames 98-121). */
    LANCE,
    /** Attack 2: the plus-shaped cross with spinning end caps (frames 146-167). */
    CROSS,
    /** Attack 3: radial spikes on every side, spinning faster and faster (frames 417-444). */
    SUNBURST,
    /** Attack 4: a stack of glowing plates in front of the core, three pieces behind (frames 520-530). */
    SENTINEL,
    /** Final form: the unfolding upside-down star (frames 725-746). */
    STAR,
    /** Shield posture: one face of the octahedron turned toward the threat (frames 505-515). */
    SHIELD,
    /** Stunned / dying: dark spikes in every direction, core exposed (frames 646-648, 915-963). */
    URCHIN;

    private static final Form[] ALL = values();

    public static Form byId(int id) {
        return ALL[Math.floorMod(id, ALL.length)];
    }
}
