package br.com.ascendant.ui.client;

import br.com.ascendant.Ascendant;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Ascendant.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AscendantClientControls {
    private static final KeyMapping OPEN_CATALOG = new KeyMapping(
            "key.ascendant.open_catalog", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6,
            "key.categories.ascendant.controls");
    private static final KeyMapping OPEN_RADIAL = new KeyMapping(
            "key.ascendant.open_radial", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
            "key.categories.ascendant.controls");
    private static final KeyMapping ACTIVATE_SELECTED = new KeyMapping(
            "key.ascendant.activate_selected", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
            "key.categories.ascendant.controls");
    private static final KeyMapping TOGGLE_SELECTED = new KeyMapping(
            "key.ascendant.toggle_selected", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B,
            "key.categories.ascendant.controls");
    private static final KeyMapping NEXT_ENTRY = new KeyMapping(
            "key.ascendant.next_entry", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN,
            "key.categories.ascendant.controls");
    private static final KeyMapping PREVIOUS_ENTRY = new KeyMapping(
            "key.ascendant.previous_entry", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP,
            "key.categories.ascendant.controls");

    private static boolean clientTickListenerRegistered;

    private AscendantClientControls() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CATALOG);
        event.register(OPEN_RADIAL);
        event.register(ACTIVATE_SELECTED);
        event.register(TOGGLE_SELECTED);
        event.register(NEXT_ENTRY);
        event.register(PREVIOUS_ENTRY);
        if (!clientTickListenerRegistered) {
            MinecraftForge.EVENT_BUS.addListener(AscendantClientControls::onClientTick);
            clientTickListenerRegistered = true;
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            if (minecraft.screen instanceof WorkshopMarketplaceScreen || minecraft.screen instanceof PowerCatalogScreen || minecraft.screen instanceof RadialMenuScreen) {
                minecraft.setScreen(null);
            }
            drainClicks();
            return;
        }

        while (OPEN_CATALOG.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new WorkshopMarketplaceScreen(null));
            } else if (minecraft.screen instanceof WorkshopMarketplaceScreen || minecraft.screen instanceof HeroPackScreen || minecraft.screen instanceof PowerCatalogScreen) {
                minecraft.setScreen(null);
            }
        }
        while (OPEN_RADIAL.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new RadialMenuScreen(null));
            } else if (minecraft.screen instanceof RadialMenuScreen) {
                minecraft.setScreen(null);
            }
        }

        if (minecraft.screen instanceof PowerCatalogScreen catalog) {
            if (catalog.isSearchFocused()) {
                drainActionClicks();
            } else {
                while (PREVIOUS_ENTRY.consumeClick()) catalog.moveSelection(-1);
                while (NEXT_ENTRY.consumeClick()) catalog.moveSelection(1);
                while (ACTIVATE_SELECTED.consumeClick()) catalog.activateSelected();
                while (TOGGLE_SELECTED.consumeClick()) catalog.toggleSelected();
            }
        } else if (minecraft.screen instanceof RadialMenuScreen radial) {
            while (PREVIOUS_ENTRY.consumeClick()) radial.nextPage(-1);
            while (NEXT_ENTRY.consumeClick()) radial.nextPage(1);
            while (ACTIVATE_SELECTED.consumeClick()) radial.activateSelected();
            while (TOGGLE_SELECTED.consumeClick()) radial.toggleSelected();
        } else {
            drainActionClicks();
        }
    }

    private static void drainClicks() {
        while (OPEN_CATALOG.consumeClick()) { }
        while (OPEN_RADIAL.consumeClick()) { }
        drainActionClicks();
    }

    private static void drainActionClicks() {
        while (ACTIVATE_SELECTED.consumeClick()) { }
        while (TOGGLE_SELECTED.consumeClick()) { }
        while (NEXT_ENTRY.consumeClick()) { }
        while (PREVIOUS_ENTRY.consumeClick()) { }
    }
}
