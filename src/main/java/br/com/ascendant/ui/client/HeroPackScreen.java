package br.com.ascendant.ui.client;

import br.com.ascendant.Ascendant;
import br.com.ascendant.network.AscendantNetwork;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.suit.SuitDefinition;
import br.com.ascendant.suit.SuitRegistry;
import br.com.ascendant.suit.SuitManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Client-only catalog entry screen: Heroes / Packs.
 * <p>
 * This is the main landing screen of the Ascendant catalog. It presents the
 * installed/registered hero suits (packs) as selectable cards, each summarizing
 * the powers and abilities granted by that suit. From here the player can
 * navigate to the Powers, Abilities and Suits sections.
 */
@OnlyIn(Dist.CLIENT)
public final class HeroPackScreen extends Screen {

    private final Screen parent;
    private final List<SuitCard> suitCards = new ArrayList<>();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    private Button powersButton;
    private Button abilitiesButton;
    private Button suitsButton;
    private Button equipButton;
    private Button unequipButton;

    public HeroPackScreen(Screen parent) {
        super(Component.translatable("gui.ascendant.catalog.heroes.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        suitCards.clear();
        for (SuitDefinition suit : SuitRegistry.getAll()) {
            suitCards.add(new SuitCard(suit));
        }
        suitCards.sort(Comparator.comparingInt(c -> c.suit.getRarity().ordinal()));

        int navX = width - 130;
        int navY = 45;
        int navWidth = 116;
        int navHeight = 40;

        powersButton = Button.builder(
                Component.translatable("gui.ascendant.catalog.powers", "Powers"),
                btn -> Minecraft.getInstance().setScreen(new PowerCatalogScreen(this))
        ).bounds(navX, navY, navWidth, navHeight).build();
        addRenderableWidget(powersButton);

        abilitiesButton = Button.builder(
                Component.translatable("gui.ascendant.catalog.abilities", "Abilities"),
                btn -> Minecraft.getInstance().setScreen(new PowerCatalogScreen(this))
        ).bounds(navX, navY + 48, navWidth, navHeight).build();
        addRenderableWidget(abilitiesButton);

        suitsButton = Button.builder(
                Component.translatable("gui.ascendant.catalog.suits", "Suits"),
                btn -> Minecraft.getInstance().setScreen(new SuitScreen(this))
        ).bounds(navX, navY + 96, navWidth, navHeight).build();
        addRenderableWidget(suitsButton);

        int buttonY = height - 30;
        equipButton = Button.builder(
                Component.translatable("gui.ascendant.catalog.equip", "Equip"),
                btn -> equipSelected()
        ).bounds(12, buttonY, 90, 20).build();
        addRenderableWidget(equipButton);

        unequipButton = Button.builder(
                Component.translatable("gui.ascendant.catalog.unequip", "Unequip suit"),
                btn -> unequipSelected()
        ).bounds(110, buttonY, 120, 20).build();
        addRenderableWidget(unequipButton);

        updateActionButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int listWidth = Math.max(200, (int) (width * 0.55D));
        int listX = 12;
        int listY = 45;
        int cardHeight = 78;
        int cardGap = 10;

        graphics.drawCenteredString(font, this.title, width / 2, 16, 0xFFFFFF);
        graphics.drawString(font,
                Component.translatable("gui.ascendant.catalog.heroes.subtitle", "Heroes / Packs").getString(),
                listX, listY - 8, 0xAAAAAA, false);

        int visibleCards = Math.max(1, (height - 75) / (cardHeight + cardGap));
        int maxOffset = Math.max(0, suitCards.size() - visibleCards);

        int y = listY;
        for (int i = scrollOffset; i < suitCards.size() && y < height - 40; i++) {
            SuitCard card = suitCards.get(i);
            boolean highlighted = i == selectedIndex;
            int cardY = y;
            int cardX = listX;

            int cardBg = highlighted ? 0xFF405080 : 0xFF202838;
            graphics.fill(cardX, cardY, cardX + listWidth, cardY + cardHeight, cardBg);
            graphics.renderOutline(cardX, cardY, listWidth, cardHeight,
                    highlighted ? 0xFFFFD040 : 0xFF607090);

            int rarityColor = card.suit.getRarity().getColorHex();
            graphics.fill(cardX, cardY, cardX + 6, cardY + cardHeight, rarityColor | 0xFF000000);

            String name = displayName(card.suit.getNameKey(), card.suit.getId());
            graphics.drawString(font, name, cardX + 14, cardY + 6, 0xFFFFFF, false);

            String rarity = localized(card.suit.getRarity().getTranslationKey(),
                    card.suit.getRarity().name());
            graphics.drawString(font, rarity, cardX + 14, cardY + 18, rarityColor, false);

            String desc = description(card.suit.getDescriptionKey());
            graphics.drawString(font, desc, cardX + 14, cardY + 30, 0xCCCCCC, false);

            StringBuilder powers = new StringBuilder();
            boolean first = true;
            for (ResourceLocation pId : card.suit.getGrantedPowers()) {
                if (!first) powers.append(", ");
                first = false;
                powers.append(humanize(pId.getPath()));
            }
            String powersText = "Powers: " + (powers.isEmpty() ? "none" : powers);
            graphics.drawString(font, powersText, cardX + 14, cardY + 44, 0xA0D0FF, false);

            StringBuilder abilities = new StringBuilder();
            first = true;
            for (ResourceLocation aId : card.suit.getGrantedAbilities()) {
                if (!first) abilities.append(", ");
                first = false;
                abilities.append(humanize(aId.getPath()));
            }
            String abilitiesText = "Abilities: " + (abilities.isEmpty() ? "none" : abilities);
            graphics.drawString(font, abilitiesText, cardX + 14, cardY + 56, 0xD0A0FF, false);

            y += cardHeight + cardGap;
        }

        if (suitCards.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.ascendant.catalog.no_results").getString(),
                    width / 2, height / 2, 0xFFFFC060);
        }

        if (!suitCards.isEmpty()) {
            String page = (Math.min(selectedIndex, suitCards.size() - 1) + 1) + " / " + suitCards.size();
            graphics.drawString(font, page, width - 130, height - 32, 0x888888, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            int listWidth = Math.max(200, (int) (width * 0.55D));
            int listX = 12;
            int listY = 45;
            int cardHeight = 78;
            int cardGap = 10;
            int y = listY;
            for (int i = scrollOffset; i < suitCards.size() && y < height - 40; i++) {
                if (mouseX >= listX && mouseX < listX + listWidth
                        && mouseY >= y && mouseY < y + cardHeight) {
                    selectedIndex = i;
                    updateActionButtons();
                    return true;
                }
                y += cardHeight + cardGap;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int cardHeight = 78;
        int cardGap = 10;
        int visibleCards = Math.max(1, (height - 75) / (cardHeight + cardGap));
        int maxOffset = Math.max(0, suitCards.size() - visibleCards);
        scrollOffset = net.minecraft.util.Mth.clamp(scrollOffset - (int) Math.signum(delta), 0, maxOffset);
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
        if (keyCode == 257 || keyCode == 335) {
            equipSelected();
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
        if (suitCards.isEmpty()) return;
        selectedIndex = net.minecraft.util.Mth.clamp(selectedIndex + delta, 0, suitCards.size() - 1);
        int cardHeight = 78;
        int cardGap = 10;
        int visibleCards = Math.max(1, (height - 75) / (cardHeight + cardGap));
        int maxOffset = Math.max(0, suitCards.size() - visibleCards);
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + visibleCards) {
            scrollOffset = selectedIndex - visibleCards + 1;
        }
        scrollOffset = net.minecraft.util.Mth.clamp(scrollOffset, 0, maxOffset);
        updateActionButtons();
    }

    private void equipSelected() {
        if (suitCards.isEmpty()) return;
        SuitCard card = suitCards.get(selectedIndex);
        AscendantNetwork.sendEquipSuit(card.suit.getId(), true);
    }

    private void unequipSelected() {
        AscendantNetwork.sendUnequipSuit();
    }

    private void updateActionButtons() {
        boolean hasSuit = !suitCards.isEmpty();
        equipButton.visible = hasSuit;
        unequipButton.visible = hasSuit;
        equipButton.active = hasSuit;
        unequipButton.active = hasSuit;
    }

    private static String displayName(String key, ResourceLocation id) {
        return net.minecraft.client.resources.language.I18n.exists(key)
                ? net.minecraft.client.resources.language.I18n.get(key)
                : humanize(id.getPath());
    }

    private static String description(String key) {
        if (net.minecraft.client.resources.language.I18n.exists(key)) {
            return net.minecraft.client.resources.language.I18n.get(key);
        }
        return "No description available.";
    }

    private static String localized(String key, String fallback) {
        return net.minecraft.client.resources.language.I18n.exists(key)
                ? net.minecraft.client.resources.language.I18n.get(key)
                : fallback;
    }

    private static String humanize(String path) {
        String[] parts = path.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return result.toString();
    }

    private record SuitCard(SuitDefinition suit) {
    }
}