package dev.ramiel.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ramiel.Form;
import dev.ramiel.client.model.Brick;
import dev.ramiel.client.model.FormPoses;
import dev.ramiel.client.model.RamielMesh;
import dev.ramiel.client.model.RamielModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the Mini Ramiel item (inventory, hand, dropped, item frame) as a small spinning crystal
 * with a pulsing red core you can see through the glass. It uses exactly the same model code as the
 * real boss, so the item always looks like the Ramiel the mod draws.
 *
 * The item's JSON model only says "parent: builtin/entity"; this class does all the drawing.
 */
public class MiniRamielRenderer extends BlockEntityWithoutLevelRenderer {
    public MiniRamielRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay) {
        // our own clock, so the item animates the same everywhere without needing a level
        float time = (float) ((System.nanoTime() / 1_000_000L) % 3_600_000L) / 50f;

        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 0.18f);
        Brick[] bricks = RamielModel.shared().compute(Form.DORMANT, Form.DORMANT, 1f, time, 0f, 0f, 0.35f + 0.5f * pulse);
        // make the two shells glassy so the core shows through
        bricks[FormPoses.HALF_TOP].alpha = 0.55f;
        bricks[FormPoses.HALF_BOT].alpha = 0.55f;

        float bob = 0.03f * (float) Math.sin(time * 0.12f);
        Quaternionf spin = new Quaternionf().rotationY(time * 0.05f).rotateX(0.12f);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5 + bob, 0.5);
        RamielMesh.emit(buffers.getBuffer(RamielRenderTypes.crystal()), buffers.getBuffer(RamielRenderTypes.crystalGlow()),
                poseStack.last(), bricks, spin, 0.40f, new Vector3f(0.2f, 0.4f, 3f), 1f);
        poseStack.popPose();
    }
}
