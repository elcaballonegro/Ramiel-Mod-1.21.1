package dev.ramiel.client.gui;

import cyou.rimuru.rimulib.mc1211.client.api.UiApi;
import dev.ramiel.Setting;
import dev.ramiel.net.Payloads;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Ramiel's settings screen (opened with Shift + right click on the Mini Ramiel; the server sends
 * the current values in {@code Payloads.SettingsOpen}).
 *
 * The 17 settings in {@link Setting} are split over four tabs so everything stays readable at any
 * GUI scale. Each tab starts with a short explanation, and hovering (or keyboard-focusing) a
 * setting shows its help text near the bottom as well as in a tooltip.
 *
 * Nothing is sent until Save is pressed ({@code Payloads.SettingsSet}); the server only applies it
 * for operators or the owner of a single-player world. Without that permission the controls are
 * disabled and a note says why.
 *
 * Sliders and buttons are small subclasses of the vanilla widgets that draw themselves in this
 * screen's style, so clicking, dragging, focus and keyboard behave exactly like vanilla.
 */
public class RamielSettingsScreen extends Screen {
    // ---- layout (GUI units) ------------------------------------------------------------------
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 218;
    private static final int PAD = 10;

    // ---- style ---------------------------------------------------------------------------------
    private static final int ORANGE = 0xFFFF9A2E;
    private static final int CYAN = 0xFF5EE9FF;
    private static final int RED = 0xFFFF5A4A;
    private static final int TEXT = 0xFFE6F0FF;
    private static final int DIM = 0xFF8FA3C8;
    private static final int BORDER = 0xFF2C4E86;

    // ---- content ------------------------------------------------------------------------------
    private static final String[] TAB_NAMES = {"DESTRUCTION", "COMBAT", "BEHAVIOUR", "SYSTEM"};
    private static final String[] TAB_TEXT = {
            "The impact strike drills in, charges with a warning, then detonates. These set how much world it scars, and whether trees fall.",
            "How tough and deadly Ramiel is, and how hard its shockwaves throw you. Railcannon shots ignore the shield.",
            "Who Ramiel notices, and how the rest of the world reacts to it.",
            "Cutscenes, the distant-horizon render, and chunk loading so Ramiel works while nobody is near."
    };
    private static final Setting[][] TABS = {
            {Setting.DESTRUCTION, Setting.DEBRIS, Setting.BLAST_RADIUS, Setting.BLAST_SECONDS, Setting.INTERRUPT, Setting.TREES_FALL},
            {Setting.DAMAGE, Setting.HEALTH, Setting.AGGRESSION, Setting.SPEED, Setting.PHASE2_COOLDOWN, Setting.SHOCKWAVE},
            {Setting.AGGRO_RADIUS, Setting.IGNORE_PLAYERS, Setting.MOBS_FLEE, Setting.MOBS_ATTACK},
            {Setting.CUTSCENES, Setting.FAR_RENDER, Setting.CHUNK_ANCHOR}
    };

    // ---- state ----------------------------------------------------------------------------------
    private final double[] values;       // what the widgets edit, indexed by Setting.ordinal()
    private final double[] original;     // what the server told us, for Revert and the unsaved marker
    private final boolean canEdit;
    private int tab;
    private int left, top, panelW;

    public RamielSettingsScreen(double[] serverValues, boolean canEdit) {
        super(Component.literal("Ramiel settings"));
        Setting[] all = Setting.values();
        this.values = new double[all.length];
        for (int i = 0; i < all.length; i++) {
            this.values[i] = i < serverValues.length ? serverValues[i] : all[i].get();
        }
        this.original = this.values.clone();
        this.canEdit = canEdit;
    }

    // ==========================================================================================
    // Widgets
    // ==========================================================================================

    @Override
    protected void init() {
        panelW = Math.min(PANEL_W, this.width - 12);
        left = (this.width - panelW) / 2;
        top = Math.max(4, (this.height - PANEL_H) / 2);
        int inner = panelW - 2 * PAD;

        // tabs
        int tabGap = 4;
        int tabW = (inner - tabGap * (TABS.length - 1)) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            StyleButton b = new StyleButton(left + PAD + i * (tabW + tabGap), top + 38, tabW, 16,
                    Component.literal(TAB_NAMES[i]), btn -> { tab = index; rebuildWidgets(); });
            b.selected = (i == tab);
            addRenderableWidget(b);
        }

        // this tab's settings, two columns
        int colGap = 10;
        int colW = (inner - colGap) / 2;
        Setting[] list = TABS[tab];
        for (int i = 0; i < list.length; i++) {
            Setting s = list[i];
            int x = left + PAD + (i % 2) * (colW + colGap);
            int y = top + 88 + (i / 2) * 26;
            AbstractWidget w = s.isToggle() ? toggle(x, y, colW, s) : new SettingSlider(x, y, colW, 20, s, values[s.ordinal()]);
            w.active = canEdit;
            w.setTooltip(Tooltip.create(Component.literal(s.help)));
            addRenderableWidget(w);
        }

        // footer
        int fy = top + 192;
        if (canEdit) {
            addRenderableWidget(new StyleButton(left + PAD, fy, 96, 20, Component.literal("SAVE"), btn -> save()));
            addRenderableWidget(new StyleButton(left + PAD + 104, fy, 80, 20, Component.literal("REVERT"), btn -> {
                System.arraycopy(original, 0, values, 0, values.length);
                rebuildWidgets();
            }));
        }
        addRenderableWidget(new StyleButton(left + panelW - PAD - 80, fy, 80, 20, Component.literal("CLOSE"), btn -> onClose()));
    }

    private StyleButton toggle(int x, int y, int w, Setting s) {
        final int index = s.ordinal();
        StyleButton b = new StyleButton(x, y, w, 20, Component.literal(s.label), btn -> {
            values[index] = values[index] >= 0.5 ? 0.0 : 1.0;
            ((StyleButton) btn).on = values[index] >= 0.5;
        });
        b.label = s.label;
        b.isToggle = true;
        b.on = values[index] >= 0.5;
        b.help = s.help;
        return b;
    }

    private void save() {
        PacketDistributor.sendToServer(new Payloads.SettingsSet(values.clone()));
        onClose();
    }

    // ==========================================================================================
    // Drawing
    // ==========================================================================================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        // panel
        g.fill(left - 1, top - 1, left + panelW + 1, top + PANEL_H + 1, BORDER);
        g.fill(left, top, left + panelW, top + PANEL_H, 0xF2060A14);
        // header band and the orange rule under it
        g.fill(left, top, left + panelW, top + 34, 0xFF0A1630);
        g.fill(left, top + 34, left + panelW, top + 35, ORANGE);
        // corner brackets
        int c = 8;
        g.fill(left - 2, top - 2, left + c, top - 1, ORANGE);
        g.fill(left - 2, top - 2, left - 1, top + c, ORANGE);
        g.fill(left + panelW - c, top + PANEL_H + 1, left + panelW + 2, top + PANEL_H + 2, ORANGE);
        g.fill(left + panelW + 1, top + PANEL_H - c, left + panelW + 2, top + PANEL_H + 2, ORANGE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);   // background, then all widgets
        Font font = Minecraft.getInstance().font;
        int textW = panelW - 2 * PAD;

        // header: pattern tag and unsaved marker (vanilla font, top right)
        String tag = "PATTERN: BLUE";
        g.drawString(font, tag, left + panelW - PAD - font.width(tag), top + 8, ORANGE, false);
        if (canEdit && !Arrays.equals(values, original)) {
            String unsaved = "* UNSAVED";
            g.drawString(font, unsaved, left + panelW - PAD - font.width(unsaved), top + 20, CYAN, false);
        }

        // what this tab is about
        g.drawWordWrap(font, Component.literal(TAB_TEXT[tab]), left + PAD, top + 60, textW, DIM);

        // help for the setting under the mouse / keyboard focus
        String help = null;
        for (var child : children()) {
            if (child instanceof SettingSlider s && s.isHoveredOrFocused()) help = s.setting.help;
            else if (child instanceof StyleButton b && b.help != null && b.isHoveredOrFocused()) help = b.help;
        }
        g.fill(left + PAD, top + 164, left + panelW - PAD, top + 165, BORDER);
        g.drawWordWrap(font, Component.literal(help != null ? help : "Hover a setting for details."), left + PAD, top + 169, textW,
                help != null ? TEXT : DIM);

        if (!canEdit) {
            g.drawString(font, "READ-ONLY: operators (or the world owner) only.", left + PAD, top + 198, RED, false);
        }

        // the big title uses Rimulib's text, which lays itself out in its own frame, so place it by fractions of the screen
        final float fx = (left + 12f) / this.width;
        final float fyTitle = (top + 21f) / this.height;
        final float fySub = (top + 31f) / this.height;
        UiApi.overlay(g, () -> {
            float fw = UiApi.width(g), fh = UiApi.height(g);
            UiApi.text(g, UiApi.Font.DISPLAY, "RAMIEL", fw * fx, fh * fyTitle, 0f, 1.6f, ORANGE);
            UiApi.text(g, UiApi.Font.LABEL, "ANGEL CONTROL  //  SETTINGS", fw * fx, fh * fySub, 0f, 0.9f, 0xFF8FB4FF);
        });
    }

    // ==========================================================================================
    // Value helpers
    // ==========================================================================================

    private static double norm(Setting s, double real) {
        return Mth.clamp((real - s.min) / (s.max - s.min), 0.0, 1.0);
    }

    private static double snap(Setting s, double real) {
        double snapped = Math.round(real / s.step) * s.step;
        snapped = Math.round(snapped * 10000.0) / 10000.0;
        return Mth.clamp(snapped, s.min, s.max);
    }

    /** How a value reads on its slider. */
    private static String format(Setting s, double v) {
        if (s.isToggle()) return v >= 0.5 ? "ON" : "OFF";
        switch (s) {
            case BLAST_RADIUS: case AGGRO_RADIUS:
                return Math.round(v) + " blocks";
            case BLAST_SECONDS: case PHASE2_COOLDOWN:
                return Math.round(v) + " s";
            case CUTSCENES:
                switch ((int) Math.round(v)) {
                    case 0: return "Off";
                    case 1: return "Key moments";
                    default: return "Every form";
                }
            default:
                return String.format(Locale.ROOT, "%.2fx", v);
        }
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    // ==========================================================================================
    // Widgets in this screen's style
    // ==========================================================================================

    /** Tabs, toggles and the footer buttons. */
    private static final class StyleButton extends Button {
        boolean selected;       // a tab that is currently open
        boolean isToggle;       // draw "label ........ ON/OFF"
        boolean on;
        String label;
        String help;

        StyleButton(int x, int y, int w, int h, Component message, OnPress press) {
            super(x, y, w, h, message, press, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hot = active && isHoveredOrFocused();
            int border = !active ? 0xFF3A4254 : (selected || hot) ? ORANGE : BORDER;
            int fill = selected ? 0xFF3A2410 : hot ? 0xFF14264A : 0xFF0B1630;
            panel(g, getX(), getY(), getWidth(), getHeight(), fill, border);
            Font font = Minecraft.getInstance().font;
            int ty = getY() + (getHeight() - 8) / 2;
            if (isToggle) {
                g.drawString(font, label, getX() + 6, ty, active ? TEXT : DIM, false);
                String v = on ? "ON" : "OFF";
                g.drawString(font, v, getX() + getWidth() - 6 - font.width(v), ty, !active ? DIM : on ? CYAN : RED, false);
            } else {
                g.drawCenteredString(font, getMessage(), getX() + getWidth() / 2, ty, !active ? DIM : selected ? ORANGE : TEXT);
            }
        }
    }

    /** A slider that shows its label on the left and its value on the right. */
    private final class SettingSlider extends AbstractSliderButton {
        private final Setting setting;

        SettingSlider(int x, int y, int w, int h, Setting setting, double current) {
            super(x, y, w, h, Component.empty(), norm(setting, current));
            this.setting = setting;
            updateMessage();
        }

        private double real() {
            return snap(setting, setting.min + this.value * (setting.max - setting.min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(setting.label + ": " + format(setting, real())));
        }

        /** Called while dragging: snap to the setting's step and store it. */
        @Override
        protected void applyValue() {
            double real = real();
            values[setting.ordinal()] = real;
            this.value = norm(setting, real);
        }

        /** Left/right arrows move one whole step (vanilla's tiny steps would round back to the same value). */
        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (isFocused() && active && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT)) {
                double next = real() + (keyCode == GLFW.GLFW_KEY_RIGHT ? setting.step : -setting.step);
                this.value = norm(setting, next);
                applyValue();
                updateMessage();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hot = active && isHoveredOrFocused();
            panel(g, getX(), getY(), getWidth(), getHeight(), 0xFF0B1630, !active ? 0xFF3A4254 : hot ? ORANGE : BORDER);
            int fillW = (int) Math.round((getWidth() - 2) * this.value);
            if (fillW > 0) g.fill(getX() + 1, getY() + 1, getX() + 1 + fillW, getY() + getHeight() - 1, active ? 0xFF1B3F8F : 0xFF222A3A);
            int hx = getX() + 1 + Math.min(fillW, getWidth() - 4);
            g.fill(hx, getY() + 1, hx + 2, getY() + getHeight() - 1, !active ? DIM : hot ? ORANGE : CYAN);
            Font font = Minecraft.getInstance().font;
            int ty = getY() + (getHeight() - 8) / 2;
            String v = format(setting, real());
            g.drawString(font, setting.label, getX() + 6, ty, active ? TEXT : DIM, false);
            g.drawString(font, v, getX() + getWidth() - 6 - font.width(v), ty, active ? CYAN : DIM, false);
        }
    }
}
