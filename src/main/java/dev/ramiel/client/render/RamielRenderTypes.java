package dev.ramiel.client.render;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.ramiel.RamielMod;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import net.minecraft.client.renderer.RenderStateShard.TexturingStateShard;
import net.minecraft.client.renderer.RenderStateShard.TransparencyStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;

/**
 * Our custom render types. We extend RenderType only to reach its protected "state shard"
 * constants (the same trick other mods use); this class is never instantiated.
 *
 *   BEAM           additive glow, plain colour vertices, ignores fog (beams, rings, halos)
 *   BEAM_ALWAYS    same, but drawn over everything (screen-space flashes, space scene)
 *   DARKEN         normal alpha blending, ignores depth (dimming the sky for the space scene)
 *   FAR_CRYSTAL    the crystal for the horizon version: no fog, so distance does not tint it
 */
public final class RamielRenderTypes extends RenderType {
    public static final ResourceLocation CRYSTAL_TEX = RamielMod.id("textures/entity/crystal.png");
    public static final ResourceLocation EARTH_TEX = RamielMod.id("textures/fx/earth.png");

    private static float savedFogStart;

    /** src_alpha, one: colour is added, alpha says how strongly. */
    private static final TransparencyStateShard GLOW_BLEND = new TransparencyStateShard("ramiel_glow_blend", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    private static final TexturingStateShard NO_FOG = new TexturingStateShard("ramiel_no_fog", () -> {
        savedFogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
    }, () -> RenderSystem.setShaderFogStart(savedFogStart));

    public static final RenderType BEAM = create("ramiel_beam", DefaultVertexFormat.POSITION_COLOR, Mode.QUADS, 262144, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(GLOW_BLEND)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setTexturingState(NO_FOG)
                    .createCompositeState(false));

    public static final RenderType BEAM_ALWAYS = create("ramiel_beam_always", DefaultVertexFormat.POSITION_COLOR, Mode.QUADS, 262144, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(GLOW_BLEND)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setTexturingState(NO_FOG)
                    .createCompositeState(false));

    public static final RenderType DARKEN = create("ramiel_darken", DefaultVertexFormat.POSITION_COLOR, Mode.QUADS, 4096, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setTexturingState(NO_FOG)
                    .createCompositeState(false));

    public static final RenderType FAR_CRYSTAL = create("ramiel_far_crystal", DefaultVertexFormat.NEW_ENTITY, Mode.QUADS, 65536, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(CRYSTAL_TEX, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setTexturingState(NO_FOG)
                    .createCompositeState(true));

    public static RenderType crystal() { return RenderType.entityTranslucent(CRYSTAL_TEX); }
    public static RenderType crystalGlow() { return RenderType.entityTranslucentEmissive(CRYSTAL_TEX); }

    private RamielRenderTypes() {
        super("", DefaultVertexFormat.POSITION, Mode.QUADS, 0, false, false, () -> {}, () -> {});
    }
}
