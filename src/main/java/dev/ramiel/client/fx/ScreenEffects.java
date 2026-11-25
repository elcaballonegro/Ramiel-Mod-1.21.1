package dev.ramiel.client.fx;

import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import dev.ramiel.RamielMod;
import dev.ramiel.entity.BeamKind;
import dev.ramiel.entity.RamielEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Full-screen effects through Rimulib's FxApi: camera shake, anime impact frames (a custom shader),
 * a red pulse while standing in a blast zone, and a little chromatic fringe while Ramiel charges
 * or fires nearby.
 *
 * FxApi calls our layer every frame and clears the effect values afterwards, so this only has
 * to say what should be on RIGHT NOW.
 */
public final class ScreenEffects {
    private ScreenEffects() {}

    /** Call once from the client init. */
    public static void init() {
        var stage = FxApi.stage(RamielMod.MODID);
        stage.add(frame -> {
            var s = frame.screen();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            float time = FxState.now + frame.partial();

            // camera shake from explosions and beams
            if (FxState.shake() > 0f) s.shake = Math.max(s.shake, FxState.shake());

            // impact frame: flicker between a high-contrast "negative" and "positive" for a few ticks
            if (FxState.impactActive()) {
                final float strength = FxState.impactStrength();
                final int phase = FxState.impactPhase();
                frame.pass("ramiel_impact", u -> {
                    u.f1("Strength", strength);
                    u.f1("Mode", phase);
                });
                s.flash = Math.max(s.flash, 0.25f * strength * phase);
            }

            // standing inside a blast zone: red pulse at the edges of the screen
            Vec3 p = mc.player.position();
            for (FxState.Zone z : FxState.zones.values()) {
                long left = z.end - FxState.now;
                if (left <= 0) continue;
                if (Math.hypot(p.x - z.centre.x, p.z - z.centre.z) < z.radius) {
                    float pulse = 0.5f + 0.5f * (float) Math.sin(time * (left < 200 ? 0.8 : 0.3));
                    s.vignette = Math.max(s.vignette, 0.35f + 0.3f * pulse);
                    s.vignetteR = 1f; s.vignetteG = 0.08f; s.vignetteB = 0.04f;
                    s.aberration = Math.max(s.aberration, 0.08f + 0.12f * pulse);
                }
            }

            // Ramiel charging or firing close by
            for (RamielEntity r : FxRender.ramiels()) {
                double d = r.position().distanceTo(p);
                if (d > 400) continue;
                float near = (float) (1.0 - d / 400.0);
                float charge = r.charge(frame.partial());
                if (charge > 0.05f) s.aberration = Math.max(s.aberration, 0.18f * charge * near);
                int beam = r.beamKind();
                if (beam != BeamKind.NONE && beam != BeamKind.TELEGRAPH) {
                    s.bloom = Math.max(s.bloom, 0.35f * near);
                    s.zoomBlur = Math.max(s.zoomBlur, 0.08f * near);
                }
            }
        });
    }
}
