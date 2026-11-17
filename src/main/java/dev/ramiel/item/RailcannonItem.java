package dev.ramiel.item;

import dev.ramiel.ModRegistry;
import dev.ramiel.world.RailcannonShot;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Hand-held positron railcannon. Hold right click to charge (a scope appears and the view zooms),
 * release once it reads CHARGED to fire one piercing beam. Each shot uses one plasma cell.
 * Releasing early cancels the shot and keeps the cell.
 */
public class RailcannonItem extends Item {
    public static final int FULL_CHARGE = 40;      // ticks

    public RailcannonItem(Properties properties) { super(properties); }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!hasAmmo(player)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("ramiel.rail.no_ammo"), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModRegistry.SND_CHARGE.get(), SoundSource.PLAYERS, 3f, 1.6f);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int charged = getUseDuration(stack, entity) - remaining;
        if (!level.isClientSide && charged == FULL_CHARGE && entity instanceof ServerPlayer p) {
            p.displayClientMessage(Component.translatable("ramiel.rail.charged"), true);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2f, 1.8f);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        int charged = getUseDuration(stack, entity) - timeLeft;
        if (charged < FULL_CHARGE) {
            player.displayClientMessage(Component.translatable("ramiel.rail.unstable"), true);
            return;
        }
        if (!consumeAmmo(player)) return;
        RailcannonShot.fire(player);
        player.getCooldowns().addCooldown(this, 100);
    }

    // ---- ammo ---------------------------------------------------------------------------------
    private static boolean hasAmmo(Player player) {
        if (player.getAbilities().instabuild) return true;
        for (ItemStack s : player.getInventory().items) if (s.is(ModRegistry.PLASMA_CELL.get())) return true;
        return player.getOffhandItem().is(ModRegistry.PLASMA_CELL.get());
    }

    private static boolean consumeAmmo(Player player) {
        if (player.getAbilities().instabuild) return true;
        if (player.getOffhandItem().is(ModRegistry.PLASMA_CELL.get())) { player.getOffhandItem().shrink(1); return true; }
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModRegistry.PLASMA_CELL.get())) { s.shrink(1); return true; }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ramiel.railcannon.tip1"));
        tooltip.add(Component.translatable("item.ramiel.railcannon.tip2"));
    }
}
