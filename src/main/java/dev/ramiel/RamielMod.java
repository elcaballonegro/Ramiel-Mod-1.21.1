package dev.ramiel;

import com.mojang.logging.LogUtils;
import cyou.rimuru.rimulib.mc1211.api.RimulibApi;
import dev.ramiel.client.RamielClient;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.net.Payloads;
import dev.ramiel.world.ServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

/**
 * Mod entry point. Read this first: it shows what talks to what.
 *
 *   ModRegistry ........ entity, items, sounds
 *   RamielConfig ....... every tunable number (also edited by the in-game settings screen)
 *   Payloads ........... the network messages (server -> client effects, cutscenes, settings)
 *   ServerEvents ....... game-wide hooks (projectile hits, mob AI, far-render beacons, commands)
 *   RamielClient ....... everything that only exists on the client (rendering, GUI, cutscenes)
 */
@Mod(RamielMod.MODID)
public final class RamielMod {
    public static final String MODID = "ramiel";
    public static final Logger LOG = LogUtils.getLogger();

    /** Rimulib asks mods to keep the handle it returns in a static field. */
    public static final Object RIMULIB = RimulibApi.connect(MODID, RamielMod.class);

    public RamielMod(IEventBus modBus, ModContainer container, Dist dist) {
        container.registerConfig(ModConfig.Type.COMMON, RamielConfig.SPEC);
        ModRegistry.register(modBus);
        modBus.addListener(Payloads::register);
        modBus.addListener(RamielMod::attributes);
        ServerEvents.register();
        if (dist.isClient()) {
            RamielClient.init(modBus);
        }
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(ModRegistry.RAMIEL.get(), RamielEntity.attributes().build());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
