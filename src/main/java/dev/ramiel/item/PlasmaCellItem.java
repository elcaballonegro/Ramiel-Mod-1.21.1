package dev.ramiel.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Ammunition for the railcannon. One cell per shot. */
public class PlasmaCellItem extends Item {
    public PlasmaCellItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ramiel.plasma_cell.tip"));
    }
}
