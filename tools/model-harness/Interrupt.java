import dev.ramiel.Form;
import dev.ramiel.client.model.*;

/** Simulates what the client sees when the form pair changes, including the entity's morph glide (old value -> 0 over one tick). */
public class Interrupt {
    static float[][] snap(Brick[] b) { float[][] o = new float[b.length][3]; for (int i = 0; i < b.length; i++) { o[i][0] = b[i].x; o[i][1] = b[i].y; o[i][2] = b[i].z; } return o; }
    static double worst(float[][] a, float[][] c, Brick[] bc) {
        double w = 0; for (int i = 0; i < a.length; i++) { if (!bc[i].visible()) continue; double d = Math.sqrt(Math.pow(a[i][0]-c[i][0],2)+Math.pow(a[i][1]-c[i][1],2)+Math.pow(a[i][2]-c[i][2],2)); w = Math.max(w, d); } return w; }

    static void run(String title, Form f1, Form t1, float stopAt, Form f2, Form t2, boolean continuity) {
        RamielModel m = RamielModel.shared();
        float[][] prev = null; double worstAcross = 0; int frames = 0;
        // 1) first transformation up to stopAt (20 frames), morph rises
        float t = 0;
        for (int i = 0; i <= 20; i++) { float mo = stopAt * i / 20f; Brick[] b = continuity ? m.computeFor(7, f1, t1, mo, t++, 0, 0, 1) : m.compute(f1, t1, mo, t++, 0, 0, 1); prev = snap(b); }
        // 2) the new pair arrives: morph glides stopAt -> 0 across one tick (6 frames), then restarts 0, 0.02, 0.04...
        float[] seq = new float[6 + 8];
        for (int i = 0; i < 6; i++) seq[i] = stopAt * (1 - (i + 1) / 6f);
        for (int i = 0; i < 8; i++) seq[6 + i] = 0.02f * (i + 1);
        for (float mo : seq) {
            Brick[] b = continuity ? m.computeFor(7, f2, t2, mo, t++, 0, 0, 1) : m.compute(f2, t2, mo, t++, 0, 0, 1);
            double w = worst(prev, snap(b), b); if (frames++ < 7) worstAcross = Math.max(worstAcross, w); prev = snap(b);
        }
        System.out.printf("  %-58s biggest jump in the 7 frames around the switch: %.3f R%n", title + (continuity ? "  [with memory]" : "  [naive]"), worstAcross);
    }
    public static void main(String[] a) {
        for (boolean c : new boolean[]{false, true}) {
            run("interrupted: DORMANT->LANCE at 50%, then LANCE->URCHIN", Form.DORMANT, Form.LANCE, 0.5f, Form.LANCE, Form.URCHIN, c);
            run("interrupted: DORMANT->CROSS at 70%, then CROSS->DORMANT", Form.DORMANT, Form.CROSS, 0.7f, Form.CROSS, Form.DORMANT, c);
            run("normal: LANCE finished (100%), then LANCE->DORMANT", Form.DORMANT, Form.LANCE, 1.0f, Form.LANCE, Form.DORMANT, c);
        }
    }
}
