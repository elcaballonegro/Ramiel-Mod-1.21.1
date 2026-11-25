package dev.ramiel.client.fx;

import dev.ramiel.client.render.RamielRenderTypes;
import dev.ramiel.entity.RamielEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shield: a plate of golden hexagon outlines floating in front of the face Ramiel has turned
 * toward the threat (frames 505-515). Quiet while idle; when something hits it, a ring of rainbow
 * colour spreads across the cells from the impact point; when it breaks the cells fly apart.
 */
final class ShieldFx {
    private ShieldFx() {}

    private static final double CELL = 3.4;               // hexagon radius in blocks
    private static final double PLATE = RamielEntity.R * 0.95;
    private static final double OFFSET = RamielEntity.R * 0.66;   // plate distance from the centre

    static void render(Camera camera, float partial) {
        if (Minecraft.getInstance().level == null) return;
        Draw d = new Draw(RamielRenderTypes.BEAM, camera);
        for (RamielEntity r : FxRender.ramiels()) {
            if (r.shieldUp()) plate(d, r, partial, false);
            else if (hasShatter(r)) plate(d, r, partial, true);
        }
        d.flush();
    }

    private static boolean hasShatter(RamielEntity r) {
        for (FxState.Ripple rp : FxState.ripples) if (rp.shatter && rp.pos.distanceTo(r.centre()) < RamielEntity.R * 2) return true;
        return false;
    }

    private static void plate(Draw d, RamielEntity r, float partial, boolean broken) {
        Vec3 centre = r.getPosition(partial).add(0, RamielEntity.CORE_BOX / 2.0, 0);
        Vec3 n = r.aimDir(partial).normalize();
        Vec3 u = Draw.perpendicular(n), v = n.cross(u);
        Vec3 plateC = centre.add(n.scale(OFFSET));
        float strength = r.shieldRatio();
        float time = FxState.now + partial;

        FxState.Ripple shatter = null;
        for (FxState.Ripple rp : FxState.ripples) if (rp.shatter && rp.pos.distanceTo(centre) < RamielEntity.R * 2) shatter = rp;

        int range = (int) Math.ceil(PLATE / (CELL * 1.5)) + 1;
        for (int q = -range; q <= range; q++) {
            for (int s = -range; s <= range; s++) {
                double x = CELL * Math.sqrt(3.0) * (q + s * 0.5);
                double y = CELL * 1.5 * s;
                double dist = Math.sqrt(x * x + y * y);
                if (dist > PLATE) continue;
                Vec3 c = plateC.add(u.scale(x)).add(v.scale(y));

                float base = broken ? 0f : 0.10f + 0.10f * strength + 0.03f * (float) Math.sin(time * 0.15 + dist * 0.2);
                float[] col = {1f, 0.82f, 0.35f};
                float boost = 0f;
                float[] tint = null;

                for (FxState.Ripple rp : FxState.ripples) {
                    if (rp.shatter) continue;
                    double rr = rippleRadius(rp);
                    Vec3 hit = plateHit(centre, n, rp.dir);
                    double dd = c.distanceTo(hit);
                    double band = Math.abs(dd - rr);
                    if (band < 6.0) {
                        float k = (float) (1.0 - band / 6.0) * rp.strength * (1f - rp.age / 22f);
                        if (k > boost) {
                            boost = k;
                            tint = rainbow((float) (dd * 0.06 + rp.age * 0.05));
                        }
                    }
                }
                if (tint != null) { col = mix(col, tint, Math.min(1f, boost * 1.4f)); }
                float a = base + boost * 0.9f;

                Vec3 offset = Vec3.ZERO;
                if (shatter != null) {                          // flying apart
                    float sp = shatter.age / 30f;
                    offset = n.scale(sp * 25.0).add(u.scale(x).add(v.scale(y)).normalize().scale(sp * 40.0));
                    a = (1f - sp) * 0.9f;
                }
                if (a > 0.02f) hexagon(d, c.add(offset), u, v, n, CELL * 0.94, col, a);
            }
        }
    }

    private static void hexagon(Draw d, Vec3 c, Vec3 u, Vec3 v, Vec3 n, double radius, float[] col, float a) {
        Vec3[] p = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            double ang = Math.toRadians(60.0 * i - 30.0);
            p[i] = c.add(u.scale(Math.cos(ang) * radius)).add(v.scale(Math.sin(ang) * radius));
        }
        for (int i = 0; i < 6; i++) {
            Vec3 e0 = p[i], e1 = p[(i + 1) % 6];
            Vec3 side = n.cross(e1.subtract(e0)).normalize();
            d.ribbon(e0, e1, side, 0.16, col[0], col[1], col[2], a);
        }
    }

    /** Where a hit coming from direction {@code dir} lands on the plate (clamped near the edge). */
    private static Vec3 plateHit(Vec3 centre, Vec3 n, Vec3 dir) {
        double dn = Math.max(0.25, dir.dot(n));
        return centre.add(dir.scale(OFFSET / dn));
    }

    private static double rippleRadius(FxState.Ripple r) { return r.age * 3.2; }

    private static float[] rainbow(float h) {
        h = h - (float) Math.floor(h);
        float r = Mth.clamp(Math.abs(h * 6f - 3f) - 1f, 0f, 1f);
        float g = Mth.clamp(2f - Math.abs(h * 6f - 2f), 0f, 1f);
        float b = Mth.clamp(2f - Math.abs(h * 6f - 4f), 0f, 1f);
        return new float[]{0.4f + 0.6f * r, 0.4f + 0.6f * g, 0.4f + 0.6f * b};
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }
}
