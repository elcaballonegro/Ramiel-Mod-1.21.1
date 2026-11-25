package dev.ramiel.entity;

import dev.ramiel.ModRegistry;
import dev.ramiel.world.ShockMath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A whole tree falling over.
 *
 * The shockwave removes a tree's blocks from the world and creates ONE of these instead. It carries the blocks
 * (offsets from the base block, plus a palette of block states) and the direction to fall. The client draws the
 * tree turning 90 degrees about the centre of its base block ({@code ToppleRenderer}); when the animation ends the
 * server puts the blocks back down rotated into the fallen position ({@link ShockMath#rotateOffset}, the very same
 * rotation) and removes the entity.
 *
 * The tree travels in one synced {@link CompoundTag} ({@code D_TREE}): palette, block data, direction, duration. The same tag
 * is saved with the entity, so a tree that is mid-fall when the world closes finishes falling after a reload.
 *
 * Block data layout: 4 ints per block = offset x, y, z from the base block, then the palette index.
 */
public class ToppleEntity extends Entity {
    private static final EntityDataAccessor<CompoundTag> D_TREE =
            SynchedEntityData.defineId(ToppleEntity.class, EntityDataSerializers.COMPOUND_TAG);

    // parsed copy of D_TREE (both sides)
    private boolean parsed;
    private int dirX = 1, dirZ = 0, duration = 30;
    private int[] data = new int[0];
    private BlockState[] palette = new BlockState[0];

    public ToppleEntity(EntityType<? extends ToppleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * Server side. {@code ox, oy, oz} are offsets from {@code base}; the entity stands at the bottom centre of the base block.
     * The caller removes the blocks from the world and then adds the entity.
     */
    public static ToppleEntity create(ServerLevel level, BlockPos base, int dx, int dz,
                                      int[] ox, int[] oy, int[] oz, BlockState[] states, int height) {
        ToppleEntity e = new ToppleEntity(ModRegistry.TOPPLE.get(), level);
        e.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);

        List<BlockState> pal = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        int[] blocks = new int[states.length * 4];
        for (int i = 0; i < states.length; i++) {
            Integer id = index.get(states[i]);
            if (id == null) { id = pal.size(); index.put(states[i], id); pal.add(states[i]); }
            blocks[4 * i] = ox[i]; blocks[4 * i + 1] = oy[i]; blocks[4 * i + 2] = oz[i]; blocks[4 * i + 3] = id;
        }
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (BlockState s : pal) list.add(NbtUtils.writeBlockState(s));
        tag.put("Palette", list);
        tag.putIntArray("Blocks", blocks);
        tag.putInt("DX", dx);
        tag.putInt("DZ", dz);
        tag.putInt("Ticks", ShockMath.fallTicks(height));
        e.entityData.set(D_TREE, tag);
        return e;
    }

    // ---- data -----------------------------------------------------------------------------------
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(D_TREE, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (D_TREE.equals(key)) parsed = false;
        super.onSyncedDataUpdated(key);
    }

    private void parse() {
        parsed = true;
        CompoundTag tag = entityData.get(D_TREE);
        dirX = tag.getInt("DX");
        dirZ = tag.getInt("DZ");
        duration = Math.max(8, tag.getInt("Ticks"));
        data = tag.getIntArray("Blocks");
        ListTag list = tag.getList("Palette", Tag.TAG_COMPOUND);
        palette = new BlockState[list.size()];
        var blocks = level().holderLookup(Registries.BLOCK);
        for (int i = 0; i < palette.length; i++) palette[i] = NbtUtils.readBlockState(blocks, list.getCompound(i));
    }

    /** False until the tree data has arrived (it comes right after the entity itself). */
    public boolean ready() {
        if (!parsed) parse();
        return data.length >= 4 && palette.length > 0;
    }

    public int count() { return data.length / 4; }
    public int ox(int i) { return data[4 * i]; }
    public int oy(int i) { return data[4 * i + 1]; }
    public int oz(int i) { return data[4 * i + 2]; }
    public BlockState state(int i) { return palette[data[4 * i + 3]]; }
    public int dirX() { return dirX; }
    public int dirZ() { return dirZ; }
    public int duration() { return duration; }

    // ---- falling and landing ----------------------------------------------------------------
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel server)) return;
        if (!ready()) {
            if (tickCount > 40) discard();                       // the data never came: nothing to land
            return;
        }
        if (tickCount == 1) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 3.0f, 0.55f + random.nextFloat() * 0.2f);
        }
        if (tickCount >= duration) land(server);
    }

    private void land(ServerLevel level) {
        BlockPos base = BlockPos.containing(getX(), getY() + 0.5, getZ());
        double tipAlong = -1e9;
        BlockPos tip = base;
        for (int i = 0; i < count(); i++) {
            BlockState state = state(i);
            int[] r = ShockMath.rotateOffset(ox(i), oy(i), oz(i), dirX, dirZ);
            BlockState placed = rotated(state);
            boolean ok = false;
            for (int up = 0; up <= 3 && !ok; up++) {           // blocked by terrain: try a little higher
                BlockPos p = base.offset(r[0], r[1] + up, r[2]);
                if (!level.isLoaded(p)) break;
                if (replaceable(level.getBlockState(p))) {
                    level.setBlock(p, placed, Block.UPDATE_ALL);
                    ok = true;
                    if (state.is(BlockTags.LOGS)) {
                        double along = r[0] * dirX + r[2] * dirZ;
                        if (along > tipAlong) { tipAlong = along; tip = p; }
                    }
                }
            }
            if (!ok && state.is(BlockTags.LOGS)) Block.popResource(level, base, new ItemStack(state.getBlock()));
        }
        level.playSound(null, tip.getX() + 0.5, tip.getY() + 0.5, tip.getZ() + 0.5, SoundEvents.WOOD_FALL, SoundSource.BLOCKS, 4.0f, 0.5f);
        level.sendParticles(ParticleTypes.CLOUD, tip.getX() + 0.5, tip.getY() + 0.8, tip.getZ() + 0.5, 20, 1.2, 0.2, 1.2, 0.02);
        discard();
    }

    /** What a fallen block may replace: air, plants, leaves. Never fluids or solid terrain. */
    private static boolean replaceable(BlockState there) {
        if (!there.getFluidState().isEmpty()) return false;
        return there.isAir() || there.canBeReplaced() || there.is(BlockTags.LEAVES);
    }

    /** Logs lie along the fall direction instead of standing up. */
    private BlockState rotated(BlockState s) {
        if (!s.hasProperty(RotatedPillarBlock.AXIS)) return s;
        int axis = switch (s.getValue(RotatedPillarBlock.AXIS)) { case X -> 0; case Y -> 1; case Z -> 2; };
        int out = ShockMath.rotateAxis(axis, dirX, dirZ);
        return s.setValue(RotatedPillarBlock.AXIS, out == 0 ? Direction.Axis.X : out == 1 ? Direction.Axis.Y : Direction.Axis.Z);
    }

    // ---- save, load, culling ----------------------------------------------------------------
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.put("Tree", entityData.get(D_TREE).copy()); }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Tree")) entityData.set(D_TREE, tag.getCompound("Tree"));
    }

    /** A tree is tall and the pivot is at its foot: keep it drawn while any part of it is on screen. */
    @Override public AABB getBoundingBoxForCulling() {
        return new AABB(getX() - 24, getY() - 4, getZ() - 24, getX() + 24, getY() + 48, getZ() + 24);
    }
    @Override public boolean shouldRenderAtSqrDistance(double distanceSqr) { return true; }
}
