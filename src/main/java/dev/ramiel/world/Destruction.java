package dev.ramiel.world;

import cyou.rimuru.rimulib.mc1211.api.CarveApi;
import dev.ramiel.RamielConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Everything that changes terrain.
 *
 *  - craterCut / crater : big bowls, built with Rimulib's CarveApi (fast, one-tick landing).
 *  - launchDebris       : picks surface blocks and throws them as real falling-block entities,
 *                         with a push along the beam direction ("force physics").
 *  - scorchBurst        : a small hand-made crater for the many little hits of sweeping beams.
 *
 * All sizes are multiplied by the "destruction" setting, so 0 makes Ramiel harmless to the world.
 */
public final class Destruction {
    private Destruction() {}

    public static final int UNLOADED = Integer.MIN_VALUE;

    /** Y of the top face of the highest solid block in a column, or UNLOADED. Never loads chunks. */
    public static int columnTop(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return UNLOADED;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    // ------------------------------------------------------------------------------------------
    // Craters (Rimulib)
    // ------------------------------------------------------------------------------------------

    /**
     * Build (but do not start) a bowl-shaped crater. Call {@code .prepare()} early and {@code .tick()}
     * at the exact moment it should land, or just {@code .start()} for "now".
     * Returns null when destruction is switched off.
     */
    public static CarveApi.Cut craterCut(ServerLevel level, Vec3 at, double radius, double depth) {
        double k = RamielConfig.destruction();
        if (k <= 0.001) return null;
        final double r = Math.max(3.0, radius * k);
        final double d = depth * Math.min(k, 1.6);
        final double r2 = r * r;
        final Vec3 centre = at;

        CarveApi.Floor bowl = (x, z) -> {
            double dx = x + 0.5 - centre.x, dz = z + 0.5 - centre.z;
            double dist2 = dx * dx + dz * dz;
            if (dist2 > r2) return CarveApi.UNTOUCHED;
            int top = columnTop(level, x, z);
            if (top == UNLOADED) return CarveApi.UNTOUCHED;
            double f = 1.0 - dist2 / r2;                           // 1 in the middle, 0 at the rim
            double rough = 1.0 + 0.14 * Mth.sin(x * 0.37f) * Mth.cos(z * 0.41f);
            return top - 1 - (int) Math.round(d * f * rough);
        };
        CarveApi.Surface scorch = (x, y, z, was) -> scorchState(x, y, z, centre, r);

        return CarveApi.flatten(level, centre, r, bowl, scorch)
                .named("Ramiel crater").keepBlocks().perTick(4).lazy();
    }

    /** Debris + an immediate crater. */
    public static void crater(ServerLevel level, Vec3 at, double radius, double depth, Vec3 force) {
        CarveApi.Cut cut = craterCut(level, at, radius, depth);
        if (cut == null) return;
        launchDebris(level, at, radius, force, 90);
        cut.start();
    }

    /** What the freshly exposed floor of a crater turns into: hot in the middle, ash toward the rim. */
    private static BlockState scorchState(int x, int y, int z, Vec3 centre, double radius) {
        double d = Math.hypot(x + 0.5 - centre.x, z + 0.5 - centre.z) / radius;       // 0 centre .. 1 rim
        double r = (hash(x, y, z) & 1023) / 1024.0;
        if (d < 0.35) {
            return r < 0.45 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                    : r < 0.8 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState();
        }
        if (d < 0.7) {
            return r < 0.15 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                    : r < 0.55 ? Blocks.BLACKSTONE.defaultBlockState()
                    : r < 0.8 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
        }
        return r < 0.3 ? Blocks.COARSE_DIRT.defaultBlockState()
                : r < 0.5 ? Blocks.GRAVEL.defaultBlockState()
                : r < 0.65 ? Blocks.BLACKSTONE.defaultBlockState() : null;          // null = keep the block
    }

    // ------------------------------------------------------------------------------------------
    // Debris physics
    // ------------------------------------------------------------------------------------------

    /**
     * Throw surface blocks around {@code at} as falling-block entities.
     *
     * @param force    extra push added to every block, e.g. the beam direction (zero for a plain blast)
     * @param maxCount hard cap, before the "block physics" setting is applied
     */
    public static void launchDebris(ServerLevel level, Vec3 at, double radius, Vec3 force, int maxCount) {
        double amount = RamielConfig.debris() * RamielConfig.destruction();
        int count = (int) Math.min(maxCount * Math.min(amount, 2.5), radius * 0.9 * amount);
        if (count <= 0) return;
        RandomSource rnd = level.random;
        Vec3 push = force.lengthSqr() > 1.0e-6 ? force.normalize() : Vec3.ZERO;
        for (int i = 0; i < count; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(rnd.nextDouble()) * radius;
            int x = Mth.floor(at.x + Math.cos(ang) * dist);
            int z = Mth.floor(at.z + Math.sin(ang) * dist);
            int top = columnTop(level, x, z);
            if (top == UNLOADED) continue;
            BlockPos pos = new BlockPos(x, top - 1, z);
            BlockState state = level.getBlockState(pos);
            if (!canThrow(level, pos, state)) continue;

            FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
            block.dropItem = false;
            double out = 0.25 + rnd.nextDouble() * 0.55 * (1.0 - 0.5 * dist / radius);
            Vec3 v = new Vec3(Math.cos(ang) * out, 0.55 + rnd.nextDouble() * 0.95, Math.sin(ang) * out)
                    .add(push.scale(0.7 + rnd.nextDouble() * 0.9));
            block.setDeltaMovement(v);
            block.hurtMarked = true;
        }
    }

    private static boolean canThrow(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir()
                && !(state.getBlock() instanceof LiquidBlock)
                && !state.hasBlockEntity()
                && !state.is(BlockTags.WITHER_IMMUNE)
                && state.getDestroySpeed(level, pos) >= 0.0f;
    }

    // ------------------------------------------------------------------------------------------
    // Small hand-made crater (sweeping beams hit the ground dozens of times)
    // ------------------------------------------------------------------------------------------

    /** Remove a ball of terrain around {@code at}, scorch its edge and fling a few blocks along {@code dir}. */
    public static void scorchBurst(ServerLevel level, Vec3 at, double radius, Vec3 dir) {
        double k = RamielConfig.destruction();
        if (k <= 0.001) return;
        radius = Math.min(10.0, radius * Math.min(k, 2.0));
        int r = (int) Math.ceil(radius);
        BlockPos centre = BlockPos.containing(at);
        RandomSource rnd = level.random;
        int throwBudget = (int) (6 * RamielConfig.debris());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        final int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > radius) continue;
                    p.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.hasChunkAt(p)) continue;
                    BlockState st = level.getBlockState(p);
                    if (st.isAir() || st.getBlock() instanceof LiquidBlock) continue;
                    if (st.hasBlockEntity() || st.is(BlockTags.WITHER_IMMUNE) || st.getDestroySpeed(level, p) < 0) continue;

                    double f = d / radius;
                    if (f < 0.72) {
                        if (throwBudget > 0 && rnd.nextInt(14) == 0) {
                            throwBudget--;
                            FallingBlockEntity fb = FallingBlockEntity.fall(level, p.immutable(), st);
                            fb.dropItem = false;
                            Vec3 v = dir.scale(0.8).add((rnd.nextDouble() - 0.5) * 0.5, 0.4 + rnd.nextDouble() * 0.6, (rnd.nextDouble() - 0.5) * 0.5);
                            fb.setDeltaMovement(v);
                            fb.hurtMarked = true;
                        } else {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
                        }
                    } else if (rnd.nextFloat() < 0.65f) {
                        level.setBlock(p, rnd.nextFloat() < 0.18f ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), flags);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    private static int hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }
}
