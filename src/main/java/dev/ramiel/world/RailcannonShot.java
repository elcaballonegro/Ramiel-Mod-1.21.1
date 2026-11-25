package dev.ramiel.world;

import cyou.rimuru.rimulib.mc1211.api.CarveApi;
import dev.ramiel.ModRegistry;
import dev.ramiel.RamielConfig;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.entity.RamielHitTest;
import dev.ramiel.net.FxKind;
import dev.ramiel.net.Payloads;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The railcannon beam: an instant ray that pierces everything.
 *  - living things on the line take heavy damage that ignores armour
 *  - Ramiel takes a percentage of its health: 20% for the body, 50% for the exposed core
 *    (so two core shots kill it). The shield does not stop it.
 *  - terrain above the beam is sliced away in a narrow trench
 */
public final class RailcannonShot {
    private RailcannonShot() {}

    public static final double RANGE = 520.0;
    public static final float DAMAGE = 300f;

    public static void fire(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = player.getLookAngle();
        Vec3 start = player.getEyePosition().add(dir.scale(1.5));
        BeamCaster.Result march = BeamCaster.march(level, start, dir, RANGE, true);
        Vec3 end = march.end();

        // living things
        DamageSource source = Damage.of(level, ModRegistry.DMG_RAILCANNON, player);
        AABB box = new AABB(start, end).inflate(4.0);
        Vec3 axis = end.subtract(start);
        double lenSqr = Math.max(1e-6, axis.lengthSqr());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x != player && !(x instanceof RamielEntity) && x.isAlive())) {
            Vec3 c = e.getBoundingBox().getCenter();
            double t = Math.max(0, Math.min(1, c.subtract(start).dot(axis) / lenSqr));
            if (c.distanceTo(start.add(axis.scale(t))) <= 2.2 + Math.max(e.getBbWidth(), e.getBbHeight()) * 0.5) {
                e.hurt(source, DAMAGE);
            }
        }
        // Ramiel: exact hit test, so it only counts if the line really crosses the crystal
        for (RamielEntity ramiel : RamielTracker.all()) {
            if (ramiel.level() != level) continue;
            RamielHitTest.Hit hit = RamielHitTest.trace(ramiel, start, end);
            if (hit.type() != RamielHitTest.Type.MISS) {
                ramiel.railcannonHit(player, hit.type() == RamielHitTest.Type.CORE);
                Payloads.fx(level, hit.point(), 400, FxKind.IMPACT_FRAME, 4, 0, 0, 0f, 0f);
                Payloads.fx(level, hit.point(), 400, FxKind.SHAKE, 2.0, 40, 0, 0f, 0f);
            }
        }

        sliceTerrain(level, start, dir, march.length());

        Payloads.fx(level, start, 1200, FxKind.RAIL_BEAM, end.x, end.y, end.z, 1.0f, 16f);
        Payloads.fx(level, start, 200, FxKind.IMPACT_FRAME, 2, 0, 0, 0f, 0f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModRegistry.SND_RAIL.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 60f, 1f);
        Sfx.laser(level, start, 1.2f);                                                // layered sonic crack under the shot
        player.setDeltaMovement(player.getDeltaMovement().add(dir.scale(-0.7)));       // recoil
        player.hurtMarked = true;
    }

    /** Cut everything above the beam along its path, like the beam slicing through a hillside. */
    private static void sliceTerrain(ServerLevel level, Vec3 start, Vec3 dir, double length) {
        if (RamielConfig.destruction() <= 0.001) return;
        double hLen = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (hLen < 0.2) return;                                         // nearly vertical: skip
        final double hx = dir.x / hLen, hz = dir.z / hLen;
        final double slope = dir.y / hLen;
        final double halfWidth = 2.6;
        final double reach = Math.min(150.0, length * hLen);
        final double ox = start.x, oy = start.y, oz = start.z;

        CarveApi.Floor floor = (x, z) -> {
            double dx = x + 0.5 - ox, dz = z + 0.5 - oz;
            double along = dx * hx + dz * hz;
            double side = Math.abs(-dx * hz + dz * hx);
            if (along < 0 || along > reach || side > halfWidth) return CarveApi.UNTOUCHED;
            return (int) Math.floor(oy + slope * along - 1.4);
        };
        CarveApi.Surface glass = (x, y, z, was) -> Blocks.BLACKSTONE.defaultBlockState();
        CarveApi.line(level, new Vec3(ox, oy, oz), new Vec3(hx, 0, hz), reach, halfWidth, floor, glass)
                .named("Railcannon").keepBlocks().perTick(4).lazy().start();
    }
}
