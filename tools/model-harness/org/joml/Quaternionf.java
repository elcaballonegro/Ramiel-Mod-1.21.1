package org.joml;
/** Minimal stand-in for JOML's Quaternionf (only what the model code uses), so the model can run in the sandbox. */
public class Quaternionf {
    public float x, y, z, w = 1f;
    public Quaternionf() {}
    public Quaternionf set(float x, float y, float z, float w) { this.x = x; this.y = y; this.z = z; this.w = w; return this; }
    public Quaternionf rotationAxis(float angle, float ax, float ay, float az) {
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-8f) return set(0, 0, 0, 1);
        float s = (float) Math.sin(angle / 2) / len;
        return set(ax * s, ay * s, az * s, (float) Math.cos(angle / 2));
    }
    public Quaternionf rotationX(float a) { return rotationAxis(a, 1, 0, 0); }
    public Quaternionf rotationY(float a) { return rotationAxis(a, 0, 1, 0); }
    public Quaternionf rotationZ(float a) { return rotationAxis(a, 0, 0, 1); }
    /** this = this * R(angle about the local axis), like JOML's rotateX/Y/Z */
    public Quaternionf rotateX(float a) { return mul(new Quaternionf().rotationX(a)); }
    public Quaternionf rotateY(float a) { return mul(new Quaternionf().rotationY(a)); }
    public Quaternionf rotateZ(float a) { return mul(new Quaternionf().rotationZ(a)); }
    public Quaternionf rotationXYZ(float ax, float ay, float az) {
        Quaternionf q = new Quaternionf().rotationX(ax);
        q.mul(new Quaternionf().rotationY(ay)).mul(new Quaternionf().rotationZ(az));
        return set(q.x, q.y, q.z, q.w);
    }
    /** this = this * o */
    public Quaternionf mul(Quaternionf o) {
        float nx = w * o.x + x * o.w + y * o.z - z * o.y;
        float ny = w * o.y - x * o.z + y * o.w + z * o.x;
        float nz = w * o.z + x * o.y - y * o.x + z * o.w;
        float nw = w * o.w - x * o.x - y * o.y - z * o.z;
        return set(nx, ny, nz, nw);
    }
    public Quaternionf rotationTo(float fx, float fy, float fz, float tx, float ty, float tz) {
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz), tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        fx /= fl; fy /= fl; fz /= fl; tx /= tl; ty /= tl; tz /= tl;
        float dot = fx * tx + fy * ty + fz * tz;
        if (dot < -0.999999f) { // opposite: any perpendicular axis
            float ax = 1, ay = 0, az = 0;
            if (Math.abs(fx) > 0.9f) { ax = 0; ay = 1; }
            float cx = fy * az - fz * ay, cy = fz * ax - fx * az, cz = fx * ay - fy * ax;
            return rotationAxis((float) Math.PI, cx, cy, cz);
        }
        float cx = fy * tz - fz * ty, cy = fz * tx - fx * tz, cz = fx * ty - fy * tx;
        float nw = 1 + dot;
        float l = (float) Math.sqrt(cx * cx + cy * cy + cz * cz + nw * nw);
        return set(cx / l, cy / l, cz / l, nw / l);
    }
    public Quaternionf slerp(Quaternionf t, float k) {
        float dot = x * t.x + y * t.y + z * t.z + w * t.w;
        float tx = t.x, ty = t.y, tz = t.z, tw = t.w;
        if (dot < 0) { dot = -dot; tx = -tx; ty = -ty; tz = -tz; tw = -tw; }
        float s0, s1;
        if (dot > 0.9995f) { s0 = 1 - k; s1 = k; }
        else { float th = (float) Math.acos(dot), sn = (float) Math.sin(th); s0 = (float) Math.sin((1 - k) * th) / sn; s1 = (float) Math.sin(k * th) / sn; }
        float nx = s0 * x + s1 * tx, ny = s0 * y + s1 * ty, nz = s0 * z + s1 * tz, nw = s0 * w + s1 * tw;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz + nw * nw);
        return set(nx / l, ny / l, nz / l, nw / l);
    }
}
