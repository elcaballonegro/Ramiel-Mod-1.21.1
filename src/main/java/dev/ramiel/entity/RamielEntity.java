package dev.ramiel.entity;

import dev.ramiel.Form;
import dev.ramiel.ModRegistry;
import dev.ramiel.RamielConfig;
import dev.ramiel.entity.attack.Attack;
import dev.ramiel.net.FilmKind;
import dev.ramiel.net.FxKind;
import dev.ramiel.net.Payloads;
import dev.ramiel.world.ChunkAnchor;
import dev.ramiel.world.RamielTracker;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * Ramiel. The server-side brain lives in {@link RamielBrain}; the attacks in entity/attack.
 * This class owns the BODY: synced state, hit boxes, damage rules, movement, shield, death.
 *
 * Size reference: R = 24 blocks is the half-diagonal of the octahedron (a 48 block tall crystal).
 * The vanilla bounding box is only the 5.2 block CORE; see {@link RamielPart} for the rest.
 */
public class RamielEntity extends Mob {
    // ---- scale and balance --------------------------------------------------------------------
    public static final double R = 24.0;
    public static final float CORE_BOX = 5.2f;
    public static final float CORE_RADIUS = CORE_BOX / 2f;
    public static final float BASE_HP = 3000f;
    public static final float BODY_FACTOR = 0.25f;     // body hits do 25% damage
    public static final float CORE_FACTOR = 5.0f;      // exposed core takes 5x
    public static final float PHASE_GATE = 2f / 3f;    // at 2/3 health: stun, then the final phase
    public static final float STUN_FLOOR = 0.30f;      // normal damage cannot kill during the stun
    public static final float RAIL_BODY = 0.20f;       // a railcannon hit takes 20% of max health from the body...
    public static final float RAIL_CORE = 0.50f;       // ...and 50% from the exposed core (two shots)
    public static final int ARRIVAL_TICKS = 280;
    public static final double ARRIVAL_HEIGHT = 2600.0;
    public static final int DEATH_TICKS = 220;
    public static final float SHIELD_MAX = 1500f;

    // ---- synced data --------------------------------------------------------------------------
    private static final EntityDataAccessor<Integer> D_FORM_FROM = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_FORM_TO = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_PHASE = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_BEAM_KIND = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_DEATH_T = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> D_MORPH = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_AIM_YAW = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_AIM_PITCH = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_BEAM_LEN = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_BEAM_T = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_CHARGE = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_SPIN_SPEED = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_ARRIVAL = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_DRILL = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_SHIELD = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> D_CORE_OPEN = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> D_SHIELD_UP = SynchedEntityData.defineId(RamielEntity.class, EntityDataSerializers.BOOLEAN);

    // ---- body boxes (blocks): centre offset from the core, and half-width, of each slab ---------
    private static final float[] PART_Y = {4f, 12f, 20f, -4f, -12f, -20f};
    private static final float[] PART_HALF = {24f, 16f, 8f, 24f, 16f, 8f};
    private final RamielPart[] parts = new RamielPart[PART_Y.length];

    // ---- values mirrored on both sides, for smooth rendering ---------------------------------
    private float spinC, spinO, morphC, morphO, aimYawC, aimYawO, aimPitchC, aimPitchO;
    private float arrivalC, arrivalO, chargeC, chargeO, coreAnim, coreAnimO;

    // ---- server-only state --------------------------------------------------------------------
    final RamielBrain brain = new RamielBrain(this);
    Attack attack;                                       // the attack running right now, or null
    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.ramiel.ramiel"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private final ChunkAnchor anchor = new ChunkAnchor();
    float hp = -1f;                                      // -1 = not set up yet
    float shieldDur = SHIELD_MAX;
    int shieldBroken = 0;                                // ticks the shield stays down after breaking
    private int morphTick, morphDur = 1;
    private Vec3 velocity = Vec3.ZERO;
    private int groundTimer;
    private double groundY = Double.NaN;
    private DamageSource lastSource;
    private int lastSourceTick = -1;
    private RamielHitTest.Type hitClass = RamielHitTest.Type.BODY;
    private int hitClassTick = -1;
    private float damageDuringCharge;

    public RamielEntity(EntityType<? extends RamielEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.xpReward = 0;
        this.setPersistenceRequired();
        for (int i = 0; i < parts.length; i++) {
            parts[i] = new RamielPart(this, i, PART_HALF[i] * 2f, 8f);
        }
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)              // vanilla caps at 1024; the real pool is `hp`
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 256.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(D_FORM_FROM, 0).define(D_FORM_TO, 0).define(D_PHASE, 1).define(D_BEAM_KIND, 0).define(D_DEATH_T, 0);
        b.define(D_MORPH, 1f).define(D_AIM_YAW, 0f).define(D_AIM_PITCH, 0f).define(D_BEAM_LEN, 0f).define(D_BEAM_T, 0f);
        b.define(D_CHARGE, 0f).define(D_SPIN_SPEED, 0.35f).define(D_ARRIVAL, 1f).define(D_DRILL, 0f).define(D_SHIELD, 1f);
        b.define(D_CORE_OPEN, false).define(D_SHIELD_UP, false);
    }

    // ==========================================================================================
    // Read-only state, used by the renderer, hit tests and attacks
    // ==========================================================================================
    public Form form() { return Form.byId(entityData.get(D_FORM_TO)); }
    public Form formFrom() { return Form.byId(entityData.get(D_FORM_FROM)); }
    public float morph(float partial) { return Mth.lerp(partial, morphO, morphC); }
    public float spin(float partial) { return Mth.lerp(partial, spinO, spinC); }
    public float aimYaw(float partial) { return Mth.rotLerp(partial, aimYawO, aimYawC); }
    public float aimPitch(float partial) { return Mth.lerp(partial, aimPitchO, aimPitchC); }
    public float charge(float partial) { return Mth.lerp(partial, chargeO, chargeC); }
    public float coreAnim(float partial) { return Mth.lerp(partial, coreAnimO, coreAnim); }
    public float arrival(float partial) { return Mth.lerp(partial, arrivalO, arrivalC); }
    public Vec3 aimDir(float partial) { return Vec3.directionFromRotation(aimPitch(partial), aimYaw(partial)); }
    public Vec3 aimDir() { return Vec3.directionFromRotation(entityData.get(D_AIM_PITCH), entityData.get(D_AIM_YAW)); }
    public float aimYaw() { return entityData.get(D_AIM_YAW); }
    public float aimPitch() { return entityData.get(D_AIM_PITCH); }
    public boolean coreOpen() { return entityData.get(D_CORE_OPEN); }
    public boolean shieldUp() { return entityData.get(D_SHIELD_UP); }
    public float shieldRatio() { return entityData.get(D_SHIELD); }
    public float drillLen() { return entityData.get(D_DRILL); }
    public int phase() { return entityData.get(D_PHASE); }
    public int beamKind() { return entityData.get(D_BEAM_KIND); }
    public float beamLen() { return entityData.get(D_BEAM_LEN); }
    public float beamT() { return entityData.get(D_BEAM_T); }
    public int deathTicks() { return entityData.get(D_DEATH_T); }
    public boolean arrived() { return entityData.get(D_ARRIVAL) >= 1f; }
    public float healthRatio() { return hp < 0 ? 1f : Mth.clamp(hp / maxHp(), 0f, 1f); }

    /** Centre of the core = centre of the whole body. */
    public Vec3 centre() { return position().add(0, CORE_BOX / 2.0, 0); }

    /** How big the octahedron hit volume is, relative to R, for the current form. */
    public double bodyScale() {
        switch (form()) {
            case LANCE: case CROSS: case SENTINEL: return 0.85;
            case URCHIN: return 0.75;
            case SUNBURST: return 0.9;
            default: return 1.0;
        }
    }

    /** Client-only: how far the model is still dropping in from space. */
    public double arrivalOffsetY(float partial) {
        float p = Mth.clamp(arrival(partial), 0f, 1f);
        if (p >= 1f) return 0.0;
        double inv = 1.0 - p;
        return ARRIVAL_HEIGHT * inv * inv * (0.4 + 0.6 * inv);   // fast at first, gentle at the end
    }

    public float maxHp() { return (float) (BASE_HP * RamielConfig.health()); }

    // ==========================================================================================
    // Server-side control used by the brain and the attacks
    // ==========================================================================================
    public ServerLevel serverLevel() { return (ServerLevel) level(); }

    public void setForm(Form next, int morphTicks) {
        if (next == form() && entityData.get(D_MORPH) >= 1f) return;
        entityData.set(D_FORM_FROM, form().ordinal());
        entityData.set(D_FORM_TO, next.ordinal());
        morphTick = 0;
        morphDur = Math.max(1, morphTicks);
        entityData.set(D_MORPH, 0f);
    }

    public void setCharge(float c) { entityData.set(D_CHARGE, Mth.clamp(c, 0f, 1f)); }
    public void setCoreOpen(boolean open) { if (coreOpen() != open) entityData.set(D_CORE_OPEN, open); }
    public void setSpinSpeed(float degPerTick) { entityData.set(D_SPIN_SPEED, degPerTick); }
    public void setDrill(float lengthBlocks) { entityData.set(D_DRILL, lengthBlocks); }
    public void setPhase(int p) { entityData.set(D_PHASE, p); }
    public void setAim(float yaw, float pitch) { entityData.set(D_AIM_YAW, yaw); entityData.set(D_AIM_PITCH, pitch); }
    public void setBeam(int kind, float len, float t) {
        entityData.set(D_BEAM_KIND, kind);
        entityData.set(D_BEAM_LEN, len);
        entityData.set(D_BEAM_T, t);
    }
    public void clearBeam() { setBeam(0, 0f, 0f); }
    void setShieldUp(boolean up) { if (shieldUp() != up) entityData.set(D_SHIELD_UP, up); }

    /** Turn toward a point, slowly. {@code maxStep} is degrees per tick; the delay comes from easing. */
    public void aimToward(Vec3 point, float maxStep) {
        Vec3 d = point.subtract(centre());
        float wantYaw = (float) (Mth.atan2(-d.x, d.z) * (180.0 / Math.PI));
        float wantPitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0 / Math.PI)));
        float yaw = entityData.get(D_AIM_YAW), pitch = entityData.get(D_AIM_PITCH);
        float dy = Mth.wrapDegrees(wantYaw - yaw);
        float dp = wantPitch - pitch;
        setAim(yaw + Mth.clamp(dy * 0.07f, -maxStep, maxStep), pitch + Mth.clamp(dp * 0.07f, -maxStep, maxStep));
    }

    /** Start the fall from space (called right after spawning). */
    public void beginArrival() {
        entityData.set(D_ARRIVAL, 0f);
        arrivalC = arrivalO = 0f;
    }

    /** True while an attack script is running. */
    public boolean isBusy() { return attack != null; }

    /** Used by commands: set health to a fraction of the maximum. */
    public void setHealthRatio(float ratio) {
        hp = Math.max(1f, maxHp() * Mth.clamp(ratio, 0f, 1f));
        syncHealth();
        brain.afterDamage(false);
    }

    /** Ground height below Ramiel (cached; never loads chunks). */
    public double groundY() { return groundBelow(serverLevel()); }

    /** Aim at the current target, if any. */
    public void aimAtTarget(float maxStep) {
        LivingEntity t = target();
        if (t != null) aimToward(t.getBoundingBox().getCenter(), maxStep);
    }

    @Nullable public LivingEntity target() { return brain.target(); }

    public void playLoud(SoundEvent sound, float volume, float pitch) {
        Vec3 c = centre();
        level().playSound(null, c.x, c.y, c.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** Send a client effect to everyone within a long range. */
    public void fx(int kind, Vec3 at, double a, double b, double c, float f1, float f2) {
        Payloads.fx(serverLevel(), at, 700, kind, a, b, c, f1, f2);
    }

    public void fx(int kind, Vec3 at, double a, double b) { fx(kind, at, a, b, 0, 0f, 0f); }

    /** Ask nearby players' clients to play a cutscene. */
    public void playFilm(int kind) {
        int minLevel = FilmKind.minCutsceneSetting(kind);
        if (RamielConfig.cutscenes() < minLevel) return;
        double range = FilmKind.range(kind);
        for (ServerPlayer p : serverLevel().players()) {
            double dx = p.getX() - getX(), dz = p.getZ() - getZ();
            if (dx * dx + dz * dz <= range * range) {
                Payloads.toPlayer(p, new Payloads.Film(kind, getId(), 0));
            }
        }
    }

    // ==========================================================================================
    // Tick
    // ==========================================================================================
    @Override
    public void tick() {
        // remember last tick's values so the renderer can interpolate between ticks
        spinO = spinC; morphO = morphC; aimYawO = aimYawC; aimPitchO = aimPitchC;
        arrivalO = arrivalC; chargeO = chargeC; coreAnimO = coreAnim;
        morphC = entityData.get(D_MORPH);
        aimYawC = entityData.get(D_AIM_YAW);
        aimPitchC = entityData.get(D_AIM_PITCH);
        arrivalC = entityData.get(D_ARRIVAL);
        chargeC = entityData.get(D_CHARGE);
        spinC += entityData.get(D_SPIN_SPEED);
        if (spinC >= 360f) { spinC -= 360f; spinO -= 360f; }
        coreAnim += ((coreOpen() ? 1f : 0f) - coreAnim) * 0.12f;

        super.tick();

        if (!level().isClientSide && isAlive()) serverTick();
        updateParts();
    }

    private void serverTick() {
        ServerLevel level = serverLevel();
        if (hp < 0) { hp = maxHp(); syncHealth(); }

        // morph progress (the renderer interpolates between these values)
        if (entityData.get(D_MORPH) < 1f) {
            morphTick++;
            entityData.set(D_MORPH, Math.min(1f, morphTick / (float) morphDur));
        }

        if (!arrived()) tickArrival(level); else brain.tick();

        tickMovement(level);
        if (tickCount % 20 == 0) {
            anchor.update(level, blockPosition());
            updateBossBar(level);
        }
        if (tickCount % 10 == 0) brain.broadcastBeacon(level);
    }

    // ---- arrival from space -----------------------------------------------------------------
    private void tickArrival(ServerLevel level) {
        float p = entityData.get(D_ARRIVAL) + 1f / ARRIVAL_TICKS;
        entityData.set(D_ARRIVAL, Math.min(1f, p));
        setCoreOpen(false);
        if (p >= 1f) {
            Vec3 ground = new Vec3(getX(), groundBelow(level), getZ());
            fx(FxKind.SHOCKWAVE, ground, 140, 70);
            dev.ramiel.world.Shockwave.launch(level, ground, 140, 70, 0.9, true, this);
            fx(FxKind.SHAKE, ground, 3.0, 50);
            fx(FxKind.IMPACT_FRAME, centre(), 4, 0);
            playLoud(ModRegistry.SND_BOOM.get(), 60f, 0.7f);
            brain.onArrived();
        }
    }

    // ---- hovering and drifting ----------------------------------------------------------------
    private double groundBelow(ServerLevel level) {
        if (groundTimer-- <= 0 || Double.isNaN(groundY)) {
            groundTimer = 10;
            BlockPos pos = blockPosition();
            if (level.hasChunkAt(pos)) groundY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
            else if (Double.isNaN(groundY)) groundY = getY() - RamielConfig.hover();
        }
        return groundY;
    }

    private void tickMovement(ServerLevel level) {
        double speed = brain.moveSpeed();
        Vec3 want = Vec3.ZERO;
        Vec3 goal = brain.moveGoal;
        if (goal != null && speed > 0) {
            Vec3 to = new Vec3(goal.x - getX(), 0, goal.z - getZ());
            double dist = to.length();
            if (dist < 3.0) brain.moveGoal = null; else want = to.scale(speed / dist);
        }
        velocity = velocity.add(want.subtract(velocity).scale(0.03));

        double desiredCentreY = groundBelow(level) + RamielConfig.hover();
        double vy = Mth.clamp((desiredCentreY - centre().y) * 0.02, -0.5, 0.5);
        if (!arrived()) vy = 0;                                    // while arriving it stays where it will land

        // We move the entity ourselves and keep vanilla motion at zero, so the two never add up.
        setPos(getX() + velocity.x, getY() + vy, getZ() + velocity.z);
        setDeltaMovement(Vec3.ZERO);
    }

    // ---- hit boxes ----------------------------------------------------------------------------
    private void updateParts() {
        Vec3 c = centre();
        for (int i = 0; i < parts.length; i++) {
            parts[i].setPos(c.x, c.y + PART_Y[i] - 4.0, c.z);       // part position = bottom centre of its box
        }
    }

    @Override public boolean isMultipartEntity() { return true; }
    @Override public PartEntity<?>[] getParts() { return parts; }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);     // like the Ender Dragon
    }

    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(R * 3.0); }
    @Override public boolean shouldRenderAtSqrDistance(double distanceSqr) { return true; }

    // ==========================================================================================
    // Damage
    // ==========================================================================================

    /** The exact-hit test (see HitResolver) tells us here what a projectile touched this tick. */
    public void noteHit(RamielHitTest.Type type) {
        hitClass = type;
        hitClassTick = tickCount;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return hurtPart(null, source, amount);          // a hit on the core box itself
    }

    /** Every hit on any box lands here. */
    public boolean hurtPart(@Nullable RamielPart part, DamageSource source, float amount) {
        if (level().isClientSide || isDeadOrDying() || hp < 0) return false;

        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {   // /kill and the void
            return applyDamage(Float.MAX_VALUE, source, true, true);
        }
        if (!arrived()) return false;                                                          // invulnerable while arriving
        if (source == lastSource && tickCount == lastSourceTick) return false;                  // an explosion hits every box; count it once
        lastSource = source;
        lastSourceTick = tickCount;

        boolean creative = source.getEntity() instanceof Player p && p.getAbilities().instabuild;
        boolean projectileOrBlast = source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION);

        // Shield: swallows projectiles and explosions
        if (shieldUp() && projectileOrBlast && !creative) {
            Vec3 from = source.getSourcePosition() != null ? source.getSourcePosition() : centre().add(0, 1, 0);
            absorbShield(Math.max(amount, 4f), from);
            return false;
        }

        RamielHitTest.Type type = hitClassTick == tickCount ? hitClass
                : (part == null && coreOpen() ? RamielHitTest.Type.CORE : RamielHitTest.Type.BODY);
        boolean core = type == RamielHitTest.Type.CORE && coreOpen();
        return applyDamage(amount * (core ? CORE_FACTOR : BODY_FACTOR), source, core, false);
    }

    /** A railcannon beam went through Ramiel. Called by the weapon, which already did the exact hit test. */
    public void railcannonHit(ServerPlayer shooter, boolean core) {
        if (isDeadOrDying() || !arrived() || hp < 0) return;
        float dmg = maxHp() * (core ? RAIL_CORE : RAIL_BODY);
        applyDamage(dmg, damageSources().playerAttack(shooter), core, true);
    }

    /**
     * The one place health goes down.
     * @param core true if this hit the exposed core
     * @param bypassGates true for the railcannon and /kill: ignores the phase and stun floors
     */
    private boolean applyDamage(float dmg, DamageSource source, boolean core, boolean bypassGates) {
        if (dmg <= 0) return false;
        float max = maxHp();
        float newHp = hp - dmg;

        if (!bypassGates) {
            if (phase() == 1 && !brain.stunStarted()) newHp = Math.max(newHp, max * PHASE_GATE);   // cannot skip the stun
            else newHp = Math.max(newHp, max * STUN_FLOOR * (brain.inStun() ? 1f : 0f));
        }
        hp = Math.max(0f, newHp);
        syncHealth();

        if (source.getEntity() instanceof LivingEntity attacker) brain.noteAttacker(attacker);
        brain.noteThreat();

        // damage during the impact charge can cancel the explosion
        if (brain.chargingImpact() && RamielConfig.interrupt()) {
            damageDuringCharge += dmg;
            if (core || damageDuringCharge >= max * 0.08f) { brain.interruptImpact(); damageDuringCharge = 0; }
        } else {
            damageDuringCharge = 0;
        }

        brain.afterDamage(core);

        if (hp <= 0f) {
            setHealth(0f);
            die(source);
        }
        return true;
    }

    private void syncHealth() {
        float ratio = hp / maxHp();
        setHealth(Math.max(hp > 0 ? 1f : 0f, ratio * getMaxHealth()));
        bossBar.setProgress(Mth.clamp(ratio, 0f, 1f));
    }

    // ---- shield -------------------------------------------------------------------------------
    /** A projectile or blast hit the raised shield. {@code from} is where it came from. */
    public void absorbShield(float amount, Vec3 from) {
        if (!shieldUp() || level().isClientSide) return;
        Vec3 dir = from.subtract(centre()).normalize();
        brain.setShieldDirection(dir);
        brain.noteThreat();
        shieldDur -= amount * 3f;
        entityData.set(D_SHIELD, Mth.clamp(shieldDur / SHIELD_MAX, 0f, 1f));
        fx(FxKind.SHIELD_HIT, centre().add(dir.scale(R * 0.95)), dir.x, dir.y, dir.z, Math.min(1f, amount / 12f + 0.3f), 0f);
        playLoud(ModRegistry.SND_SHIELD.get(), 3f, 0.9f + random.nextFloat() * 0.3f);
        dev.ramiel.world.Sfx.deflect(serverLevel(), centre().add(dir.scale(R)));
        if (shieldDur <= 0f) breakShield();
    }

    private void breakShield() {
        shieldDur = 0f;
        shieldBroken = 600;
        setShieldUp(false);
        entityData.set(D_SHIELD, 0f);
        fx(FxKind.SHIELD_BREAK, centre().add(brain.shieldDirection().scale(R * 0.95)), 0, 0);
        playLoud(SoundEvents.GLASS_BREAK, 8f, 0.5f);
        playLoud(SoundEvents.ENDER_DRAGON_HURT, 6f, 0.5f);
        setForm(Form.DORMANT, 12);
    }

    void tickShieldRegen() {
        if (shieldBroken > 0) {
            if (--shieldBroken == 0) shieldDur = SHIELD_MAX * 0.4f;
        } else if (shieldDur < SHIELD_MAX) {
            shieldDur = Math.min(SHIELD_MAX, shieldDur + 1.2f);
        }
        float ratio = Mth.clamp(shieldDur / SHIELD_MAX, 0f, 1f);
        if (Math.abs(entityData.get(D_SHIELD) - ratio) > 0.004f) entityData.set(D_SHIELD, ratio);
    }

    /** Bounce a projectile off the shield: mirror its velocity around the outward direction. */
    public void deflect(Projectile projectile, Vec3 hitPoint) {
        Vec3 n = hitPoint.subtract(centre()).normalize();
        Vec3 v = projectile.getDeltaMovement();
        Vec3 bounced = v.subtract(n.scale(2.0 * v.dot(n))).scale(0.65);
        projectile.setPos(hitPoint.add(n.scale(2.5)));
        projectile.setDeltaMovement(bounced);
        projectile.hasImpulse = true;
        projectile.hurtMarked = true;
        if (projectile instanceof AbstractArrow arrow) arrow.setBaseDamage(arrow.getBaseDamage() * 0.5);
    }

    /** Rough "how hard did this projectile hit" for shield wear. */
    public static float projectilePower(Projectile p) {
        if (p instanceof AbstractArrow a) return (float) (a.getBaseDamage() * p.getDeltaMovement().length() * 1.5);
        return 8f;
    }

    // ==========================================================================================
    // Boss bar, persistence, death
    // ==========================================================================================
    private void updateBossBar(ServerLevel level) {
        bossBar.setName(getDisplayName());
        bossBar.setProgress(Mth.clamp(healthRatio(), 0f, 1f));
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - getX(), dz = p.getZ() - getZ();
            boolean near = dx * dx + dz * dz < 700.0 * 700.0;
            if (near) bossBar.addPlayer(p); else bossBar.removePlayer(p);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!level().isClientSide) {
            bossBar.removeAllPlayers();
            anchor.releaseAll();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("RamielHp", hp);
        tag.putFloat("RamielShield", shieldDur);
        tag.putInt("RamielPhase", phase());
        brain.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("RamielHp")) hp = tag.getFloat("RamielHp");
        if (tag.contains("RamielShield")) shieldDur = tag.getFloat("RamielShield");
        if (tag.contains("RamielPhase")) setPhase(tag.getInt("RamielPhase"));
        entityData.set(D_ARRIVAL, 1f);                          // a saved Ramiel has long since landed
        brain.load(tag);
    }

    @Override
    protected void tickDeath() {
        if (level().isClientSide) return;
        int t = entityData.get(D_DEATH_T) + 1;
        entityData.set(D_DEATH_T, t);
        ServerLevel level = serverLevel();
        Vec3 c = centre();
        if (t == 1) {
            attack = null;
            clearBeam();
            setCoreOpen(true);
            setForm(Form.URCHIN, 30);
            setSpinSpeed(1.2f);
            playFilm(FilmKind.DEATH);
            fx(FxKind.IMPACT_FRAME, c, 5, 0);
            playLoud(SoundEvents.ENDER_DRAGON_DEATH, 40f, 0.5f);
        }
        if (t % 9 == 0 && t < DEATH_TICKS - 20) {
            Vec3 at = c.add((random.nextDouble() - 0.5) * R * 1.6, (random.nextDouble() - 0.5) * R * 1.6, (random.nextDouble() - 0.5) * R * 1.6);
            fx(FxKind.POOF, at, 10 + random.nextInt(14), 0);
            playLoud(SoundEvents.GENERIC_EXPLODE.value(), 14f, 0.5f + random.nextFloat() * 0.4f);
        }
        if (t % 40 == 0) fx(FxKind.SHAKE, c, 1.2, 30);
        if (t == DEATH_TICKS - 8) {
            fx(FxKind.BLAST, c, 70, 0);
            fx(FxKind.IMPACT_FRAME, c, 6, 0);
            fx(FxKind.SHOCKWAVE, new Vec3(c.x, groundBelow(level), c.z), 180, 90);
            dev.ramiel.world.Shockwave.launch(level, new Vec3(c.x, groundBelow(level), c.z), 180, 90, 1.4, true, this);
            playLoud(ModRegistry.SND_BOOM.get(), 80f, 0.6f);
        }
        if (t >= DEATH_TICKS) {
            dropRewards(level);
            this.remove(RemovalReason.KILLED);
        }
    }

    private void dropRewards(ServerLevel level) {
        Vec3 ground = new Vec3(getX(), groundBelow(level) + 1, getZ());
        int cores = 2 + random.nextInt(3);
        for (int i = 0; i < cores; i++) {
            ItemEntity item = new ItemEntity(level, ground.x + random.nextGaussian() * 3, ground.y + 2, ground.z + random.nextGaussian() * 3,
                    new ItemStack(ModRegistry.ANGEL_CORE.get()));
            item.setUnlimitedLifetime();
            item.setGlowingTag(true);
            level.addFreshEntity(item);
        }
        ExperienceOrb.award(level, ground, 20000);
    }

    // ==========================================================================================
    // Things that make a boss a boss
    // ==========================================================================================
    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity other) {}
    @Override public void push(double x, double y, double z) {}
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean canBeAffected(MobEffectInstance effect) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public void checkDespawn() {}
    @Override public boolean isInvulnerableTo(DamageSource source) { return source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.IN_WALL) || super.isInvulnerableTo(source); }
}
