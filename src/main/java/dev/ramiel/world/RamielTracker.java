package dev.ramiel.world;

import dev.ramiel.entity.RamielEntity;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** The Ramiels currently loaded on the server, so mob AI can find one without scanning entities. */
public final class RamielTracker {
    private RamielTracker() {}

    private static final List<RamielEntity> ACTIVE = new CopyOnWriteArrayList<>();

    public static void add(RamielEntity r) { if (!ACTIVE.contains(r)) ACTIVE.add(r); }
    public static void remove(RamielEntity r) { ACTIVE.remove(r); }
    public static List<RamielEntity> all() { return ACTIVE; }
    public static boolean any(ServerLevel level) {
        for (RamielEntity r : ACTIVE) if (r.level() == level && r.isAlive()) return true;
        return false;
    }

    /** Nearest living Ramiel in this level within {@code range} blocks (horizontal), or null. */
    public static RamielEntity nearest(net.minecraft.world.level.Level level, Vec3 pos, double range) {
        RamielEntity best = null;
        double bestD = range * range;
        for (RamielEntity r : ACTIVE) {
            if (r.level() != level || !r.isAlive()) continue;
            double dx = r.getX() - pos.x, dz = r.getZ() - pos.z;
            double d = dx * dx + dz * dz;
            if (d < bestD) { bestD = d; best = r; }
        }
        return best;
    }
}
