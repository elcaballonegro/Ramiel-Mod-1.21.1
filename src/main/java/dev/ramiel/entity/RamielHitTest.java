package dev.ramiel.entity;

import dev.ramiel.Form;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Exact "what did this ray touch?" test.
 *
 * The part boxes only tell us a projectile is NEAR Ramiel. This works out whether it really hit:
 *   - the CORE (a small sphere, only while the core is exposed),
 *   - the BODY (an octahedron, scaled a little smaller while the body is opened up), or
 *   - nothing (it went through an empty corner of a box).
 */
public final class RamielHitTest {
    private RamielHitTest() {}

    public enum Type { MISS, BODY, CORE }

    /** @param t how far along the segment (0..1) the hit happened */
    public record Hit(Type type, Vec3 point, double t) {}

    public static final Hit MISS = new Hit(Type.MISS, Vec3.ZERO, 1.0);

    /** Test the segment from -> to. */
    public static Hit trace(RamielEntity r, Vec3 from, Vec3 to) {
        Vec3 centre = r.centre();
        Quaternionf inverse = Orientation.of(r, 1f).conjugate();          // world -> model space
        Vector3f p0 = inverse.transform(new Vector3f((float) (from.x - centre.x), (float) (from.y - centre.y), (float) (from.z - centre.z)));
        Vector3f p1 = inverse.transform(new Vector3f((float) (to.x - centre.x), (float) (to.y - centre.y), (float) (to.z - centre.z)));
        Vector3f d = new Vector3f(p1).sub(p0);

        // 1) core sphere (checked first: it sits inside the body)
        if (r.coreOpen()) {
            double t = raySphere(p0, d, RamielEntity.CORE_RADIUS + 0.4);
            if (t >= 0) return new Hit(Type.CORE, lerp(from, to, t), t);
        }
        // 2) body octahedron
        double t = r.form() == Form.SUNBURST ? rayWheel(p0, d, RamielEntity.R)
                : rayOctahedron(p0, d, RamielEntity.R * r.bodyScale());
        if (t >= 0) return new Hit(Type.BODY, lerp(from, to, t), t);
        return MISS;
    }

    /** Test a ray given as origin + direction + length. */
    public static Hit traceRay(RamielEntity r, Vec3 origin, Vec3 dir, double length) {
        return trace(r, origin, origin.add(dir.normalize().scale(length)));
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }

    /** First t in [0,1] where p0 + t*d is inside the sphere of radius rad around the origin, or -1. */
    private static double raySphere(Vector3f p0, Vector3f d, double rad) {
        double a = d.lengthSquared();
        if (a < 1e-9) return p0.lengthSquared() <= rad * rad ? 0 : -1;
        double b = 2.0 * p0.dot(d);
        double c = p0.lengthSquared() - rad * rad;
        if (c <= 0) return 0;                                 // starts inside
        double disc = b * b - 4 * a * c;
        if (disc < 0) return -1;
        double t = (-b - Math.sqrt(disc)) / (2 * a);
        return (t >= 0 && t <= 1) ? t : -1;
    }

    /**
     * The Sunburst wheel: a flat disc (radius ~1 R, half-height ~0.36 R) holding the little central
     * octahedron, the spokes, the ring of small octahedra and their shields. The long spikes above and
     * below are not part of it. Found by clipping the segment to a bounding sphere and stepping along it.
     */
    private static double rayWheel(Vector3f p0, Vector3f d, double R) {
        final double bound = 1.12 * R;                         // sphere that holds the disc
        double a = d.lengthSquared();
        if (a < 1e-9) return insideWheel(p0.x, p0.y, p0.z, R) ? 0 : -1;
        double b = 2.0 * p0.dot(d);
        double c = p0.lengthSquared() - bound * bound;
        double ta, tb;
        if (c <= 0) { ta = 0; tb = Math.min(1.0, (-b + Math.sqrt(Math.max(0, b * b - 4 * a * c))) / (2 * a)); }
        else {
            double disc = b * b - 4 * a * c;
            if (disc < 0) return -1;
            double sq = Math.sqrt(disc);
            ta = (-b - sq) / (2 * a); tb = (-b + sq) / (2 * a);
            if (tb < 0 || ta > 1) return -1;
            ta = Math.max(0, ta); tb = Math.min(1, tb);
        }
        final int steps = 96;
        for (int i = 0; i <= steps; i++) {
            double t = ta + (tb - ta) * i / steps;
            if (insideWheel(p0.x + d.x * t, p0.y + d.y * t, p0.z + d.z * t, R)) return t;
        }
        return -1;
    }

    private static boolean insideWheel(double x, double y, double z, double R) {
        return Math.abs(y) <= 0.36 * R && x * x + z * z <= (1.02 * R) * (1.02 * R);
    }

    /**
     * Clip the segment against the 8 planes |x|+|y|+|z| <= s of an octahedron.
     * Returns the entry t in [0,1], or -1 for no overlap.
     */
    private static double rayOctahedron(Vector3f p0, Vector3f d, double s) {
        double t0 = 0, t1 = 1;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    double a = sx * p0.x + sy * p0.y + sz * p0.z;      // plane value at start
                    double b = sx * d.x + sy * d.y + sz * d.z;         // how fast it grows
                    if (Math.abs(b) < 1e-9) {
                        if (a > s) return -1;
                    } else {
                        double t = (s - a) / b;
                        if (b > 0) t1 = Math.min(t1, t); else t0 = Math.max(t0, t);
                        if (t0 > t1) return -1;
                    }
                }
            }
        }
        return t0;
    }
}
