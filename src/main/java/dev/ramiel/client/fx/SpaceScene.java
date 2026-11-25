package dev.ramiel.client.fx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ramiel.client.render.RamielRenderTypes;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The view from orbit during the arrival cutscene: black sky, stars, the sun, and the Earth as a
 * textured sphere with a glowing rim.
 *
 * It switches itself on by altitude: above ~900 blocks the sky fades to space, fully by ~2000. The
 * arrival starts 2600 blocks up, so only that cutscene (or an extremely high elytra flight with
 * cheats) ever sees it. It is drawn right after the sky, before the world, so the real terrain and
 * Ramiel are drawn on top.
 *
 * The Earth is drawn at a fixed distance inside the far clip plane but sized so it takes up the same
 * angle in your view as a real 6000-block planet would. Same trick as the horizon Ramiel.
 */
final class SpaceScene {
    private SpaceScene() {}

    private static final ByteBufferBuilder EARTH_BYTES = new ByteBufferBuilder(1 << 20);
    private static final double PLANET_RADIUS = 6000.0;
    private static final Vec3 SUN = new Vec3(0.55, 0.30, -0.78).normalize();
    private static final float[][] STARS = makeStars(1400);

    /** 0 = normal sky, 1 = full space. */
    static float blend(Camera camera) {
        var level = Minecraft.getInstance().level;
        if (level == null) return 0f;
        double alt = camera.getPosition().y - level.getSeaLevel();
        return Mth.clamp((float) ((alt - 900.0) / 1100.0), 0f, 1f);
    }

    static void render(Camera camera, float partial) {
        float blend = blend(camera);
        if (blend <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        double far = mc.gameRenderer.getDepthFar();
        double dist = far * 0.85;
        Vec3 cam = camera.getPosition();
        float time = FxState.now + partial;

        // 1) paint the sky black
        Draw dark = new Draw(RamielRenderTypes.DARKEN, camera);
        double s = dist * 0.9;
        float a = Mth.clamp(blend * 1.25f, 0f, 1f);
        Vec3[] c = new Vec3[8];
        for (int i = 0; i < 8; i++) c[i] = cam.add(((i & 1) == 0 ? -s : s), ((i & 2) == 0 ? -s : s), ((i & 4) == 0 ? -s : s));
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        for (int[] f : faces) dark.quad(c[f[0]], c[f[1]], c[f[2]], c[f[3]], 0.01f, 0.01f, 0.025f, a);
        dark.flush();

        // 2) stars and sun
        Draw stars = new Draw(RamielRenderTypes.BEAM_ALWAYS, camera);
        for (int i = 0; i < STARS.length; i++) {
            float[] st = STARS[i];
            Vec3 p = cam.add(st[0] * dist * 0.97, st[1] * dist * 0.97, st[2] * dist * 0.97);
            float twinkle = 0.7f + 0.3f * (float) Math.sin(time * 0.07 + i * 1.7);
            stars.billboard(p, dist * 0.0014 * st[3], 0.85f + 0.15f * st[4], 0.9f, 1f, 0.8f * twinkle * blend);
        }
        Vec3 sunPos = cam.add(SUN.scale(dist * 0.95));
        stars.halo(sunPos, dist * 0.10, 1f, 0.95f, 0.8f, blend);
        stars.halo(sunPos, dist * 0.30, 1f, 0.8f, 0.5f, 0.35f * blend);
        stars.flush();

        // 3) the Earth, straight below us
        double alt = Math.max(1.0, cam.y - mc.level.getSeaLevel());
        double sinTheta = Math.min(0.96, PLANET_RADIUS / (PLANET_RADIUS + alt));
        double radius = dist * sinTheta;
        Vec3 centre = cam.add(0, -dist, 0);
        earth(camera, centre, radius, time, blend);

        // 4) a thin blue glow around its edge
        Draw rim = new Draw(RamielRenderTypes.BEAM, camera);
        rim.sphere(centre, radius * 1.035, 24, 48, 0.35f, 0.65f, 1f, 0.55f * blend);
        rim.flush();
    }

    private static void earth(Camera camera, Vec3 centre, double radius, float time, float blend) {
        RenderType type = RenderType.entityTranslucentEmissive(RamielRenderTypes.EARTH_TEX);
        BufferBuilder bb = new BufferBuilder(EARTH_BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        Vec3 cam = camera.getPosition();
        int lat = 24, lon = 48;
        double spin = time * 0.0004;
        for (int i = 0; i < lat; i++) {
            double p0 = Math.PI * (0.5 - (double) i / lat), p1 = Math.PI * (0.5 - (double) (i + 1) / lat);
            for (int j = 0; j < lon; j++) {
                double t0 = 2 * Math.PI * j / lon, t1 = 2 * Math.PI * (j + 1) / lon;
                vert(bb, cam, centre, radius, p0, t0, spin, blend, i / (double) lat, j / (double) lon);
                vert(bb, cam, centre, radius, p0, t1, spin, blend, i / (double) lat, (j + 1) / (double) lon);
                vert(bb, cam, centre, radius, p1, t1, spin, blend, (i + 1) / (double) lat, (j + 1) / (double) lon);
                vert(bb, cam, centre, radius, p1, t0, spin, blend, (i + 1) / (double) lat, j / (double) lon);
            }
        }
        MeshData mesh = bb.build();
        if (mesh != null) type.draw(mesh);
    }

    private static void vert(BufferBuilder bb, Vec3 cam, Vec3 centre, double radius, double phi, double theta,
                             double spin, float blend, double v, double u) {
        double nx = Math.cos(phi) * Math.cos(theta + spin), ny = Math.sin(phi), nz = Math.cos(phi) * Math.sin(theta + spin);
        // light from the sun: lit side bright, night side nearly black, soft terminator
        double lambert = nx * SUN.x + ny * SUN.y + nz * SUN.z;
        float light = (float) Mth.clamp(lambert * 1.4 + 0.18, 0.05, 1.0);
        float px = (float) (centre.x + nx * radius - cam.x), py = (float) (centre.y + ny * radius - cam.y), pz = (float) (centre.z + nz * radius - cam.z);
        bb.addVertex(px, py, pz)
                .setColor(light, light, Math.min(1f, light * 1.06f), blend)
                .setUv((float) u, (float) v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal((float) nx, (float) ny, (float) nz);
    }

    /** Fixed random stars so they never jump around: x,y,z unit vector, size, colour tint. */
    private static float[][] makeStars(int n) {
        Random rnd = new Random(618);
        float[][] out = new float[n][5];
        for (int i = 0; i < n; i++) {
            double z = rnd.nextDouble() * 2 - 1, a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(1 - z * z);
            out[i][0] = (float) (r * Math.cos(a)); out[i][1] = (float) z; out[i][2] = (float) (r * Math.sin(a));
            out[i][3] = 0.6f + 2.4f * (float) Math.pow(rnd.nextDouble(), 3.0);
            out[i][4] = rnd.nextFloat();
        }
        return out;
    }
}
