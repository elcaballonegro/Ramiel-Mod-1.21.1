package dev.ramiel.world;

import dev.ramiel.ModRegistry;
import dev.ramiel.RamielConfig;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.net.FilmKind;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Calls Ramiel down. It looks for the nearest body of water around the player (only chunks that
 * are already loaded, so the search never generates terrain) and drops Ramiel above it. If there is
 * no water nearby it lands above the ground a little way in front of the player instead.
 */
public final class RamielSpawner {
    private RamielSpawner() {}

    /** @return true if Ramiel is now arriving */
    public static boolean summon(ServerPlayer player, boolean allowAnywhere) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) {
            player.sendSystemMessage(Component.translatable("ramiel.summon.dimension"));
            return false;
        }
        if (RamielTracker.any(level)) {
            player.sendSystemMessage(Component.translatable("ramiel.summon.already"));
            return false;
        }

        Vec3 site = allowAnywhere ? null : findWater(level, player);
        boolean water = site != null;
        if (site == null) {
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
            double x = player.getX() + flat.x * 110, z = player.getZ() + flat.z * 110;
            int top = level.hasChunk((int) x >> 4, (int) z >> 4)
                    ? level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) x, (int) z) : player.getBlockY();
            site = new Vec3(x, top, z);
        }

        RamielEntity ramiel = ModRegistry.RAMIEL.get().create(level);
        if (ramiel == null) return false;
        double centreY = site.y + RamielConfig.hover();
        ramiel.setPos(site.x, centreY - RamielEntity.CORE_BOX / 2.0, site.z);
        ramiel.setAim(player.getYRot() + 180f, 0f);
        ramiel.beginArrival();
        level.addFreshEntity(ramiel);

        ramiel.playFilm(FilmKind.INTRO);
        player.sendSystemMessage(Component.translatable(water ? "ramiel.summon.water" : "ramiel.summon.land"));
        return true;
    }

    /** Ring search outward from the player. A "body of water" is deep, and has water around it too. */
    @Nullable
    private static Vec3 findWater(ServerLevel level, ServerPlayer player) {
        BlockPos origin = player.blockPosition();
        for (int radius = 24; radius <= 224; radius += 16) {
            int samples = Math.max(16, radius / 4);
            for (int i = 0; i < samples; i++) {
                double a = (i + radius * 0.37) * (Math.PI * 2.0 / samples);
                int x = origin.getX() + (int) Math.round(Math.cos(a) * radius);
                int z = origin.getZ() + (int) Math.round(Math.sin(a) * radius);
                if (isDeepWater(level, x, z) && isDeepWater(level, x + 6, z) && isDeepWater(level, x - 6, z)
                        && isDeepWater(level, x, z + 6) && isDeepWater(level, x, z - 6)) {
                    return new Vec3(x + 0.5, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z + 0.5);
                }
            }
        }
        return null;
    }

    private static boolean isDeepWater(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return false;
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        return surface - floor >= 3 && level.getFluidState(new BlockPos(x, surface - 1, z)).is(FluidTags.WATER);
    }
}
