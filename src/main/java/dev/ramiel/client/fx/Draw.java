package dev.ramiel.client.fx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A tiny immediate-mode helper for glowing geometry: quads, ribbons, rings, spheres, halos.
 * All positions are WORLD coordinates; it subtracts the camera itself.
 *
 *   Draw d = new Draw(RamielRenderTypes.BEAM, camera);
 *   d.ribbon(a, b, side, 0.5, r, g, b, alpha);
 *   d.flush();
 *
 * Only one Draw may be open at a time (they share one memory buffer), so always flush before
 * creating the next one.
 */
public final class Draw {
    private static final ByteBufferBuilder BYTES = new ByteBufferBuilder(1 << 21);

    private final RenderType type;
    private final BufferBuilder bb;
    private final double cx, cy, cz;
    private final Vector3f right = new Vector3f(), up = new Vector3f();
    private int vertices;

    public Draw(RenderType type, Camera camera) {
        this.type = type;
        Vec3 c = camera.getPosition();
        this.cx = c.x; this.cy = c.y; this.cz = c.z;
        camera.getLeftVector().negate(right);          // camera right
        up.set(camera.getUpVector());
        this.bb = new BufferBuilder(BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    public Vec3 cam() { return new Vec3(cx, cy, cz); }

    // ---- primitives --------------------------------------------------------------------------
    public void vertex(double x, double y, double z, float r, float g, float b, float a) {
        bb.addVertex((float) (x - cx), (float) (y - cy), (float) (z - cz)).setColor(r, g, b, Math.max(0f, Math.min(1f, a)));
        vertices++;
    }

    public void quad(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float r, float g, float b, float a) {
        vertex(p0.x, p0.y, p0.z, r, g, b, a);
        vertex(p1.x, p1.y, p1.z, r, g, b, a);
        vertex(p2.x, p2.y, p2.z, r, g, b, a);
        vertex(p3.x, p3.y, p3.z, r, g, b, a);
    }

    /** A flat strip from a to b, {@code half} wide on each side along the unit vector {@code side}. */
    public void ribbon(Vec3 a, Vec3 b, Vec3 side, double half, float r, float g, float bl, float alpha) {
        quad(a.subtract(side.scale(half)), a.add(side.scale(half)), b.add(side.scale(half)), b.subtract(side.scale(half)), r, g, bl, alpha);
    }

    /** Same, but fading from alphaA at the start to alphaB at the end. */
    public void ribbonFade(Vec3 a, Vec3 b, Vec3 side, double half, float r, float g, float bl, float alphaA, float alphaB) {
        Vec3 s = side.scale(half);
        Vec3 p0 = a.subtract(s), p1 = a.add(s), p2 = b.add(s), p3 = b.subtract(s);
        vertex(p0.x, p0.y, p0.z, r, g, bl, alphaA);
        vertex(p1.x, p1.y, p1.z, r, g, bl, alphaA);
        vertex(p2.x, p2.y, p2.z, r, g, bl, alphaB);
        vertex(p3.x, p3.y, p3.z, r, g, bl, alphaB);
    }

    /**
     * A glowing line seen from any angle: two ribbons crossed at right angles around the axis a->b.
     * Several of these with growing width and falling alpha give the soft glow of a beam.
     */
    public void beam(Vec3 a, Vec3 b, double half, float r, float g, float bl, float alpha) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1e-6) return;
        dir = dir.normalize();
        Vec3 u = perpendicular(dir);
        Vec3 v = dir.cross(u);
        ribbon(a, b, u, half, r, g, bl, alpha);
        ribbon(a, b, v, half, r, g, bl, alpha);
    }

    /** A flat ring (annulus) around {@code centre}, lying in the plane with unit normal {@code n}. */
    public void ring(Vec3 centre, Vec3 n, double r0, double r1, int segments, float r, float g, float b, float aIn, float aOut) {
        Vec3 u = perpendicular(n), v = n.cross(u);
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2 / segments, a1 = (i + 1) * Math.PI * 2 / segments;
            Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            Vec3 i0 = centre.add(d0.scale(r0)), i1 = centre.add(d1.scale(r0));
            Vec3 o0 = centre.add(d0.scale(r1)), o1 = centre.add(d1.scale(r1));
            vertex(i0.x, i0.y, i0.z, r, g, b, aIn);
            vertex(o0.x, o0.y, o0.z, r, g, b, aOut);
            vertex(o1.x, o1.y, o1.z, r, g, b, aOut);
            vertex(i1.x, i1.y, i1.z, r, g, b, aIn);
        }
    }

    /** One camera-facing square (stars, sparks). */
    public void billboard(Vec3 c, double size, float r, float g, float b, float a) {
        Vec3 rx = new Vec3(right.x, right.y, right.z).scale(size), uy = new Vec3(up.x, up.y, up.z).scale(size);
        quad(c.subtract(rx).subtract(uy), c.add(rx).subtract(uy), c.add(rx).add(uy), c.subtract(rx).add(uy), r, g, b, a);
    }

    /** A camera-facing glow made of soft layers: bright in the middle, fading to nothing at {@code size}. */
    public void halo(Vec3 c, double size, float r, float g, float b, float alpha) {
        int layers = 6;
        for (int i = 0; i < layers; i++) {
            double k = (i + 1) / (double) layers;                      // 1/6 .. 1
            double s = size * k;
            float a = alpha * (float) Math.pow(1.0 - k + 1.0 / layers, 2.0) * 0.55f;
            Vec3 rx = new Vec3(right.x, right.y, right.z).scale(s), uy = new Vec3(up.x, up.y, up.z).scale(s);
            quad(c.subtract(rx).subtract(uy), c.add(rx).subtract(uy), c.add(rx).add(uy), c.subtract(rx).add(uy), r, g, b, a);
        }
    }

    /**
     * A glowing translucent ball. Edges are brighter than the middle (like a force bubble), so
     * stacks of these look like energy rather than solid balls.
     */
    public void sphere(Vec3 c, double radius, int lat, int lon, float r, float g, float b, float alpha) {
        for (int i = 0; i < lat; i++) {
            double p0 = Math.PI * (-0.5 + (double) i / lat), p1 = Math.PI * (-0.5 + (double) (i + 1) / lat);
            for (int j = 0; j < lon; j++) {
                double t0 = 2 * Math.PI * j / lon, t1 = 2 * Math.PI * (j + 1) / lon;
                spherePoint(c, radius, p0, t0, r, g, b, alpha);
                spherePoint(c, radius, p0, t1, r, g, b, alpha);
                spherePoint(c, radius, p1, t1, r, g, b, alpha);
                spherePoint(c, radius, p1, t0, r, g, b, alpha);
            }
        }
    }

    private void spherePoint(Vec3 c, double radius, double phi, double theta, float r, float g, float b, float alpha) {
        double nx = Math.cos(phi) * Math.cos(theta), ny = Math.sin(phi), nz = Math.cos(phi) * Math.sin(theta);
        double px = c.x + nx * radius, py = c.y + ny * radius, pz = c.z + nz * radius;
        // brighter where the surface turns away from the camera (rim light)
        double vx = cx - px, vy = cy - py, vz = cz - pz;
        double vl = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-6;
        double facing = Math.abs((nx * vx + ny * vy + nz * vz) / vl);
        float a = alpha * (float) (0.35 + 0.65 * Math.pow(1.0 - facing, 1.5));
        vertex(px, py, pz, r, g, b, a);
    }

    /** Any unit vector perpendicular to {@code n}. */
    public static Vec3 perpendicular(Vec3 n) {
        Vec3 helper = Math.abs(n.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return n.cross(helper).normalize();
    }

    /** Send everything to the GPU. Safe to call with nothing drawn. */
    public void flush() {
        if (vertices == 0) {
            MeshData none = bb.build();
            if (none != null) none.close();
            return;
        }
        MeshData mesh = bb.build();
        if (mesh != null) type.draw(mesh);
    }
}
