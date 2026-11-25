package dev.ramiel.client.fx;

import dev.ramiel.entity.BeamKind;
import dev.ramiel.entity.RamielEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Entry point for all custom world drawing. RamielClient forwards the render-stage and tick events
 * here, and this decides what is drawn when:
 *
 *   after the sky           : space scene (only when very high up)
 *   after particles         : horizon Ramiel, ground effects, shield, beams, glows (in that order,
 *                             so brighter things end up on top)
 */
public final class FxRender {
    private FxRender() {}

    private static final List<RamielEntity> LOADED = new ArrayList<>();

    /** Every Ramiel that is currently loaded on this client (refreshed once per tick). */
    public static List<RamielEntity> ramiels() { return LOADED; }

    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RenderLevelStageEvent.Stage stage = event.getStage();
        Camera camera = event.getCamera();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            SpaceScene.render(camera, partial);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            FarRender.render(camera, partial);
            GroundFx.render(camera, partial);
            ShieldFx.render(camera, partial);
            BeamFx.render(camera, partial);
            GlowFx.render(camera, partial);
        }
    }

    /** Once per client tick. */
    public static void tick() {
        FxState.tick();
        LOADED.clear();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof RamielEntity r) {
                LOADED.add(r);
                // remember where the sweeping beam has been, so it can draw its cone
                if (r.beamKind() == BeamKind.SUNBURST) {
                    ArrayDeque<float[]> h = FxState.sweep.computeIfAbsent(r.getId(), k -> new ArrayDeque<>());
                    h.addFirst(new float[]{r.aimYaw(), r.aimPitch(), r.beamLen()});
                    while (h.size() > 16) h.removeLast();
                } else {
                    FxState.sweep.remove(r.getId());
                }
            }
        }
    }

    public static void clear() {
        FxState.clear();
        FarRender.clear();
        LOADED.clear();
    }
}
