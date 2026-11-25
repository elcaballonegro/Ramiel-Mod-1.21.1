package dev.ramiel.client.fx;

import dev.ramiel.client.render.RamielRenderTypes;
import dev.ramiel.entity.BeamKind;
import dev.ramiel.entity.RamielEntity;
import java.util.ArrayDeque;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every beam: Ramiel's five kinds (read straight from the entity's synced beam state) and the
 * player's railcannon (from server events). Each beam is several nested glowing lines, white in
 * the middle and coloured toward the outside, which is what gives that hot-core look.
 */
final class BeamFx {
    private BeamFx() {}

    static void render(Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Draw d = new Draw(RamielRenderTypes.BEAM, camera);
        float time = FxState.now + partial;
        for (RamielEntity r : FxRender.ramiels()) ramielBeam(d, r, partial, time);
        for (FxState.Rail rail : FxState.rails) railBeam(d, rail, partial, time);
        d.flush();
    }

    // ------------------------------------------------------------------------------------------
    private static void ramielBeam(Draw d, RamielEntity r, float partial, float time) {
        int kind = r.beamKind();
        if (kind == BeamKind.NONE) return;
        Vec3 origin = r.getPosition(partial).add(0, RamielEntity.CORE_BOX / 2.0 + r.arrivalOffsetY(partial), 0);
        Vec3 dir = r.aimDir(partial);
        double len = r.beamLen();
        if (len < 1) return;
        Vec3 end = origin.add(dir.scale(len));
        float t = r.beamT();
        float env = smooth(t / 0.06f) * (1f - smooth((t - 0.82f) / 0.18f));

        switch (kind) {
            case BeamKind.TELEGRAPH -> {
                // a faint, flickering red line: "this is where it is about to go"
                float flick = 0.55f + 0.45f * (float) Math.sin(time * 1.3f);
                d.beam(origin, end, 0.18, 1f, 0.15f, 0.1f, 0.22f * flick);
                d.beam(origin, end, 0.5, 1f, 0.2f, 0.1f, 0.06f * flick);
            }
            case BeamKind.LANCE -> {
                glow(d, origin, end, env, new double[]{0.5, 1.0, 1.9},
                        new float[][]{{1f, 1f, 1f}, {1f, 0.55f, 0.85f}, {0.9f, 0.25f, 0.9f}}, new float[]{0.9f, 0.5f, 0.2f});
                end(d, end, 5.0 * env, 1f, 0.7f, 0.9f);
            }
            case BeamKind.CROSS -> crossBeam(d, origin, dir, len, env, time);
            case BeamKind.SUNBURST -> sunburst(d, r, origin, dir, len, env, time);
            case BeamKind.SENTINEL -> {
                glow(d, origin, end, env, new double[]{1.2, 2.6, 4.4},
                        new float[][]{{1f, 1f, 1f}, {0.7f, 0.95f, 1f}, {0.3f, 0.7f, 1f}}, new float[]{0.95f, 0.55f, 0.25f});
                end(d, end, 9.0 * env, 0.8f, 0.95f, 1f);
            }
            case BeamKind.STAR -> starBeam(d, origin, dir, len, env, time);
            default -> { }
        }
        if (kind != BeamKind.TELEGRAPH) d.halo(origin, 8.0 + 10.0 * env, 1f, 0.85f, 0.9f, 0.8f * env);
    }

    /** Nested beams: widths[i] with colours[i] and alphas[i]. */
    private static void glow(Draw d, Vec3 a, Vec3 b, float env, double[] widths, float[][] colours, float[] alphas) {
        for (int i = widths.length - 1; i >= 0; i--) {
            d.beam(a, b, widths[i], colours[i][0], colours[i][1], colours[i][2], alphas[i] * env);
        }
    }

    private static void end(Draw d, Vec3 at, double size, float r, float g, float b) {
        if (size > 0.2) d.halo(at, size, r, g, b, 0.9f);
    }

    /** Thick gold beam that blooms into a cross: after 62% of its length four blades spread apart. */
    private static void crossBeam(Draw d, Vec3 origin, Vec3 dir, double len, float env, float time) {
        double split = len * 0.62;
        Vec3 p = origin.add(dir.scale(split));
        Vec3 end = origin.add(dir.scale(len));
        glow(d, origin, p, env, new double[]{1.4, 2.8, 5.0},
                new float[][]{{1f, 1f, 0.9f}, {1f, 0.85f, 0.4f}, {1f, 0.5f, 0.15f}}, new float[]{0.95f, 0.6f, 0.3f});
        Vec3 u = Draw.perpendicular(dir), v = dir.cross(u);
        double spread = len * 0.16;
        double twist = time * 0.04;                                   // the cross turns slowly
        Vec3 uu = u.scale(Math.cos(twist)).add(v.scale(Math.sin(twist)));
        Vec3 vv = v.scale(Math.cos(twist)).subtract(u.scale(Math.sin(twist)));
        Vec3[] arms = {uu, uu.scale(-1), vv, vv.scale(-1)};
        for (Vec3 arm : arms) {
            Vec3 tip = end.add(arm.scale(spread));
            Vec3 side = dir.cross(arm).normalize();
            d.ribbonFade(p, tip, side, 1.6, 1f, 0.95f, 0.7f, 0.9f * env, 0.5f * env);
            d.ribbonFade(p, tip, side, 3.4, 1f, 0.55f, 0.2f, 0.35f * env, 0.15f * env);
            end(d, tip, 8.0 * env, 1f, 0.8f, 0.4f);
        }
        end(d, end, 11.0 * env, 1f, 0.9f, 0.6f);
    }

    /** Thin magenta line that sweeps all the way round, leaving a fading cone behind it. */
    private static void sunburst(Draw d, RamielEntity r, Vec3 origin, Vec3 dir, double len, float env, float time) {
        Vec3 end = origin.add(dir.scale(len));
        d.beam(origin, end, 0.25, 1f, 0.9f, 1f, 0.95f * env);
        d.beam(origin, end, 0.7, 1f, 0.3f, 0.9f, 0.45f * env);
        d.beam(origin, end, 1.5, 0.8f, 0.2f, 1f, 0.15f * env);
        end(d, end, 4.0 * env, 1f, 0.5f, 0.95f);

        // the cone: a fan between the last positions of the beam tip
        ArrayDeque<float[]> hist = FxState.sweep.get(r.getId());
        if (hist == null || hist.size() < 2) return;
        Vec3 prev = null;
        int i = 0, n = hist.size();
        for (float[] h : hist) {                                      // newest first
            Vec3 tip = origin.add(Vec3.directionFromRotation(h[1], h[0]).scale(h[2]));
            if (prev != null) {
                float a = 0.30f * env * (1f - (float) i / n);
                d.vertex(origin.x, origin.y, origin.z, 1f, 0.4f, 0.9f, a);
                d.vertex(prev.x, prev.y, prev.z, 1f, 0.4f, 0.9f, 0f);
                d.vertex(tip.x, tip.y, tip.z, 1f, 0.4f, 0.9f, 0f);
                d.vertex(origin.x, origin.y, origin.z, 1f, 0.4f, 0.9f, a);
            }
            prev = tip;
            i++;
        }
    }

    /** The mountain-breaker: huge, white-hot, with pink pressure rings racing down it. */
    private static void starBeam(Draw d, Vec3 origin, Vec3 dir, double len, float env, float time) {
        Vec3 end = origin.add(dir.scale(len));
        glow(d, origin, end, env, new double[]{3.0, 6.0, 10.0, 16.0},
                new float[][]{{1f, 1f, 1f}, {1f, 0.8f, 0.95f}, {1f, 0.4f, 0.85f}, {0.6f, 0.25f, 1f}}, new float[]{1f, 0.7f, 0.4f, 0.18f});
        int rings = 10;
        for (int k = 0; k < rings; k++) {
            double s = ((time * 3.6 + k * len / rings) % len);
            Vec3 c = origin.add(dir.scale(s));
            float fade = (float) (1.0 - s / len * 0.6) * env;
            d.ring(c, dir, 9.0, 12.5, 28, 1f, 0.55f, 0.9f, 0.5f * fade, 0.0f);
            d.ring(c, dir, 6.0, 9.0, 28, 1f, 0.9f, 1f, 0.0f, 0.5f * fade);
        }
        end(d, end, 40.0 * env, 1f, 0.8f, 0.7f);
    }

    // ------------------------------------------------------------------------------------------
    // Railcannon
    // ------------------------------------------------------------------------------------------
    private static void railBeam(Draw d, FxState.Rail r, float partial, float time) {
        float p = (r.age + partial) / r.life;
        float env = (1f - smooth((p - 0.35f) / 0.65f)) * smooth(p / 0.05f);
        double w = r.width;
        d.beam(r.from, r.to, 0.22 * w, 1f, 1f, 1f, 0.95f * env);
        d.beam(r.from, r.to, 0.6 * w, 0.5f, 1f, 0.9f, 0.55f * env);
        d.beam(r.from, r.to, 1.3 * w, 0.2f, 0.85f, 0.75f, 0.25f * env);
        Vec3 dir = r.to.subtract(r.from);
        double len = dir.length();
        if (len < 2) return;
        dir = dir.scale(1.0 / len);
        // a stream of small rings along the beam
        for (int k = 0; k < 8; k++) {
            double s = ((time * 7.0 + k * len / 8.0) % len);
            Vec3 c = r.from.add(dir.scale(s));
            d.ring(c, dir, 1.2 * w, 2.0 * w, 16, 0.6f, 1f, 0.9f, 0f, 0.4f * env);
        }
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}
