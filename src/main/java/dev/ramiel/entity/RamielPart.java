package dev.ramiel.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One box of Ramiel's body. The body is six stacked boxes that roughly follow the octahedron, the
 * same trick the Ender Dragon uses. Arrows and swords collide with these boxes; the exact hit
 * (body, core, or empty corner of the box) is decided afterwards by {@link RamielHitTest}.
 */
public class RamielPart extends PartEntity<RamielEntity> {
    private final EntityDimensions size;
    public final int index;

    public RamielPart(RamielEntity parent, int index, float width, float height) {
        super(parent);
        this.index = index;
        this.size = EntityDimensions.scalable(width, height);
        this.refreshDimensions();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override public boolean isPickable() { return true; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !this.isInvulnerableTo(source) && getParent().hurtPart(this, source, amount);
    }

    @Override public boolean is(Entity other) { return this == other || getParent() == other; }

    @Override public EntityDimensions getDimensions(Pose pose) { return size; }

    @Override public boolean shouldBeSaved() { return false; }
}
