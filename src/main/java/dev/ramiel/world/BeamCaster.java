package dev.ramiel.world;

import dev.ramiel.entity.RamielEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The "ray" part of every laser: walks along a line to find where it ends, and hurts whatever
 * stands inside it. It never loads chunks (a beam into unloaded land simply ends at the edge).
 */
public final class BeamCaster {
    private BeamCaster() {}

    /** Where a beam ended. {@code hitSolid} is false if it ran out of range or loaded land. */
    public record Result(Vec3 end, double length, boolean hitSolid) {}

    /**
     * Walk from {@code from} along {@code dir} (unit vector) for at most {@code maxLen} blocks.
     * Stops at the first solid block or water surface unless {@code pierce} is true.
     */
    public static Result march(ServerLevel level, Vec3 from, Vec3 dir, double maxLen, boolean pierce) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double step = 1.0;
        double bottom = level.getMinBuildHeight() + 2;
        for (double t = 0; t < maxLen; t += step) {
            double x = from.x + dir.x * t, y = from.y + dir.y * t, z = from.z + dir.z * t;
            if (y < bottom) return new Result(new Vec3(x, bottom, z), t, true);
            pos.set(x, y, z);
            if (!level.hasChunkAt(pos)) return new Result(new Vec3(x, y, z), t, false);
            if (pierce || y > level.getMaxBuildHeight()) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (!state.getCollisionShape(level, pos).isEmpty() || state.getFluidState().is(FluidTags.WATER)) {
                return new Result(new Vec3(x, y, z), t, true);
            }
        }
        return new Result(from.add(dir.scale(maxLen)), maxLen, false);
    }

    /** Hurt every living thing within {@code radius} of the segment. Returns how many were hit. */
    public static int damageAlong(ServerLevel level, RamielEntity source, Vec3 from, Vec3 to,
                                  double radius, float damage, double knockback) {
        AABB box = new AABB(from, to).inflate(radius + 3.0);
        DamageSource src = Damage.of(level, dev.ramiel.ModRegistry.DMG_BEAM, source);
        Vec3 axis = to.subtract(from);
        double lengthSqr = Math.max(1.0e-6, axis.lengthSqr());
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> !(e instanceof RamielEntity) && e.isAlive());
        int hit = 0;
        for (LivingEntity e : victims) {
            Vec3 c = e.getBoundingBox().getCenter();
            double t = Math.max(0.0, Math.min(1.0, c.subtract(from).dot(axis) / lengthSqr));
            Vec3 closest = from.add(axis.scale(t));
            double reach = radius + Math.max(e.getBbWidth(), e.getBbHeight()) * 0.5;
            if (c.distanceTo(closest) <= reach) {
                e.hurt(src, damage);
                e.setRemainingFireTicks(160);
                if (knockback > 0) {
                    Vec3 push = c.subtract(closest).add(axis.normalize().scale(0.6)).normalize().scale(knockback);
                    e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.3 + Math.abs(push.y) * 0.5, push.z));
                    e.hurtMarked = true;
                }
                hit++;
            }
        }
        return hit;
    }

    /** Damage with a radial falloff, used by explosions. Full damage at the centre, none at the rim. */
    public static void damageRadius(ServerLevel level, RamielEntity source, Vec3 centre, double radius, float maxDamage) {
        AABB box = new AABB(centre, centre).inflate(radius);
        DamageSource src = Damage.of(level, dev.ramiel.ModRegistry.DMG_BEAM, source);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> !(x instanceof RamielEntity) && x.isAlive())) {
            double d = e.position().distanceTo(centre);
            if (d > radius) continue;
            float scaled = maxDamage * (float) Math.pow(1.0 - d / radius, 0.8);
            e.hurt(src, Math.max(2f, scaled));
            Vec3 push = e.position().subtract(centre).normalize().scale(2.2 * (1.0 - d / radius) + 0.3);
            e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.8 * (1.0 - d / radius) + 0.2, push.z));
            e.hurtMarked = true;
            if (d < radius * 0.6) e.setRemainingFireTicks(200);
        }
    }
}
