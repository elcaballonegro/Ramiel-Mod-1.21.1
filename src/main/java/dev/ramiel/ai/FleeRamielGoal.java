package dev.ramiel.ai;

import dev.ramiel.RamielConfig;
import dev.ramiel.RamielMod;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.world.RamielTracker;
import java.util.EnumSet;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Animals and villagers run away from Ramiel. They run farther and faster while it is attacking.
 * This is the same idea as vanilla's AvoidEntityGoal, with two additions: a much bigger
 * panic radius, and a temporary speed boost.
 */
public class FleeRamielGoal extends Goal {
    private static final net.minecraft.resources.ResourceLocation BOOST_ID = RamielMod.id("panic_speed");

    private final PathfinderMob mob;
    private RamielEntity threat;
    private int repathTimer;

    public FleeRamielGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private double panicRadius(RamielEntity r) { return r.isBusy() ? 260.0 : 120.0; }

    @Override
    public boolean canUse() {
        if (!RamielConfig.mobsFlee()) return false;
        threat = RamielTracker.nearest(mob.level(), mob.position(), 260.0);
        return threat != null && horizontalDistance() < panicRadius(threat);
    }

    @Override
    public boolean canContinueToUse() {
        return RamielConfig.mobsFlee() && threat != null && threat.isAlive()
                && horizontalDistance() < panicRadius(threat) + 20.0;
    }

    @Override
    public void start() {
        repathTimer = 0;
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(BOOST_ID, 0.45, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    @Override
    public void stop() {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(BOOST_ID);
        mob.getNavigation().stop();
        threat = null;
    }

    @Override
    public void tick() {
        if (threat == null) return;
        if (--repathTimer > 0 && !mob.getNavigation().isDone()) return;
        repathTimer = 15;
        Vec3 flee = DefaultRandomPos.getPosAway(mob, 28, 8, threat.position());
        if (flee == null) {
            Vec3 away = mob.position().subtract(threat.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
            flee = mob.position().add(away.scale(24));
        }
        mob.getNavigation().moveTo(flee.x, flee.y, flee.z, threat.isBusy() ? 1.9 : 1.4);
    }

    private double horizontalDistance() {
        return Math.hypot(mob.getX() - threat.getX(), mob.getZ() - threat.getZ());
    }
}
