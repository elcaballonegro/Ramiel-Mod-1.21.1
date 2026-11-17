package dev.ramiel.world;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** Makes DamageSource objects for our own damage types (see data/ramiel/damage_type). */
public final class Damage {
    private Damage() {}

    public static DamageSource of(ServerLevel level, ResourceKey<DamageType> key, @Nullable Entity cause) {
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        return new DamageSource(type, cause);
    }
}
