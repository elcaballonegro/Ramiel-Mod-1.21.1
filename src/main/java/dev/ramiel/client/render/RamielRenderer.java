package dev.ramiel.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ramiel.Form;
import dev.ramiel.client.model.Brick;
import dev.ramiel.client.model.RamielMesh;
import dev.ramiel.client.model.RamielModel;
import dev.ramiel.entity.Orientation;
import dev.ramiel.entity.RamielEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the body of Ramiel. Beams, shockwaves, the shield hexagons and so on are drawn by
 * client/fx/FxManager, so this class only turns the entity's state into bricks and bricks into quads.
 */
public class RamielRenderer extends EntityRenderer<RamielEntity> {
    public RamielRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public ResourceLocation getTextureLocation(RamielEntity entity) { return RamielRenderTypes.CRYSTAL_TEX; }

    /** It is enormous and sometimes far off screen; never let vanilla culling hide it. */
    @Override
    public boolean shouldRender(RamielEntity entity, Frustum frustum, double x, double y, double z) { return true; }

    @Override
    public void render(RamielEntity e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        float time = e.tickCount + partial;
        float dropY = (float) e.arrivalOffsetY(partial);

        Brick[] bricks = RamielModel.shared().computeFor(e.getId(), e.formFrom(), e.form(), e.morph(partial), time,
                e.charge(partial), e.drillLen() / (float) RamielEntity.R, e.coreAnim(partial));
        Quaternionf orient = Orientation.of(e, partial);

        // death: the whole thing fades out over the last second
        float fade = 1f;
        int death = e.deathTicks();
        if (death > RamielEntity.DEATH_TICKS - 20) fade = Mth.clamp((RamielEntity.DEATH_TICKS - death) / 20f, 0f, 1f);

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 pos = e.getPosition(partial);
        Vector3f camLocal = new Vector3f((float) (cam.x - pos.x), (float) (cam.y - (pos.y + RamielEntity.CORE_BOX / 2.0 + dropY)), (float) (cam.z - pos.z));

        stack.pushPose();
        stack.translate(0.0, RamielEntity.CORE_BOX / 2.0 + dropY, 0.0);
        RamielMesh.emit(buffers.getBuffer(RamielRenderTypes.crystal()), buffers.getBuffer(RamielRenderTypes.crystalGlow()),
                stack.last(), bricks, orient, (float) RamielEntity.R, camLocal, fade);
        stack.popPose();
    }

    @Override public boolean shouldShowName(RamielEntity entity) { return false; }
}
