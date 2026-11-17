package dev.ramiel.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Dropped by a defeated Ramiel. Makes cheaper plasma cells than a nether star does. */
public class AngelCoreItem extends Item {
    public AngelCoreItem(Properties properties) { super(properties); }

    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ramiel.angel_core.tip"));
    }
}
