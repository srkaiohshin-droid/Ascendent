package br.com.ascendant.ui.client;

import br.com.ascendant.catalog.CatalogInteraction;
import br.com.ascendant.core.AbilityCategory;
import br.com.ascendant.core.PowerCategory;
import br.com.ascendant.network.AscendantNetwork;
import br.com.ascendant.pack.PackManager;
import br.com.ascendant.pack.PackManifest;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.suit.SuitDefinition;
import br.com.ascendant.suit.SuitRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static br.com.ascendant.ui.client.ComicUi.withAlpha;

/**
 * Main catalog browser. The visual layer is comic-print themed and driven by content
 * metadata; every control still maps to the same server-authoritative actions.
 */
@OnlyIn(Dist.CLIENT)
public class PowerCatalogScreen extends Screen {
    enum Tab {
        HEROES("gui.ascendant.catalog.heroes", "Heroes / Packs"),
        POWERS("gui.ascendant.catalog.powers", "Powers"),
        ABILITIES("gui.ascendant.catalog.abilities", "Abilities"),
        SUITS("gui.ascendant.catalog.suits", "Suits");

        private final String key;
        private final String fallback;

        Tab(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }
    }

    private enum EntryType {
        POWER, ABILITY, SUIT, PACK
    }

    private record Entry(EntryType type, ResourceLocation id, String name, String description,
                         String category, String categoryKey, boolean functional, int cooldown,
                         String metadata, CatalogInteraction.Action action, String packId) {
    }

    private static final int MARGIN = 12;
    private static final int TAB_Y = 45;
    private static final int TAB_HEIGHT = 18;
    private static final int SEARCH_Y = 70;
    private static final int CHIP_Y = 92;
    private static final int CHIP_HEIGHT = 16;
    private static final int LIST_TOP = 114;
    private static final int CARD_HEIGHT = 46;
    private static final int CARD_GAP = 4;
    private static final int BOTTOM_RESERVED = 38;

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, PackManifest> packsById = new LinkedHashMap<>();
    private List<Entry> visibleEntries = List.of();
    private Tab tab = Tab.HEROES;
    private String categoryFilter = "all";
    private String sourceFilter = "all";
    private String searchQuery = "";
    private String feedback = "";
    private int feedbackColor = 0xFFB9F4FF;
    private boolean filterDirty;
    private int selectedIndex;
    private int scrollOffset;

    private EditBox searchBox;
    private Button executeButton;
    private Button unequipButton;

    public PowerCatalogScreen(Screen parent) {
        this(parent, Tab.HEROES);
    }

    PowerCatalogScreen(Screen parent, Tab initialTab) {
        super(Component.translatable("gui.ascendant.catalog.title"));
        this.parent = parent;
        this.tab = initialTab;
    }

    @Override
    protected void init() {
        int listWidth = listWidth();
        searchBox = new EditBox(font, MARGIN + 6, SEARCH_Y + 2, listWidth - 12, 14,
                label("gui.ascendant.catalog.search", "Search name, ID or category"));
        searchBox.setMaxLength(96);
        searchBox.setBordered(false);
        searchBox.setValue(searchQuery);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setResponder(value -> {
            searchQuery = value;
            filterDirty = true;
        });
        addRenderableWidget(searchBox);

        int detailX = MARGIN + listWidth + 8;
        int detailWidth = Math.max(60, width - detailX - MARGIN);
        int actionGap = 6;
        int actionWidth = Math.max(44, (detailWidth - actionGap) / 2);
        executeButton = Button.builder(label("gui.ascendant.catalog.execute", "Use selection"),
                        button -> executeSelected(null))
                .bounds(detailX, height - 30, actionWidth, 20).build();
        addRenderableWidget(executeButton);
        unequipButton = Button.builder(label("gui.ascendant.catalog.unequip", "Unequip suit"),
                        button -> unequipSuit())
                .bounds(detailX + actionWidth + actionGap, height - 30,
                        Math.max(44, detailWidth - actionWidth - actionGap), 20).build();
        addRenderableWidget(unequipButton);
        addRenderableWidget(Button.builder(label("gui.ascendant.catalog.close", "Close"), button -> onClose())
                .bounds(width - 76, 6, 64, 16).build());

        rebuildEntries();
        applyFilter();
        updateActionButtons();
        AscendantNetwork.requestCatalogState();
    }

    private void changeTab(Tab next) {
        if (tab == next) {
            return;
        }
        tab = next;
        categoryFilter = "all";
        sourceFilter = "all";
        selectedIndex = 0;
        scrollOffset = 0;
        feedback = "";
        rebuildEntries();
        applyFilter();
        updateActionButtons();
    }

    private void cycleCategory() {
        List<String> categories = categoriesForTab();
        int current = Math.max(0, categories.indexOf(categoryFilter));
        categoryFilter = categories.get((current + 1) % categories.size());
        selectedIndex = 0;
        scrollOffset = 0;
        filterDirty = true;
    }

    private List<String> categoriesForTab() {
        List<String> values = new ArrayList<>();
        values.add("all");
        switch (tab) {
            case HEROES -> {
                values.add("suits");
                values.add("packs");
            }
            case POWERS -> {
                for (PowerCategory value : PowerCategory.values()) values.add(value.name());
            }
            case ABILITIES -> {
                for (AbilityCategory value : AbilityCategory.values()) values.add(value.name());
            }
            case SUITS -> SuitRegistry.getAll().stream().map(suit -> suit.getRarity().name())
                    .distinct().sorted().forEach(values::add);
        }
        return values;
    }

    private void cycleSource() {
        List<String> sources = sourceFilters();
        int current = Math.max(0, sources.indexOf(sourceFilter));
        sourceFilter = sources.get((current + 1) % sources.size());
        selectedIndex = 0;
        scrollOffset = 0;
        filterDirty = true;
    }

    private List<String> sourceFilters() {
        List<String> sources = new ArrayList<>();
        sources.add("all");
        if (tab == Tab.POWERS || tab == Tab.ABILITIES) {
            SuitRegistry.getAll().stream().map(suit -> "suit:" + suit.getId())
                    .sorted().forEach(sources::add);
            PackManager.getLoadedPackIds().stream().map(id -> "pack:" + id)
                    .sorted().forEach(sources::add);
        }
        return sources;
    }

    private Component sourceLabel() {
        if (sourceFilter.equals("all")) {
            return label("gui.ascendant.catalog.all_sources", "All sources");
        }
        String[] parts = sourceFilter.split(":", 2);
        if (parts.length != 2) {
            return Component.literal(sourceFilter);
        }
        if (parts[0].equals("suit")) {
            SuitDefinition suit = SuitRegistry.get(ResourceLocation.tryParse(parts[1]));
            return Component.literal(text("gui.ascendant.catalog.source_suit", "Suit") + ": "
                    + (suit == null ? parts[1] : displayName(suit.getNameKey(), suit.getId())));
        }
        return Component.literal(text("gui.ascendant.catalog.source_pack", "Pack") + ": " + parts[1]);
    }

    private Component categoryLabel() {
        if (categoryFilter.equals("all")) {
            return label("gui.ascendant.catalog.all_categories", "All categories");
        }
        if (tab == Tab.HEROES) {
            return categoryFilter.equals("suits")
                    ? label("gui.ascendant.catalog.suits_category", "Suit loadouts")
                    : label("gui.ascendant.catalog.packs_category", "Content packs");
        }
        if (tab == Tab.POWERS) {
            try {
                PowerCategory category = PowerCategory.valueOf(categoryFilter);
                return Component.literal(localized(category.getTranslationKey(), category.getDisplayName()));
            } catch (IllegalArgumentException ignored) {
                // Fall through to the raw identifier.
            }
        }
        if (tab == Tab.ABILITIES) {
            try {
                AbilityCategory category = AbilityCategory.valueOf(categoryFilter);
                return Component.literal(localized(category.getTranslationKey(), category.getDisplayName()));
            } catch (IllegalArgumentException ignored) {
                // Fall through to the raw identifier.
            }
        }
        return Component.literal(categoryFilter);
    }

    private void rebuildEntries() {
        entries.clear();
        packsById.clear();
        availablePacks().forEach(pack -> packsById.put(pack.getId(), pack));
        switch (tab) {
            case HEROES -> {
                SuitRegistry.getAll().stream()
                        .sorted(Comparator.comparing(suit -> suit.getId().toString()))
                        .map(this::entryFor).forEach(entries::add);
                packsById.values().stream()
                        .map(this::entryFor).forEach(entries::add);
            }
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
                power.getType().name(), CatalogInteraction.forPower(power), PackManager.getPowerSource(power.getId()));
    }

    private Entry entryFor(AbilityDefinition ability) {
        String requirement = ability.isUniversal() ? text("gui.ascendant.catalog.universal", "Universal")
                : text("gui.ascendant.catalog.requires_power", "Requires power") + ": " + ability.getPowerId();
        return new Entry(EntryType.ABILITY, ability.getId(), displayName(ability.getNameKey(), ability.getId()),
                description(ability.getDescriptionKey()), ability.getCategory().name(),
                ability.getCategory().getTranslationKey(), ability.isFunctional(), ability.getCooldownTicks(),
                ability.getTrigger().name() + " | " + requirement, CatalogInteraction.forAbility(ability),
                PackManager.getAbilitySource(ability.getId()));
    }

    private Entry entryFor(SuitDefinition suit) {
        String details = suit.getRarity().name() + " | " + suit.getUniverse() + " | " + suit.getAuthor();
        return new Entry(EntryType.SUIT, suit.getId(), displayName(suit.getNameKey(), suit.getId()),
                description(suit.getDescriptionKey()), suit.getRarity().name(), "gui.ascendant.catalog.rarity",
                true, 0, details, CatalogInteraction.forSuit(suit.getId()), PackManager.getSuitSource(suit.getId()));
    }

    private Entry entryFor(PackManifest pack) {
        ResourceLocation id = new ResourceLocation("ascendant", "pack/" + pack.getId());
        String details = text("gui.ascendant.catalog.pack_version", "Version") + " " + pack.getVersion()
                + " | " + pack.getAuthor() + " | " + pack.getPowerCount() + " "
                + text("gui.ascendant.catalog.powers", "Powers") + " | " + pack.getAbilityCount() + " "
                + text("gui.ascendant.catalog.abilities", "Abilities");
        return new Entry(EntryType.PACK, id, pack.getName(), pack.getDescription(), "packs", null,
                true, 0, details, new CatalogInteraction.Action(CatalogInteraction.ActionType.NONE, id),
                pack.getId());
    }

    private List<PackManifest> availablePacks() {
        if (PackManager.getLocalCatalog() == null) {
            return List.of();
        }
        Map<String, PackManifest> byId = new LinkedHashMap<>();
        PackManager.getLocalCatalog().listAvailablePacks().stream()
                .sorted(Comparator.comparing((PackManifest pack) ->
                        !PackManager.getLoadedPackIds().contains(pack.getId()))
                        .thenComparing(PackManifest::getId))
                .forEach(pack -> byId.putIfAbsent(pack.getId(), pack));
        return byId.values().stream().sorted(Comparator.comparing(PackManifest::getId)).toList();
    }

    private void applyFilter() {
        visibleEntries = entries.stream()
                .filter(entry -> CatalogInteraction.matches(searchQuery, entry.id().toString(),
                        entry.name() + " " + entry.description(), entry.category()))
                .filter(this::matchesCategoryFilter)
                .filter(this::matchesSourceFilter)
                .toList();
        selectedIndex = visibleEntries.isEmpty() ? -1 : Mth.clamp(selectedIndex, 0, visibleEntries.size() - 1);
        filterDirty = false;
        updateActionButtons();
    }

    private boolean matchesCategoryFilter(Entry entry) {
        if (tab == Tab.HEROES) {
            if (categoryFilter.equals("suits")) {
                return entry.type() == EntryType.SUIT;
            }
            if (categoryFilter.equals("packs")) {
                return entry.type() == EntryType.PACK;
            }
        }
        return CatalogInteraction.matchesCategory(categoryFilter, entry.category());
    }

    private boolean matchesSourceFilter(Entry entry) {
        if (sourceFilter.equals("all")) {
            return true;
        }
        String[] parts = sourceFilter.split(":", 2);
        if (parts.length != 2) {
            return true;
        }
        if (parts[0].equals("pack")) {
            return parts[1].equals(entry.packId());
        }
        if (!parts[0].equals("suit") || entry.type() != EntryType.POWER && entry.type() != EntryType.ABILITY) {
            return false;
        }
        ResourceLocation suitId = ResourceLocation.tryParse(parts[1]);
        SuitDefinition suit = suitId == null ? null : SuitRegistry.get(suitId);
        if (suit == null) {
            return false;
        }
        return entry.type() == EntryType.POWER
                ? suit.getGrantedPowers().contains(entry.id())
                : suit.getGrantedAbilities().contains(entry.id());
    }

    private void updateActionButtons() {
        Entry selected = selectedEntry();
        executeButton.active = selected != null && selected.action().type() != CatalogInteraction.ActionType.NONE
                && isAuthorizedAndReady(selected);
        unequipButton.visible = tab == Tab.SUITS || tab == Tab.HEROES;
        unequipButton.active = unequipButton.visible && CatalogClientState.isInitialized()
                && CatalogClientState.getEquippedSuit() != null;
        if (selected == null) {
            executeButton.setMessage(label("gui.ascendant.catalog.execute", "Use selection"));
            return;
        }
        boolean toggled = isToggled(selected);
        String key = switch (selected.action().type()) {
            case ACTIVATE_POWER, ACTIVATE_ABILITY -> "gui.ascendant.catalog.activate";
            case TOGGLE_POWER, TOGGLE_ABILITY -> toggled
                    ? "gui.ascendant.catalog.turn_off" : "gui.ascendant.catalog.turn_on";
            case EQUIP_SUIT -> "gui.ascendant.catalog.equip";
            case NONE -> "gui.ascendant.catalog.unavailable";
        };
        String fallback = switch (selected.action().type()) {
            case ACTIVATE_POWER, ACTIVATE_ABILITY -> "Activate";
            case TOGGLE_POWER, TOGGLE_ABILITY -> toggled ? "Turn off" : "Turn on";
            case EQUIP_SUIT -> "Equip";
            case NONE -> "Unavailable";
        };
        executeButton.setMessage(label(key, fallback));
    }

    private boolean isAuthorizedAndReady(Entry entry) {
        CatalogInteraction.ActionType action = entry.action().type();
        if (action == CatalogInteraction.ActionType.EQUIP_SUIT) {
            return true;
        }
        if (!CatalogClientState.isInitialized()) {
            return false;
        }
        boolean authorized = entry.type() == EntryType.POWER
                ? CatalogClientState.hasPower(entry.id())
                : entry.type() == EntryType.ABILITY && CatalogClientState.hasAbility(entry.id());
        if (authorized && isToggled(entry)) {
            return true;
        }
        return authorized && CatalogClientState.getCooldown(entry.id()) <= 0;
    }

    private static boolean isToggle(CatalogInteraction.ActionType action) {
        return action == CatalogInteraction.ActionType.TOGGLE_POWER
                || action == CatalogInteraction.ActionType.TOGGLE_ABILITY;
    }

    private static boolean isToggled(Entry entry) {
        if (!isToggle(entry.action().type())) {
            return false;
        }
        return entry.type() == EntryType.POWER
                ? CatalogClientState.isPowerToggled(entry.id())
                : entry.type() == EntryType.ABILITY && CatalogClientState.isAbilityToggled(entry.id());
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
            setFeedback(text("gui.ascendant.catalog.no_selection", "Select an entry first."), 0xFFFFC060);
            return;
        }
        CatalogInteraction.Action action = selected.action();
        boolean isToggle = isToggle(action.type());
        if (toggleOnly != null && toggleOnly != isToggle) {
            setFeedback(toggleOnly
                    ? text("gui.ascendant.catalog.not_toggle", "The selection is not a toggle.")
                    : text("gui.ascendant.catalog.use_toggle_key", "Use the toggle key for this power."),
                    0xFFFFC060);
            return;
        }
        if (action.type() != CatalogInteraction.ActionType.EQUIP_SUIT
                && !isAuthorizedAndReady(selected)) {
            setFeedback(unavailableReason(selected), 0xFFFFA080);
            return;
        }
        switch (action.type()) {
            case ACTIVATE_POWER -> AscendantNetwork.sendActivatePower(action.id());
            case TOGGLE_POWER -> AscendantNetwork.sendTogglePowerRequest(action.id());
            case ACTIVATE_ABILITY -> AscendantNetwork.sendActivateAbility(action.id());
            case TOGGLE_ABILITY -> AscendantNetwork.sendToggleAbilityRequest(action.id());
            case EQUIP_SUIT -> AscendantNetwork.sendEquipSuit(action.id(), true);
            case NONE -> {
                setFeedback(unavailableReason(selected), 0xFFFFA080);
                return;
            }
        }
        setFeedback(text("gui.ascendant.catalog.server_validation",
                "Request sent; the server validates ownership, cooldown and conditions."), 0xFFB9F4FF);
    }

    private void unequipSuit() {
        if (tab != Tab.SUITS && tab != Tab.HEROES) return;
        ResourceLocation equippedSuit = CatalogClientState.getEquippedSuit();
        if (equippedSuit == null) {
            setFeedback(text("gui.ascendant.catalog.no_equipped_suit", "No suit is currently equipped."), 0xFFFFC060);
            return;
        }
        AscendantNetwork.sendUnequipSuit();
        setFeedback(text("gui.ascendant.catalog.server_validation",
                "Request sent; the server validates ownership, cooldown and conditions."), 0xFFB9F4FF);
    }

    private void setFeedback(String value, int color) {
        feedback = value;
        feedbackColor = color;
    }

    private String unavailableReason(Entry entry) {
        if (!CatalogClientState.isInitialized()) {
            return text("gui.ascendant.catalog.loading_state", "Waiting for authoritative server state.");
        }
        if (!entry.functional()) {
            return text("gui.ascendant.catalog.template_reason", "Template only; no working executor.");
        }
        if (entry.type() == EntryType.PACK) {
            return text("gui.ascendant.catalog.pack_not_actionable", "Packs describe content; select an entry to use it.");
        }
        if ((entry.type() == EntryType.POWER && !CatalogClientState.hasPower(entry.id()))
                || (entry.type() == EntryType.ABILITY && !CatalogClientState.hasAbility(entry.id()))) {
            return text("gui.ascendant.catalog.not_owned",
                    "Not available from current powers or equipped suit.");
        }
        int cooldown = CatalogClientState.getCooldown(entry.id());
        if (cooldown > 0) {
            return text("gui.ascendant.catalog.cooldown_remaining", "On cooldown: %s ticks").formatted(cooldown);
        }
        if (entry.type() == EntryType.POWER) {
            return text("gui.ascendant.catalog.power_type_reason",
                    "This power type is passive or not manually supported.");
        }
        if (entry.type() == EntryType.ABILITY) {
            return text("gui.ascendant.catalog.ability_trigger_reason", "This ability uses an automatic trigger.");
        }
        return text("gui.ascendant.catalog.unavailable_reason", "This entry is unavailable.");
    }

    private Entry selectedEntry() {
        return selectedIndex >= 0 && selectedIndex < visibleEntries.size()
                ? visibleEntries.get(selectedIndex) : null;
    }

    /**
     * The selected entry drives the presentation: a suit supplies its universe identity and
     * a pack supplies its optional {@code theme} metadata. Without a selection the equipped
     * suit (or the neutral cosmic fallback) is used, so nothing is hardcoded per hero.
     */
    private CatalogTheme themeFor(Entry entry) {
        if (entry == null) {
            return CatalogTheme.current();
        }
        if (entry.type() == EntryType.SUIT) {
            SuitDefinition suit = SuitRegistry.get(entry.id());
            if (suit != null) {
                return CatalogTheme.forSuit(suit);
            }
        }
        if (entry.packId() != null) {
            PackManifest pack = packsById.get(entry.packId());
            if (pack != null) {
                return CatalogTheme.forPack(pack);
            }
        }
        return CatalogTheme.current();
    }

    private void ensureSelectionVisible() {
        int rows = visibleRowCount();
        if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
        if (selectedIndex >= scrollOffset + rows) scrollOffset = selectedIndex - rows + 1;
        scrollOffset = Math.max(0, scrollOffset);
    }

    private int listWidth() {
        return Math.max(170, Math.min((int) (width * 0.40D), width - 190));
    }

    private int visibleRowCount() {
        return Math.max(1, (height - BOTTOM_RESERVED - LIST_TOP) / (CARD_HEIGHT + CARD_GAP));
    }

    private int detailX() {
        return MARGIN + listWidth() + 8;
    }

    private int detailWidth() {
        return width - detailX() - MARGIN;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (filterDirty) applyFilter();
        updateActionButtons();
        CatalogTheme theme = themeFor(selectedEntry());

        ComicUi.backdrop(graphics, theme, width, height);
        renderHeader(graphics, theme, mouseX, mouseY);
        renderSearchAndFilters(graphics, theme, mouseX, mouseY);
        renderList(graphics, theme, mouseX, mouseY);
        renderDetail(graphics, theme);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics, CatalogTheme theme, int mouseX, int mouseY) {
        graphics.drawString(font, text("gui.ascendant.catalog.brand", "ASCENDANT"),
                MARGIN, 8, theme.accentBright(), false);
        graphics.drawString(font, text("gui.ascendant.catalog.title", "Ascendant Catalog"),
                MARGIN, 19, theme.ink(), false);
        String subtitle = text("gui.ascendant.catalog.subtitle",
                "Server-authoritative powers, abilities and suits");
        graphics.drawString(font, ComicUi.fit(font, subtitle, width - 240), MARGIN, 31, theme.muted(), false);

        String themeLabel = localized(theme.labelKey(), theme.id());
        ComicUi.badge(graphics, font, themeLabel, width - 190, 27, withAlpha(theme.accent(), 0x44), theme.accentBright());

        int tabsX = MARGIN;
        int available = width - MARGIN * 2 - 84;
        int tabWidth = Math.max(48, (available - (Tab.values().length - 1) * 4) / Tab.values().length);
        int x = tabsX;
        for (Tab value : Tab.values()) {
            boolean selected = value == tab;
            boolean hovered = mouseX >= x && mouseX < x + tabWidth && mouseY >= TAB_Y && mouseY < TAB_Y + TAB_HEIGHT;
            int fill = selected ? withAlpha(theme.accent(), 0x55)
                    : hovered ? withAlpha(theme.accent(), 0x28) : 0x66101620;
            graphics.fill(x, TAB_Y, x + tabWidth, TAB_Y + TAB_HEIGHT, fill);
            graphics.renderOutline(x, TAB_Y, tabWidth, TAB_HEIGHT,
                    selected ? theme.accent() : withAlpha(theme.muted(), 0x66));
            if (selected) {
                graphics.fill(x, TAB_Y + TAB_HEIGHT - 2, x + tabWidth, TAB_Y + TAB_HEIGHT, theme.accentBright());
            }
            graphics.drawCenteredString(font, localized(value.key, value.fallback),
                    x + tabWidth / 2, TAB_Y + 5, selected ? theme.ink() : theme.muted());
            x += tabWidth + 4;
        }
    }

    private void renderSearchAndFilters(GuiGraphics graphics, CatalogTheme theme, int mouseX, int mouseY) {
        int listWidth = listWidth();
        ComicUi.panel(graphics, MARGIN, SEARCH_Y, listWidth, 20, theme, false);
        if (searchBox != null && searchQuery.isEmpty() && !searchBox.isFocused()) {
            graphics.drawString(font, text("gui.ascendant.catalog.search", "Search name, ID or category"),
                    MARGIN + 8, SEARCH_Y + 6, theme.muted(), false);
        }

        int x = MARGIN;
        x = drawChip(graphics, theme, x, CHIP_Y, categoryLabel().getString(), mouseX, mouseY);
        if (tab == Tab.POWERS || tab == Tab.ABILITIES) {
            x = drawChip(graphics, theme, x, CHIP_Y, sourceLabel().getString(), mouseX, mouseY);
        }
        if (filtersActive()) {
            drawChip(graphics, theme, x, CHIP_Y, text("gui.ascendant.catalog.reset_filters", "Reset"), mouseX, mouseY);
        }

        String count = text("gui.ascendant.catalog.result_count", "%s entries").formatted(visibleEntries.size());
        graphics.drawString(font, count, MARGIN + listWidth - font.width(count), CHIP_Y + 4, theme.muted(), false);
    }

    private int drawChip(GuiGraphics graphics, CatalogTheme theme, int x, int y, String label,
                         int mouseX, int mouseY) {
        int chipWidth = Math.min(font.width(label) + 16, Math.max(60, listWidth() - (x - MARGIN)));
        boolean hovered = mouseX >= x && mouseX < x + chipWidth && mouseY >= y && mouseY < y + CHIP_HEIGHT;
        graphics.fill(x, y, x + chipWidth, y + CHIP_HEIGHT,
                hovered ? withAlpha(theme.accent(), 0x44) : 0x88101620);
        graphics.renderOutline(x, y, chipWidth, CHIP_HEIGHT, withAlpha(theme.accent(), 0x77));
        graphics.drawString(font, ComicUi.fit(font, label, chipWidth - 12), x + 6, y + 4,
                hovered ? theme.accentBright() : theme.ink(), false);
        return x + chipWidth + 5;
    }

    private boolean filtersActive() {
        return !searchQuery.isBlank() || !categoryFilter.equals("all") || !sourceFilter.equals("all");
    }

    private void renderList(GuiGraphics graphics, CatalogTheme theme, int mouseX, int mouseY) {
        int listWidth = listWidth();
        int listBottom = height - BOTTOM_RESERVED;
        ComicUi.panel(graphics, MARGIN, LIST_TOP, listWidth, listBottom - LIST_TOP, theme, false);
        graphics.enableScissor(MARGIN + 4, LIST_TOP + 2, MARGIN + listWidth - 4, listBottom - 2);
        int rows = visibleRowCount();
        for (int row = 0; row < rows; row++) {
            int index = scrollOffset + row;
            if (index >= visibleEntries.size()) break;
            int top = LIST_TOP + 3 + row * (CARD_HEIGHT + CARD_GAP);
            renderCard(graphics, theme, visibleEntries.get(index), index, top, listWidth, mouseX, mouseY);
        }
        graphics.disableScissor();

        if (visibleEntries.isEmpty()) {
            graphics.drawString(font, text("gui.ascendant.catalog.no_results", "No matching entries."),
                    MARGIN + 10, LIST_TOP + 10, 0xFFFFC060, false);
            graphics.drawString(font, text("gui.ascendant.catalog.no_results_hint",
                            "Adjust the search or use Reset."),
                    MARGIN + 10, LIST_TOP + 24, theme.muted(), false);
        }
        if (visibleEntries.size() > rows) {
            String pageText = (scrollOffset + 1) + "-" + Math.min(scrollOffset + rows, visibleEntries.size())
                    + " / " + visibleEntries.size();
            graphics.drawString(font, pageText, MARGIN + 8, listBottom - 12, theme.muted(), false);
        }
    }

    private void renderCard(GuiGraphics graphics, CatalogTheme theme, Entry entry, int index, int top,
                            int listWidth, int mouseX, int mouseY) {
        boolean selected = index == selectedIndex;
        boolean hovered = mouseX >= MARGIN && mouseX < MARGIN + listWidth
                && mouseY >= top && mouseY < top + CARD_HEIGHT;
        int fill = selected ? withAlpha(theme.accent(), 0x3C)
                : hovered ? withAlpha(theme.accent(), 0x20) : 0x8C10151E;
        int accent = categoryAccent(entry, theme);
        graphics.fill(MARGIN + 4, top, MARGIN + listWidth - 4, top + CARD_HEIGHT, fill);
        graphics.renderOutline(MARGIN + 4, top, listWidth - 8, CARD_HEIGHT,
                selected ? theme.accent() : withAlpha(theme.muted(), 0x55));
        graphics.fill(MARGIN + 4, top, MARGIN + 8, top + CARD_HEIGHT, accent);

        int textX = MARGIN + 14;
        graphics.drawString(font, ComicUi.fit(font, entry.name(), listWidth - 100),
                textX, top + 5, selected ? theme.ink() : 0xFFE8EEF6, false);
        String meta = categoryLabel(entry) + "  ·  " + typeLabel(entry);
        graphics.drawString(font, ComicUi.fit(font, meta, listWidth - 100), textX, top + 18,
                theme.muted(), false);
        graphics.drawString(font, ComicUi.fit(font, activationHint(entry, false), listWidth - 100),
                textX, top + 30, withAlpha(accent, 0xDD), false);

        int stateColor = stateColor(entry, theme);
        String state = shortState(entry);
        int badgeWidth = font.width(state) + 12;
        int badgeX = MARGIN + listWidth - badgeWidth - 8;
        graphics.fill(badgeX, top + 6, badgeX + badgeWidth, top + 17, withAlpha(stateColor, 0x33));
        graphics.renderOutline(badgeX, top + 6, badgeWidth, 11, withAlpha(stateColor, 0x88));
        graphics.drawString(font, state, badgeX + 6, top + 8, stateColor, false);
    }

    private void renderDetail(GuiGraphics graphics, CatalogTheme theme) {
        int x = detailX();
        int y = SEARCH_Y;
        int width = detailWidth();
        int bottom = height - BOTTOM_RESERVED;
        ComicUi.panel(graphics, x, y, width, bottom - y, theme, true);
        int contentX = x + 12;
        int contentWidth = width - 24;
        int cursor = y + 10;

        Entry entry = selectedEntry();
        if (entry == null) {
            graphics.drawString(font, text("gui.ascendant.catalog.select_prompt",
                    "Select an entry to see its details."), contentX, cursor, theme.ink(), false);
            renderFeedback(graphics, theme, contentX, bottom - 34, contentWidth);
            return;
        }

        ComicUi.headerBand(graphics, contentX, cursor, contentWidth, 30, theme);
        graphics.drawString(font, ComicUi.fit(font, entry.name(), contentWidth - 16),
                contentX + 9, cursor + 5, theme.ink(), false);
        graphics.drawString(font, ComicUi.fit(font, entry.id().toString(), contentWidth - 16),
                contentX + 9, cursor + 18, theme.muted(), false);
        cursor += 38;

        int badgeX = contentX;
        badgeX = detailBadge(graphics, theme, badgeX, cursor, typeLabel(entry), theme.accent());
        badgeX = detailBadge(graphics, theme, badgeX, cursor, categoryLabel(entry), 0xFF9AD8A0);
        if (entry.cooldown() > 0) {
            detailBadge(graphics, theme, badgeX, cursor,
                    text("gui.ascendant.catalog.cooldown_short", "%ss cd")
                            .formatted(String.format(Locale.ROOT, "%.1f", entry.cooldown() / 20.0F)),
                    0xFFFFC060);
        }
        cursor += 18;

        cursor = detailSection(graphics, theme, contentX, cursor, contentWidth,
                text("gui.ascendant.catalog.mechanics", "Mechanics"), mechanicsText(entry));
        cursor = detailSection(graphics, theme, contentX, cursor, contentWidth,
                text("gui.ascendant.catalog.activation", "Activation"), activationHint(entry, true));
        cursor = detailSection(graphics, theme, contentX, cursor, contentWidth,
                text("gui.ascendant.catalog.requirements", "Requirements"), requirementsText(entry));
        cursor = detailSection(graphics, theme, contentX, cursor, contentWidth,
                text("gui.ascendant.catalog.runtime_state", "Runtime state"), runtimeStatus(entry));

        if (entry.type() == EntryType.SUIT) {
            cursor = renderSuitDetails(graphics, theme, entry, contentX, cursor, contentWidth, bottom);
        } else if (entry.type() == EntryType.PACK) {
            cursor = detailSection(graphics, theme, contentX, cursor, contentWidth,
                    text("gui.ascendant.catalog.pack_status", "Pack status"), packStatus(entry));
        } else {
            cursor = detailSection(graphics, theme, contentX, cursor, contentWidth,
                    text("gui.ascendant.catalog.source", "Source"), sourceText(entry));
        }

        if (entry.action().type() == CatalogInteraction.ActionType.NONE) {
            ComicUi.wrapped(graphics, font, unavailableReason(entry), contentX,
                    Math.min(cursor, bottom - 44), contentWidth, 3, 11, 0xFFFFC060);
        }
        renderFeedback(graphics, theme, contentX, bottom - 34, contentWidth);
    }

    private int renderSuitDetails(GuiGraphics graphics, CatalogTheme theme, Entry entry, int x, int y,
                                  int width, int bottom) {
        SuitDefinition suit = SuitRegistry.get(entry.id());
        if (suit == null) {
            return y;
        }
        y = detailSection(graphics, theme, x, y, width,
                text("gui.ascendant.catalog.granted_powers", "Granted powers"),
                suit.getGrantedPowers().size() + " · " + joinIds(suit.getGrantedPowers(), 4));
        y = detailSection(graphics, theme, x, y, width,
                text("gui.ascendant.catalog.granted_abilities", "Granted abilities"),
                suit.getGrantedAbilities().size() + " · " + joinIds(suit.getGrantedAbilities(), 4));
        return detailSection(graphics, theme, x, y, width,
                text("gui.ascendant.catalog.suit_identity", "Identity"),
                suit.getUniverse() + " · " + suit.getRarity().name() + " · " + suit.getAuthor());
    }

    private int detailSection(GuiGraphics graphics, CatalogTheme theme, int x, int y, int width,
                              String heading, String body) {
        if (y > height - BOTTOM_RESERVED - 26) {
            return y;
        }
        graphics.drawString(font, heading.toUpperCase(Locale.ROOT), x, y, theme.accent(), false);
        y += 11;
        ComicUi.wrapped(graphics, font, body == null ? "" : body, x, y, width, 4, 10, 0xFFD8E2EE);
        int lines = Math.min(4, font.split(Component.literal(body == null ? "" : body), width).size());
        return y + lines * 10 + 6;
    }

    private int detailBadge(GuiGraphics graphics, CatalogTheme theme, int x, int y, String label, int color) {
        String fitted = ComicUi.fit(font, label, Math.max(40, detailWidth() / 3));
        ComicUi.badge(graphics, font, fitted, x, y, withAlpha(color, 0x2A), color);
        return x + font.width(fitted) + 13;
    }

    private void renderFeedback(GuiGraphics graphics, CatalogTheme theme, int x, int y, int width) {
        if (!feedback.isEmpty()) {
            ComicUi.wrapped(graphics, font, feedback, x, y, width, 2, 10, feedbackColor);
        } else {
            String hint = text("gui.ascendant.catalog.key_hint",
                    "↑/↓ select · Enter use · V activate · B toggle · G radial");
            graphics.drawString(font, ComicUi.fit(font, hint, width), x, y, theme.muted(), false);
        }
    }

    private String mechanicsText(Entry entry) {
        if (I18n.exists(descriptionKey(entry))) {
            return I18n.get(descriptionKey(entry));
        }
        if (entry.type() == EntryType.POWER) {
            return switch (entry.metadata()) {
                case "TOGGLE", "SUSTAINED" -> entry.cooldown() > 0
                        ? text("gui.ascendant.catalog.mechanics.toggle_cd",
                        "Stays enabled until turned off; its components tick on the server. Reactivating starts a %s-tick cooldown.")
                        .formatted(entry.cooldown())
                        : text("gui.ascendant.catalog.mechanics.toggle",
                        "Stays enabled until turned off; its components run every server tick while active.");
                case "PASSIVE" -> text("gui.ascendant.catalog.mechanics.passive",
                        "Always active while the power is owned; no manual activation.");
                case "ACTIVE" -> entry.cooldown() > 0
                        ? text("gui.ascendant.catalog.mechanics.active_cd",
                        "Triggers the registered effect once, then starts a %s-tick cooldown.")
                        .formatted(entry.cooldown())
                        : text("gui.ascendant.catalog.mechanics.active",
                        "Triggers the registered effect once when used.");
                case "CHARGED" -> text("gui.ascendant.catalog.mechanics.charged",
                        "Charge-based design. This build exposes it as a template, not a working manual action.");
                default -> text("gui.ascendant.catalog.mechanics.generic",
                        "This power is registered but has no manual runtime path in this build.");
            };
        }
        if (entry.type() == EntryType.ABILITY) {
            String trigger = entry.metadata().split(" \\|", 2)[0];
            return switch (trigger) {
                case "KEY_PRESS" -> text("gui.ascendant.catalog.mechanics.key",
                        "Manual ability: use the catalog or radial while its parent power is owned.");
                case "SNEAK_KEY" -> text("gui.ascendant.catalog.mechanics.sneak",
                        "Manual ability: sneak and use it while its parent power is owned.");
                case "TOGGLE" -> text("gui.ascendant.catalog.mechanics.toggle",
                        "Stays enabled until turned off; its components run every server tick while active.");
                case "PASSIVE" -> text("gui.ascendant.catalog.mechanics.passive_ability",
                        "Passive ability: applied and maintained automatically while accessible.");
                case "TICK" -> text("gui.ascendant.catalog.mechanics.tick",
                        "Periodic ability: the server maintains it automatically.");
                case "HURT_EVENT", "ATTACK_HIT" -> text("gui.ascendant.catalog.mechanics.reactive",
                        "Reactive ability: triggered by combat events, never manually.");
                default -> text("gui.ascendant.catalog.mechanics.generic",
                        "This ability is registered but has no manual runtime path in this build.");
            };
        }
        if (entry.type() == EntryType.SUIT) {
            return text("gui.ascendant.catalog.mechanics.suit",
                    "Loadout metadata only: equipping grants the listed powers and abilities. No character profile is inferred.");
        }
        return text("gui.ascendant.catalog.mechanics.pack",
                "Content pack metadata: counts and origins only. Its powers, abilities and suits appear in their own tabs.");
    }

    private String descriptionKey(Entry entry) {
        return switch (entry.type()) {
            case POWER -> {
                PowerDefinition power = PowerRegistry.get(entry.id());
                yield power == null ? "" : power.getDescriptionKey();
            }
            case ABILITY -> {
                AbilityDefinition ability = AbilityRegistry.get(entry.id());
                yield ability == null ? "" : ability.getDescriptionKey();
            }
            case SUIT -> {
                SuitDefinition suit = SuitRegistry.get(entry.id());
                yield suit == null ? "" : suit.getDescriptionKey();
            }
            case PACK -> "";
        };
    }

    private String activationHint(Entry entry, boolean detailed) {
        if (!entry.functional()) {
            return text("gui.ascendant.catalog.hint.template", "Template only — no runtime action");
        }
        return switch (entry.type()) {
            case POWER -> switch (entry.metadata()) {
                case "ACTIVE" -> detailed
                        ? text("gui.ascendant.catalog.hint.active_long",
                        "Use from the catalog (Enter/click) or press V. Server checks ownership and cooldown.")
                        : text("gui.ascendant.catalog.hint.active", "V · use once");
                case "TOGGLE", "SUSTAINED" -> entry.id().getPath().equals("kinetic_sprint") ? detailed
                        ? text("gui.ascendant.catalog.hint.super_speed_long",
                        "Toggle super-speed: catalog button, radial, or Z. While active, WASD drives the speedster mode; [ and ] change the level; Left Alt brakes.")
                        : text("gui.ascendant.catalog.hint.super_speed", "B / Z · super-speed")
                        : detailed
                        ? text("gui.ascendant.catalog.hint.toggle_long",
                        "Toggle from the catalog (Enter/click) or press B. Toggle off still works during cooldown.")
                        : text("gui.ascendant.catalog.hint.toggle", "B · toggle");
                case "PASSIVE" -> text("gui.ascendant.catalog.hint.passive", "Always on while owned");
                default -> text("gui.ascendant.catalog.hint.automatic", "Automatic / not manually usable");
            };
            case ABILITY -> {
                String trigger = entry.metadata().split(" \\|", 2)[0];
                yield switch (trigger) {
                    case "KEY_PRESS" -> detailed
                            ? text("gui.ascendant.catalog.hint.key_long",
                            "Select it in the catalog or radial and activate; the server validates access and cooldown.")
                            : text("gui.ascendant.catalog.hint.key", "Use from catalog / radial");
                    case "SNEAK_KEY" -> detailed
                            ? text("gui.ascendant.catalog.hint.sneak_long",
                            "Hold sneak while activating; otherwise the server refuses the request.")
                            : text("gui.ascendant.catalog.hint.sneak", "Sneak + use");
                    case "TOGGLE" -> detailed
                            ? text("gui.ascendant.catalog.hint.toggle_long",
                            "Toggle from the catalog or radial. Turning it off is always allowed.")
                            : text("gui.ascendant.catalog.hint.toggle", "B · toggle");
                    default -> text("gui.ascendant.catalog.hint.automatic", "Automatic trigger");
                };
            }
            case SUIT -> detailed
                    ? text("gui.ascendant.catalog.hint.suit_long",
                    "Equip from this catalog. The server records the loadout and grants its powers while equipped.")
                    : text("gui.ascendant.catalog.hint.suit", "Click · equip");
            case PACK -> text("gui.ascendant.catalog.hint.pack", "Metadata only");
        };
    }

    private String requirementsText(Entry entry) {
        if (entry.type() == EntryType.POWER) {
            boolean owned = CatalogClientState.hasPower(entry.id());
            return owned
                    ? text("gui.ascendant.catalog.req.owned",
                    "Owned through unlocked powers or the equipped suit.")
                    : text("gui.ascendant.catalog.req.not_owned",
                    "Not owned. Unlock it or equip a suit that grants it.");
        }
        if (entry.type() == EntryType.ABILITY) {
            String requirement = entry.metadata().contains("|")
                    ? entry.metadata().substring(entry.metadata().indexOf('|') + 1).trim() : "";
            boolean accessible = CatalogClientState.hasAbility(entry.id());
            return (accessible ? text("gui.ascendant.catalog.req.available",
                    "Accessible now.") : text("gui.ascendant.catalog.req.locked",
                    "Not accessible now.")) + " " + requirement;
        }
        if (entry.type() == EntryType.SUIT) {
            return CatalogClientState.getEquippedSuit() != null
                    && CatalogClientState.getEquippedSuit().equals(entry.id())
                    ? text("gui.ascendant.catalog.req.equipped", "This loadout is currently equipped.")
                    : text("gui.ascendant.catalog.req.loadout",
                    "No ownership gate is inferred from the loadout; equipping is server-validated.");
        }
        return packStatus(entry);
    }

    private String packStatus(Entry entry) {
        return PackManager.getLoadedPackIds().contains(entry.packId())
                ? text("gui.ascendant.catalog.pack_loaded", "Installed and loaded; item origins are tracked.")
                : text("gui.ascendant.catalog.pack_unloaded", "Available locally; not loaded.");
    }

    private String sourceText(Entry entry) {
        String origin = entry.packId() == null
                ? text("gui.ascendant.catalog.builtin", "Built-in")
                : text("gui.ascendant.catalog.source_pack", "Pack") + ": " + entry.packId();
        return origin + " · " + entry.metadata();
    }

    private String runtimeStatus(Entry entry) {
        if (!CatalogClientState.isInitialized()) {
            return text("gui.ascendant.catalog.loading_state", "Waiting for authoritative server state.");
        }
        if (entry.type() == EntryType.PACK) {
            return packStatus(entry);
        }
        if (entry.type() == EntryType.SUIT) {
            return entry.id().equals(CatalogClientState.getEquippedSuit())
                    ? text("gui.ascendant.catalog.suit_equipped", "Equipped")
                    : text("gui.ascendant.catalog.suit_available", "Available loadout");
        }
        boolean accessible = entry.type() == EntryType.POWER
                ? CatalogClientState.hasPower(entry.id()) : CatalogClientState.hasAbility(entry.id());
        if (!accessible) {
            return text("gui.ascendant.catalog.not_owned",
                    "Not available from current powers or equipped suit.");
        }
        if (isToggled(entry)) {
            return text("gui.ascendant.catalog.active", "Active");
        }
        int cooldown = CatalogClientState.getCooldown(entry.id());
        if (cooldown > 0) {
            return text("gui.ascendant.catalog.cooldown_remaining", "On cooldown: %s ticks").formatted(cooldown);
        }
        if (entry.action().type() == CatalogInteraction.ActionType.NONE) {
            return text("gui.ascendant.catalog.automatic", "Automatic / passive");
        }
        return text("gui.ascendant.catalog.ready", "Ready");
    }

    private String shortState(Entry entry) {
        if (!entry.functional()) {
            return text("gui.ascendant.catalog.template_short", "Template");
        }
        if (entry.type() == EntryType.POWER && !CatalogClientState.isInitialized()) {
            return text("gui.ascendant.catalog.loading_short", "Syncing");
        }
        if (isToggled(entry)) {
            return text("gui.ascendant.catalog.active", "Active");
        }
        int cooldown = CatalogClientState.getCooldown(entry.id());
        if (cooldown > 0) {
            return cooldown + "t";
        }
        if (entry.action().type() == CatalogInteraction.ActionType.NONE) {
            return text("gui.ascendant.catalog.passive_short", "Passive");
        }
        return text("gui.ascendant.catalog.ready_short", "Ready");
    }

    private int stateColor(Entry entry, CatalogTheme theme) {
        if (!entry.functional()) {
            return 0xFFFFA060;
        }
        if (isToggled(entry)) {
            return theme.accentBright();
        }
        if (CatalogClientState.getCooldown(entry.id()) > 0) {
            return 0xFFFFC060;
        }
        if (entry.action().type() == CatalogInteraction.ActionType.NONE) {
            return 0xFF9AA8B8;
        }
        return 0xFF7CE38B;
    }

    private int categoryAccent(Entry entry, CatalogTheme theme) {
        if (entry.type() == EntryType.SUIT) {
            return 0xFFE8B84A;
        }
        if (entry.type() == EntryType.PACK) {
            return 0xFFB58CFF;
        }
        if (entry.type() == EntryType.ABILITY) {
            return 0xFF7CE3C0;
        }
        try {
            PowerCategory category = PowerCategory.valueOf(entry.category());
            return switch (category) {
                case FLIGHT -> 0xFF74C7FF;
                case SUPERSPEED -> 0xFFFFD24A;
                case SUPERSTRENGTH -> 0xFFFF8C5A;
                case ENERGY -> 0xFF9C8CFF;
                case TELEPORTATION -> 0xFFDA8CFF;
                default -> theme.accent();
            };
        } catch (IllegalArgumentException ignored) {
            return theme.accent();
        }
    }

    private String categoryLabel(Entry entry) {
        if (entry.categoryKey() == null) {
            return entry.category().equals("packs")
                    ? text("gui.ascendant.catalog.packs_category", "Content packs") : entry.category();
        }
        return localized(entry.categoryKey(), categoryFallback(entry));
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

    private String typeLabel(Entry entry) {
        return switch (entry.type()) {
            case POWER -> text("gui.ascendant.catalog.type.power", "Power");
            case ABILITY -> text("gui.ascendant.catalog.type.ability", "Ability");
            case SUIT -> text("gui.ascendant.catalog.type.suit", "Suit");
            case PACK -> text("gui.ascendant.catalog.type.pack", "Pack");
        };
    }

    private String joinIds(List<ResourceLocation> ids, int limit) {
        if (ids.isEmpty()) {
            return text("gui.ascendant.catalog.none", "none");
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < Math.min(limit, ids.size()); index++) {
            if (index > 0) builder.append(", ");
            builder.append(ids.get(index).getPath());
        }
        if (ids.size() > limit) {
            builder.append(" +").append(ids.size() - limit);
        }
        return builder.toString();
    }

    private int tabAt(double mouseX, double mouseY) {
        if (mouseY < TAB_Y || mouseY >= TAB_Y + TAB_HEIGHT) {
            return -1;
        }
        int available = width - MARGIN * 2 - 84;
        int tabWidth = Math.max(48, (available - (Tab.values().length - 1) * 4) / Tab.values().length);
        int x = MARGIN;
        for (int index = 0; index < Tab.values().length; index++) {
            if (mouseX >= x && mouseX < x + tabWidth) {
                return index;
            }
            x += tabWidth + 4;
        }
        return -1;
    }

    private int filterChipAt(double mouseX, double mouseY) {
        if (mouseY < CHIP_Y || mouseY >= CHIP_Y + CHIP_HEIGHT) {
            return -1;
        }
        List<String> labels = new ArrayList<>();
        labels.add(categoryLabel().getString());
        if (tab == Tab.POWERS || tab == Tab.ABILITIES) {
            labels.add(sourceLabel().getString());
        }
        if (filtersActive()) {
            labels.add(text("gui.ascendant.catalog.reset_filters", "Reset"));
        }
        int x = MARGIN;
        for (int index = 0; index < labels.size(); index++) {
            int chipWidth = Math.min(font.width(labels.get(index)) + 16,
                    Math.max(60, listWidth() - (x - MARGIN)));
            if (mouseX >= x && mouseX < x + chipWidth) {
                return index;
            }
            x += chipWidth + 5;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) {
            return false;
        }
        int tabIndex = tabAt(mouseX, mouseY);
        if (tabIndex >= 0) {
            changeTab(Tab.values()[tabIndex]);
            return true;
        }
        int chip = filterChipAt(mouseX, mouseY);
        if (chip == 0) {
            cycleCategory();
            return true;
        }
        if (chip == 1 && (tab == Tab.POWERS || tab == Tab.ABILITIES)) {
            cycleSource();
            return true;
        }
        if (chip > 0) {
            resetFilters();
            return true;
        }

        int listWidth = listWidth();
        int listBottom = height - BOTTOM_RESERVED;
        if (mouseX >= MARGIN && mouseX < MARGIN + listWidth
                && mouseY >= LIST_TOP && mouseY < listBottom) {
            int row = (int) ((mouseY - LIST_TOP - 3) / (CARD_HEIGHT + CARD_GAP));
            int index = scrollOffset + row;
            if (row >= 0 && row < visibleRowCount() && index >= 0 && index < visibleEntries.size()) {
                selectedIndex = index;
                updateActionButtons();
                return true;
            }
        }
        return false;
    }

    private void resetFilters() {
        searchQuery = "";
        if (searchBox != null) {
            searchBox.setValue("");
        }
        categoryFilter = "all";
        sourceFilter = "all";
        selectedIndex = 0;
        scrollOffset = 0;
        filterDirty = true;
        setFeedback("", 0xFFB9F4FF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxOffset = Math.max(0, visibleEntries.size() - visibleRowCount());
        if (mouseX < MARGIN + listWidth() && maxOffset > 0) {
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
        if (I18n.exists(key)) {
            return I18n.get(key);
        }
        return text("gui.ascendant.catalog.description_missing", "No localized description is available.");
    }

    private static String localized(String key, String fallback) {
        return I18n.exists(key) ? I18n.get(key) : fallback;
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
        return I18n.exists(key) ? I18n.get(key) : fallback;
    }
}
