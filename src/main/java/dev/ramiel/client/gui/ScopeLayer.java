package dev.ramiel.client.gui;

import cyou.rimuru.rimulib.mc1211.client.api.UiApi;
import dev.ramiel.ModRegistry;
import dev.ramiel.client.fx.FxRender;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.item.RailcannonItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The railcannon scope. Everything is drawn while the player is charging the cannon:
 *
 *   - the screen goes black outside a circle (it closes in over ~5 ticks), with a faint green
 *     tint for NIGHT sight or a warm one for ENHANCED sight
 *   - a reticle: axis lines with ticks, a gap in the middle, red dashed diagonals
 *   - four green triangles close in when the crosshair is on Ramiel's body (and tighten, red,
 *     when it is on the core while the core is exposed)
 *   - orange readouts: charge %, plasma cells left, range, target, core state, shield, hull
 *
 * Shapes use vanilla GuiGraphics (so they are exact in screen pixels). Text goes through Rimulib's
 * UiApi, which draws in its own fixed frame, so text positions are fractions of that frame.
 *
 * RamielClient also uses {@link #zoom()} (field of view), {@link #isScoped()} (hide the hand) and
 * {@link #hidesCrosshair()}.
 */
public final class ScopeLayer implements LayeredDraw.Layer {
    public static final ScopeLayer INSTANCE = new ScopeLayer();
    private ScopeLayer() {}

    private static final int ORANGE = 0xFFFF9A2E;
    private static final int RED = 0xFFFF3B2E;
    private static final int GREEN = 0xFF43FF7A;
    private static final int WHITE = 0xFFFFFFFF;

    /** 0 = no scope, 1 = fully closed in. Moves by 0.2 per tick. */
    private static float amount, amountO;

    // ==========================================================================================
    // State shared with RamielClient
    // ==========================================================================================

    /** Once per client tick. */
    public static void tick() {
        amountO = amount;
        float target = holdingScope() ? 1f : 0f;
        amount += Math.max(-0.2f, Math.min(0.2f, target - amount));
    }

    private static boolean holdingScope() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.isUsingItem() && p.getUseItem().getItem() instanceof RailcannonItem;
    }

    /** True once the scope is mostly closed: hide the held item. */
    public static boolean isScoped() { return amount > 0.3f; }

    /** True as soon as the scope starts to close: hide the vanilla crosshair. */
    public static boolean hidesCrosshair() { return amount > 0.02f; }

    /** Multiplier for the field-of-view modifier. About 4x zoom when fully charged. */
    public static float zoom() {
        if (amount <= 0.001f) return 1f;
        float e = ease(amount);
        LocalPlayer p = Minecraft.getInstance().player;
        float charge = p != null && p.isUsingItem()
                ? Mth.clamp(p.getTicksUsingItem() / (float) RailcannonItem.FULL_CHARGE, 0f, 1f) : 0f;
        return Mth.lerp(e, 1f, 0.30f) * (1f - 0.12f * charge * e);
    }

    // ==========================================================================================
    // Drawing
    // ==========================================================================================

    private record Lock(RamielEntity ramiel, double dist, boolean body, boolean core) {}

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;
        float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
        float a = Mth.lerp(partial, amountO, amount);
        if (a <= 0.01f) return;
        float e = ease(a);
        float time = player.tickCount + partial;

        // ---- what is the player looking at? -------------------------------------------------
        Lock lock = findLock(player, partial);
        int ticks = player.isUsingItem() ? player.getTicksUsingItem() : 0;
        float charge = Mth.clamp(ticks / (float) RailcannonItem.FULL_CHARGE, 0f, 1f);
        boolean ready = ticks >= RailcannonItem.FULL_CHARGE;
        boolean night = isDark(player);

        // ---- geometry -------------------------------------------------------------------------
        int w = g.guiWidth(), h = g.guiHeight();
        int cx = w / 2, cy = h / 2;
        int full = Math.round(Math.min(w, h) * 0.46f);
        int open = Math.round((float) Math.hypot(w, h) * 0.5f) + 4;
        int r = Math.round(Mth.lerp(e, open, full));

        int mask = fade(0xFF000000, Mth.clamp(a * 1.6f, 0f, 1f));
        int tint = fade(night ? 0x2A19FF6A : 0x14FF9A2E, e);
        int ring = fade(ORANGE, e);

        drawLens(g, w, h, cx, cy, r, mask, tint, ring);

        // ---- reticle --------------------------------------------------------------------------
        int line = fade(ORANGE, 0.85f * e);
        int gap = Math.max(4, r / 16);
        int len = r - 6;
        g.fill(cx - len, cy, cx - gap, cy + 1, line);
        g.fill(cx + gap, cy, cx + len, cy + 1, line);
        g.fill(cx, cy - len, cx + 1, cy - gap, line);
        g.fill(cx, cy + gap, cx + 1, cy + len, line);
        for (int i = 1; i <= 6; i++) {
            int off = gap + (len - gap) * i / 7;
            int th = (i % 2 == 0) ? 3 : 2;
            g.fill(cx + off, cy - th, cx + off + 1, cy + th + 1, line);
            g.fill(cx - off, cy - th, cx - off + 1, cy + th + 1, line);
            g.fill(cx - th, cy + off, cx + th + 1, cy + off + 1, line);
            g.fill(cx - th, cy - off, cx + th + 1, cy - off + 1, line);
        }
        // red dashed diagonals
        int dash = fade(RED, 0.8f * e);
        int start = Math.round(gap * 1.8f), end = Math.round(len * 0.94f);
        for (int i = start; i < end; i++) {
            if (((i / 6) & 1) != 0) continue;
            int o = Math.round(i * 0.7071f);
            g.fill(cx + o, cy + o, cx + o + 2, cy + o + 2, dash);
            g.fill(cx - o - 1, cy + o, cx - o + 1, cy + o + 2, dash);
            g.fill(cx + o, cy - o - 1, cx + o + 2, cy - o + 1, dash);
            g.fill(cx - o - 1, cy - o - 1, cx - o + 1, cy - o + 1, dash);
        }
        // centre dot and four little corner brackets
        boolean coreLock = lock != null && lock.core();
        int dot = fade(coreLock ? RED : WHITE, e);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, dot);
        int br = 9, bl = 4;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int x = cx + sx * br, y = cy + sy * br;
                g.fill(Math.min(x, x - sx * bl), y, Math.max(x, x - sx * bl) + 1, y + 1, line);
                g.fill(x, Math.min(y, y - sy * bl), x + 1, Math.max(y, y - sy * bl) + 1, line);
            }
        }

        // ---- lock-on triangles ----------------------------------------------------------------
        if (lock != null && lock.body() && e > 0.9f) {
            boolean blink = !ready || ((int) (time / 3f) & 1) == 0;
            if (blink) {
                int col = fade(coreLock ? RED : GREEN, e);
                int ro = Math.round(r * (coreLock ? 0.12f : 0.22f));
                int size = coreLock ? 5 : 8;
                tri(g, cx, cy - ro, size, 0, 1, col);      // above the centre, tip pointing down
                tri(g, cx, cy + ro, size, 0, -1, col);     // below, tip pointing up
                tri(g, cx - ro, cy, size, 1, 0, col);      // left, tip pointing right
                tri(g, cx + ro, cy, size, -1, 0, col);     // right, tip pointing left
            }
        }

        // ---- text -----------------------------------------------------------------------------
        final boolean creative = player.getAbilities().instabuild;
        final int cellCount = cells(player);
        final Lock lk = lock;
        final float fe = e;
        final float fcharge = charge;
        final boolean fready = ready;
        final boolean fnight = night;
        final float ftime = time;
        UiApi.overlay(g, () -> {
            float fw = UiApi.width(g), fh = UiApi.height(g);
            float lx = fw * 0.045f, rx = fw * 0.955f;
            float top = fh * 0.10f, bottom = fh * 0.90f, step = fh * 0.042f;
            int main = fade(ORANGE, fe), dim = fade(ORANGE, 0.6f * fe);

            // top left: what this is
            UiApi.text(g, UiApi.Font.LABEL, "RAILCANNON", lx, top, 0f, 1.3f, main);
            UiApi.text(g, UiApi.Font.MONO, "SIGHT  " + (fnight ? "NIGHT" : "ENHANCED"), lx, top + step, 0f, 1.0f, dim);
            UiApi.text(g, UiApi.Font.MONO, "CELLS  " + (creative ? "INF" : String.valueOf(cellCount)), lx, top + 2 * step, 0f, 1.0f,
                    (!creative && cellCount == 0) ? fade(RED, fe) : dim);

            // top right: charge
            UiApi.text(g, UiApi.Font.MONO, String.format("CHARGE %3d%%", Math.round(fcharge * 100f)), rx, top, 1f, 1.3f, main);
            boolean blink = ((int) (ftime / 4f) & 1) == 0;
            String state = fready ? "READY - RELEASE" : "CHARGING";
            int stateColor = fready ? (blink ? fade(GREEN, fe) : fade(GREEN, 0.35f * fe)) : dim;
            UiApi.text(g, UiApi.Font.MONO, state, rx, top + step, 1f, 1.0f, stateColor);

            // bottom left: range
            String range = lk != null && lk.body() ? Math.round(lk.dist()) + " M" : "---";
            UiApi.text(g, UiApi.Font.MONO, "RNG  " + range, lx, bottom, 0f, 1.0f, dim);

            // bottom right: target
            float y = bottom;
            if (lk != null && lk.body()) {
                RamielEntity t = lk.ramiel();
                if (t.shieldUp()) {
                    UiApi.text(g, UiApi.Font.MONO, "SHIELD  " + Math.round(t.shieldRatio() * 100f) + "%", rx, y, 1f, 1.0f, dim);
                    y -= step;
                }
                UiApi.text(g, UiApi.Font.MONO, "HULL  " + Math.round(t.healthRatio() * 100f) + "%", rx, y, 1f, 1.0f, dim);
                y -= step;
                boolean exposed = t.coreOpen();
                UiApi.text(g, UiApi.Font.MONO, "CORE  " + (exposed ? "EXPOSED" : "SEALED"), rx, y, 1f, 1.0f,
                        exposed ? fade(RED, fe) : dim);
                y -= step;
                UiApi.text(g, UiApi.Font.MONO, "TGT  RAMIEL", rx, y, 1f, 1.0f, fade(GREEN, fe));
            } else {
                UiApi.text(g, UiApi.Font.MONO, "TGT  NONE", rx, y, 1f, 1.0f, dim);
            }

            // bottom centre: charge bar
            float bw = fw * 0.30f, bx = (fw - bw) * 0.5f, by = fh * 0.935f;
            UiApi.box(g, bx, by, bw, 4f, fade(ORANGE, 0.25f * fe));
            UiApi.box(g, bx, by, bw * fcharge, 4f, fready ? fade(GREEN, fe) : main);
        });
    }

    // ==========================================================================================
    // Pieces
    // ==========================================================================================

    /** Black outside the circle, a tint inside it, and a 2 px ring on the edge. One row at a time. */
    private static void drawLens(GuiGraphics g, int w, int h, int cx, int cy, int r, int mask, int tint, int ring) {
        int top = cy - r, bottom = cy + r;
        if (top > 0) g.fill(0, 0, w, Math.min(top, h), mask);
        if (bottom < h) g.fill(0, Math.max(bottom, 0), w, h, mask);
        boolean tinted = (tint >>> 24) != 0;
        long outer = (long) r * r, inner = (long) (r - 2) * (r - 2);
        for (int y = Math.max(0, top); y < Math.min(h, bottom); y++) {
            double dy = y + 0.5 - cy;
            int half = (int) Math.floor(Math.sqrt(Math.max(0.0, outer - dy * dy)));
            if (cx - half > 0) g.fill(0, y, cx - half, y + 1, mask);
            if (cx + half < w) g.fill(cx + half, y, w, y + 1, mask);
            if (half <= 0) continue;
            double in2 = inner - dy * dy;
            if (in2 <= 0.0) {
                g.fill(cx - half, y, cx + half, y + 1, ring);              // top and bottom of the ring
            } else {
                int hi = (int) Math.floor(Math.sqrt(in2));
                g.fill(cx - half, y, cx - hi, y + 1, ring);
                g.fill(cx + hi, y, cx + half, y + 1, ring);
                if (tinted && hi > 0) g.fill(cx - hi, y, cx + hi, y + 1, tint);
            }
        }
    }

    /** A solid triangle whose tip is at (x, y) and which points along (dx, dy), one of the four axis directions. */
    private static void tri(GuiGraphics g, int x, int y, int size, int dx, int dy, int color) {
        for (int i = 0; i < size; i++) {
            if (dy == -1) g.fill(x - i, y + i, x + i + 1, y + i + 1, color);        // tip up, grows downwards
            else if (dy == 1) g.fill(x - i, y - i, x + i + 1, y - i + 1, color);     // tip down, grows upwards
            else if (dx == -1) g.fill(x + i, y - i, x + i + 1, y + i + 1, color);    // tip left, grows rightwards
            else g.fill(x - i, y - i, x - i + 1, y + i + 1, color);                  // tip right, grows leftwards
        }
    }

    /** The Ramiel nearest the crosshair, and whether the crosshair is on its body or its exposed core. */
    private static Lock findLock(LocalPlayer player, float partial) {
        Vec3 eye = player.getEyePosition(partial);
        Vec3 look = player.getViewVector(partial);
        Lock best = null;
        double bestAngle = Double.MAX_VALUE;
        for (RamielEntity r : FxRender.ramiels()) {
            Vec3 to = r.centre().subtract(eye);
            double d = to.length();
            if (d < 1.0) continue;
            double angle = Math.acos(Mth.clamp(look.dot(to) / d, -1.0, 1.0));
            if (angle >= bestAngle) continue;
            bestAngle = angle;
            double bodyAngle = Math.asin(Mth.clamp(RamielEntity.R * 0.75 * r.bodyScale() / d, 0.0, 1.0));
            double coreAngle = Math.asin(Mth.clamp(RamielEntity.CORE_RADIUS * 1.2 / d, 0.0, 1.0));
            boolean body = angle < bodyAngle;
            best = new Lock(r, d, body, body && r.coreOpen() && angle < coreAngle);
        }
        return best;
    }

    private static int cells(LocalPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(ModRegistry.PLASMA_CELL.get())) n += s.getCount();
        ItemStack off = p.getOffhandItem();
        if (off.is(ModRegistry.PLASMA_CELL.get())) n += off.getCount();
        return n;
    }

    /** Night or a dark cave: the scope switches to its NIGHT sight (a green tint). */
    private static boolean isDark(LocalPlayer p) {
        Level level = p.level();
        long t = level.getDayTime() % 24000L;
        return (t >= 13000L && t < 23000L) || level.getMaxLocalRawBrightness(p.blockPosition()) < 5;
    }

    private static float ease(float k) { k = Mth.clamp(k, 0f, 1f); return k * k * (3f - 2f * k); }

    /** Multiply the alpha of an ARGB colour. */
    static int fade(int argb, float k) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(k, 0f, 1f));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
