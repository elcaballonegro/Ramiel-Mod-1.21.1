package dev.ramiel.client.model;

import dev.ramiel.Form;
import org.joml.Quaternionf;

/**
 * The recipe for each shape, as bricks (see {@link Brick}). Positions are in R units.
 *
 * Every form fills the SAME pool of bricks. Which index does what is fixed, so morphing from one
 * form to another slides and turns "plate 3" into "plate 3" and so on. A piece a form does not use
 * is collapsed to nothing: when the next form needs it, it flies out of the body's centre (and
 * when the form ends it flies back in). Nothing is ever faded. See {@link #fold} for the shared
 * "folded" pose the big forms collapse into on the way in and out.
 *
 *   0..1   halves   (the two pyramids of the octahedron; caps, backing pieces in other forms)
 *   2..9   plates   (slabs and flat squares)
 *   10..41 spikes   (long pyramids)
 *   42..53 shards   (small cubes: beads, debris, blobs)
 *   54     needle   (the drill / the red rod)
 *   55..56 core     (red octahedron in the middle)
 *
 * Every function gets {@code t} (animation time in ticks) and {@code charge} (0..1), so shapes can
 * spin or grow while an attack charges.
 */
public final class FormPoses {
    private FormPoses() {}

    public static final int HALF_TOP = 0, HALF_BOT = 1;
    public static final int PLATE = 2, PLATES = 8;
    public static final int SPIKE = 10, SPIKES = 32;
    public static final int SHARD = 42, SHARDS = 12;
    public static final int NEEDLE = 54;
    /** Sunburst's wheel: 20 small octahedra (2 pyramids each), 20 spokes, 10 shields, 10 long + 10 short spikes. */
    public static final int WHEEL = 55, WHEEL_BRICKS = 90;
    public static final int WHEEL_OCTA = WHEEL, WHEEL_SPOKE = WHEEL + 40, WHEEL_SHIELD = WHEEL + 60, WHEEL_TIP = WHEEL + 70;
    public static final int CORE_TOP = 145, CORE_BOT = 146;     // the core is always the last two bricks
    public static final int COUNT = 147;

    // colours
    public static final float[] BODY = {0.16f, 0.33f, 0.95f};
    public static final float[] LIGHT = {0.35f, 0.58f, 1.00f};
    public static final float[] PLATE_C = {0.35f, 0.95f, 0.78f};
    public static final float[] DARK = {0.05f, 0.09f, 0.25f};
    public static final float[] CYAN = {0.40f, 0.95f, 1.00f};
    public static final float[] HOT = {1.00f, 0.22f, 0.20f};
    public static final float[] MAGENTA = {1.00f, 0.30f, 0.85f};
    public static final float[] DEEP = {0.10f, 0.20f, 0.72f};

    private static final float PI = (float) Math.PI;
    private static final float ROOT_HALF = 0.70710678f;

    public static Brick[] newPool() {
        Brick[] pool = new Brick[COUNT];
        for (int i = 0; i < COUNT; i++) pool[i] = new Brick().collapse();
        return pool;
    }

    /** Fill {@code b} with the bricks of {@code form}. {@code coreScale} is 0 (hidden) .. 1 (fully shown). */
    public static void fill(Form form, Brick[] b, float t, float charge, float drill, float coreScale) {
        for (Brick k : b) k.collapse();
        switch (form) {
            case DORMANT, SHIELD -> octahedron(b, 1f);
            case DRILL -> { octahedron(b, 1f); needle(b, t, drill); }
            case LANCE -> lance(b, t, charge);
            case CROSS -> cross(b, t, charge);
            case SUNBURST -> sunburst(b, t, charge);
            case SENTINEL -> sentinel(b, t, charge);
            case STAR -> star(b, t, charge);
            case URCHIN -> urchin(b, t, charge);
        }
        // the core: red inside every open form, magenta in the star
        float size = (switch (form) {
            case STAR -> 0.16f + 0.22f * charge;
            case URCHIN -> 0.30f;
            case SUNBURST -> 0.12f + 0.08f * charge;
            case CROSS, LANCE, SENTINEL -> 0.13f + 0.08f * charge;
            default -> 0.12f;
        }) * coreScale;
        core(b, size, form == Form.STAR ? MAGENTA : HOT, t);
    }

    // ------------------------------------------------------------------------------------------
    // The folded pose (frames 244-263): what EVERY big form collapses into when it ends, and what
    // the octahedron breaks into when a form begins. A stack of thin plates like pages, small caps
    // top and bottom, a bundle of long thin shards, a few loose chips and a thin spine.
    // RamielModel routes form -> dormant and dormant -> form through this pose, so all transformations
    // look like the same crystal folding up and snapping shut (or splitting open).
    // ------------------------------------------------------------------------------------------
    public static void fold(Brick[] b, float t, float coreScale) {
        for (Brick k : b) k.collapse();
        for (int i = 0; i < PLATES; i++) {
            float y = (i - 3.5f) * 0.075f;
            float hx = 0.52f + 0.12f * (float) Math.sin(i * 1.3f);
            float hz = 0.38f + 0.09f * (float) Math.cos(i * 1.7f);
            boolean mint = i % 2 == 0;
            b[PLATE + i].box(0f, y, 0f, hx, 0.026f, hz).rot(new Quaternionf().rotationY(i * 0.14f))
                    .colour(mint ? PLATE_C : BODY, mint ? 0.3f : 0.05f, 0.92f);
        }
        b[HALF_TOP].spike(0f, 0.30f, 0f, 0f, 1f, 0f, 0.30f, 0.22f).colour(LIGHT, 0.1f, 0.92f);
        b[HALF_BOT].spike(0f, -0.30f, 0f, 0f, -1f, 0f, 0.30f, 0.22f).colour(LIGHT, 0.1f, 0.92f);
        for (int k = 0; k < 16; k++) {
            float sign = (k % 2 == 0) ? 1f : -1f;
            float sx = (hash(k + 60) - 0.5f) * 0.26f, sz = (hash(k + 80) - 0.5f) * 0.26f;
            float len = 0.55f + 0.65f * hash(k + 9);
            float half = 0.022f + 0.03f * hash(k + 3);
            boolean bright = k % 4 == 0;
            b[SPIKE + k].spike(sx, sign * 0.34f, sz, sx, sign, sz, len, half).colour(bright ? CYAN : BODY, bright ? 0.35f : 0.05f, 0.92f);
        }
        for (int i = 0; i < SHARDS; i++) {
            float[] d = fib(i, SHARDS);
            float radius = 0.42f + 0.30f * hash(i + 17);
            float size = 0.035f + 0.03f * hash(i + 27);
            b[SHARD + i].box(d[0] * radius, d[1] * radius * 0.6f, d[2] * radius, size, size, size)
                    .rot(new Quaternionf().rotationXYZ(i * 0.9f, i * 0.5f, i * 0.3f))
                    .colour(i % 3 == 0 ? CYAN : LIGHT, 0.2f, 0.9f);
        }
        b[NEEDLE].spike(0f, -0.55f, 0f, 0f, 1f, 0f, 1.1f, 0.022f).colour(CYAN, 0.6f, 1f);
        core(b, 0.12f * coreScale, HOT, t);
    }

    // ------------------------------------------------------------------------------------------
    // Dormant octahedron: two pyramids, corners on the axes (so |x|+|y|+|z| <= 1, matching the hit test)
    // ------------------------------------------------------------------------------------------
    private static void octahedron(Brick[] b, float s) {
        Quaternionf yaw45 = new Quaternionf().rotationY(PI / 4f);
        pyramid(b[HALF_TOP], 0f, 0.5f * s, 0f, s, ROOT_HALF * s, yaw45).colour(BODY, 0.05f, 0.9f);
        Quaternionf flip = new Quaternionf().rotationX(PI).mul(yaw45);
        pyramid(b[HALF_BOT], 0f, -0.5f * s, 0f, s, ROOT_HALF * s, flip).colour(BODY, 0.05f, 0.9f);
    }

    private static Brick pyramid(Brick k, float cx, float cy, float cz, float height, float halfBase, Quaternionf q) {
        k.x = cx; k.y = cy; k.z = cz;
        k.rot(q);
        k.h = height; k.bw = k.bd = halfBase; k.tw = k.td = 0f;
        return k;
    }

    /** The drill: a thin needle hanging from the lower corner, with glowing beads running down it. */
    private static void needle(Brick[] b, float t, float length) {
        if (length < 0.02f) return;
        b[NEEDLE].spike(0f, -1f, 0f, 0f, -1f, 0f, length, 0.05f).colour(CYAN, 0.55f, 1f);
        for (int k = 0; k < 6; k++) {
            float y = -1f - length * (0.12f + 0.15f * k);
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.35f - k * 0.9f);
            b[SHARD + k].box(0f, y, 0f, 0.045f, 0.045f, 0.045f).rot(new Quaternionf().rotationY(PI / 4f))
                    .colour(CYAN, 0.4f + 0.6f * pulse, 1f);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Attack 1: two chunky stepped blocks around a spine of beads (frames 98-121).
    // Each block is a base slab with a U-shaped raised rim (a back bar and two prongs, open to the
    // front), a small cap on the outside, and the red core in the gap between them.
    // plates 2..5 = upper block, 6..9 = lower block
    // ------------------------------------------------------------------------------------------
    private static void lance(Brick[] b, float t, float charge) {
        float sep = 0.07f * charge;
        for (int half = 0; half < 2; half++) {
            float s = half == 0 ? 1f : -1f;
            int p = PLATE + half * 4;
            b[p].box(0f, s * (0.40f + sep), 0f, 0.82f, 0.10f, 0.60f).colour(BODY, 0.05f, 0.92f);
            b[p + 1].box(0f, s * (0.60f + sep), -0.40f, 0.82f, 0.10f, 0.20f).colour(LIGHT, 0.25f, 0.92f);
            b[p + 2].box(-0.62f, s * (0.60f + sep), 0.12f, 0.20f, 0.10f, 0.48f).colour(LIGHT, 0.25f, 0.92f);
            b[p + 3].box(0.62f, s * (0.60f + sep), 0.12f, 0.20f, 0.10f, 0.48f).colour(LIGHT, 0.25f, 0.92f);
        }
        b[HALF_TOP].spike(0f, 0.70f + sep, 0f, 0f, 1f, 0f, 0.36f, 0.30f).colour(LIGHT, 0.1f, 0.92f);
        b[HALF_BOT].spike(0f, -(0.70f + sep), 0f, 0f, -1f, 0f, 0.36f, 0.30f).colour(LIGHT, 0.1f, 0.92f);
        Quaternionf diamond = new Quaternionf().rotationY(PI / 4f);
        for (int k = 0; k < 6; k++) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.3f + k * 0.8f);
            b[SHARD + k].box(0f, -0.25f + 0.10f * k, 0f, 0.040f, 0.040f, 0.040f).rot(diamond)
                    .colour(CYAN, 0.55f + 0.45f * pulse * (0.4f + charge), 1f);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Attack 2: the plus-shaped cross with detached spinning end caps (frames 146-167)
    // ------------------------------------------------------------------------------------------
    private static void cross(Brick[] b, float t, float charge) {
        float[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        float spinRate = 0.05f * (0.4f + 2.6f * charge);
        float armLen = 1.35f + 0.18f * charge;
        // the hub: a plus-shaped cluster of six cubes
        float[][] hub = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int k = 0; k < 6; k++) {
            b[SHARD + k].box(hub[k][0] * 0.17f, hub[k][1] * 0.17f, hub[k][2] * 0.17f, 0.09f, 0.09f, 0.09f).colour(BODY, 0.1f, 0.95f);
        }
        for (int k = 0; k < 4; k++) {
            float dx = dirs[k][0], dy = dirs[k][1];
            // each arm is a long pyramid whose point meets the others at the centre; its flat base faces outward
            b[SPIKE + k].spike(dx * armLen, dy * armLen, 0f, -dx, -dy, 0f, armLen - 0.10f, 0.16f).colour(BODY, 0.05f, 0.92f);

            Quaternionf spin = new Quaternionf().rotationAxis(t * spinRate * (k % 2 == 0 ? 1f : -1f), dx, dy, 0f);
            float d = 1.62f + 0.12f * charge;
            float thin = 0.03f, wide = 0.30f;
            // a flat plate facing along the arm
            b[PLATE + k].box(dx * d, dy * d, 0f, dx != 0 ? thin : wide, dy != 0 ? thin : wide, wide)
                    .rot(spin).colour(PLATE_C, 0.35f + 0.3f * charge, 0.92f);
            // a small diamond floating beyond it, spinning faster
            Quaternionf diamond = new Quaternionf().rotationAxis(-t * spinRate * 1.8f, dx, dy, 0f).mul(new Quaternionf().rotationY(PI / 4f));
            b[SHARD + 6 + k].box(dx * (d + 0.4f), dy * (d + 0.4f), 0f, 0.10f, 0.10f, 0.10f)
                    .rot(diamond).colour(CYAN, 0.6f, 1f);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Attack 3: radial spikes, spinning faster and faster (frames 417-444)
    // ------------------------------------------------------------------------------------------
    private static void sunburst(Brick[] b, float t, float charge) {
        float c = smooth(charge);
        final int n = 20;
        final float ring = 0.80f;          // radius of the ring of small octahedra (R units; the whole wheel fits inside 1.0 R)
        final float ro = 0.11f;            // radius of each small octahedron
        final float rc = 0.26f;            // radius of the central octahedron

        // the central octahedron: glassy, so the red core shows through it
        octahedron(b, rc);
        b[HALF_TOP].colour(CYAN, 0.35f, 0.50f);
        b[HALF_BOT].colour(CYAN, 0.35f, 0.50f);

        for (int i = 0; i < n; i++) {
            float theta = (float) (2.0 * Math.PI * i / n);
            float cx = ring * (float) Math.cos(theta), cz = ring * (float) Math.sin(theta);
            float rx = (float) Math.cos(theta), rz = (float) Math.sin(theta);       // outward
            float yaw = -theta + PI / 4f;                                            // diamond corners point outward / sideways
            boolean shielded = (i % 2 == 0);

            // a small octahedron: two pyramids base to base
            Quaternionf up = new Quaternionf().rotationY(yaw);
            Quaternionf down = new Quaternionf().rotationY(yaw).mul(new Quaternionf().rotationX(PI));
            float[] tint = shielded ? LIGHT : BODY;
            pyramid(b[WHEEL_OCTA + 2 * i], cx, 0.5f * ro, cz, ro, ROOT_HALF * ro, up).colour(tint, 0.10f, 0.92f);
            pyramid(b[WHEEL_OCTA + 2 * i + 1], cx, -0.5f * ro, cz, ro, ROOT_HALF * ro, down).colour(tint, 0.10f, 0.92f);

            // a thin crystalline spoke from the central octahedron to the small one
            float inner = 0.16f, outer = ring - ro;
            b[WHEEL_SPOKE + i].spike(rx * inner, 0f, rz * inner, rx, 0f, rz, outer - inner, 0.03f)
                    .colour(CYAN, 0.45f + 0.4f * charge, 0.90f);

            int j = i / 2;
            if (shielded) {
                // a shield standing just outside it, facing outward: a kite, wide at the top and pointed below
                float at = ring + ro + 0.07f;
                Brick sh = b[WHEEL_SHIELD + j].box(rx * at, 0f, rz * at, 0.02f, 0.24f, 0.17f)
                        .rot(new Quaternionf().rotationY(-theta).rotateZ(-0.10f));   // leaned outward at the top
                sh.bd = 0.06f;                       // bottom half-width (tangential); the top keeps 0.17
                sh.colour(PLATE_C, 0.30f + 0.3f * charge, 0.85f);
            } else {
                // a long spike up and a short one down
                float tall = 0.9f + 0.9f * c, stub = 0.35f + 0.2f * c;
                b[WHEEL_TIP + 2 * j].spike(cx, ro, cz, 0f, 1f, 0f, tall, 0.045f).colour(DEEP, 0.08f, 0.92f);
                b[WHEEL_TIP + 2 * j + 1].spike(cx, -ro, cz, 0f, -1f, 0f, stub, 0.035f).colour(BODY, 0.08f, 0.92f);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Attack 4: nested plates in front of the core, three pieces behind (frames 520-530)
    // ------------------------------------------------------------------------------------------
    private static void sentinel(Brick[] b, float t, float charge) {
        for (int i = 0; i < 6; i++) {
            float half = 0.95f - 0.035f * i;
            float off = 0.045f * i;
            b[PLATE + i].box(-off, off, 1.0f + 0.075f * i, half, half, 0.02f).colour(PLATE_C, 0.4f + 0.35f * charge, 0.92f);
        }
        // three big backing pieces (two pyramids and a prism)
        b[HALF_TOP].spike(-0.55f, 0.30f, 0.30f, 0f, 0.05f, -1f, 1.2f, 0.55f).colour(BODY, 0.05f, 0.9f);
        b[HALF_BOT].spike(0.50f, -0.35f, 0.30f, 0.05f, -0.2f, -1f, 1.1f, 0.50f).colour(BODY, 0.05f, 0.9f);
        b[PLATE + 6].spike(0.10f, -0.10f, -0.30f, -0.4f, -1f, -0.2f, 1.0f, 0.45f).colour(DEEP, 0.05f, 0.92f);
        // the thin rod with the red tip
        b[NEEDLE].spike(0f, 0f, 0f, 0f, 0f, 1f, 1.3f, 0.035f).colour(HOT, 1f, 1f);
        // loose pieces drifting off to one side: four long blades and four chips
        for (int i = 0; i < 8; i++) {
            float bob = 0.05f * (float) Math.sin(t * 0.05f + i);
            float px = 1.3f + 0.16f * i, py = 0.5f - 0.14f * i + bob, pz = -0.1f + 0.07f * i;
            if (i < 4) {
                b[SHARD + i].spike(px, py, pz, 0.9f, 0.4f, 0.05f, 0.55f + 0.1f * i, 0.045f).colour(i % 2 == 0 ? BODY : LIGHT, 0.1f, 0.9f);
            } else {
                Quaternionf q = new Quaternionf().rotationXYZ(i * 0.7f, t * 0.01f * (i + 1), i * 0.3f);
                b[SHARD + i].box(px, py, pz, 0.07f, 0.07f, 0.07f).rot(q).colour(i % 2 == 0 ? BODY : LIGHT, 0.1f, 0.9f);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Final form: upside-down five-point star, unfolding three times as the charge grows (725-746)
    // ------------------------------------------------------------------------------------------
    private static void star(Brick[] b, float t, float charge) {
        float u1 = smooth(charge / 0.34f), u2 = smooth((charge - 0.34f) / 0.33f), u3 = smooth((charge - 0.67f) / 0.33f);
        float l1 = 0.70f + 0.50f * u1;
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(-90.0 + 72.0 * k);
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            // layer 1 is always there; layers 2 and 3 are the second and third unfolding
            b[SPIKE + k].spike(dx * 0.10f, dy * 0.10f, 0f, dx, dy, 0f, l1, 0.20f).colour(BODY, 0.1f, 0.94f);
            double a2 = Math.toRadians(-90.0 + 72.0 * k + 36.0);
            b[SPIKE + 5 + k].spike((float) Math.cos(a2) * 0.08f, (float) Math.sin(a2) * 0.08f, 0f,
                    (float) Math.cos(a2), (float) Math.sin(a2), 0f, l1 * 0.45f, 0.12f).colour(LIGHT, 0.1f, 0.94f);
            b[SPIKE + 10 + k].spike(dx * 0.12f, dy * 0.12f, -0.05f, dx, dy, 0f, 1.7f * u2, 0.27f).colour(LIGHT, 0.12f, 0.9f);
            b[SPIKE + 15 + k].spike(dx * 0.14f, dy * 0.14f, -0.10f, dx, dy, 0f, 2.7f * u3, 0.34f)
                    .colour(new float[]{0.45f, 0.75f, 1f}, 0.28f, 0.88f);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Stun / death: dark spikes in every direction, red blobs around the exposed core
    // ------------------------------------------------------------------------------------------
    private static void urchin(Brick[] b, float t, float charge) {
        int n = 32;
        for (int i = 0; i < n; i++) {
            float[] d = fib(i, n);
            float len = 1.8f + 1.4f * hash(i + 3);
            float half = 0.10f + 0.10f * hash(i + 11);
            b[SPIKE + i].spike(d[0] * 0.2f, d[1] * 0.2f, d[2] * 0.2f, d[0], d[1], d[2], len, half)
                    .colour(DARK, i % 7 == 0 ? 0.25f : 0f, 0.95f);
        }
        for (int i = 0; i < 12; i++) {
            float[] d = fib(i + 5, 12);
            float radius = 0.22f + 0.2f * hash(i + 31);
            float flicker = 0.6f + 0.4f * (float) Math.sin(t * 0.4f + i * 1.7f);
            b[SHARD + i].box(d[0] * radius, d[1] * radius, d[2] * radius, 0.09f, 0.09f, 0.09f).colour(HOT, flicker, 1f);
        }
    }

    // ------------------------------------------------------------------------------------------
    private static void core(Brick[] b, float s, float[] rgb, float t) {
        if (s < 0.01f) return;
        Quaternionf q = new Quaternionf().rotationY(t * 0.08f);
        pyramid(b[CORE_TOP], 0f, 0.5f * s, 0f, s, ROOT_HALF * s, q).colour(rgb, 1f, 1f);
        Quaternionf flip = new Quaternionf().rotationX(PI).mul(new Quaternionf().rotationY(t * 0.08f));
        pyramid(b[CORE_BOT], 0f, -0.5f * s, 0f, s, ROOT_HALF * s, flip).colour(rgb, 1f, 1f);
    }

    /** Deterministic pseudo-random number in [0,1) from an integer. */
    public static float hash(int i) {
        float x = (float) Math.sin(i * 12.9898 + 78.233) * 43758.5453f;
        return x - (float) Math.floor(x);
    }

    /** The i-th of n evenly spread unit vectors on a sphere (i wraps around, so any index is safe). */
    static float[] fib(int i, int n) {
        i = Math.floorMod(i, n);
        float y = 1f - 2f * (i + 0.5f) / n;
        float rad = (float) Math.sqrt(Math.max(0f, 1f - y * y));
        float phi = i * 2.3999632f;
        return new float[]{(float) Math.cos(phi) * rad, y, (float) Math.sin(phi) * rad};
    }

    static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }
}
