package dev.ramiel.world;

import dev.ramiel.entity.RamielEntity;
import dev.ramiel.entity.RamielHitTest;
import dev.ramiel.entity.RamielPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * Decides what a projectile REALLY hit. Arrows collide with Ramiel's boxes, but a box is bigger
 * than an octahedron, so this does the exact test before the hit counts:
 *
 *   empty corner of a box  -> the projectile flies on through
 *   body or core, shield up -> the shield swallows it and bounces it away
 *   body or core, no shield -> normal hit; we remember "core" or "body" for the damage rules
 */
public final class HitResolver {
    private HitResolver() {}

    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        RamielEntity ramiel = ramielOf(hit.getEntity());
        if (ramiel == null || ramiel.level().isClientSide) return;

        if (!ramiel.arrived()) {                       // still falling from space: nothing can touch it
            event.setCanceled(true);
            return;
        }
        Projectile projectile = event.getProjectile();
        Vec3 from = projectile.position();
        Vec3 to = from.add(projectile.getDeltaMovement());
        RamielHitTest.Hit result = RamielHitTest.trace(ramiel, from, to);

        if (result.type() == RamielHitTest.Type.MISS) {
            event.setCanceled(true);
            return;
        }
        if (ramiel.shieldUp()) {
            ramiel.absorbShield(RamielEntity.projectilePower(projectile), result.point());
            ramiel.deflect(projectile, result.point());
            event.setCanceled(true);
            return;
        }
        ramiel.noteHit(result.type());
    }

    private static RamielEntity ramielOf(Entity e) {
        if (e instanceof RamielEntity r) return r;
        if (e instanceof RamielPart p) return p.getParent();
        return null;
    }
}
