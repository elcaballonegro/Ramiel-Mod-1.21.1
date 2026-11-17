package dev.ramiel;

import dev.ramiel.entity.RamielEntity;
import dev.ramiel.item.AngelCoreItem;
import dev.ramiel.item.MiniRamielItem;
import dev.ramiel.item.PlasmaCellItem;
import dev.ramiel.item.RailcannonItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Everything the mod adds to the game's registries. */
public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, RamielMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RamielMod.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, RamielMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RamielMod.MODID);

    /**
     * The entity box is only the CORE (5.2 blocks). The big body is made of RamielPart sub-entities,
     * exactly like the Ender Dragon. A huge box would make the game check ~110,000 blocks per tick.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<RamielEntity>> RAMIEL = ENTITIES.register("ramiel",
            () -> EntityType.Builder.<RamielEntity>of(RamielEntity::new, MobCategory.MISC)
                    .sized(RamielEntity.CORE_BOX, RamielEntity.CORE_BOX)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .build("ramiel"));

    public static final DeferredItem<MiniRamielItem> MINI_RAMIEL = ITEMS.register("mini_ramiel",
            () -> new MiniRamielItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<RailcannonItem> RAILCANNON = ITEMS.register("railcannon",
            () -> new RailcannonItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<PlasmaCellItem> PLASMA_CELL = ITEMS.register("plasma_cell",
            () -> new PlasmaCellItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<AngelCoreItem> ANGEL_CORE = ITEMS.register("angel_core",
            () -> new AngelCoreItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    // Custom sounds (files in assets/ramiel/sounds, listed in sounds.json)
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_ALARM = sound("alarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_CHARGE = sound("charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_BEAM = sound("beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_BOOM = sound("boom");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_MORPH = sound("morph");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RAIL = sound("rail");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_SHIELD = sound("shield");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ramiel"))
                    .icon(() -> new ItemStack(MINI_RAMIEL.get()))
                    .displayItems((params, out) -> {
                        out.accept(MINI_RAMIEL.get());
                        out.accept(RAILCANNON.get());
                        out.accept(PLASMA_CELL.get());
                        out.accept(ANGEL_CORE.get());
                    }).build());

    /** The railcannon's damage type (defined in data/ramiel/damage_type/railcannon.json). */
    public static final ResourceKey<DamageType> DMG_RAILCANNON = ResourceKey.create(Registries.DAMAGE_TYPE, RamielMod.id("railcannon"));
    /** Ramiel's own beams and blasts (data/ramiel/damage_type/angel_beam.json). */
    public static final ResourceKey<DamageType> DMG_BEAM = ResourceKey.create(Registries.DAMAGE_TYPE, RamielMod.id("angel_beam"));

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(RamielMod.id(name)));
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        TABS.register(modBus);
    }
}
