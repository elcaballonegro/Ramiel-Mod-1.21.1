package dev.ramiel.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Layered sound effects built from vanilla sounds, pitched and slowed. Each effect is several
 * sounds played at once, so they sound bigger and stranger than any single vanilla sound.
 *
 * Volume above 1.0 only makes the sound carry farther (vanilla: ~16 blocks per 1.0).
 * To change how something sounds, edit the layers here; nothing else needs to change.
 */
public final class Sfx {
    private Sfx() {}

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** Ramiel snapping shut into the dormant octahedron: a heavy metallic slam. */
    public static void slam(ServerLevel level, Vec3 at) {
        play(level, at, SoundEvents.ANVIL_LAND, 9f, 0.45f);
        play(level, at, SoundEvents.IRON_GOLEM_DAMAGE, 8f, 0.40f);
        play(level, at, SoundEvents.NETHERITE_BLOCK_PLACE, 8f, 0.50f);
        play(level, at, SoundEvents.GENERIC_EXPLODE.value(), 3f, 1.9f);
    }

    /** A projectile bouncing off the shield: a ringing clang. */
    public static void deflect(ServerLevel level, Vec3 at) {
        play(level, at, SoundEvents.SHIELD_BLOCK, 5f, 0.55f);
        play(level, at, SoundEvents.ANVIL_PLACE, 2.5f, 1.7f);
    }

    /** The moment a beam starts: a low sonic crack over a rising tone. */
    public static void laser(ServerLevel level, Vec3 at, float power) {
        play(level, at, SoundEvents.WARDEN_SONIC_BOOM, 14f * power, 0.55f);
        play(level, at, SoundEvents.BEACON_POWER_SELECT, 8f * power, 0.5f);
        play(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 6f * power, 1.5f);
    }

    /** Beam still running: a slowed-down electric hum. */
    public static void laserHum(ServerLevel level, Vec3 at, float power) {
        play(level, at, SoundEvents.BEACON_AMBIENT, 10f * power, 0.5f);
    }

    /** Big explosion: layered thunder and blasts, slowed down. */
    public static void boom(ServerLevel level, Vec3 at, float power) {
        play(level, at, SoundEvents.GENERIC_EXPLODE.value(), 40f * power, 0.45f);
        play(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 40f * power, 0.5f);
        play(level, at, SoundEvents.ANVIL_LAND, 20f * power, 0.35f);
    }
}
