package dev.ramiel.client;

import dev.ramiel.client.film.Films;
import dev.ramiel.client.fx.FarRender;
import dev.ramiel.client.fx.FxState;
import dev.ramiel.client.gui.RamielSettingsScreen;
import dev.ramiel.net.Payloads;
import net.minecraft.client.Minecraft;

/**
 * The client end of every network message. Kept in its own class so the common Payloads class
 * never loads client-only code on a dedicated server.
 */
public final class ClientNet {
    private ClientNet() {}

    public static void fx(Payloads.Fx payload) { FxState.handle(payload); }

    public static void film(Payloads.Film payload) { Films.play(payload.kind(), payload.entityId()); }

    public static void beacon(Payloads.Beacon payload) { FarRender.update(payload); }

    public static void openSettings(Payloads.SettingsOpen payload) {
        Minecraft.getInstance().setScreen(new RamielSettingsScreen(payload.values(), payload.canEdit()));
    }
}
