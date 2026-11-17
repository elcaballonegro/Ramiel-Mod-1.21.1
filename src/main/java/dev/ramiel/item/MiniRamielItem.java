package dev.ramiel.item;

import dev.ramiel.Setting;
import dev.ramiel.net.Payloads;
import dev.ramiel.world.RamielSpawner;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The miniature Ramiel (drawn as a spinning crystal, see client/render/MiniRamielRenderer).
 *   Right click        : summon Ramiel above the nearest water (the item is used up)
 *   Shift + right click: open the settings screen
 */
public class MiniRamielItem extends Item {
    public MiniRamielItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (player.isShiftKeyDown()) {
                Payloads.toPlayer(sp, new Payloads.SettingsOpen(Setting.snapshot(), Payloads.mayEdit(sp)));
            } else if (RamielSpawner.summon(sp, false) && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ramiel.mini_ramiel.tip1"));
        tooltip.add(Component.translatable("item.ramiel.mini_ramiel.tip2"));
    }
}
