package dev.ramiel.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ramiel.entity.ToppleEntity;
import dev.ramiel.world.ShockMath;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Quaternionf;

/**
 * Draws a falling tree: all its blocks, turned together about the centre of the base block.
 *
 * The entity stands at the bottom centre of the base block, so the pivot is (0, 0.5, 0). A block at offset (ox, oy, oz)
 * has its centre at (ox, oy, oz) from the pivot, which puts its corner (where a block model starts) at
 * (ox - 0.5, oy - 0.5, oz - 0.5). The rotation is +90 degrees about (dz, 0, -dx), which turns "up" into the fall
 * direction: the same rotation {@code ShockMath.rotateOffset} uses to place the blocks when the tree lands.
 *
 * Leaves are drawn with the default foliage colour (the biome tint needs a position in the world).
 */
public class ToppleRenderer extends EntityRenderer<ToppleEntity> {
    private final BlockRenderDispatcher blocks;

    public ToppleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override public ResourceLocation getTextureLocation(ToppleEntity entity) { return InventoryMenu.BLOCK_ATLAS; }

    @Override public boolean shouldRender(ToppleEntity entity, Frustum frustum, double x, double y, double z) { return true; }

    @Override
    public void render(ToppleEntity entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!entity.ready()) return;
        float p = Mth.clamp((entity.tickCount + partialTick) / entity.duration(), 0f, 1f);
        float angle = (float) (Math.PI / 2.0 * ShockMath.fallProgress(p));
        pose.pushPose();
        pose.translate(0.0, 0.5, 0.0);
        pose.mulPose(new Quaternionf().rotationAxis(angle, entity.dirZ(), 0f, -entity.dirX()));
        for (int i = 0; i < entity.count(); i++) {
            pose.pushPose();
            pose.translate(entity.ox(i) - 0.5, entity.oy(i) - 0.5, entity.oz(i) - 0.5);
            blocks.renderSingleBlock(entity.state(i), pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, light);
    }
}
