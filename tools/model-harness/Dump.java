import dev.ramiel.Form;
import dev.ramiel.client.model.*;
import java.io.*;
import java.util.*;

/** Prints bricks as JSON lines: {"tag":..., "bricks":[[x,y,z,qx,qy,qz,qw,h,bw,bd,tw,td,r,g,b,glow,alpha],...]} */
public class Dump {
    static String bricks(Brick[] b) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Brick k : b) {
            if (!k.visible()) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append(String.format(Locale.ROOT, "[%.4f,%.4f,%.4f,%.5f,%.5f,%.5f,%.5f,%.4f,%.4f,%.4f,%.4f,%.4f,%.3f,%.3f,%.3f,%.2f,%.2f]",
                k.x, k.y, k.z, k.qx, k.qy, k.qz, k.qw, k.h, k.bw, k.bd, k.tw, k.td, k.r, k.g, k.b, k.glow, k.alpha));
        }
        return sb.append(']').toString();
    }
    public static void main(String[] a) throws Exception {
        RamielModel m = RamielModel.shared();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]));
        // mode "static": each form at a given charge and time;  mode "morph": from,to, N frames
        for (int i = 1; i < a.length; i++) {
            String[] p = a[i].split(":");
            if (p[0].equals("static")) {            // static:FORM:charge:time:drill
                Form f = Form.valueOf(p[1]);
                float charge = Float.parseFloat(p[2]), time = Float.parseFloat(p[3]), drill = p.length > 4 ? Float.parseFloat(p[4]) : 0f;
                Brick[] b = m.compute(f, f, 1f, time, charge, drill, 1f);
                out.println("{\"tag\":\"" + a[i] + "\",\"bricks\":" + bricks(b) + "}");
            } else {                                 // morph:FROM:TO:frames:charge:time
                Form f = Form.valueOf(p[1]), t = Form.valueOf(p[2]);
                int n = Integer.parseInt(p[3]);
                float charge = Float.parseFloat(p[4]), time = Float.parseFloat(p[5]);
                for (int k = 0; k < n; k++) {
                    float mo = k / (float) (n - 1);
                    Brick[] b = m.compute(f, t, mo, time + k, charge, 0f, 1f);
                    out.println("{\"tag\":\"" + p[1] + "->" + p[2] + " m=" + String.format(Locale.ROOT, "%.2f", mo) + "\",\"bricks\":" + bricks(b) + "}");
                }
            }
        }
        out.close();
    }
}
