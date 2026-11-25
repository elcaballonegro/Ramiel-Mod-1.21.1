import dev.ramiel.Form;
import dev.ramiel.client.model.*;

public class Check {
    public static void main(String[] a) {
        RamielModel m = RamielModel.shared();
        Form[] forms = Form.values();
        int bad = 0; double worstAll = 0; String worstWhere = "";
        for (Form f : forms) for (Form t : forms) {
            if (f == t) continue;
            float[][] prev = null; double worst = 0; int worstIdx = -1; float worstM = 0;
            for (int s = 0; s <= 100; s++) {
                float mo = s / 100f;
                Brick[] b = m.compute(f, t, mo, 40f + s, 0.5f, 3f, 1f);
                float[][] cur = new float[b.length][4];
                for (int i = 0; i < b.length; i++) {
                    Brick k = b[i];
                    if (Float.isNaN(k.x + k.y + k.z + k.qx + k.qy + k.qz + k.qw + k.h + k.bw + k.bd + k.tw + k.td + k.alpha)) { bad++; System.out.println("NaN " + f + "->" + t + " m=" + mo + " brick " + i); }
                    // a visible brick's far tip: centre + half length along its axis (approximated by centre + h/2 in length)
                    cur[i] = new float[]{k.x, k.y, k.z, k.visible() ? 1f : 0f};
                    if (prev != null && prev[i][3] > 0 && cur[i][3] > 0) {
                        double d = Math.sqrt(sq(k.x - prev[i][0]) + sq(k.y - prev[i][1]) + sq(k.z - prev[i][2]));
                        if (d > worst) { worst = d; worstIdx = i; worstM = mo; }
                    }
                }
                prev = cur;
            }
            if (worst > worstAll) { worstAll = worst; worstWhere = f + "->" + t + " brick " + worstIdx + " at m=" + worstM; }
            if (worst > 0.25) System.out.printf("  big step %s->%s: %.3f R in one 1%% step (brick %d, m=%.2f)%n", f, t, worst, worstIdx, worstM);
        }
        System.out.printf("pairs checked: %d, NaNs: %d, largest single-step move: %.3f R (%s)%n", forms.length * (forms.length - 1), bad, worstAll, worstWhere);
    }
    static double sq(double v) { return v * v; }
}
