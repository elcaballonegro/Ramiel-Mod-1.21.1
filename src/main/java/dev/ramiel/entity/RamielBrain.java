package dev.ramiel.entity;

import dev.ramiel.Form;
import dev.ramiel.RamielConfig;
import dev.ramiel.entity.attack.Attack;
import dev.ramiel.entity.attack.CrossAttack;
import dev.ramiel.entity.attack.ImpactStrike;
import dev.ramiel.entity.attack.LanceAttack;
import dev.ramiel.entity.attack.SentinelAttack;
import dev.ramiel.entity.attack.StarAttack;
import dev.ramiel.entity.attack.StunAttack;
import dev.ramiel.entity.attack.SunburstAttack;
import dev.ramiel.net.FilmKind;
import dev.ramiel.net.Payloads;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side decision making. Each tick it either runs the current attack, or (when idle)
 * decides what to do next:
 *
 *   1. at two thirds health -> the stun, then the final phase (star attacks only)
 *   2. a target exists and the attack cooldown is over -> pick an attack
 *   3. nobody is bothering it and the impact cooldown is over -> impact strike (its own objective)
 *   4. otherwise: raise the shield if threatened, and drift slowly
 *
 * Ramiel only faces its target while an attack is running. When calm it just spins.
 */
final class RamielBrain {
    private final RamielEntity r;

    Vec3 moveGoal;                       // where it is drifting to (horizontal), or null
    private double speedNow;             // blocks per tick toward moveGoal, before the speed setting

    private int attackCooldown = 200;
    private int impactCooldown = 20 * 40;
    private int threatTimer;
    private int scanTimer;
    private int wanderTimer = 200;
    private int lastShieldHit = -1000;
    private UUID targetId;
    private final Map<UUID, Integer> attackers = new HashMap<>();   // who hurt us -> game tick
    private boolean stunStarted;
    private int lastAttackKind = -1;
    private Vec3 shieldDir = new Vec3(0, 0, 1);

    RamielBrain(RamielEntity r) { this.r = r; }

    // ==========================================================================================
    // Queries
    // ==========================================================================================
    @Nullable
    LivingEntity target() {
        if (targetId == null || r.level().isClientSide) return null;
        Entity e = ((ServerLevel) r.level()).getEntity(targetId);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    double moveSpeed() { return speedNow * RamielConfig.speed(); }
    boolean stunStarted() { return stunStarted; }
    boolean inStun() { return r.attack instanceof StunAttack; }
    boolean chargingImpact() { return r.attack instanceof ImpactStrike s && s.isCharging(); }
    Vec3 shieldDirection() { return shieldDir; }
    void setShieldDirection(Vec3 dir) { shieldDir = dir; lastShieldHit = r.tickCount; }
    void noteThreat() { threatTimer = 240; }

    void noteAttacker(LivingEntity who) {
        if (who != r) attackers.put(who.getUUID(), r.tickCount);
    }

    // ==========================================================================================
    // Events from the entity
    // ==========================================================================================
    void onArrived() {
        attackCooldown = 100;
        impactCooldown = 20 * 30;
    }

    void interruptImpact() {
        if (r.attack instanceof ImpactStrike s) s.interrupt();
    }

    void afterDamage(boolean core) {
        if (r.phase() == 1 && !stunStarted && r.hp > 0 && r.hp <= r.maxHp() * RamielEntity.PHASE_GATE + 0.5f) beginStun();
    }

    private void beginStun() {
        stunStarted = true;
        if (r.attack != null) r.attack.abort();
        r.setShieldUp(false);
        r.attack = new StunAttack(r);
    }

    // ==========================================================================================
    // Main tick
    // ==========================================================================================
    void tick() {
        ServerLevel level = r.serverLevel();
        r.tickShieldRegen();
        if (attackCooldown > 0) attackCooldown--;
        if (impactCooldown > 0) impactCooldown--;
        if (threatTimer > 0) threatTimer--;
        if (--scanTimer <= 0) { scanTimer = 10; scanTargets(level); }

        if (r.attack != null) {
            if (r.attack.tick()) finishAttack();
            return;
        }
        idle(level);
    }

    private void idle(ServerLevel level) {
        LivingEntity t = target();
        r.setSpinSpeed(0.35f);                     // calm: a slow spin, no looking at anyone
        r.setCoreOpen(false);

        if (r.phase() == 2) {
            if (r.form() != Form.STAR) r.setForm(Form.STAR, 20);
            if (t != null && attackCooldown <= 0) {
                startAttack(new StarAttack(r, !secondPhaseStarted));
                secondPhaseStarted = true;
            }
            speedNow = 0;
            return;
        }

        if (t != null && attackCooldown <= 0) {
            startAttack(chooseAttack(t));
            return;
        }
        if (t == null && attackers.isEmpty() && impactCooldown <= 0) {
            startAttack(new ImpactStrike(r));
            return;
        }
        updateShield(t);
        drift(t);
    }

    private boolean secondPhaseStarted;

    private void startAttack(Attack a) {
        r.setShieldUp(false);
        r.attack = a;
        speedNow = 0;
        moveGoal = null;
    }

    private void finishAttack() {
        Attack a = r.attack;
        r.attack = null;
        double aggression = RamielConfig.aggression();

        if (a instanceof StunAttack) {
            r.setPhase(2);
            r.shieldDur = 0;
            attackCooldown = 60;
        } else if (a instanceof StarAttack) {
            attackCooldown = RamielConfig.phase2Cooldown() * 20 + r.getRandom().nextInt(200);
        } else if (a instanceof ImpactStrike s) {
            impactCooldown = (int) ((s.wasInterrupted() ? 20 * 90 : 20 * 60 * (2.5 + r.getRandom().nextDouble())) / aggression);
            attackCooldown = 200;
            if (!s.wasInterrupted()) relocateAwayFrom(s.impactPoint());
        } else {
            attackCooldown = (int) ((200 + r.getRandom().nextInt(200)) / aggression);
        }
    }

    // ==========================================================================================
    // Choosing an attack
    // ==========================================================================================
    private Attack chooseAttack(LivingEntity t) {
        double d = Math.hypot(t.getX() - r.getX(), t.getZ() - r.getZ());
        int close = 0;
        for (ServerPlayer p : r.serverLevel().players()) {
            if (!p.isSpectator() && Math.hypot(p.getX() - r.getX(), p.getZ() - r.getZ()) < 90) close++;
        }
        // weights: lance is the common one, sunburst is for crowds near it
        double[] w = {40, 20, 15 + (d < 100 ? 25 : 0) + close * 8, d < 260 ? 25 : 5};
        if (lastAttackKind >= 0) w[lastAttackKind] *= 0.4;                    // avoid repeats
        double sum = w[0] + w[1] + w[2] + w[3];
        double roll = r.getRandom().nextDouble() * sum;
        int pick = 0;
        for (; pick < 3; pick++) { if ((roll -= w[pick]) < 0) break; }
        lastAttackKind = pick;
        return switch (pick) {
            case 0 -> new LanceAttack(r);
            case 1 -> new CrossAttack(r);
            case 2 -> new SunburstAttack(r);
            default -> new SentinelAttack(r);
        };
    }

    // ==========================================================================================
    // Targets
    // ==========================================================================================
    private void scanTargets(ServerLevel level) {
        double range = RamielConfig.aggroRadius();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;

        if (!RamielConfig.ignorePlayers()) {
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || !p.isAlive()) continue;
                double d = Math.hypot(p.getX() - r.getX(), p.getZ() - r.getZ());
                if (d <= range && d < bestD) { best = p; bestD = d; }
            }
        }
        if (best != null) {
            threatTimer = Math.max(threatTimer, 100);              // players nearby: stay guarded
        } else {
            // players always come first; otherwise anything that recently attacked us
            attackers.values().removeIf(tick -> r.tickCount - tick > 600);
            for (UUID id : attackers.keySet()) {
                Entity e = level.getEntity(id);
                if (e instanceof LivingEntity le && le.isAlive()) {
                    double d = Math.hypot(le.getX() - r.getX(), le.getZ() - r.getZ());
                    if (d < bestD && d < 500) { best = le; bestD = d; }
                }
            }
        }
        targetId = best == null ? null : best.getUUID();
    }

    // ==========================================================================================
    // Shield and drifting
    // ==========================================================================================
    private void updateShield(@Nullable LivingEntity t) {
        boolean want = threatTimer > 0 && r.shieldBroken == 0 && r.shieldDur > 0 && r.phase() == 1;
        if (want && !r.shieldUp()) { r.setShieldUp(true); r.setForm(Form.SHIELD, 14); }
        if (!want && r.shieldUp()) { r.setShieldUp(false); r.setForm(Form.DORMANT, 14); }
        if (r.shieldUp()) {
            // face the last thing that hit us; with no recent hit, face the target
            if (t != null && r.tickCount - lastShieldHit > 100) {
                shieldDir = t.getBoundingBox().getCenter().subtract(r.centre()).normalize();
            }
            r.aimToward(r.centre().add(shieldDir.scale(100)), 2.5f);
        }
    }

    private void drift(@Nullable LivingEntity t) {
        if (t != null) {
            // stalk: close in until about 150 blocks away
            double d = Math.hypot(t.getX() - r.getX(), t.getZ() - r.getZ());
            if (d > 160) { moveGoal = new Vec3(t.getX(), 0, t.getZ()); speedNow = 0.22; }
            else { moveGoal = null; speedNow = 0; }
            return;
        }
        if (wanderTimer-- <= 0 && moveGoal == null) {
            wanderTimer = 400 + r.getRandom().nextInt(400);
            double a = r.getRandom().nextDouble() * Math.PI * 2.0;
            double dist = 60 + r.getRandom().nextDouble() * 80;
            moveGoal = new Vec3(r.getX() + Math.cos(a) * dist, 0, r.getZ() + Math.sin(a) * dist);
            speedNow = 0.08;
        }
        if (moveGoal == null) speedNow = 0;
    }

    /** After an impact: head for somewhere far from the crater. */
    private void relocateAwayFrom(Vec3 crater) {
        double a = r.getRandom().nextDouble() * Math.PI * 2.0;
        double dist = 350 + r.getRandom().nextDouble() * 250;
        moveGoal = new Vec3(crater.x + Math.cos(a) * dist, 0, crater.z + Math.sin(a) * dist);
        speedNow = 0.32;
    }

    // ==========================================================================================
    // Beacon for far-away clients (the horizon version)
    // ==========================================================================================
    void broadcastBeacon(ServerLevel level) {
        if (!RamielConfig.farRender()) return;
        double range = RamielConfig.farRange();
        Payloads.Beacon b = new Payloads.Beacon(r.getUUID(), r.getX(), r.getY(), r.getZ(),
                r.aimYaw(), r.aimPitch(), r.spin(1f), r.formFrom().ordinal(), r.form().ordinal(), r.morph(1f), r.isAlive());
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - r.getX(), dz = p.getZ() - r.getZ();
            if (dx * dx + dz * dz <= range * range) PacketDistributor.sendToPlayer(p, b);
        }
    }

    // ==========================================================================================
    // Persistence
    // ==========================================================================================
    void save(CompoundTag tag) {
        tag.putBoolean("StunStarted", stunStarted);
        tag.putInt("ImpactCd", impactCooldown);
        tag.putInt("AttackCd", attackCooldown);
    }

    void load(CompoundTag tag) {
        stunStarted = tag.getBoolean("StunStarted");
        if (tag.contains("ImpactCd")) impactCooldown = tag.getInt("ImpactCd");
        if (tag.contains("AttackCd")) attackCooldown = tag.getInt("AttackCd");
        // a Ramiel that was saved in the final phase carries on in it
        secondPhaseStarted = r.phase() == 2;
    }
}
