package dev.ramiel.world;

import dev.ramiel.RamielConfig;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.entity.RamielPart;
import dev.ramiel.entity.ToppleEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The pressure wave of a huge explosion. See docs/SHOCKWAVE.md for the design and the numbers.
 *
 * {@link #launch} starts a wave. Each server tick it grows along the same curve the client draws, and everything it
 * passes is dealt with exactly once:
 *   - entities are thrown away from the blast (less behind terrain), and hurt if they are close;
 *   - trees are found, queued and turned into falling {@link ToppleEntity} trees.
 *
 * Call it with the SAME radius and duration as the matching {@code FxKind.SHOCKWAVE} so the force travels with the ring.
 * Epicentres should be at GROUND level: the vertical reach of a wave is limited, so one centred on hovering Ramiel
 * would miss everyone below it.
 */
public final class Shockwave {
    private Shockwave() {}

    private static final int MAX_TREES_PER_TICK = 6;
    private static final int MAX_TREES_PER_WAVE = 140;
    private static final int MAX_SCANS_PER_TICK = 40;
    private static final int MAX_TREE_BLOCKS = 420;
    private static final int MAX_WAVES = 24;

    private static final List<Wave> WAVES = new ArrayList<>();

    private static final class Wave {
        final ServerLevel level;
        final Vec3 centre;
        final Vec3 bias;               // horizontal; its length (0..1) is how much of the throw follows the beam
        final double radius;
        final int life;
        final double strength;
        final boolean trees;
        @Nullable final Entity cause;
        int age;
        double reached;
        int treesDone;
        final Set<Integer> hit = new HashSet<>();
        final Set<Long> seen = new HashSet<>();       // log columns already queued
        final Set<Long> felled = new HashSet<>();     // tree bases already felled
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        Wave(ServerLevel level, Vec3 centre, Vec3 bias, double radius, int life, double strength, boolean trees, @Nullable Entity cause) {
            this.level = level; this.centre = centre; this.bias = bias; this.radius = radius; this.life = life;
            this.strength = strength; this.trees = trees; this.cause = cause;
        }
    }

    // ==========================================================================================
    // Starting a wave
    // ==========================================================================================

    /**
     * @param centre   epicentre, at ground level
     * @param bias     horizontal vector pointing down-range for beam impacts (its length, 0..1, is the weight); {@link Vec3#ZERO} for a plain blast
     * @param radius   how far it reaches, in blocks
     * @param life     ticks the ring takes to get there (use the same number as the FX ring)
     * @param strength 1 = a hard shove close in; the Shockwave setting multiplies this
     * @param trees    may this wave knock trees over?
     */
    public static void launch(ServerLevel level, Vec3 centre, Vec3 bias, double radius, int life, double strength,
                              boolean trees, @Nullable Entity cause) {
        double s = strength * RamielConfig.shockwave();
        if (s <= 0.001 || radius < 8.0) return;
        boolean fell = trees && RamielConfig.treesFall() && RamielConfig.destruction() > 0.001;
        WAVES.add(new Wave(level, centre, bias, radius, Math.max(8, life), s, fell, cause));
        if (WAVES.size() > MAX_WAVES) WAVES.remove(0);
    }

    public static void launch(ServerLevel level, Vec3 centre, double radius, int life, double strength, boolean trees, @Nullable Entity cause) {
        launch(level, centre, Vec3.ZERO, radius, life, strength, trees, cause);
    }

    /** A bias vector for a beam travelling along {@code dir}: its horizontal part, scaled to {@code weight}. */
    public static Vec3 along(Vec3 dir, double weight) {
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        return len < 1e-3 ? Vec3.ZERO : new Vec3(dir.x / len * weight, 0.0, dir.z / len * weight);
    }

    // ==========================================================================================
    // Ticking (registered in ServerEvents)
    // ==========================================================================================

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (WAVES.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) return;
        for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            if (w.level != level) continue;
            w.age++;
            double previous = w.reached;
            w.reached = ShockMath.radiusAt(w.radius, w.life, w.age);
            push(w, w.reached);
            if (w.trees) {
                scanForTrees(w, previous, w.reached);
                fellQueued(w);
            }
            if (w.age >= w.life && w.queue.isEmpty()) it.remove();
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) { WAVES.clear(); }

    // ==========================================================================================
    // Throwing entities
    // ==========================================================================================

    private static boolean affects(Entity e) {
        if (e instanceof RamielEntity || e instanceof RamielPart || e instanceof ToppleEntity) return false;
        if (e.isSpectator() || e.isRemoved()) return false;
        return !(e instanceof Player p && p.getAbilities().flying);
    }

    private static void push(Wave w, double reach) {
        double vertical = 48.0 + 0.2 * w.radius;
        AABB box = new AABB(w.centre.x - reach, w.centre.y - vertical, w.centre.z - reach,
                w.centre.x + reach, w.centre.y + vertical, w.centre.z + reach);
        double biasWeight = Math.min(1.0, w.bias.length());
        Vec3 biasDir = biasWeight > 1e-6 ? w.bias.scale(1.0 / w.bias.length()) : Vec3.ZERO;

        for (Entity e : w.level.getEntitiesOfClass(Entity.class, box, Shockwave::affects)) {
            double dx = e.getX() - w.centre.x, dz = e.getZ() - w.centre.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > reach || !w.hit.add(e.getId())) continue;
            double falloff = ShockMath.falloff(d, w.radius);
            if (falloff <= 0.01) continue;
            double cover = cover(w, e);
            double f = falloff * cover;

            // direction: away from the blast, pulled down-range for beam impacts
            Vec3 radial = d < 1e-3 ? new Vec3(w.level.random.nextDouble() - 0.5, 0, w.level.random.nextDouble() - 0.5).normalize()
                                   : new Vec3(dx / d, 0, dz / d);
            Vec3 dir = radial.scale(1.0 - biasWeight).add(biasDir.scale(biasWeight));
            dir = dir.lengthSqr() < 1e-6 ? radial : dir.normalize();

            double speed = ShockMath.pushSpeed(w.strength, f);
            if (e instanceof LivingEntity living) {
                speed *= 1.0 - Mth.clamp(living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
            } else {
                speed *= 0.6;
            }
            if (e instanceof Player p) {
                if (p.isShiftKeyDown()) speed *= 0.8;
                if (p.isBlocking()) speed *= 0.6;
            }
            double lift = ShockMath.lift(w.strength, f) * (e.onGround() ? 1.0 : 0.4);

            Vec3 m = e.getDeltaMovement().add(dir.x * speed, lift, dir.z * speed);
            double horizontal = Math.hypot(m.x, m.z);
            if (horizontal > ShockMath.MAX_HORIZONTAL) m = new Vec3(m.x * ShockMath.MAX_HORIZONTAL / horizontal, m.y, m.z * ShockMath.MAX_HORIZONTAL / horizontal);
            e.setDeltaMovement(m.x, Math.min(m.y, ShockMath.MAX_VERTICAL), m.z);
            e.hurtMarked = true;          // the server sends the new velocity to the player (vanilla knockback path)
            e.hasImpulse = true;

            if (e instanceof LivingEntity living) {
                double damage = ShockMath.damage(d, w.radius, w.strength) * cover * RamielConfig.damage();
                if (damage > 0.5) living.hurt(Damage.of(w.level, DamageTypes.EXPLOSION, w.cause), (float) damage);
            }
            if (e instanceof ServerPlayer player) Sfx.boom(w.level, player.position(), (float) (0.8 + 2.5 * f));
        }
    }

    /** How much of the wave reaches the entity through terrain: a ray from the blast, counting solid blocks. */
    private static double cover(Wave w, Entity e) {
        Vec3 from = new Vec3(w.centre.x, w.centre.y + 2.0, w.centre.z);
        Vec3 delta = e.getEyePosition().subtract(from);
        double length = delta.length();
        if (length < 6.0) return 1.0;
        int steps = (int) Math.min(120, Math.floor(length / 3.0));
        int solid = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 1; i < steps; i++) {
            Vec3 p = from.add(delta.scale(i / (double) steps));
            m.set(p.x, p.y, p.z);
            if (!w.level.hasChunkAt(m)) continue;
            BlockState s = w.level.getBlockState(m);
            if (!s.isAir() && s.canOcclude()) solid++;
        }
        return ShockMath.coverFactor(solid, length / steps);
    }

    // ==========================================================================================
    // Trees
    // ==========================================================================================

    /** Samples the band the ring just crossed, one block apart, and queues the top log of every trunk column it finds. */
    private static void scanForTrees(Wave w, double from, double to) {
        if (w.queue.size() > 800) return;
        ServerLevel level = w.level;
        for (double rho = Math.max(from, 1.0); rho < to; rho += 1.0) {
            int n = (int) Math.ceil(2.0 * Math.PI * rho);
            for (int k = 0; k < n; k++) {
                double a = (k + 0.5) / n * (2.0 * Math.PI) + w.age * 0.37;
                int x = Mth.floor(w.centre.x + Math.cos(a) * rho), z = Mth.floor(w.centre.z + Math.sin(a) * rho);
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos p = new BlockPos(x, top, z);
                if (!w.seen.add(p.asLong())) continue;
                if (level.getBlockState(p).is(BlockTags.LOGS)) w.queue.add(p);
            }
        }
    }

    private static void fellQueued(Wave w) {
        int felled = 0, scans = 0;
        while (!w.queue.isEmpty() && felled < MAX_TREES_PER_TICK && scans < MAX_SCANS_PER_TICK && w.treesDone < MAX_TREES_PER_WAVE) {
            BlockPos log = w.queue.poll();
            scans++;
            if (fellTree(w, log)) { felled++; w.treesDone++; }
        }
        if (w.treesDone >= MAX_TREES_PER_WAVE) w.queue.clear();
    }

    private static boolean fellTree(Wave w, BlockPos log) {
        ServerLevel level = w.level;
        if (!level.getBlockState(log).is(BlockTags.LOGS)) return false;
        TreeScan.Tree t = TreeScan.scan(grid(level), log.getX(), log.getY(), log.getZ(), MAX_TREE_BLOCKS);
        if (t == null || t.naturalLeaves < 3) return false;                       // a log house, a stump, a mushroom: leave it alone
        BlockPos base = new BlockPos(t.baseX, t.baseY, t.baseZ);
        if (!level.getBlockState(base.below()).is(BlockTags.DIRT)) return false;  // must be standing in the ground
        if (!w.felled.add(base.asLong())) return false;

        int[] dir = ShockMath.cardinal(base.getX() + 0.5 - w.centre.x, base.getZ() + 0.5 - w.centre.z);
        int n = t.count;
        int[] ox = new int[n], oy = new int[n], oz = new int[n];
        BlockState[] states = new BlockState[n];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            m.set(t.x[i], t.y[i], t.z[i]);
            states[i] = level.getBlockState(m);
            ox[i] = t.x[i] - base.getX(); oy[i] = t.y[i] - base.getY(); oz[i] = t.z[i] - base.getZ();
        }
        // take the tree out of the world (leaves first): no drops, no neighbour updates; the entity carries it now
        final int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                boolean log = t.kind[i] == TreeScan.Grid.LOG;
                if (log == (pass == 1)) level.setBlock(m.set(t.x[i], t.y[i], t.z[i]), Blocks.AIR.defaultBlockState(), flags);
            }
        }
        level.addFreshEntity(ToppleEntity.create(level, base, dir[0], dir[1], ox, oy, oz, states, t.topY - t.baseY + 1));
        return true;
    }

    /** The world as TreeScan sees it. Never loads a chunk: anything unloaded counts as "not part of the tree". */
    private static TreeScan.Grid grid(ServerLevel level) {
        final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        return new TreeScan.Grid() {
            @Override public int kind(int x, int y, int z) {
                m.set(x, y, z);
                if (!level.hasChunkAt(m)) return OTHER;
                BlockState s = level.getBlockState(m);
                if (s.is(BlockTags.LOGS)) return LOG;
                return s.getBlock() instanceof LeavesBlock ? LEAF : OTHER;
            }
            @Override public int leafDistance(int x, int y, int z) {
                return level.getBlockState(m.set(x, y, z)).getValue(LeavesBlock.DISTANCE);
            }
            @Override public boolean leafPersistent(int x, int y, int z) {
                return level.getBlockState(m.set(x, y, z)).getValue(LeavesBlock.PERSISTENT);
            }
        };
    }
}
