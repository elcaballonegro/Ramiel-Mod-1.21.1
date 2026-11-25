package dev.ramiel.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Finds one whole tree starting from one of its logs. Pure Java (the world is read through {@link Grid}),
 * so it can be unit tested.
 *
 *  1. flood the connected logs (26 neighbours, so diagonal branches are included)
 *  2. add leaves outward from ALL those logs, one distance step at a time: a leaf joins only through a
 *     neighbour whose distance is exactly one less (the vanilla {@code distance} property). This keeps two
 *     trees with touching canopies apart.
 *
 * The caller decides if it is a "natural tree" ({@link Tree#naturalLeaves} and what stands under {@link Tree#baseY}).
 */
public final class TreeScan {
    private TreeScan() {}

    public interface Grid {
        int OTHER = 0, LOG = 1, LEAF = 2;
        int kind(int x, int y, int z);
        /** 1..7, only asked for leaves. */
        int leafDistance(int x, int y, int z);
        /** True for leaves placed by a player (they never decay), only asked for leaves. */
        boolean leafPersistent(int x, int y, int z);
    }

    public static final class Tree {
        public int count, logs, naturalLeaves;
        public int[] x, y, z, kind;
        /** The lowest log (ties: smallest x, then z). The tree pivots here. */
        public int baseX, baseY, baseZ;
        public int topY;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static final int[][] SIX = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** @return the tree, or null if {@code (sx, sy, sz)} is not a log or the structure has more than {@code max} blocks. */
    public static Tree scan(Grid g, int sx, int sy, int sz, int max) {
        if (g.kind(sx, sy, sz) != Grid.LOG) return null;
        List<int[]> out = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();

        // 1) logs
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sx, sy, sz});
        seen.add(key(sx, sy, sz));
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            out.add(new int[]{p[0], p[1], p[2], Grid.LOG});
            if (out.size() > max) return null;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if ((dx | dy | dz) == 0) continue;
                int nx = p[0] + dx, ny = p[1] + dy, nz = p[2] + dz;
                if (seen.contains(key(nx, ny, nz))) continue;
                if (g.kind(nx, ny, nz) == Grid.LOG) { seen.add(key(nx, ny, nz)); queue.add(new int[]{nx, ny, nz}); }
            }
        }
        int logs = out.size();

        // 2) leaves, growing outward from every log
        ArrayDeque<int[]> leaves = new ArrayDeque<>();
        for (int[] l : out) leaves.add(new int[]{l[0], l[1], l[2], 0});
        int natural = 0;
        while (!leaves.isEmpty()) {
            int[] p = leaves.poll();
            for (int[] d : SIX) {
                int nx = p[0] + d[0], ny = p[1] + d[1], nz = p[2] + d[2];
                if (seen.contains(key(nx, ny, nz))) continue;
                if (g.kind(nx, ny, nz) != Grid.LEAF) continue;
                int dist = g.leafDistance(nx, ny, nz);
                if (dist != p[3] + 1) continue;
                seen.add(key(nx, ny, nz));
                out.add(new int[]{nx, ny, nz, Grid.LEAF});
                if (!g.leafPersistent(nx, ny, nz)) natural++;
                if (out.size() > max) return null;
                leaves.add(new int[]{nx, ny, nz, dist});
            }
        }

        Tree t = new Tree();
        t.count = out.size(); t.logs = logs; t.naturalLeaves = natural;
        t.x = new int[t.count]; t.y = new int[t.count]; t.z = new int[t.count]; t.kind = new int[t.count];
        t.baseY = Integer.MAX_VALUE; t.topY = Integer.MIN_VALUE;
        for (int i = 0; i < t.count; i++) {
            int[] b = out.get(i);
            t.x[i] = b[0]; t.y[i] = b[1]; t.z[i] = b[2]; t.kind[i] = b[3];
            if (b[3] != Grid.LOG) continue;
            if (b[1] < t.baseY || (b[1] == t.baseY && (b[0] < t.baseX || (b[0] == t.baseX && b[2] < t.baseZ)))) {
                t.baseX = b[0]; t.baseY = b[1]; t.baseZ = b[2];
            }
            t.topY = Math.max(t.topY, b[1]);
        }
        return t;
    }
}
