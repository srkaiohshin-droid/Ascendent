package br.com.ascendant.ui.client;

import br.com.ascendant.catalog.CatalogInteraction;
import br.com.ascendant.core.AbilityCategory;
import br.com.ascendant.core.PowerCategory;
import br.com.ascendant.network.AscendantNetwork;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.suit.SuitDefinition;
import br.com.ascendant.suit.SuitRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class PowerCatalogScreen extends Screen {
    enum Tab {
        POWERS,
        ABILITIES,
        SUITS
    }

    private enum EntryType {
        POWER,
        ABILITY,
        SUIT
    }

    private record Entry(EntryType type, ResourceLocation id, String name, String description,
                         String category, String categoryKey, boolean functional, int cooldown,
                         String metadata, CatalogInteraction.Action action) {
    }

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private List<Entry> visibleEntries = List.of();
    private Tab tab = Tab.POWERS;
    private String categoryFilter = "all";
    private String searchQuery = "";
    private String feedback = "";
    private boolean filterDirty;
    private int selectedIndex;
    private int scrollOffset;

    private EditBox searchBox;
    private Button categoryButton;
    private Button executeButton;
    private Button unequipButton;

    public PowerCatalogScreen(Screen parent) {
        this(parent, Tab.POWERS);
    }

    PowerCatalogScreen(Screen parent, Tab initialTab) {
        super(Component.translatable("gui.ascendant.catalog.title"));
        this.parent = parent;
        this.tab = initialTab;
    }

    @Override
    protected void init() {
        int listWidth = Math.max(150, (int) (width * 0.43D));
        int tabWidth = Math.max(66, (listWidth - 12) / 3);
        int tabY = 25;
        addRenderableWidget(Button.builder(label("gui.ascendant.catalog.powers", "Powers"), button -> changeTab(Tab.POWERS))
                .bounds(12, tabY, tabWidth, 20).build());
        addRenderableWidget(Button.builder(label("gui.ascendant.catalog.abilities", "Abilities"), button -> changeTab(Tab.ABILITIES))
                .bounds(16 + tabWidth, tabY, tabWidth, 20).build());
        addRenderableWidget(Button.builder(label("gui.ascendant.catalog.suits", "Suits"), button -> changeTab(Tab.SUITS))
                .bounds(20 + tabWidth * 2, tabY, tabWidth, 20).build());

        searchBox = new EditBox(font, 12, 51, listWidth, 20,
                label("gui.ascendant.catalog.search", "Search by name, ID or category"));
        searchBox.setMaxLength(96);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value;
            filterDirty = true;
        });
        addRenderableWidget(searchBox);

        categoryButton = Button.builder(categoryLabel(), button -> cycleCategory())
                .bounds(12, 76, listWidth, 20).build();
        addRenderableWidget(categoryButton);

        int detailX = 24 + listWidth;
        int detailWidth = Math.max(130, width - detailX - 12);
        executeButton = Button.builder(label("gui.ascendant.catalog.execute", "Use selection"),
                        button -> executeSelected(null))
                .bounds(detailX, height - 28, Math.min(132, detailWidth), 20).build();
        addRenderableWidget(executeButton);
        unequipButton = Button.builder(label("gui.ascendant.catalog.unequip", "Unequip suit"),
                        button -> unequipSuit())
                .bounds(detailX + Math.min(138, detailWidth), height - 28,
                        Math.max(90, detailWidth - 138), 20).build();
        addRenderableWidget(unequipButton);
        addRenderableWidget(Button.builder(label("gui.ascendant.catalog.close", "Close"), button -> onClose())
                .bounds(width - 82, 5, 70, 18).build());

        rebuildEntries();
        applyFilter();
        updateActionButtons();
    }

    private void changeTab(Tab next) {
        if (tab == next) {
            return;
        }
        tab = next;
        categoryFilter = "all";
        selectedIndex = 0;
        scrollOffset = 0;
        feedback = "";
        rebuildEntries();
        applyFilter();
        categoryButton.setMessage(categoryLabel());
        updateActionButtons();
    }

    private void cycleCategory() {
        List<String> categories = categoriesForTab();
        int current = categories.indexOf(categoryFilter);
        categoryFilter = categories.get((current + 1) % categories.size());
        selectedIndex = 0;
        scrollOffset = 0;
        filterDirty = true;
        categoryButton.setMessage(categoryLabel());
    }

    private List<String> categoriesForTab() {
        List<String> values = new ArrayList<>();
        values.add("all");
        switch (tab) {
            case POWERS -> {
                for (PowerCategory value : PowerCategory.values()) values.add(value.name());
            }
            case ABILITIES -> {
                for (AbilityCategory value : AbilityCategory.values()) values.add(value.name());
            }
            case SUITS -> {
                SuitRegistry.getAll().stream().map(suit -> suit.getRarity().name())
                        .distinct().sorted().forEach(values::add);
            }
        }
        return values;
    }

    private Component categoryLabel() {
        String value = categoryFilter.equals("all") ? "gui.ascendant.catalog.all_categories" : null;
        String fallback = categoryFilter.equals("all") ? "All categories" : categoryFilter;
        if (value == null && tab == Tab.POWERS) {
            try {
                PowerCategory category = PowerCategory.valueOf(categoryFilter);
                return Component.literal(localized(category.getTranslationKey(), category.getDisplayName()));
            } catch (IllegalArgumentException ignored) {
                // Suit rarity has no power category translation key.
            }
        }
        if (value == null && tab == Tab.ABILITIES) {
            try {
                AbilityCategory category = AbilityCategory.valueOf(categoryFilter);
                return Component.literal(localized(category.getTranslationKey(), category.getDisplayName()));
            } catch (IllegalArgumentException ignored) {
                // Invalid filters fall back to their identifier instead of breaking the screen.
            }
        }
        return value == null ? Component.literal(fallback) : label(value, fallback);
    }

    private void rebuildEntries() {
        entries.clear();
        switch (tab) {
            case POWERS -> PowerRegistry.getAll().stream()
                    .sorted(Comparator.comparing(power -> power.getId().toString()))
                    .map(this::entryFor).forEach(entries::add);
            case ABILITIES -> AbilityRegistry.getAll().stream()
                    .sorted(Comparator.comparing(ability -> ability.getId().toString()))
                    .map(this::entryFor).forEach(entries::add);
            case SUITS -> SuitRegistry.getAll().stream()
                    .sorted(Comparator.comparing(suit -> suit.getId().toString()))
                    .map(this::entryFor).forEach(entries::add);
        }
        filterDirty = true;
    }

    private Entry entryFor(PowerDefinition power) {
        return new Entry(EntryType.POWER, power.getId(), displayName(power.getNameKey(), power.getId()),
                description(power.getDescriptionKey()), power.getCategory().name(),
                power.getCategory().getTranslationKey(), power.isFunctional(), power.getCooldownTicks(),
                power.getType().name(), CatalogInteraction.forPower(power));
    }

    private Entry entryFor(AbilityDefinition ability) {
        String requirement = ability.isUniversal() ? text("gui.ascendant.catalog.universal", "Universal")
                : text("gui.ascendant.catalog.requires_power", "Requires power") + ": " + ability.getPowerId();
        return new Entry(EntryType.ABILITY, ability.getId(), displayName(ability.getNameKey(), ability.getId()),
                description(ability.getDescriptionKey()), ability.getCategory().name(),
                ability.getCategory().getTranslationKey(), ability.isFunctional(), ability.getCooldownTicks(),
                ability.getTrigger().name() + " | " + requirement, CatalogInteraction.forAbility(ability));
    }

    private Entry entryFor(SuitDefinition suit) {
        String details = suit.getRarity().name() + " | " + suit.getUniverse() + " | " + suit.getAuthor();
        return new Entry(EntryType.SUIT, suit.getId(), displayName(suit.getNameKey(), suit.getId()),
                description(suit.getDescriptionKey()), suit.getRarity().name(), "gui.ascendant.catalog.rarity",
                true, 0, details, CatalogInteraction.forSuit(suit.getId()));
    }

    private void applyFilter() {
        visibleEntries = entries.stream()
                .filter(entry -> CatalogInteraction.matches(searchQuery, entry.id().toString(), entry.name(), entry.category()))
                .filter(entry -> CatalogInteraction.matchesCategory(categoryFilter, entry.category()))
                .toList();
        selectedIndex = visibleEntries.isEmpty() ? -1 : Mth.clamp(selectedIndex, 0, visibleEntries.size() - 1);
        filterDirty = false;
        updateActionButtons();
    }

    private void updateActionButtons() {
        Entry selected = selectedEntry();
        executeButton.active = selected != null && selected.action().type() != CatalogInteraction.ActionType.NONE;
        unequipButton.visible = tab == Tab.SUITS;
        unequipButton.active = tab == Tab.SUITS;
        if (selected == null) {
            executeButton.setMessage(label("gui.ascendant.catalog.execute", "Use selection"));
            return;
        }
        String key = switch (selected.action().type()) {
            case ACTIVATE_POWER, ACTIVATE_ABILITY -> "gui.ascendant.catalog.activate";
            case TOGGLE_POWER -> "gui.ascendant.catalog.toggle";
            case EQUIP_SUIT -> "gui.ascendant.catalog.equip";
            case NONE -> "gui.ascendant.catalog.unavailable";
        };
        String fallback = switch (selected.action().type()) {
            case ACTIVATE_POWER, ACTIVATE_ABILITY -> "Activate";
            case TOGGLE_POWER -> "Toggle";
            case EQUIP_SUIT -> "Equip";
            case NONE -> "Unavailable";
        };
        executeButton.setMessage(label(key, fallback));
    }

    public void moveSelection(int delta) {
        if (filterDirty) applyFilter();
        if (visibleEntries.isEmpty()) return;
        selectedIndex = Math.floorMod(selectedIndex + delta, visibleEntries.size());
        ensureSelectionVisible();
        updateActionButtons();
    }

    public void activateSelected() {
        executeSelected(Boolean.FALSE);
    }

    public void toggleSelected() {
        executeSelected(Boolean.TRUE);
    }

    public boolean isSearchFocused() {
        return searchBox != null && searchBox.isFocused();
    }

    private void executeSelected(Boolean toggleOnly) {
        if (filterDirty) applyFilter();
        Entry selected = selectedEntry();
        if (selected == null) {
            feedback = text("gui.ascendant.catalog.no_selection", "Select an entry first.");
            return;
        }
        CatalogInteraction.Action action = selected.action();
        boolean isToggle = action.type() == CatalogInteraction.ActionType.TOGGLE_POWER;
        if (toggleOnly != null && toggleOnly != isToggle) {
            feedback = toggleOnly
                    ? text("gui.ascendant.catalog.not_toggle", "The selection is not a toggle power.")
                    : text("gui.ascendant.catalog.use_toggle_key", "Use the toggle key for this power.");
            return;
        }
        switch (action.type()) {
            case ACTIVATE_POWER -> AscendantNetwork.sendActivatePower(action.id());
            case TOGGLE_POWER -> AscendantNetwork.sendTogglePowerRequest(action.id());
            case ACTIVATE_ABILITY -> AscendantNetwork.sendActivateAbility(action.id());
            case EQUIP_SUIT -> AscendantNetwork.sendEquipSuit(action.id(), true);
            case NONE -> {
                feedback = unavailableReason(selected);
                return;
            }
        }
        feedback = text("gui.ascendant.catalog.server_validation",
                "Request sent; the server validates ownership, cooldown and conditions.");
    }

    private void unequipSuit() {
        if (tab != Tab.SUITS) return;
        AscendantNetwork.sendUnequipSuit();
        feedback = text("gui.ascendant.catalog.server_validation",
                "Request sent; the server validates ownership, cooldown and conditions.");
    }

    private String unavailableReason(Entry entry) {
        if (!entry.functional()) {
            return text("gui.ascendant.catalog.template_reason", "Template only; no working executor.");
        }
        if (entry.type() == EntryType.POWER) {
            return text("gui.ascendant.catalog.power_type_reason", "This power type is passive or not manually supported.");
        }
        if (entry.type() == EntryType.ABILITY) {
            return text("gui.ascendant.catalog.ability_trigger_reason", "This ability uses an automatic trigger.");
        }
        return text("gui.ascendant.catalog.unavailable_reason", "This entry is unavailable.");
    }

    private Entry selectedEntry() {
        return selectedIndex >= 0 && selectedIndex < visibleEntries.size() ? visibleEntries.get(selectedIndex) : null;
    }

    private void ensureSelectionVisible() {
        int rows = visibleRowCount();
        if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
        if (selectedIndex >= scrollOffset + rows) scrollOffset = selectedIndex - rows + 1;
        scrollOffset = Math.max(0, scrollOffset);
    }

    private int visibleRowCount() {
        return Math.max(1, (height - 152) / 22);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (filterDirty) applyFilter();
        super.render(graphics, mouseX, mouseY, partialTick);
        int listWidth = Math.max(150, (int) (width * 0.43D));
        int listTop = 101;
        int listBottom = height - 35;
        int detailX = 24 + listWidth;
        int detailWidth = width - detailX - 12;
        graphics.drawCenteredString(font, title, width / 2, 7, 0xFFFFFF);

        graphics.fill(12, listTop, 12 + listWidth, listBottom, 0xA0202020);
        int rowCount = visibleRowCount();
        for (int row = 0; row < rowCount; row++) {
            int index = scrollOffset + row;
            if (index >= visibleEntries.size()) break;
            int top = listTop + row * 22;
            Entry entry = visibleEntries.get(index);
            boolean selected = index == selectedIndex;
            graphics.fill(14, top, 10 + listWidth, top + 21, selected ? 0xC06088B0 : 0x60303030);
            int color = entry.functional() ? 0xFFFFFF : 0xFFAAAAAA;
            graphics.drawString(font, font.plainSubstrByWidth(entry.name(), listWidth - 70), 19, top + 3, color);
            String state = entry.functional()
                    ? text("gui.ascendant.catalog.functional_short", "Functional")
                    : text("gui.ascendant.catalog.template_short", "Template");
            graphics.drawString(font, font.plainSubstrByWidth(state, 62), 19, top + 12,
                    entry.functional() ? 0xFF80E080 : 0xFFFFC060);
        }
        if (visibleEntries.isEmpty()) {
            graphics.drawString(font, text("gui.ascendant.catalog.no_results", "No matching entries."),
                    20, listTop + 8, 0xFFFFC060);
        }
        if (visibleEntries.size() > rowCount) {
            String pageText = (scrollOffset + 1) + "-" + Math.min(scrollOffset + rowCount, visibleEntries.size())
                    + " / " + visibleEntries.size();
            graphics.drawString(font, pageText, 16, listBottom - 11, 0xFFCCCCCC);
        }

        graphics.fill(detailX, 51, width - 12, height - 35, 0xA0202020);
        renderDetails(graphics, detailX + 8, 57, detailWidth - 16);
    }

    private void renderDetails(GuiGraphics graphics, int x, int y, int detailWidth) {
        Entry entry = selectedEntry();
        if (entry == null) {
            graphics.drawString(font, text("gui.ascendant.catalog.select_prompt", "Select an entry to see details."),
                    x, y, 0xFFFFFFFF);
            drawFeedback(graphics, x, height - 48, detailWidth);
            return;
        }
        int lineY = y;
        graphics.drawString(font, font.plainSubstrByWidth(entry.name(), detailWidth), x, lineY, 0xFFFFFFFF);
        lineY += 16;
        drawDetailLine(graphics, x, lineY, detailWidth, text("gui.ascendant.catalog.id", "ID") + ": " + entry.id());
        lineY += 13;
        String categoryLabel = entry.categoryKey() == null ? entry.category()
                : localized(entry.categoryKey(), categoryFallback(entry));
        drawDetailLine(graphics, x, lineY, detailWidth,
                text("gui.ascendant.catalog.category", "Category") + ": " + categoryLabel);
        lineY += 13;
        drawDetailLine(graphics, x, lineY, detailWidth,
                text("gui.ascendant.catalog.status", "Status") + ": "
                        + (entry.functional() ? text("gui.ascendant.catalog.functional", "Functional")
                        : text("gui.ascendant.catalog.template", "Template — not executable")));
        lineY += 13;
        drawDetailLine(graphics, x, lineY, detailWidth,
                text("gui.ascendant.catalog.type", "Type") + ": " + entry.metadata());
        lineY += 13;
        if (entry.cooldown() > 0) {
            drawDetailLine(graphics, x, lineY, detailWidth,
                    text("gui.ascendant.catalog.cooldown", "Cooldown") + ": " + entry.cooldown() + " ticks");
            lineY += 13;
        }
        drawDetailLine(graphics, x, lineY, detailWidth, entry.description());
        lineY += 35;
        if (entry.type() == EntryType.SUIT) {
            SuitDefinition suit = SuitRegistry.get(entry.id());
            if (suit != null) {
                drawDetailLine(graphics, x, lineY, detailWidth,
                        text("gui.ascendant.catalog.granted_powers", "Granted powers") + ": "
                                + suit.getGrantedPowers().size());
                lineY += 13;
                for (ResourceLocation powerId : suit.getGrantedPowers()) {
                    if (lineY > height - 75) break;
                    drawDetailLine(graphics, x, lineY, detailWidth, powerId.toString());
                    lineY += 11;
                }
            }
        }
        if (entry.action().type() == CatalogInteraction.ActionType.NONE) {
            drawWrapped(graphics, unavailableReason(entry), x, Math.min(lineY, height - 74), detailWidth, 12, 0xFFFFC060);
        } else if (entry.type() != EntryType.SUIT) {
            drawWrapped(graphics, text("gui.ascendant.catalog.ownership_note",
                    "Use may require server ownership; the server decides."), x,
                    Math.min(lineY, height - 74), detailWidth, 12, 0xFFB0D0FF);
        }
        drawFeedback(graphics, x, height - 48, detailWidth);
    }

    private String categoryFallback(Entry entry) {
        if (entry.type() == EntryType.POWER) {
            return PowerCategory.valueOf(entry.category()).getDisplayName();
        }
        if (entry.type() == EntryType.ABILITY) {
            return AbilityCategory.valueOf(entry.category()).getDisplayName();
        }
        return entry.category();
    }

    private void drawFeedback(GuiGraphics graphics, int x, int y, int detailWidth) {
        if (!feedback.isEmpty()) drawWrapped(graphics, feedback, x, y, detailWidth, 11, 0xFF80E0FF);
    }

    private void drawDetailLine(GuiGraphics graphics, int x, int y, int detailWidth, String value) {
        graphics.drawString(font, font.plainSubstrByWidth(value, detailWidth), x, y, 0xFFCCCCCC);
    }

    private void drawWrapped(GuiGraphics graphics, String value, int x, int y, int maxWidth, int lineHeight, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(value), maxWidth);
        int maxLines = Math.max(1, (height - y - 58) / lineHeight);
        for (int index = 0; index < Math.min(lines.size(), maxLines); index++) {
            graphics.drawString(font, lines.get(index), x, y + index * lineHeight, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int listWidth = Math.max(150, (int) (width * 0.43D));
        int listTop = 101;
        int listBottom = height - 35;
        if (button == 0 && mouseX >= 12 && mouseX < 12 + listWidth && mouseY >= listTop && mouseY < listBottom) {
            int row = (int) ((mouseY - listTop) / 22);
            int index = scrollOffset + row;
            if (index >= 0 && index < visibleEntries.size()) {
                selectedIndex = index;
                updateActionButtons();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listWidth = Math.max(150, (int) (width * 0.43D));
        int maxOffset = Math.max(0, visibleEntries.size() - visibleRowCount());
        if (mouseX < 12 + listWidth && maxOffset > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta), 0, maxOffset);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 265) {
            moveSelection(-1);
            return true;
        }
        if (keyCode == 264) {
            moveSelection(1);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            executeSelected(null);
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

    private String displayName(String key, ResourceLocation id) {
        return localized(key, humanize(id.getPath()));
    }

    private String description(String key) {
        if (net.minecraft.client.resources.language.I18n.exists(key)) {
            return net.minecraft.client.resources.language.I18n.get(key);
        }
        return text("gui.ascendant.catalog.description_missing", "No localized description is available.")
                + " [" + key + "]";
    }

    private static String localized(String key, String fallback) {
        return net.minecraft.client.resources.language.I18n.exists(key)
                ? net.minecraft.client.resources.language.I18n.get(key) : fallback;
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

    private static Component label(String key, String fallback) {
        return Component.translatableWithFallback(key, fallback);
    }

    private static String text(String key, String fallback) {
        return net.minecraft.client.resources.language.I18n.get(key).equals(key)
                ? fallback : net.minecraft.client.resources.language.I18n.get(key);
    }
}
