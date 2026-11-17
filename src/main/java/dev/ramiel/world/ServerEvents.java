package dev.ramiel.world;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import cyou.rimuru.rimulib.mc1211.api.CarveApi;
import dev.ramiel.Setting;
import dev.ramiel.ai.MobHooks;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.net.Payloads;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/** Game-wide hooks (the NeoForge "game bus"). Registered once from RamielMod. */
public final class ServerEvents {
    private ServerEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(HitResolver::onImpact);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onJoin);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onLeave);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onCommands);

        // Tell Rimulib which blocks Ramiel's cuts must never remove (bedrock, end portal frames...).
        // Each cut opts in with .keepBlocks().
        CarveApi.keepBlocks(state -> state.is(BlockTags.WITHER_IMMUNE));
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof RamielEntity r) RamielTracker.add(r);
        else if (event.getEntity() instanceof Mob mob) MobHooks.attach(mob);
    }

    private static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof RamielEntity r) RamielTracker.remove(r);
    }

    // ---- /ramiel ... (operators) ------------------------------------------------------------
    private static void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("ramiel").requires(src -> src.hasPermission(2))
                .then(Commands.literal("summon").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    RamielSpawner.summon(p, false);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("summon_here").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    RamielSpawner.summon(p, true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("kill").executes(ctx -> {
                    int n = 0;
                    for (RamielEntity r : RamielTracker.all()) { r.kill(); n++; }
                    final int count = n;
                    ctx.getSource().sendSuccess(() -> Component.literal("Killed " + count + " Ramiel"), true);
                    return count;
                }))
                .then(Commands.literal("phase2").executes(ctx -> {
                    for (RamielEntity r : RamielTracker.all()) r.setHealthRatio(0.66f);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("heal").executes(ctx -> {
                    for (RamielEntity r : RamielTracker.all()) r.setHealthRatio(1f);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("settings").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Payloads.toPlayer(p, new Payloads.SettingsOpen(Setting.snapshot(), Payloads.mayEdit(p)));
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
