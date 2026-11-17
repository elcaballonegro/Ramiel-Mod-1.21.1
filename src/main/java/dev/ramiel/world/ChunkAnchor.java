package dev.ramiel.world;

import dev.ramiel.RamielConfig;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Keeps the chunks around Ramiel loaded so the boss keeps working while nobody is close.
 * It only releases chunks it forced itself, so it never undoes a player's /forceload.
 */
public final class ChunkAnchor {
    private static final int RADIUS = 2;                  // 5 x 5 chunks
    private final Set<Long> owned = new HashSet<>();
    private ServerLevel level;

    public void update(ServerLevel level, BlockPos centre) {
        this.level = level;
        if (!RamielConfig.chunkAnchor()) {
            releaseAll();
            return;
        }
        int cx = centre.getX() >> 4, cz = centre.getZ() >> 4;
        Set<Long> want = new HashSet<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                want.add(ChunkPos.asLong(cx + dx, cz + dz));
            }
        }
        for (long c : want) {
            if (!owned.contains(c) && level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true)) {
                owned.add(c);
            }
        }
        Iterator<Long> it = owned.iterator();
        while (it.hasNext()) {
            long c = it.next();
            if (!want.contains(c)) {
                level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
                it.remove();
            }
        }
    }

    public void releaseAll() {
        if (level != null) {
            for (long c : owned) level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
        }
        owned.clear();
    }
}
