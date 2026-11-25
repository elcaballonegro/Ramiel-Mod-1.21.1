package dev.ramiel.client.gui;

import com.mojang.math.Axis;
import cyou.rimuru.rimulib.mc1211.client.api.UiApi;
import dev.ramiel.client.fx.FxState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The blast-zone warning for Ramiel's impact strike. While the player stands inside a zone:
 * "BLAST ZONE", the seconds until detonation, a pulsing arrow showing the shortest way OUT (it
 * turns as the player turns), the distance to the edge, and "RUN". Just outside a zone, a small
 * amber distance line. The red screen pulse and the action-bar messages already exist
 * (ScreenEffects / the server), so this only adds the big readable part.
 *
 * Reads {@link FxState#zones} (centre, radius, end tick) and {@link FxState#now}.
 */
public final class WarningHud implements LayeredDraw.Layer {
    public static final WarningHud INSTANCE = new WarningHud();
    private WarningHud() {}

    private static final int RED = 0xFFFF3B2E;
    private static final int AMBER = 0xFFFFB02E;
    private static final int WHITE = 0xFFFFFFFF;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || FxState.zones.isEmpty()) return;
        float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
        double time = FxState.now + partial;

        // the zone we are standing in that goes off soonest, and the closest one we are not in
        FxState.Zone inside = null, near = null;
        double insideDist = 0.0, nearGap = Double.MAX_VALUE;
        for (FxState.Zone z : FxState.zones.values()) {
            if (z.centre == null || z.end - FxState.now <= 0) continue;
            double d = Math.hypot(p.getX() - z.centre.x, p.getZ() - z.centre.z);
            if (d < z.radius) {
                if (inside == null || z.end < inside.end) { inside = z; insideDist = d; }
            } else if (d - z.radius < nearGap) {
                near = z; nearGap = d - z.radius;
            }
        }

        if (inside != null) {
            drawInside(g, p, inside, insideDist, partial, time);
        } else if (near != null && nearGap < 48.0) {
            final long meters = Math.round(nearGap);
            UiApi.overlay(g, () -> {
                float fw = UiApi.width(g), fh = UiApi.height(g);
                UiApi.text(g, UiApi.Font.MONO, "BLAST ZONE  " + meters + " M", fw * 0.5f, fh * 0.13f, 0.5f, 1.1f,
                        ScopeLayer.fade(AMBER, 0.85f));
            });
        }
    }

    private static void drawInside(GuiGraphics g, LocalPlayer p, FxState.Zone zone, double dist, float partial, double time) {
        double ticksLeft = Math.max(0.0, zone.end - FxState.now - partial);
        final double seconds = ticksLeft / 20.0;
        final long edge = Math.max(0L, Math.round(zone.radius - dist));
        boolean urgent = ticksLeft < 200.0;
        boolean flash = ((int) (time / (urgent ? 3.0 : 6.0)) & 1) == 0;
        final int title = flash ? RED : ScopeLayer.fade(RED, 0.55f);
        final int count = urgent && flash ? WHITE : AMBER;

        // the way out: from the zone's centre through the player, relative to where the player is looking
        double ex = p.getX() - zone.centre.x, ez = p.getZ() - zone.centre.z;
        double elen = Math.hypot(ex, ez);
        if (elen < 1e-3) { ex = 0.0; ez = 1.0; } else { ex /= elen; ez /= elen; }
        double yaw = Math.toRadians(p.getViewYRot(partial));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);       // forward (flat)
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);      // right (flat)
        float angle = (float) Math.toDegrees(Math.atan2(ex * rx + ez * rz, ex * fx + ez * fz));

        UiApi.overlay(g, () -> {
            float fw = UiApi.width(g), fh = UiApi.height(g);
            UiApi.text(g, UiApi.Font.DISPLAY, "BLAST ZONE", fw * 0.5f, fh * 0.17f, 0.5f, 2.0f, title);
            UiApi.text(g, UiApi.Font.MONO, String.format("DETONATION IN %.1f S", seconds), fw * 0.5f, fh * 0.23f, 0.5f, 1.2f, count);
            UiApi.text(g, UiApi.Font.DISPLAY, "RUN", fw * 0.5f, fh * 0.80f, 0.5f, 2.4f, title);
            UiApi.text(g, UiApi.Font.MONO, "EDGE  " + edge + " M", fw * 0.5f, fh * 0.85f, 0.5f, 1.0f, ScopeLayer.fade(WHITE, 0.85f));
        });

        // the arrow (screen pixels, so it can be rotated with the pose stack)
        int cx = g.guiWidth() / 2, cy = Math.round(g.guiHeight() * 0.58f);
        int color = flash ? RED : ScopeLayer.fade(RED, 0.6f);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        for (int i = 0; i < 14; i++) g.fill(-i, -30 + i, i + 1, -29 + i, color);     // head
        g.fill(-4, -16, 5, 10, color);                                                 // shaft
        g.pose().popPose();
    }
}
