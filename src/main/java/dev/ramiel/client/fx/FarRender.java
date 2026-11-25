package dev.ramiel.client.fx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ramiel.Form;
import dev.ramiel.client.model.Brick;
import dev.ramiel.client.model.RamielMesh;
import dev.ramiel.client.model.RamielModel;
import dev.ramiel.client.render.RamielRenderTypes;
import dev.ramiel.entity.Orientation;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.net.Payloads;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The "horizon" Ramiel for players too far away for the game to load it.
 *
 * The server sends a small Beacon (position, form, angle) a few times a second. When the real
 * entity is not loaded here, we draw a copy ourselves. The copy sits as far away as the far clip
 * plane allows but is scaled down by the same ratio, so on screen it has exactly the size and
 * position the real one would. This is the same idea as the "LOD" used by other big-boss mods.
 */
public final class FarRender {
    private FarRender() {}

    private static final ByteBufferBuilder BYTES = new ByteBufferBuilder(1 << 18);
    private static final Map<UUID, Payloads.Beacon> BEACONS = new HashMap<>();
    private static final Map<UUID, Long> SEEN = new HashMap<>();

    public static void update(Payloads.Beacon b) {
        if (!b.alive()) { BEACONS.remove(b.id()); SEEN.remove(b.id()); return; }
        BEACONS.put(b.id(), b);
        SEEN.put(b.id(), FxState.now);
    }

    public static void clear() { BEACONS.clear(); SEEN.clear(); }

    static void render(Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || BEACONS.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        double far = mc.gameRenderer.getDepthFar();
        double limit = far * 0.9;
        float time = FxState.now + partial;

        for (Iterator<Map.Entry<UUID, Payloads.Beacon>> it = BEACONS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (FxState.now - SEEN.getOrDefault(entry.getKey(), 0L) > 60) { it.remove(); continue; }   // stopped hearing from it
            Payloads.Beacon b = entry.getValue();
            if (isLoaded(b.id())) continue;                                                              // the real one is drawn instead

            Vec3 centre = new Vec3(b.x(), b.y() + RamielEntity.CORE_BOX / 2.0, b.z());
            Vec3 to = centre.subtract(cam);
            double dist = to.length();
            if (dist < 150) continue;

            Vec3 drawAt = centre;
            float scale = (float) RamielEntity.R;
            if (dist > limit) {                                    // pull it in, shrink it by the same factor
                double k = limit / dist;
                drawAt = cam.add(to.scale(k));
                scale *= (float) k;
            }

            Form from = Form.byId(b.formFrom()), target = Form.byId(b.formTo());
            Brick[] bricks = RamielModel.shared().computeFor(b.id(), from, target, b.morph(), time, 0f, 0f, 0f);
            Quaternionf orient = blendedOrientation(from, target, b);

            BufferBuilder bb = new BufferBuilder(BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            PoseStack ps = new PoseStack();
            ps.translate(drawAt.x - cam.x, drawAt.y - cam.y, drawAt.z - cam.z);
            Vector3f camLocal = new Vector3f((float) (cam.x - drawAt.x), (float) (cam.y - drawAt.y), (float) (cam.z - drawAt.z));
            RamielMesh.emit(bb, null, ps.last(), bricks, orient, scale, camLocal, 1f);
            MeshData mesh = bb.build();
            if (mesh != null) RamielRenderTypes.FAR_CRYSTAL.draw(mesh);
        }
    }

    private static Quaternionf blendedOrientation(Form from, Form to, Payloads.Beacon b) {
        Quaternionf q = Orientation.forAngles(to, b.aimYaw(), b.aimPitch(), b.spin());
        if (from != to && b.morph() < 1f) {
            Quaternionf p = Orientation.forAngles(from, b.aimYaw(), b.aimPitch(), b.spin());
            float m = b.morph();
            return p.slerp(q, m * m * (3f - 2f * m));
        }
        return q;
    }

    private static boolean isLoaded(UUID id) {
        for (RamielEntity r : FxRender.ramiels()) if (r.getUUID().equals(id)) return true;
        return false;
    }
}
