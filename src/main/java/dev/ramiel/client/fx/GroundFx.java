package dev.ramiel.client.fx;

import dev.ramiel.client.render.RamielRenderTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Effects that lie ON the ground and follow the terrain: shockwave rings, the red blast-zone marker,
 * and the glowing scars. These are drawn as geometry (not a screen shader) because that works on
 * every graphics setting and shader pack, and still hugs hills because every point samples the
 * terrain height.
 */
final class GroundFx {
    private GroundFx() {}

    /** Top face of the highest solid block at x,z, or {@code fallback} if that chunk is not loaded. */
    static double top(ClientLevel level, double x, double z, double fallback) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        if (!level.hasChunk(ix >> 4, iz >> 4)) return fallback;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
    }

    static void render(Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Draw d = new Draw(RamielRenderTypes.BEAM, camera);
        scars(d, level, camera.getPosition());
        zones(d, level, partial);
        waves(d, level, partial);
        d.flush();
    }

    // ------------------------------------------------------------------------------------------
    // Shockwave rings
    // ------------------------------------------------------------------------------------------
    private static void waves(Draw d, ClientLevel level, float partial) {
        for (FxState.Wave w : FxState.waves) {
            float p = (w.age + partial) / w.life;
            double radius = w.radius * (1.0 - Math.pow(1.0 - p, 2.2));
            float fade = (float) Math.pow(1.0 - p, 1.4);
            double thick = Math.max(1.5, w.radius * 0.07 * (1.0 - 0.5 * p));
            int segs = (int) Mth.clamp(radius * 0.8, 24, 96);
            float[] core = w.kind == 1 ? new float[]{1f, 0.45f, 0.75f} : new float[]{1f, 0.95f, 0.75f};
            float[] edge = w.kind == 1 ? new float[]{1f, 0.6f, 0.2f} : new float[]{1f, 0.55f, 0.15f};
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                double ix0 = w.pos.x + Math.cos(a0) * (radius - thick), iz0 = w.pos.z + Math.sin(a0) * (radius - thick);
                double ox0 = w.pos.x + Math.cos(a0) * radius, oz0 = w.pos.z + Math.sin(a0) * radius;
                double ix1 = w.pos.x + Math.cos(a1) * (radius - thick), iz1 = w.pos.z + Math.sin(a1) * (radius - thick);
                double ox1 = w.pos.x + Math.cos(a1) * radius, oz1 = w.pos.z + Math.sin(a1) * radius;
                double lift = 0.6 + thick * 0.15;
                d.vertex(ix0, top(level, ix0, iz0, w.pos.y) + lift, iz0, core[0], core[1], core[2], 0f);
                d.vertex(ox0, top(level, ox0, oz0, w.pos.y) + lift, oz0, edge[0], edge[1], edge[2], 0.8f * fade);
                d.vertex(ox1, top(level, ox1, oz1, w.pos.y) + lift, oz1, edge[0], edge[1], edge[2], 0.8f * fade);
                d.vertex(ix1, top(level, ix1, iz1, w.pos.y) + lift, iz1, core[0], core[1], core[2], 0f);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Blast-zone marker: red ring, inner rings, a faint fill, spokes and a radar sweep
    // ------------------------------------------------------------------------------------------
    private static final int SEGS = 96;
    private static final int RINGS = 7;                       // radii 0, 1/6 ... 1

    private static void zones(Draw d, ClientLevel level, float partial) {
        float time = FxState.now + partial;
        for (FxState.Zone z : FxState.zones.values()) {
            long left = z.end - FxState.now;
            if (left <= 0) continue;
            refreshHeights(level, z);
            // pulse faster as the end approaches
            double rate = left < 100 ? 0.9 : left < 400 ? 0.45 : 0.22;
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * rate);
            float urgent = left < 200 ? 1f : 0.75f;

            for (int k = 0; k < RINGS - 1; k++) {                                 // faint filled bands
                float a = 0.05f + 0.05f * pulse;
                band(d, z, k, k + 1, 1f, 0.12f, 0.06f, a * 0.6f, a);
            }
            band(d, z, RINGS - 1.6, RINGS - 1, 1f, 0.2f, 0.1f, 0.0f, 0.75f * urgent);   // bright rim
            band(d, z, RINGS - 2.0, RINGS - 1.9, 1f, 0.3f, 0.15f, 0.35f * urgent, 0.35f * urgent);
            band(d, z, 2.0, 2.08, 1f, 0.3f, 0.15f, 0.30f, 0.30f);
            band(d, z, 4.0, 4.08, 1f, 0.3f, 0.15f, 0.30f, 0.30f);

            // radar sweep: a wedge that turns once every 3 seconds
            double sweep = (time * 0.105) % (Math.PI * 2);
            for (int i = 0; i < 12; i++) {
                double a0 = sweep - i * 0.045, a1 = sweep - (i + 1) * 0.045;
                float a = 0.30f * (1f - i / 12f) * urgent;
                wedge(d, level, z, a0, a1, a);
            }
        }
    }

    /** Samples the ground under the zone once a second (it changes when blocks do). */
    private static void refreshHeights(ClientLevel level, FxState.Zone z) {
        if (z.heights != null && FxState.now - z.refreshed < 20) return;
        z.refreshed = FxState.now;
        z.heights = new double[RINGS][SEGS + 1];
        for (int k = 0; k < RINGS; k++) {
            double r = z.radius * k / (RINGS - 1.0);
            for (int i = 0; i <= SEGS; i++) {
                double a = i * Math.PI * 2 / SEGS;
                z.heights[k][i] = top(level, z.centre.x + Math.cos(a) * r, z.centre.z + Math.sin(a) * r, z.centre.y);
            }
        }
    }

    /** A band between ring indices f0 and f1 (fractions allowed). */
    private static void band(Draw d, FxState.Zone z, double f0, double f1, float r, float g, float b, float aIn, float aOut) {
        double r0 = z.radius * f0 / (RINGS - 1.0), r1 = z.radius * f1 / (RINGS - 1.0);
        int k0 = Mth.clamp((int) Math.round(f0), 0, RINGS - 1), k1 = Mth.clamp((int) Math.round(f1), 0, RINGS - 1);
        for (int i = 0; i < SEGS; i++) {
            double a0 = i * Math.PI * 2 / SEGS, a1 = (i + 1) * Math.PI * 2 / SEGS;
            double lift = 0.35;
            d.vertex(z.centre.x + Math.cos(a0) * r0, z.heights[k0][i] + lift, z.centre.z + Math.sin(a0) * r0, r, g, b, aIn);
            d.vertex(z.centre.x + Math.cos(a0) * r1, z.heights[k1][i] + lift, z.centre.z + Math.sin(a0) * r1, r, g, b, aOut);
            d.vertex(z.centre.x + Math.cos(a1) * r1, z.heights[k1][i + 1] + lift, z.centre.z + Math.sin(a1) * r1, r, g, b, aOut);
            d.vertex(z.centre.x + Math.cos(a1) * r0, z.heights[k0][i + 1] + lift, z.centre.z + Math.sin(a1) * r0, r, g, b, aIn);
        }
    }

    private static void wedge(Draw d, ClientLevel level, FxState.Zone z, double a0, double a1, float alpha) {
        int steps = 8;
        for (int s = 0; s < steps; s++) {
            double r0 = z.radius * s / steps, r1 = z.radius * (s + 1) / steps;
            double x00 = z.centre.x + Math.cos(a0) * r0, z00 = z.centre.z + Math.sin(a0) * r0;
            double x01 = z.centre.x + Math.cos(a0) * r1, z01 = z.centre.z + Math.sin(a0) * r1;
            double x10 = z.centre.x + Math.cos(a1) * r1, z10 = z.centre.z + Math.sin(a1) * r1;
            double x11 = z.centre.x + Math.cos(a1) * r0, z11 = z.centre.z + Math.sin(a1) * r0;
            d.vertex(x00, top(level, x00, z00, z.centre.y) + 0.4, z00, 1f, 0.35f, 0.2f, alpha);
            d.vertex(x01, top(level, x01, z01, z.centre.y) + 0.4, z01, 1f, 0.35f, 0.2f, alpha);
            d.vertex(x10, top(level, x10, z10, z.centre.y) + 0.4, z10, 1f, 0.35f, 0.2f, alpha * 0.4f);
            d.vertex(x11, top(level, x11, z11, z.centre.y) + 0.4, z11, 1f, 0.35f, 0.2f, alpha * 0.4f);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Scars: ground that glows like cooling metal, then fades
    // ------------------------------------------------------------------------------------------
    private static final double STEP = 3.0;
    private static final double DRAW_DISTANCE = 240.0;

    private static void scars(Draw d, ClientLevel level, Vec3 cam) {
        float time = FxState.now * 0.1f;
        for (FxState.Scar s : FxState.scars) {
            if (cam.distanceTo(s.pos) - s.radius > DRAW_DISTANCE) continue;
            double age = (double) (FxState.now - s.born) / s.life;
            double base = Math.pow(1.0 - Mth.clamp(age, 0, 1), 1.7);
            if (base < 0.03) continue;

            int n = (int) Math.ceil(s.radius * 2 / STEP) + 1;
            if (s.heights == null || s.cells != n) { s.heights = new int[n * n]; java.util.Arrays.fill(s.heights, Integer.MIN_VALUE); s.cells = n; s.refreshed = -999; }
            boolean refresh = FxState.now - s.refreshed >= 20;
            if (refresh) s.refreshed = FxState.now;

            for (int gx = 0; gx < n; gx++) {
                for (int gz = 0; gz < n; gz++) {
                    double x = s.pos.x - s.radius + gx * STEP + STEP * 0.5;
                    double z = s.pos.z - s.radius + gz * STEP + STEP * 0.5;
                    double dx = x - s.pos.x, dz = z - s.pos.z;
                    double dist = Math.sqrt(dx * dx + dz * dz) / s.radius;
                    if (dist > 1.0) continue;
                    if (Math.abs(x - cam.x) > DRAW_DISTANCE || Math.abs(z - cam.z) > DRAW_DISTANCE) continue;

                    int idx = gx * n + gz;
                    if (refresh || s.heights[idx] == Integer.MIN_VALUE) s.heights[idx] = (int) top(level, x, z, level.getMinBuildHeight());
                    int h = s.heights[idx];
                    if (h <= level.getMinBuildHeight() + 1) continue;

                    double rnd = hash(gx * 73 + gz * 151 + (int) (s.born % 997));
                    double radial = Math.pow(Mth.clamp(1.15 - dist, 0, 1), 0.8);
                    double flicker = 0.9 + 0.1 * Math.sin(time * 3.0 + rnd * 20.0);
                    double heat = base * radial * (0.55 + 0.45 * rnd) * flicker;
                    if (heat < 0.04) continue;
                    float[] c = heatColour((float) heat);
                    float a = (float) Math.min(0.62, 0.12 + heat * 0.8);
                    double y = h + 0.04;
                    double e = STEP * 0.5;
                    d.vertex(x - e, y, z - e, c[0], c[1], c[2], a);
                    d.vertex(x - e, y, z + e, c[0], c[1], c[2], a);
                    d.vertex(x + e, y, z + e, c[0], c[1], c[2], a);
                    d.vertex(x + e, y, z - e, c[0], c[1], c[2], a);
                }
            }
        }
    }

    /** White-yellow when fresh, through orange and red, to a dull ember. */
    private static float[] heatColour(float h) {
        if (h > 0.75f) return new float[]{1f, 0.95f, 0.70f};
        if (h > 0.50f) return new float[]{1f, 0.60f, 0.15f};
        if (h > 0.25f) return new float[]{0.95f, 0.20f, 0.05f};
        return new float[]{0.50f, 0.05f, 0.02f};
    }

    private static double hash(int i) {
        double x = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
        return x - Math.floor(x);
    }
}
