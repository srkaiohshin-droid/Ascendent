package br.com.ascendant.ui.client;

import br.com.ascendant.pack.PackManager;
import br.com.ascendant.pack.PackManifest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client-only catalog entry screen: Heroes / Packs.
 * <p>
 * This is the main landing screen of the Ascendant catalog. It lists the content
 * packs that are actually installed in the local {@code ascendant_packs} directory.
 * The manifest contract exposes metadata and content counts, not per-character
 * previews, so the screen shows exactly those installed fields and links to the
 * separate Powers, Abilities and Suits sections.
 */
@OnlyIn(Dist.CLIENT)
public final class HeroPackScreen extends Screen {

    private static final int CARD_HEIGHT = 66;
    private static final int CARD_GAP = 10;

    private final Screen parent;
    private final List<PackManifest> packs = new ArrayList<>();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    private Button powersButton;
    private Button abilitiesButton;
    private Button suitsButton;
    private Button closeButton;

    public HeroPackScreen(Screen parent) {
        super(Component.translatable("gui.ascendant.catalog.heroes.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        packs.clear();
        packs.addAll(PackManager.getInstalledPacks());
        packs.sort(Comparator.comparing(PackManifest::getId));

        int navX = width - 130;
        int navY = 45;
        int navWidth = 116;
        int navHeight = 40;

        powersButton = Button.builder(
                Component.translatableWithFallback("gui.ascendant.catalog.powers", "Powers"),
                btn -> Minecraft.getInstance().setScreen(
                        new PowerCatalogScreen(this, PowerCatalogScreen.Tab.POWERS))
        ).bounds(navX, navY, navWidth, navHeight).build();
        addRenderableWidget(powersButton);

        abilitiesButton = Button.builder(
                Component.translatableWithFallback("gui.ascendant.catalog.abilities", "Abilities"),
                btn -> Minecraft.getInstance().setScreen(
                        new PowerCatalogScreen(this, PowerCatalogScreen.Tab.ABILITIES))
        ).bounds(navX, navY + 48, navWidth, navHeight).build();
        addRenderableWidget(abilitiesButton);

        suitsButton = Button.builder(
                Component.translatableWithFallback("gui.ascendant.catalog.suits", "Suits"),
                btn -> Minecraft.getInstance().setScreen(new SuitScreen(this))
        ).bounds(navX, navY + 96, navWidth, navHeight).build();
        addRenderableWidget(suitsButton);

        closeButton = Button.builder(
                Component.translatableWithFallback("gui.ascendant.catalog.close", "Close"),
                btn -> onClose()
        ).bounds(width - 110, height - 30, 100, 20).build();
        addRenderableWidget(closeButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int listWidth = Math.max(200, (int) (width * 0.55D));
        int listX = 12;
        int listY = 45;

        graphics.drawCenteredString(font, this.title, width / 2, 16, 0xFFFFFF);
        graphics.drawString(font,
                Component.translatableWithFallback("gui.ascendant.catalog.heroes.subtitle",
                        "Heroes / Packs").getString(),
                listX, listY - 8, 0xAAAAAA, false);

        int visibleCards = Math.max(1, (height - 80) / (CARD_HEIGHT + CARD_GAP));
        int maxOffset = Math.max(0, packs.size() - visibleCards);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxOffset);

        int y = listY;
        for (int i = scrollOffset; i < packs.size() && y < height - 45; i++) {
            PackManifest pack = packs.get(i);
            boolean highlighted = i == selectedIndex;
            int cardBg = highlighted ? 0xFF405080 : 0xFF202838;
            graphics.fill(listX, y, listX + listWidth, y + CARD_HEIGHT, cardBg);
            graphics.renderOutline(listX, y, listWidth, CARD_HEIGHT,
                    highlighted ? 0xFFFFD040 : 0xFF607090);
            graphics.fill(listX, y, listX + 6, y + CARD_HEIGHT, 0xFF75E8FF);

            graphics.drawString(font, displayName(pack), listX + 14, y + 6, 0xFFFFFF, false);
            graphics.drawString(font, pack.getId() + "  v" + pack.getVersion(),
                    listX + 14, y + 18, 0xFFB0D0FF, false);
            graphics.drawString(font,
                    Component.translatableWithFallback("gui.ascendant.catalog.pack_author", "Author")
                            .getString() + ": " + pack.getAuthor(),
                    listX + 14, y + 30, 0xFFCCCCCC, false);
            graphics.drawString(font, countsText(pack), listX + 14, y + 42, 0xFFA0E0A0, false);
            graphics.drawString(font, font.plainSubstrByWidth(description(pack), listWidth - 28),
                    listX + 14, y + 54, 0xFFAAAAAA, false);

            y += CARD_HEIGHT + CARD_GAP;
        }

        if (packs.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatableWithFallback("gui.ascendant.catalog.packs_empty",
                            "No content packs installed.").getString(),
                    width / 2, height / 2 - 10, 0xFFFFC060);
            graphics.drawCenteredString(font,
                    Component.translatableWithFallback("gui.ascendant.catalog.packs_contract_note",
                            "Built-in powers, abilities and suits remain available in their sections.")
                            .getString(),
                    width / 2, height / 2 + 6, 0xFFB0D0FF);
        } else {
            String page = (Math.min(selectedIndex, packs.size() - 1) + 1) + " / " + packs.size();
            graphics.drawString(font, page, width - 40, height - 44, 0x888888, false);
        }

        graphics.drawString(font,
                Component.translatableWithFallback("gui.ascendant.catalog.packs_contract_note",
                        "Built-in powers, abilities and suits remain available in their sections.")
                        .getString(),
                12, height - 44, 0xFF888888, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            int listWidth = Math.max(200, (int) (width * 0.55D));
            int y = 45;
            for (int i = scrollOffset; i < packs.size() && y < height - 45; i++) {
                if (mouseX >= 12 && mouseX < 12 + listWidth
                        && mouseY >= y && mouseY < y + CARD_HEIGHT) {
                    selectedIndex = i;
                    return true;
                }
                y += CARD_HEIGHT + CARD_GAP;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int visibleCards = Math.max(1, (height - 80) / (CARD_HEIGHT + CARD_GAP));
        int maxOffset = Math.max(0, packs.size() - visibleCards);
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta), 0, maxOffset);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 265) {
            moveSelection(-1);
            return true;
        }
        if (keyCode == 264) {
            moveSelection(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void moveSelection(int delta) {
        if (packs.isEmpty()) {
            return;
        }
        selectedIndex = Mth.clamp(selectedIndex + delta, 0, packs.size() - 1);
        int visibleCards = Math.max(1, (height - 80) / (CARD_HEIGHT + CARD_GAP));
        int maxOffset = Math.max(0, packs.size() - visibleCards);
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + visibleCards) {
            scrollOffset = selectedIndex - visibleCards + 1;
        }
        scrollOffset = Mth.clamp(scrollOffset, 0, maxOffset);
    }

    private static String displayName(PackManifest pack) {
        return pack.getName() == null || pack.getName().isBlank() ? pack.getId() : pack.getName();
    }

    private static String description(PackManifest pack) {
        if (pack.getDescription() == null || pack.getDescription().isBlank()) {
            return Component.translatableWithFallback("gui.ascendant.catalog.pack_no_description",
                    "No description provided.").getString();
        }
        return pack.getDescription();
    }

    private static String countsText(PackManifest pack) {
        String powers = Component.translatableWithFallback("gui.ascendant.catalog.powers", "Powers").getString();
        String abilities = Component.translatableWithFallback("gui.ascendant.catalog.abilities", "Abilities").getString();
        String suits = Component.translatableWithFallback("gui.ascendant.catalog.suits", "Suits").getString();
        return powers + ": " + pack.getPowerCount() + "  "
                + abilities + ": " + pack.getAbilityCount() + "  "
                + suits + ": " + pack.getSuitCount();
    }
}
