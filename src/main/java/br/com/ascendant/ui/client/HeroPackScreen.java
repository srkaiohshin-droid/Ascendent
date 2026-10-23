package br.com.ascendant.ui.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Landing entry point for the catalog. The HEROES page is the first page of
 * {@link PowerCatalogScreen}: it lists suit loadouts and content packs with the
 * same themed cards, filters and server-authoritative actions as every other
 * tab. Keeping this as a small subclass avoids a second, divergent catalog.
 */
@OnlyIn(Dist.CLIENT)
public final class HeroPackScreen extends PowerCatalogScreen {

    public HeroPackScreen(Screen parent) {
        super(parent, Tab.HEROES);
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.ascendant.catalog.heroes.title");
    }
}
