package br.com.ascendant.ui.client;

import br.com.ascendant.network.AscendantNetwork;
import br.com.ascendant.suit.SuitDefinition;
import br.com.ascendant.suit.SuitManager;
import br.com.ascendant.suit.SuitRegistry;
import net.minecraft.client.Minecraft;
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

import static br.com.ascendant.ui.client.ComicUi.withAlpha;

@OnlyIn(Dist.CLIENT)
public final class SuitScreen extends Screen {

    private final Screen parent;
    private final List<SuitEntry> entries = new ArrayList<>();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    private Button equipButton;
    private Button unequipButton;
    private Button backButton;

    public SuitScreen(Screen parent) {
        super(Component.translatable("gui.ascendant.catalog.suits", "Suits"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        entries.clear();
        for (SuitDefinition suit : SuitRegistry.getAll()) {
            entries.add(new SuitEntry(suit));
        }
        entries.sort(Comparator.comparingInt(e -> e.suit.getRarity().ordinal()));

        int listWidth = Math.max(200, (int) (width * 0.55D));
        int listX = 12;
        int listY = 45;
        int listHeight = height - 110;

        backButton = Button.builder(
                Component.translatable("gui.ascendant.catalog.close", "Close"),
                btn -> Minecraft.getInstance().setScreen(parent)
        ).bounds(width - 110, height - 28, 100, 20).build();
        addRenderableWidget(backButton);

        equipButton = Button.builder(
                Component.translatable("gui.ascendant.catalog.equip", "Equip"),
                btn -> equipSelected()
        ).bounds(listX, height - 28, 90, 20).build();
        addRenderableWidget(equipButton);

        unequipButton = Button.builder(
                Component.translatable("gui.ascendant.catalog.unequip", "Unequip suit"),
                btn -> unequipSelected()
        ).bounds(listX + 100, height - 28, 120, 20).build();
        addRenderableWidget(unequipButton);

        updateActionButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CatalogTheme theme = CatalogTheme.current();
        ComicUi.backdrop(graphics, theme, width, height);
        graphics.drawCenteredString(font, this.title, width / 2, 14, theme.accentBright());
        String subtitle = text("gui.ascendant.catalog.suit_available", "Available loadouts")
                + "  ·  " + entries.size() + " " + text("gui.ascendant.catalog.type.suit", "suits");
        graphics.drawString(font, ComicUi.fit(font, subtitle, width - 24), width / 2 - font.width(subtitle) / 2, 26,
                theme.muted(), false);

        int listX = 12;
        int listY = 45;
        int listWidth = Math.max(200, (int) (width * 0.55D));
        int entryHeight = 44;
        int entryGap = 8;

        int visible = Math.max(1, (height - 110) / (entryHeight + entryGap));
        int maxOffset = Math.max(0, entries.size() - visible);

        int y = listY;
        for (int i = scrollOffset; i < entries.size() && y < height - 55; i++) {
            SuitEntry entry = entries.get(i);
            boolean highlighted = i == selectedIndex;
            boolean equipped = isEquipped(entry.suit.getId());

            int bg = equipped ? withAlpha(theme.accent(), 0x3C)
                    : highlighted ? withAlpha(theme.accent(), 0x20) : theme.panel();
            graphics.fill(listX, y, listX + listWidth, y + entryHeight, bg);
            graphics.renderOutline(listX, y, listWidth, entryHeight,
                    equipped ? theme.accentBright()
                            : highlighted ? theme.accent() : withAlpha(theme.muted(), 0x55));

            int rarityColor = entry.suit.getRarity().getColorHex();
            graphics.fill(listX, y, listX + 6, y + entryHeight, rarityColor | 0xFF000000);

            String name = displayName(entry.suit.getNameKey(), entry.suit.getId());
            graphics.drawString(font, ComicUi.fit(font, name, listWidth - 120),
                    listX + 14, y + 6, equipped ? theme.accentBright() : theme.ink(), false);

            String rarity = localized(entry.suit.getRarity().getTranslationKey(),
                    entry.suit.getRarity().name());
            graphics.drawString(font, ComicUi.fit(font, rarity, listWidth - 120), listX + 14, y + 18, rarityColor, false);

            String equippedText = equipped ? text("gui.ascendant.catalog.suit_equipped", "Equipped") : "";
            graphics.drawString(font, equippedText, listX + 14, y + 30, withAlpha(theme.accentBright(), 0xEE), false);

            y += entryHeight + entryGap;
        }

        if (entries.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.ascendant.catalog.no_results").getString(),
                    width / 2, height / 2, 0xFFFFC060);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            int listX = 12;
            int listY = 45;
            int entryHeight = 44;
            int entryGap = 8;
            int listWidth = Math.max(200, (int) (width * 0.55D));
            int y = listY;
            for (int i = scrollOffset; i < entries.size() && y < height - 55; i++) {
                if (mouseX >= listX && mouseX < listX + listWidth
                        && mouseY >= y && mouseY < y + entryHeight) {
                    selectedIndex = i;
                    updateActionButtons();
                    return true;
                }
                y += entryHeight + entryGap;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int entryHeight = 44;
        int entryGap = 8;
        int visible = Math.max(1, (height - 110) / (entryHeight + entryGap));
        int maxOffset = Math.max(0, entries.size() - visible);
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
        if (entries.isEmpty()) return;
        selectedIndex = net.minecraft.util.Mth.clamp(selectedIndex + delta, 0, entries.size() - 1);
        int entryHeight = 44;
        int entryGap = 8;
        int visible = Math.max(1, (height - 110) / (entryHeight + entryGap));
        int maxOffset = Math.max(0, entries.size() - visible);
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + visible) {
            scrollOffset = selectedIndex - visible + 1;
        }
        scrollOffset = net.minecraft.util.Mth.clamp(scrollOffset, 0, maxOffset);
        updateActionButtons();
    }

    private void equipSelected() {
        if (entries.isEmpty()) return;
        SuitEntry entry = entries.get(selectedIndex);
        if (isEquipped(entry.suit.getId())) {
            return;
        }
        AscendantNetwork.sendEquipSuit(entry.suit.getId(), true);
    }

    private void unequipSelected() {
        ResourceLocation equippedSuit = CatalogClientState.getEquippedSuit();
        if (equippedSuit == null) {
            return;
        }
        AscendantNetwork.sendUnequipSuit();
    }

    private boolean isEquipped(ResourceLocation suitId) {
        ResourceLocation equipped = CatalogClientState.getEquippedSuit();
        return equipped != null && equipped.equals(suitId);
    }

    private void updateActionButtons() {
        boolean hasEntries = !entries.isEmpty();
        SuitEntry selected = hasEntries ? entries.get(selectedIndex) : null;
        boolean equipped = selected != null && isEquipped(selected.suit.getId());

        equipButton.visible = hasEntries;
        unequipButton.visible = hasEntries;
        equipButton.active = hasEntries && !equipped && selected != null;
        unequipButton.active = hasEntries && equipped;
    }

    private static String displayName(String key, ResourceLocation id) {
        return net.minecraft.client.resources.language.I18n.exists(key)
                ? net.minecraft.client.resources.language.I18n.get(key)
                : humanize(id.getPath());
    }

    private static String text(String key, String fallback) {
        return net.minecraft.client.resources.language.I18n.exists(key)
                ? net.minecraft.client.resources.language.I18n.get(key)
                : fallback;
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

    private record SuitEntry(SuitDefinition suit) {
    }
}