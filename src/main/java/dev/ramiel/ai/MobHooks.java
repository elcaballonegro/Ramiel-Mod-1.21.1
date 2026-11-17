package dev.ramiel.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.npc.AbstractVillager;

/** Decides which vanilla mobs get Ramiel-related goals when they enter the world. */
public final class MobHooks {
    private MobHooks() {}

    public static void attach(Mob mob) {
        if (mob instanceof PathfinderMob pm && (mob instanceof Animal || mob instanceof AbstractVillager)) {
            pm.goalSelector.addGoal(0, new FleeRamielGoal(pm));
        }
        if (isShooter(mob)) {
            mob.goalSelector.addGoal(1, new ShootRamielGoal(mob));
        }
    }

    private static boolean isShooter(Mob mob) {
        return mob instanceof AbstractSkeleton || mob instanceof Pillager || mob instanceof Illusioner
                || mob instanceof Blaze || mob instanceof Ghast;
    }
}
