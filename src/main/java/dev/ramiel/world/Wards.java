package dev.ramiel.world;

import cyou.rimuru.rimulib.mc1211.api.WardApi;
import cyou.rimuru.rimulib.mc1211.bulwark.Threat;
import dev.ramiel.RamielMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Tells other mods' protective wards (Rimulib "bulwarks") that something dangerous is coming,
 * so they can react before the hit. It is purely a courtesy hook; Ramiel works without wards.
 */
public final class Wards {
    private Wards() {}

    public static void warnBlast(ServerLevel level, Vec3 at, double radius, int leadTicks) {
        WardApi.warn(level, RamielMod.MODID, Threat.Kind.BLAST, at, radius, 0xFFFF4A2E, leadTicks, 40);
    }

    public static void warnBeam(ServerLevel level, Vec3 at, double radius, int leadTicks) {
        WardApi.warn(level, RamielMod.MODID, Threat.Kind.BOLT, at, radius, 0xFFFFD27A, leadTicks, 30);
    }
}
