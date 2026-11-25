package dev.ramiel.client;

import cyou.rimuru.rimulib.mc1211.client.api.FilmApi;
import dev.ramiel.ModRegistry;
import dev.ramiel.RamielMod;
import dev.ramiel.client.film.Films;
import dev.ramiel.client.fx.FxRender;
import dev.ramiel.client.fx.ScreenEffects;
import dev.ramiel.client.gui.ScopeLayer;
import dev.ramiel.client.gui.WarningHud;
import dev.ramiel.client.render.MiniRamielRenderer;
import dev.ramiel.client.render.RamielRenderer;
import dev.ramiel.client.render.ToppleRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Everything that only exists on the client. {@link RamielMod} calls {@link #init} when the game
 * is running with a screen; a dedicated server never loads this class.
 *
 *   mod bus   : entity renderer, Mini Ramiel item renderer, HUD layers, Rimulib set-up
 *   game bus  : world rendering, the per-tick update, the scope (zoom / hide hand / hide crosshair)
 *
 * Where the real work is:  client/render (models), client/fx (effects), client/film (cutscenes),
 * client/gui (scope, warning, settings screen).
 */
public final class RamielClient {
    private RamielClient() {}

    /** Built on first use: the item renderer needs Minecraft's render dispatcher, which does not exist yet at registration time. */
    private static MiniRamielRenderer miniRenderer;

    public static void init(IEventBus modBus) {
        modBus.addListener(RamielClient::registerRenderers);
        modBus.addListener(RamielClient::registerItemExtensions);
        modBus.addListener(RamielClient::registerGuiLayers);
        modBus.addListener(RamielClient::clientSetup);

        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(RamielClient::onRenderLevel);
        game.addListener(RamielClient::onClientTick);
        game.addListener(RamielClient::onLoggingOut);
        game.addListener(RamielClient::onFov);
        game.addListener(RamielClient::onRenderHand);
        game.addListener(RamielClient::onGuiLayer);
    }

    // ==========================================================================================
    // Mod bus
    // ==========================================================================================

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.RAMIEL.get(), RamielRenderer::new);
        event.registerEntityRenderer(ModRegistry.TOPPLE.get(), ToppleRenderer::new);
    }

    private static void registerItemExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (miniRenderer == null) miniRenderer = new MiniRamielRenderer();
                return miniRenderer;
            }
        }, ModRegistry.MINI_RAMIEL.get());
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(RamielMod.id("scope"), ScopeLayer.INSTANCE);
        event.registerAboveAll(RamielMod.id("blast_warning"), WarningHud.INSTANCE);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ScreenEffects.init();                                  // camera shake, impact frames, red pulse (Rimulib FxApi)
            FilmApi.enableKey();                                   // the shared "skip cutscene" key
            FilmApi.holdInput(() -> Films.holdsInput());           // key films freeze movement
            FilmApi.hideHand(() -> FilmApi.active());              // no floating hand during a cutscene
        });
    }

    // ==========================================================================================
    // Game bus
    // ==========================================================================================

    private static void onRenderLevel(RenderLevelStageEvent event) {
        FxRender.render(event);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        FxRender.tick();
        Films.tick();
        ScopeLayer.tick();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FxRender.clear();
        Films.clear();
    }

    /** Scope zoom. This event only reports the player's own field-of-view modifier. */
    private static void onFov(ComputeFovModifierEvent event) {
        if (event.getPlayer() == Minecraft.getInstance().player) {
            event.setNewFovModifier(event.getNewFovModifier() * ScopeLayer.zoom());
        }
    }

    private static void onRenderHand(RenderHandEvent event) {
        if (ScopeLayer.isScoped()) event.setCanceled(true);
    }

    private static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (ScopeLayer.hidesCrosshair() && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) event.setCanceled(true);
    }
}
