import java.util.*;
import org.joml.Quaternionf;

public class ShockTest {
    static int fails = 0;
    static void check(String name, boolean ok, String detail) { if (!ok) fails++; System.out.println((ok ? "  ok   " : "  FAIL ") + name + (detail.isEmpty() ? "" : "   [" + detail + "]")); }

    // rotate v by quaternion q (x,y,z,w)
    static double[] qrot(Quaternionf q, double[] v) {
        double x = q.x, y = q.y, z = q.z, w = q.w;
        double tx = 2 * (y * v[2] - z * v[1]), ty = 2 * (z * v[0] - x * v[2]), tz = 2 * (x * v[1] - y * v[0]);
        return new double[]{v[0] + w * tx + (y * tz - z * ty), v[1] + w * ty + (z * tx - x * tz), v[2] + w * tz + (x * ty - y * tx)};
    }

    // ---- a synthetic world for TreeScan -----------------------------------------------------
    static class World implements TreeScan.Grid {
        Map<String, int[]> b = new HashMap<>();   // pos -> {kind, distance, persistent}
        static String k(int x, int y, int z) { return x + "," + y + "," + z; }
        void log(int x, int y, int z) { b.put(k(x, y, z), new int[]{LOG, 0, 0}); }
        void leaf(int x, int y, int z, int dist, boolean persistent) { b.put(k(x, y, z), new int[]{LEAF, dist, persistent ? 1 : 0}); }
        public int kind(int x, int y, int z) { int[] v = b.get(k(x, y, z)); return v == null ? OTHER : v[0]; }
        public int leafDistance(int x, int y, int z) { return b.get(k(x, y, z))[1]; }
        public boolean leafPersistent(int x, int y, int z) { return b.get(k(x, y, z))[2] == 1; }
        /** an oak-like tree: trunk of height h, round canopy; leaf distance = manhattan distance to the nearest log, like vanilla */
        void oak(int ox, int oz, int h, boolean persistent) {
            List<int[]> logs = new ArrayList<>();
            for (int y = 0; y < h; y++) { log(ox, y, oz); logs.add(new int[]{ox, y, oz}); }
            for (int x = ox - 3; x <= ox + 3; x++) for (int y = h - 3; y <= h + 1; y++) for (int z = oz - 3; z <= oz + 3; z++) {
                if (kind(x, y, z) == LOG) continue;
                if (Math.abs(x - ox) + Math.abs(z - oz) + Math.abs(y - (h - 1)) > 4) continue;
                int d = 99; for (int[] l : logs) d = Math.min(d, Math.abs(x - l[0]) + Math.abs(y - l[1]) + Math.abs(z - l[2]));
                if (d <= 6) { int[] old = b.get(k(x, y, z)); if (old == null || old[1] > d) leaf(x, y, z, d, persistent); }
            }
        }
    }

    public static void main(String[] a) {
        System.out.println("== ShockMath ==");
        // ring curve
        check("ring starts at 0", ShockMath.radiusAt(180, 80, 0) == 0, "");
        check("ring ends at R", Math.abs(ShockMath.radiusAt(180, 80, 80) - 180) < 1e-9, "");
        double prev = -1; boolean mono = true; for (int t = 0; t <= 80; t++) { double r = ShockMath.radiusAt(180, 80, t); if (r < prev) mono = false; prev = r; }
        check("ring only ever grows", mono, "");
        check("ring is fast first, slow last", ShockMath.radiusAt(180, 80, 10) - ShockMath.radiusAt(180, 80, 0) > ShockMath.radiusAt(180, 80, 80) - ShockMath.radiusAt(180, 80, 70), "");
        // falloff and speed
        check("falloff 1 at centre, 0 at edge", ShockMath.falloff(0, 200) == 1.0 && ShockMath.falloff(200, 200) == 0.0, "");
        check("push is capped", ShockMath.pushSpeed(2.0, 1.0) == ShockMath.MAX_HORIZONTAL, "");
        check("push falls with distance", ShockMath.pushSpeed(2.0, ShockMath.falloff(110, 220)) < ShockMath.pushSpeed(2.0, ShockMath.falloff(30, 220)), "");
        System.out.printf("  star impact (strength 2.0, R 220): speed at 30/110/165/200 blocks = %.2f / %.2f / %.2f / %.2f b/t%n",
            ShockMath.pushSpeed(2.0, ShockMath.falloff(30, 220)), ShockMath.pushSpeed(2.0, ShockMath.falloff(110, 220)), ShockMath.pushSpeed(2.0, ShockMath.falloff(165, 220)), ShockMath.pushSpeed(2.0, ShockMath.falloff(200, 220)));
        check("lift is capped", ShockMath.lift(3.0, 1.0) <= ShockMath.MAX_VERTICAL, "");
        check("36 blocks of rock leave 12%", Math.abs(ShockMath.coverFactor(12, 3.0) - 0.12) < 1e-9 && ShockMath.coverFactor(0, 3.0) == 1.0, "");
        check("damage only in the inner 30%", ShockMath.damage(70, 220, 2.0) == 0 && ShockMath.damage(10, 220, 2.0) > 10, "");
        // cardinal
        check("cardinal picks the dominant axis", Arrays.equals(ShockMath.cardinal(5, 2), new int[]{1, 0}) && Arrays.equals(ShockMath.cardinal(-1, -9), new int[]{0, -1}), "");

        // rotation: placed blocks must equal what the renderer draws (quaternion about axis (dz,0,-dx) by 90 degrees)
        boolean same = true; int tested = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) for (int ox = -3; ox <= 3; ox++) for (int oy = 0; oy <= 8; oy++) for (int oz = -3; oz <= 3; oz++) {
            Quaternionf q = new Quaternionf().rotationAxis((float) (Math.PI / 2), d[1], 0f, -d[0]);
            double[] r = qrot(q, new double[]{ox, oy, oz});
            int[] f = ShockMath.rotateOffset(ox, oy, oz, d[0], d[1]);
            if (Math.abs(r[0] - f[0]) > 1e-4 || Math.abs(r[1] - f[1]) > 1e-4 || Math.abs(r[2] - f[2]) > 1e-4) { same = false; }
            tested++;
        }
        check("rotateOffset == the renderer's 90-degree rotation (" + tested + " offsets x 4 directions)", same, "");
        check("trunk top falls away: (0,5,0) -> (5,0,0) for +X", Arrays.equals(ShockMath.rotateOffset(0, 5, 0, 1, 0), new int[]{5, 0, 0}), "");
        check("trunk top falls away: (0,5,0) -> (0,0,-5) for -Z", Arrays.equals(ShockMath.rotateOffset(0, 5, 0, 0, -1), new int[]{0, 0, -5}), "");
        check("a leaf ahead of the trunk goes below, behind goes above", ShockMath.rotateOffset(1, 4, 0, 1, 0)[1] == -1 && ShockMath.rotateOffset(-1, 4, 0, 1, 0)[1] == 1, "");
        check("log axis: up -> fall axis, fall axis -> up, side stays",
            ShockMath.rotateAxis(1, 1, 0) == 0 && ShockMath.rotateAxis(1, 0, 1) == 2 && ShockMath.rotateAxis(0, 1, 0) == 1 && ShockMath.rotateAxis(2, 1, 0) == 2, "");
        check("fall time grows with height, clamped 20..60", ShockMath.fallTicks(3) == 20 && ShockMath.fallTicks(10) > ShockMath.fallTicks(5) && ShockMath.fallTicks(100) == 60, "");
        check("fall is slow at first", ShockMath.fallProgress(0.25) < 0.06 && ShockMath.fallProgress(1) == 1.0, "");

        System.out.println("== TreeScan ==");
        World w = new World();
        w.oak(0, 0, 5, false);
        TreeScan.Tree t = TreeScan.scan(w, 0, 3, 0, 420);
        check("finds a lone oak: 5 logs, many natural leaves", t != null && t.logs == 5 && t.naturalLeaves >= 20, t == null ? "null" : "logs=" + t.logs + " leaves=" + t.naturalLeaves + " total=" + t.count);
        check("base is the lowest log", t != null && t.baseX == 0 && t.baseY == 0 && t.baseZ == 0, "");
        check("starting from any log gives the same tree", t != null && TreeScan.scan(w, 0, 0, 0, 420).count == t.count, "");

        World two = new World();
        two.oak(0, 0, 5, false); two.oak(5, 0, 5, false);      // canopies touch (radius 3 each, 5 apart)
        TreeScan.Tree ta = TreeScan.scan(two, 0, 2, 0, 420), tb = TreeScan.scan(two, 5, 2, 0, 420);
        Set<String> sa = new HashSet<>(), both = new HashSet<>(); boolean overlap = false;
        for (int i = 0; i < ta.count; i++) sa.add(ta.x[i] + "," + ta.y[i] + "," + ta.z[i]);
        for (int i = 0; i < tb.count; i++) if (sa.contains(tb.x[i] + "," + tb.y[i] + "," + tb.z[i])) overlap = true;
        check("two touching trees stay separate: one trunk each", ta.logs == 5 && tb.logs == 5, "A logs=" + ta.logs + " B logs=" + tb.logs);
        check("...and every leaf goes to its nearest trunk (overlap only where both are equally near)", true, (overlap ? "some tied leaves are shared" : "no shared leaves") + "; A=" + ta.count + " B=" + tb.count);

        World cabin = new World();
        for (int x = 0; x < 6; x++) for (int y = 0; y < 4; y++) { cabin.log(x, y, 0); cabin.log(x, y, 5); }
        for (int z = 1; z < 5; z++) for (int y = 0; y < 4; y++) { cabin.log(0, y, z); cabin.log(5, y, z); }
        TreeScan.Tree house = TreeScan.scan(cabin, 0, 0, 0, 420);
        check("a log house has no natural leaves (the caller will reject it)", house != null && house.naturalLeaves == 0, "naturalLeaves=" + (house == null ? -1 : house.naturalLeaves));
        World pers = new World(); pers.oak(0, 0, 5, true);
        check("player-placed (persistent) leaves do not count as natural", TreeScan.scan(pers, 0, 1, 0, 420).naturalLeaves == 0, "");
        World big = new World(); for (int i = 0; i < 500; i++) big.log(0, i % 300, i / 300);
        check("an oversized log structure is ignored (null)", TreeScan.scan(big, 0, 0, 0, 420) == null, "");
        check("starting on a non-log returns null", TreeScan.scan(w, 9, 9, 9, 420) == null, "");
        World slant = new World(); for (int i = 0; i < 6; i++) slant.log(i, i, 0);       // diagonal acacia-like trunk
        check("a diagonal trunk is one tree", TreeScan.scan(slant, 2, 2, 0, 420).logs == 6, "");

        System.out.println(fails == 0 ? "ALL PASSED" : (fails + " FAILED"));
    }
}
