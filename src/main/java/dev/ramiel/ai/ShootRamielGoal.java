package dev.ramiel.ai;

import dev.ramiel.RamielConfig;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.world.RamielTracker;
import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Ranged mobs (skeletons, pillagers, blazes, ghasts...) shoot at Ramiel when it is within reach.
 * Vanilla ranged AI only works up to ~15 blocks, which is useless against something floating 80
 * blocks up, so this fires faster projectiles on a slightly lofted line.
 * The shots are real projectiles: Ramiel's shield deflects them, and a lucky one can hit the core.
 */
public class ShootRamielGoal extends Goal {
    private final Mob mob;
    private RamielEntity target;
    private int cooldown = 40;

    public ShootRamielGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!RamielConfig.mobsAttack()) return false;
        target = RamielTracker.nearest(mob.level(), mob.position(), 150.0);
        return target != null && target.arrived() && !target.isDeadOrDying();
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void tick() {
        if (target == null) return;
        Vec3 aim = aimPoint();
        mob.getLookControl().setLookAt(aim.x, aim.y, aim.z);
        if (--cooldown > 0) return;
        cooldown = 45 + mob.getRandom().nextInt(35);
        shoot(aim);
    }

    /** Somewhere on the crystal, not always the dead centre. */
    private Vec3 aimPoint() {
        Vec3 c = target.centre();
        RamielEntity r = target;
        return c.add((mob.getRandom().nextDouble() - 0.5) * RamielEntity.R * 0.7,
                (mob.getRandom().nextDouble() - 0.5) * RamielEntity.R * 0.7,
                (mob.getRandom().nextDouble() - 0.5) * RamielEntity.R * 0.7);
    }

    private void shoot(Vec3 aim) {
        var level = mob.level();
        Vec3 from = mob.getEyePosition();
        Vec3 delta = aim.subtract(from);
        double flat = Math.hypot(delta.x, delta.z);

        if (mob instanceof Ghast) {
            LargeFireball fb = new LargeFireball(level, mob, delta.normalize().scale(0.1), 2);
            fb.setPos(from.x, from.y, from.z);
            level.addFreshEntity(fb);
        } else if (mob instanceof Blaze) {
            SmallFireball fb = new SmallFireball(level, mob, delta.normalize().scale(0.1));
            fb.setPos(from.x, from.y, from.z);
            level.addFreshEntity(fb);
        } else {
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            arrow.setOwner(mob);
            arrow.setPos(from.x, from.y, from.z);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setBaseDamage(3.0);
            // fast arrow, aimed a little high to make up for gravity over a long distance
            arrow.shoot(delta.x, delta.y + flat * 0.045, delta.z, 4.5f, 1.5f);
            level.addFreshEntity(arrow);
        }
    }
}
