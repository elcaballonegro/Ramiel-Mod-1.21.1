package dev.ramiel.world;

/**
 * The numbers behind the shockwave. Plain maths with no Minecraft types, so it can be unit tested
 * (see docs/SHOCKWAVE.md for what each rule means).
 */
public final class ShockMath {
    private ShockMath() {}

    /** Horizontal speed cap (blocks per tick). Faster and the server starts rubber-banding players. */
    public static final double MAX_HORIZONTAL = 2.8;
    /** Upward speed cap (blocks per tick): about 5 blocks of air. */
    public static final double MAX_VERTICAL = 0.9;

    /** Where the ring is after {@code tick} ticks. Same curve as the client's ground ring (GroundFx.waves). */
    public static double radiusAt(double radius, int life, double tick) {
        double p = clamp(tick / Math.max(1, life), 0.0, 1.0);
        return radius * (1.0 - Math.pow(1.0 - p, 2.2));
    }

    /** 1 at the centre, 0 at the edge. */
    public static double falloff(double distance, double radius) {
        if (distance >= radius) return 0.0;
        return Math.pow(1.0 - Math.max(0.0, distance) / radius, 1.6);
    }

    public static double pushSpeed(double strength, double falloff) {
        return Math.min(MAX_HORIZONTAL, 2.4 * strength * falloff);
    }

    public static double lift(double strength, double falloff) {
        return Math.min(MAX_VERTICAL, 0.12 + 0.30 * strength * falloff);
    }

    /** How much of the wave gets through terrain: {@code solidSamples} samples were solid, {@code spacing} blocks apart. */
    public static double coverFactor(int solidSamples, double spacing) {
        return clamp(1.0 - solidSamples * spacing / 36.0, 0.12, 1.0);
    }

    /** Damage inside the inner 30% of the radius, 0 outside. */
    public static double damage(double distance, double radius, double strength) {
        double inner = 0.30 * radius;
        if (distance >= inner) return 0.0;
        return 10.0 * strength * (1.0 - distance / inner);
    }

    /** Nearest of the four directions blocks can fall in: {dx, dz}. */
    public static int[] cardinal(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return new int[]{dx >= 0 ? 1 : -1, 0};
        return new int[]{0, dz >= 0 ? 1 : -1};
    }

    /**
     * Where a block ends up when a tree falls 90 degrees toward (dx, dz), as an offset from the base block.
     * The tree turns about the centre of its base block: what was "up" becomes "toward (dx, dz)", what was
     * toward (dx, dz) becomes "down", and sideways stays sideways. This is exactly the rotation the client draws
     * (about the axis (dz, 0, -dx)), so the entity and the placed blocks agree.
     */
    public static int[] rotateOffset(int ox, int oy, int oz, int dx, int dz) {
        int along = ox * dx + oz * dz;
        int side = -ox * dz + oz * dx;
        return new int[]{oy * dx - side * dz, -along, oy * dz + side * dx};
    }

    /** Axis of a log (0 = X, 1 = Y, 2 = Z) after the tree falls toward (dx, dz). */
    public static int rotateAxis(int axis, int dx, int dz) {
        int fallAxis = dx != 0 ? 0 : 2;
        if (axis == 1) return fallAxis;
        if (axis == fallAxis) return 1;
        return axis;
    }

    /** How long a tree of this height takes to fall. */
    public static int fallTicks(int height) {
        return (int) clamp(16 + height * 1.3, 20, 60);
    }

    /** 0..1 progress of the fall: a slow creak, then faster and faster. */
    public static double fallProgress(double p) {
        return Math.pow(clamp(p, 0.0, 1.0), 2.2);
    }

    private static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }
}
