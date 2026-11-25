package dev.ramiel.client.fx;

import dev.ramiel.client.render.RamielRenderTypes;
import dev.ramiel.entity.RamielEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Soft glows: the halo around the exposed core, streaks of energy gathering toward it while it
 * charges, the chain-of-spheres explosion look (frames 484-500), and the little flares where
 * beams strike.
 */
final class GlowFx {
    private GlowFx() {}

    static void render(Camera camera, float partial) {
        if (Minecraft.getInstance().level == null) return;
        Draw d = new Draw(RamielRenderTypes.BEAM, camera);
        float time = FxState.now + partial;
        for (RamielEntity r : FxRender.ramiels()) core(d, r, partial, time);
        for (FxState.Blast b : FxState.blasts) blast(d, b, partial);
        for (FxState.Flare f : FxState.flares) {
            float p = (f.age + partial) / f.life;
            d.halo(f.pos, f.size * (0.6 + 0.8 * p), f.r, f.g, f.b, 1f - p);
        }
        d.flush();
    }

    // ------------------------------------------------------------------------------------------
    private static void core(Draw d, RamielEntity r, float partial, float time) {
        float open = r.coreAnim(partial);
        float charge = r.charge(partial);
        if (open < 0.05f) return;
        Vec3 c = r.getPosition(partial).add(0, RamielEntity.CORE_BOX / 2.0 + r.arrivalOffsetY(partial), 0);
        boolean star = r.form() == dev.ramiel.Form.STAR;
        float cr = 1f, cg = star ? 0.3f : 0.2f, cb = star ? 0.85f : 0.15f;
        float pulse = 0.85f + 0.15f * (float) Math.sin(time * 0.35f);

        d.halo(c, (5.0 + 14.0 * charge) * open * pulse, cr, cg, cb, 0.9f * open);

        if (charge > 0.05f && r.beamKind() == 0) {
            // an energy ball swells at the core
            d.sphere(c, (2.5 + 7.0 * charge) * open, 10, 14, cr, cg + 0.3f, cb + 0.1f, 0.35f * charge);
            // streaks of light fall inward from every side; more of them, and faster, as it charges
            int streaks = 26;
            for (int i = 0; i < streaks; i++) {
                float[] dir = fib(i, streaks);
                double phase = frac(time * (0.015 + 0.03 * charge) + hash(i));
                double dist = 4.0 + 46.0 * (1.0 - phase);
                Vec3 v = new Vec3(dir[0], dir[1], dir[2]);
                Vec3 from = c.add(v.scale(dist + 3.0)), to = c.add(v.scale(dist));
                Vec3 side = Draw.perpendicular(v);
                d.ribbonFade(from, to, side, 0.18, 1f, 0.6f, 0.85f, 0f, 0.8f * charge * (float) phase);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    /** A chain of glowing spheres that swell, then a big fireball (frames 484-500). */
    private static void blast(Draw d, FxState.Blast b, float partial) {
        float p = (b.age + partial) / b.life;
        java.util.Random rnd = new java.util.Random(b.seed);

        // a white flash right at the start
        if (p < 0.18f) d.sphere(b.pos.add(0, b.radius * 0.1, 0), b.radius * 0.5 * (p / 0.18), 12, 18, 1f, 1f, 0.95f, 0.9f * (1f - p / 0.18f));

        // many small glowing spheres rising out of the ground
        int spheres = 26;
        for (int i = 0; i < spheres; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * b.radius * 0.45;
            double h = rnd.nextDouble() * b.radius * 0.25;
            double size = (0.06 + rnd.nextDouble() * 0.12) * b.radius;
            double delay = rnd.nextDouble() * 0.35;
            float local = Mth.clamp((p - delay) / (1f - delay), 0f, 1f);
            if (local <= 0f) continue;
            Vec3 c = b.pos.add(Math.cos(a) * rr, h + local * b.radius * 0.18, Math.sin(a) * rr);
            float fade = (float) Math.pow(1.0 - local, 1.3);
            d.sphere(c, size * (0.4 + 0.6 * Math.sqrt(local)), 8, 10, 1f, 0.9f, 0.6f, 0.55f * fade);
            d.sphere(c, size * 1.6 * (0.4 + 0.6 * Math.sqrt(local)), 8, 10, 1f, 0.5f, 0.15f, 0.25f * fade);
        }
        // the main fireball
        float k = (float) (1.0 - Math.pow(1.0 - Mth.clamp(p * 1.4f, 0f, 1f), 2.0));
        float fade = Mth.clamp(1f - (p - 0.25f) / 0.75f, 0f, 1f);
        Vec3 c = b.pos.add(0, b.radius * 0.28 * k, 0);
        d.sphere(c, b.radius * 0.5 * k, 14, 20, 1f, 0.55f, 0.12f, 0.45f * fade);
        d.sphere(c, b.radius * 0.35 * k, 12, 18, 1f, 0.85f, 0.5f, 0.45f * fade);
    }

    // ------------------------------------------------------------------------------------------
    private static float[] fib(int i, int n) {
        float y = 1f - 2f * (i + 0.5f) / n;
        float rad = (float) Math.sqrt(Math.max(0f, 1f - y * y));
        float phi = i * 2.3999632f;
        return new float[]{(float) Math.cos(phi) * rad, y, (float) Math.sin(phi) * rad};
    }

    private static double hash(int i) {
        double x = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
        return x - Math.floor(x);
    }

    private static double frac(double x) { return x - Math.floor(x); }
}
