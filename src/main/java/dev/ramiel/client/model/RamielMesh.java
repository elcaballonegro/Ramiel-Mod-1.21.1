package dev.ramiel.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Turns bricks into coloured quads. All of the "crystal" look is computed here on the CPU, so it
 * needs no shaders and works with any resource pack:
 *
 *   base colour x diffuse light            the facets
 *   + sky/ground colour reflected          a fake mirror: look at a face and it shows the sky
 *     off the face (stronger at grazing    (stronger at glancing angles, like real glass)
 *     angles, "fresnel")
 *   + a sharp sun glint                    the bright flash when a facet catches the light
 *
 * Bricks are drawn far-to-near and faces back-to-front, because the crystal is translucent.
 */
public final class RamielMesh {
    private RamielMesh() {}

    private static final float[] SUN = normalise(0.45f, 0.8f, 0.4f);
    /** Corners 0-3 are the bottom ring, 4-7 the top ring. Each row is one face. */
    private static final int[][] FACES = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 1, 5, 4}, {2, 3, 7, 6}, {3, 0, 4, 7}, {1, 2, 6, 5}};
    private static final float[][] UV = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    // scratch storage (rendering is single threaded)
    private static final Vector3f[] V = new Vector3f[8];
    private static final Vector3f TMP = new Vector3f();
    private static final Quaternionf BQ = new Quaternionf();
    private static final float[] COL = new float[3];
    static { for (int i = 0; i < 8; i++) V[i] = new Vector3f(); }

    /**
     * @param body     where ordinary bricks go
     * @param glow     where self-lit bricks (glow > 0.5) go
     * @param pose     translation to the centre of the model (the model itself is centred on 0)
     * @param orient   the whole model's rotation
     * @param scale    blocks per R unit (24 for the real thing)
     * @param camLocal camera position relative to the model centre, in blocks
     * @param fade     overall opacity multiplier
     */
    public static void emit(VertexConsumer body, VertexConsumer glow, PoseStack.Pose pose, Brick[] bricks,
                            Quaternionf orient, float scale, Vector3f camLocal, float fade) {
        // sort visible bricks far to near
        int n = 0;
        int[] order = new int[bricks.length];
        float[] dist = new float[bricks.length];
        for (int i = 0; i < bricks.length; i++) {
            Brick b = bricks[i];
            if (!b.visible()) continue;
            TMP.set(b.x, b.y, b.z);
            orient.transform(TMP).mul(scale);
            float d = TMP.distanceSquared(camLocal);
            int j = n++;
            while (j > 0 && dist[j - 1] < d) { dist[j] = dist[j - 1]; order[j] = order[j - 1]; j--; }
            dist[j] = d;
            order[j] = i;
        }
        for (int k = 0; k < n; k++) {
            int i = order[k];
            drawBrick(bricks[i], i, glow != null && bricks[i].glow > 0.5f ? glow : body, pose, orient, scale, camLocal, fade);
        }
    }

    private static void drawBrick(Brick b, int index, VertexConsumer vc, PoseStack.Pose pose,
                                  Quaternionf orient, float scale, Vector3f cam, float fade) {
        // 1) the eight corners, in model space
        float hh = b.h * 0.5f;
        corner(0, -b.bw, -hh, -b.bd, b, orient, scale);
        corner(1, b.bw, -hh, -b.bd, b, orient, scale);
        corner(2, b.bw, -hh, b.bd, b, orient, scale);
        corner(3, -b.bw, -hh, b.bd, b, orient, scale);
        corner(4, -b.tw, hh, -b.td, b, orient, scale);
        corner(5, b.tw, hh, -b.td, b, orient, scale);
        corner(6, b.tw, hh, b.td, b, orient, scale);
        corner(7, -b.tw, hh, b.td, b, orient, scale);

        TMP.set(b.x, b.y, b.z);
        orient.transform(TMP).mul(scale);
        float bcx = TMP.x, bcy = TMP.y, bcz = TMP.z;

        // 2) work out each face: normal, facing, and sort back faces first
        float[][] fn = new float[6][3];
        float[] facing = new float[6];
        boolean[] ok = new boolean[6];
        int[] faceOrder = new int[6];
        int count = 0;
        for (int f = 0; f < 6; f++) {
            Vector3f p0 = V[FACES[f][0]], p1 = V[FACES[f][1]], p2 = V[FACES[f][2]], p3 = V[FACES[f][3]];
            float ax = p1.x - p0.x, ay = p1.y - p0.y, az = p1.z - p0.z;
            float cx = p3.x - p0.x, cy = p3.y - p0.y, cz = p3.z - p0.z;
            float nx = ay * cz - az * cy, ny = az * cx - ax * cz, nz = ax * cy - ay * cx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-3f) continue;                                   // flat / collapsed face
            nx /= len; ny /= len; nz /= len;
            float fx = (p0.x + p1.x + p2.x + p3.x) * 0.25f, fy = (p0.y + p1.y + p2.y + p3.y) * 0.25f, fz = (p0.z + p1.z + p2.z + p3.z) * 0.25f;
            if (nx * (fx - bcx) + ny * (fy - bcy) + nz * (fz - bcz) < 0f) { nx = -nx; ny = -ny; nz = -nz; }   // point outward
            float tx = cam.x - fx, ty = cam.y - fy, tz = cam.z - fz;
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz) + 1e-4f;
            ok[f] = true;
            fn[f][0] = nx; fn[f][1] = ny; fn[f][2] = nz;
            facing[f] = (nx * tx + ny * ty + nz * tz) / tl;
            int j = count++;
            while (j > 0 && facing[faceOrder[j - 1]] > facing[f]) { faceOrder[j] = faceOrder[j - 1]; j--; }
            faceOrder[j] = f;
        }

        // 3) emit quads
        float alpha = Math.min(1f, b.alpha * fade);
        float u0 = (index % 4) * 0.25f, v0 = ((index / 4) % 4) * 0.25f;
        for (int k = 0; k < count; k++) {
            int f = faceOrder[k];
            float nx = fn[f][0], ny = fn[f][1], nz = fn[f][2];
            // seen from behind? light the inside as if the normal pointed at us
            boolean back = facing[f] < 0f;
            float sx = back ? -nx : nx, sy = back ? -ny : ny, sz = back ? -nz : nz;
            Vector3f p0 = V[FACES[f][0]];
            float tx = cam.x - p0.x, ty = cam.y - p0.y, tz = cam.z - p0.z;
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz) + 1e-4f;
            shade(sx, sy, sz, tx / tl, ty / tl, tz / tl, b);
            float r = COL[0], g = COL[1], bl = COL[2];
            for (int c = 0; c < 4; c++) {
                Vector3f p = V[FACES[f][c]];
                vc.addVertex(pose, p.x, p.y, p.z)
                        .setColor(r, g, bl, alpha)
                        .setUv(u0 + UV[c][0] * 0.25f, v0 + UV[c][1] * 0.25f)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    private static void corner(int slot, float lx, float ly, float lz, Brick b, Quaternionf orient, float scale) {
        BQ.set(b.qx, b.qy, b.qz, b.qw);
        Vector3f v = V[slot].set(lx, ly, lz);
        BQ.transform(v);
        v.add(b.x, b.y, b.z);
        orient.transform(v);
        v.mul(scale);
    }

    /** The crystal look. n = face normal facing us, v = unit vector from the face to the camera. */
    private static void shade(float nx, float ny, float nz, float vx, float vy, float vz, Brick b) {
        float d = Math.max(0f, nx * vx + ny * vy + nz * vz);
        float fres = (float) Math.pow(1f - d, 3.0);
        // mirror direction: r = 2(n.v)n - v
        float rx = 2f * d * nx - vx, ry = 2f * d * ny - vy, rz = 2f * d * nz - vz;
        // what that direction "sees": deep blue below, bright cyan at the horizon, pale at the zenith
        float er, eg, eb;
        if (ry < 0f) {
            float k = Math.min(1f, -ry * 1.6f);
            er = lerp(0.25f, 0.02f, k); eg = lerp(0.55f, 0.06f, k); eb = lerp(0.85f, 0.2f, k);
        } else {
            float k = Math.min(1f, ry * 1.4f);
            er = lerp(0.25f, 0.70f, k); eg = lerp(0.55f, 0.90f, k); eb = lerp(0.85f, 1.0f, k);
        }
        float diffuse = Math.max(0f, nx * SUN[0] + ny * SUN[1] + nz * SUN[2]) * 0.55f + 0.45f;
        float spec = (float) Math.pow(Math.max(0f, rx * SUN[0] + ry * SUN[1] + rz * SUN[2]), 40.0);
        float env = 0.15f + 0.6f * fres;
        float r = b.r * diffuse + er * env + spec * 0.9f;
        float g = b.g * diffuse + eg * env + spec * 0.9f;
        float bl = b.b * diffuse + eb * env + spec * 0.9f;
        if (b.glow > 0f) {                          // self-lit pieces ignore most of the lighting
            r = lerp(r, b.r * 1.25f + 0.15f, b.glow);
            g = lerp(g, b.g * 1.25f + 0.15f, b.glow);
            bl = lerp(bl, b.b * 1.25f + 0.15f, b.glow);
        }
        COL[0] = Math.min(1f, r); COL[1] = Math.min(1f, g); COL[2] = Math.min(1f, bl);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float[] normalise(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }
}
