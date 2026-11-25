package dev.ramiel.client.model;

import org.joml.Quaternionf;

/**
 * One convex piece of Ramiel: a "frustum" that can be a box, a pyramid, a spike or a thin slab
 * depending on its numbers. Every form is a list of bricks, and morphing is just blending the
 * numbers of each brick, which is why a pyramid can melt into a slab without any modelling tools.
 *
 * Units are "R units": 1.0 = half the height of the octahedron (24 blocks).
 *
 * Local shape (before rotation): the bottom face sits at y = -h/2 and has half-sizes (bw, bd);
 * the top face sits at y = +h/2 with half-sizes (tw, td). A pyramid has tw = td = 0.
 */
public final class Brick {
    public float x, y, z;                  // centre
    public float qx, qy, qz, qw = 1f;      // rotation
    public float h, bw, bd, tw, td;        // shape
    public float r = 1f, g = 1f, b = 1f;   // base colour
    public float glow;                     // 0 = lit by the environment, 1 = self-lit
    public float alpha = 1f;

    /** Collapse to nothing at the centre (an unused brick). */
    public Brick collapse() {
        x = y = z = 0f;
        qx = qy = qz = 0f; qw = 1f;
        h = bw = bd = tw = td = 0f;
        glow = 0f;
        alpha = 0f;
        return this;
    }

    public boolean visible() { return alpha > 0.01f && (h > 1e-4f || bw > 1e-4f || tw > 1e-4f); }

    public Brick colour(float[] c, float glow, float alpha) {
        this.r = c[0]; this.g = c[1]; this.b = c[2];
        this.glow = glow;
        this.alpha = alpha;
        return this;
    }

    public Brick rot(Quaternionf q) {
        qx = q.x; qy = q.y; qz = q.z; qw = q.w;
        return this;
    }

    /** An axis-aligned box centred at (cx,cy,cz) with half-extents (hx,hy,hz). */
    public Brick box(float cx, float cy, float cz, float hx, float hy, float hz) {
        x = cx; y = cy; z = cz;
        qx = qy = qz = 0f; qw = 1f;
        h = hy * 2f; bw = tw = hx; bd = td = hz;
        return this;
    }

    /**
     * A pyramid or spike: base centre at {@code base}, apex at base + dir * length.
     * {@code half} is the half-size of the square base.
     */
    public Brick spike(float bx, float by, float bz, float dx, float dy, float dz, float length, float half) {
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f || length < 1e-3f) return collapse();
        dx /= len; dy /= len; dz /= len;
        x = bx + dx * length * 0.5f;
        y = by + dy * length * 0.5f;
        z = bz + dz * length * 0.5f;
        Quaternionf q = new Quaternionf().rotationTo(0f, 1f, 0f, dx, dy, dz);
        qx = q.x; qy = q.y; qz = q.z; qw = q.w;
        h = length;
        bw = bd = half;
        tw = td = 0f;
        return this;
    }

    public Brick set(Brick o) {
        x = o.x; y = o.y; z = o.z; qx = o.qx; qy = o.qy; qz = o.qz; qw = o.qw;
        h = o.h; bw = o.bw; bd = o.bd; tw = o.tw; td = o.td;
        r = o.r; g = o.g; b = o.b; glow = o.glow; alpha = o.alpha;
        return this;
    }

    /**
     * Blend two bricks.
     *
     * Both exist: position, rotation, size, colour and glow are blended (a piece slides and turns
     * into its place in the next form).
     *
     * Only one exists: the piece flies out of, or back into, the centre of the body. It keeps its
     * own rotation, colour and opacity and is simply scaled about the core, so it is never faded
     * in or out. At t = 0 it is at the centre as a point; at t = 1 it is in full.
     *
     * @return how much of the piece is "out": 1 if it exists at both ends, otherwise 0..1
     */
    public static float lerp(Brick a, Brick c, float t, Brick out, Quaternionf scratchA, Quaternionf scratchB) {
        boolean av = a.visible(), cv = c.visible();
        if (!av && !cv) { out.collapse(); return 0f; }
        if (av != cv) {
            Brick real = av ? a : c;
            float s = av ? 1f - t : t;            // how much of the piece is "out" right now
            out.x = real.x * s; out.y = real.y * s; out.z = real.z * s;
            out.qx = real.qx; out.qy = real.qy; out.qz = real.qz; out.qw = real.qw;
            out.h = real.h * s; out.bw = real.bw * s; out.bd = real.bd * s; out.tw = real.tw * s; out.td = real.td * s;
            out.r = real.r; out.g = real.g; out.b = real.b; out.glow = real.glow; out.alpha = real.alpha;
            return s;
        }
        out.x = a.x + (c.x - a.x) * t; out.y = a.y + (c.y - a.y) * t; out.z = a.z + (c.z - a.z) * t;
        scratchA.set(a.qx, a.qy, a.qz, a.qw);
        scratchB.set(c.qx, c.qy, c.qz, c.qw);
        scratchA.slerp(scratchB, t);
        out.qx = scratchA.x; out.qy = scratchA.y; out.qz = scratchA.z; out.qw = scratchA.w;
        out.h = a.h + (c.h - a.h) * t;
        out.bw = a.bw + (c.bw - a.bw) * t; out.bd = a.bd + (c.bd - a.bd) * t;
        out.tw = a.tw + (c.tw - a.tw) * t; out.td = a.td + (c.td - a.td) * t;
        out.r = a.r + (c.r - a.r) * t; out.g = a.g + (c.g - a.g) * t; out.b = a.b + (c.b - a.b) * t;
        out.glow = a.glow + (c.glow - a.glow) * t;
        out.alpha = a.alpha + (c.alpha - a.alpha) * t;
        return 1f;
    }
}
