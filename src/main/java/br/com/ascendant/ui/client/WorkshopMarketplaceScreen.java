package br.com.ascendant.ui.client;

import br.com.ascendant.pack.PackManager;
import br.com.ascendant.pack.PackManifest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Comic-inspired marketplace landing page and data-driven character cover archive.
 *
 * Character metadata is loaded from assets/ascendant/catalog/hero_archive.json.
 * Cover images are local mod resources; the screen never downloads images at runtime.
 */
@OnlyIn(Dist.CLIENT)
public final class WorkshopMarketplaceScreen extends Screen {
    private enum Page {
        MARKETPLACE, UNIVERSE, CHARACTER, COMMUNITY_PACKS
    }

    private record CharacterEntry(
            String id, String name, String universe, String alignment, String era, String team,
            String description, List<String> powers, ResourceLocation cover, int accent) {
    }

    private static final ResourceLocation ARCHIVE_DATA =
            new ResourceLocation("ascendant", "catalog/hero_archive.json");
    private static final ResourceLocation FEATURE_COVER =
            new ResourceLocation("ascendant", "textures/gui/covers/featured/multiverse.png");

    private static final int MARGIN = 12;
    private static final int BLUE = 0xFF4A91FF;
    private static final int PALE_BLUE = 0xFFB9D8FF;
    private static final int INK = 0xFF070B13;
    private static final int PANEL = 0xE6121B2B;
    private static final int PANEL_HOVER = 0xF01B2D49;

    private final Screen parent;
    private final List<CharacterEntry> archive = new ArrayList<>();
    private Page page = Page.MARKETPLACE;
    private String universe = "DC";
    private String roleFilter = "ALL";
    private String searchQuery = "";
    private int pageIndex;
    private CharacterEntry selectedCharacter;
    private PackManifest selectedPack;
    private EditBox searchBox;
    private Button roleButton;
    private Button previousPageButton;
    private Button nextPageButton;
    private String feedback = "";

    public WorkshopMarketplaceScreen(Screen parent) {
        super(Component.literal("Ascendant Marketplace"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (archive.isEmpty()) {
            loadArchive();
        }

        addRenderableWidget(Button.builder(Component.literal("X  FECHAR"), button -> onClose())
                .bounds(width - 82, 8, 70, 18).build());

        switch (page) {
            case MARKETPLACE -> initMarketplace();
            case UNIVERSE -> initUniverse();
            case CHARACTER -> initCharacter();
            case COMMUNITY_PACKS -> initCommunityPacks();
        }
    }

    private void initMarketplace() {
        // Universe banners use click hitboxes in mouseClicked so their artwork stays unobstructed.
        int buttonY = height - 30;
        addRenderableWidget(Button.builder(Component.literal("CATÁLOGO DE PODERES  →"),
                        button -> minecraft.setScreen(new PowerCatalogScreen(this)))
                .bounds(MARGIN, buttonY, Math.max(160, width / 2 - MARGIN - 4), 20).build());
        addRenderableWidget(Button.builder(Component.literal("VER TODOS OS PACKS  →"),
                        button -> openCommunityPacks())
                .bounds(width / 2 + 4, buttonY, Math.max(160, width / 2 - MARGIN - 4), 20).build());
    }

    private void initUniverse() {
        searchBox = new EditBox(font, MARGIN + 4, height < 300 ? 62 : 91, Math.max(90, width - 202), 16,
                Component.literal("Buscar personagem"));
        searchBox.setMaxLength(64);
        searchBox.setBordered(false);
        searchBox.setValue(searchQuery);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setResponder(value -> {
            searchQuery = value;
            pageIndex = 0;
            updatePagination();
        });
        addRenderableWidget(searchBox);

        roleButton = Button.builder(roleLabel(), button -> {
                    roleFilter = switch (roleFilter) {
                        case "ALL" -> "HERO";
                        case "HERO" -> "VILLAIN";
                        default -> "ALL";
                    };
                    pageIndex = 0;
                    button.setMessage(roleLabel());
                    updatePagination();
                }).bounds(width - 184, height < 300 ? 60 : 89, 91, 20).build();
        addRenderableWidget(roleButton);

        addRenderableWidget(Button.builder(Component.literal("← MARKETPLACE"), button -> openMarketplace())
                .bounds(MARGIN, height - 28, 126, 20).build());
        previousPageButton = Button.builder(Component.literal("◀"), button -> {
                    pageIndex = Math.max(0, pageIndex - 1);
                    updatePagination();
                }).bounds(width - 100, height - 28, 26, 20).build();
        addRenderableWidget(previousPageButton);
        nextPageButton = Button.builder(Component.literal("▶"), button -> {
                    pageIndex++;
                    updatePagination();
                }).bounds(width - 68, height - 28, 56, 20).build();
        addRenderableWidget(nextPageButton);
        updatePagination();
    }

    private void initCharacter() {
        addRenderableWidget(Button.builder(Component.literal("← VOLTAR AO ARQUIVO"),
                        button -> {
                            page = Page.UNIVERSE;
                            init();
                        })
                .bounds(MARGIN, height - 28, 154, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CATÁLOGO COMPLETO"),
                        button -> minecraft.setScreen(new PowerCatalogScreen(this)))
                .bounds(width - 174, height - 28, 162, 20).build());
    }

    private void initCommunityPacks() {
        addRenderableWidget(Button.builder(Component.literal("← MARKETPLACE"),
                        button -> openMarketplace())
                .bounds(MARGIN, height - 28, 126, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CATÁLOGO / PACKS"),
                        button -> minecraft.setScreen(new PowerCatalogScreen(this)))
                .bounds(width - 174, height - 28, 162, 20).build());
    }

    private Component roleLabel() {
        return Component.literal(switch (roleFilter) {
            case "HERO" -> "FILTRO: HERÓIS";
            case "VILLAIN" -> "FILTRO: VILÕES";
            default -> "FILTRO: TODOS";
        });
    }

    private void openMarketplace() {
        page = Page.MARKETPLACE;
        pageIndex = 0;
        searchQuery = "";
        roleFilter = "ALL";
        selectedCharacter = null;
        selectedPack = null;
        init();
    }

    private void openUniverse(String selectedUniverse) {
        universe = selectedUniverse;
        page = Page.UNIVERSE;
        searchQuery = "";
        roleFilter = "ALL";
        pageIndex = 0;
        selectedCharacter = null;
        init();
    }

    private void openCommunityPacks() {
        page = Page.COMMUNITY_PACKS;
        selectedPack = null;
        init();
    }

    private void updatePagination() {
        if (previousPageButton == null || nextPageButton == null) {
            return;
        }
        int maxPage = Math.max(0, (filteredCharacters().size() - 1) / pageSize());
        pageIndex = Math.min(pageIndex, maxPage);
        previousPageButton.active = pageIndex > 0;
        nextPageButton.active = pageIndex < maxPage;
    }

    private int pageSize() {
        return columns() * rows();
    }

    private int columns() {
        return width < 520 ? 2 : 4;
    }

    private int rows() {
        return 2;
    }

    private List<CharacterEntry> filteredCharacters() {
        return archive.stream()
                .filter(entry -> entry.universe().equalsIgnoreCase(universe))
                .filter(entry -> roleFilter.equals("ALL")
                        || entry.alignment().equalsIgnoreCase(roleFilter))
                .filter(entry -> searchQuery.isBlank()
                        || entry.name().toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT))
                        || entry.team().toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT))
                        || String.join(" ", entry.powers()).toLowerCase(Locale.ROOT)
                                .contains(searchQuery.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(CharacterEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void loadArchive() {
        archive.clear();
        try {
            Resource resource = Minecraft.getInstance().getResourceManager()
                    .getResource(ARCHIVE_DATA).orElse(null);
            if (resource == null) {
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray characters = root.getAsJsonArray("characters");
                for (JsonElement element : characters) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject json = element.getAsJsonObject();
                    String id = string(json, "id", "");
                    String name = string(json, "name", id);
                    String entryUniverse = string(json, "universe", "DC");
                    String alignment = string(json, "alignment", "HERO").toUpperCase(Locale.ROOT);
                    if (!alignment.equals("HERO") && !alignment.equals("VILLAIN")) {
                        alignment = "HERO";
                    }
                    ResourceLocation cover = null;
                    String coverPath = string(json, "cover", "");
                    if (!coverPath.isBlank()) {
                        try {
                            cover = new ResourceLocation("ascendant", coverPath);
                        } catch (RuntimeException ignored) {
                            cover = null;
                        }
                    }
                    archive.add(new CharacterEntry(id, name, entryUniverse, alignment,
                            string(json, "era", "CLASSIC"),
                            string(json, "team", "Unknown"),
                            string(json, "description", ""),
                            stringList(json.getAsJsonArray("powers")), cover,
                            parseColor(string(json, "accent", "#4A91FF"))));
                }
            }
        } catch (IOException | RuntimeException exception) {
            archive.clear();
            feedback = "Não foi possível ler hero_archive.json.";
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString() : fallback;
    }

    private static List<String> stringList(JsonArray array) {
        List<String> result = new ArrayList<>();
        if (array != null) {
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    result.add(element.getAsString());
                }
            }
        }
        return List.copyOf(result);
    }

    private static int parseColor(String hex) {
        try {
            String digits = hex.startsWith("#") ? hex.substring(1) : hex;
            long value = Long.parseLong(digits, 16);
            if (digits.length() == 6) {
                value |= 0xFF000000L;
            }
            return (int) value;
        } catch (RuntimeException ignored) {
            return BLUE;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ComicUi.backdrop(graphics, CatalogTheme.fallback(), width, height);
        drawTopBar(graphics);

        switch (page) {
            case MARKETPLACE -> renderMarketplace(graphics, mouseX, mouseY);
            case UNIVERSE -> renderUniverse(graphics, mouseX, mouseY);
            case CHARACTER -> renderCharacter(graphics);
            case COMMUNITY_PACKS -> renderCommunityPacks(graphics, mouseX, mouseY);
        }

        if (!feedback.isBlank()) {
            graphics.drawString(font, Component.literal(feedback), MARGIN, height - 42, 0xFFFFC978, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawTopBar(GuiGraphics graphics) {
        ComicUi.panel(graphics, MARGIN, 8, width - MARGIN * 2, 35, CatalogTheme.fallback(), true);
        graphics.drawString(font, Component.literal("ASCENDANT"), MARGIN + 10, 13, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.literal("WORKSHOP  //  COMIC ARCHIVE"),
                MARGIN + 10, 25, 0xFF80B6FF, false);
        String right = "MC 1.20.1  •  FORGE 47.3.0";
        graphics.drawString(font, right, width - MARGIN - font.width(right) - 8, 20, 0xFF8D9FBA, false);
    }

    private void renderMarketplace(GuiGraphics graphics, int mouseX, int mouseY) {
        int contentWidth = width - MARGIN * 2;
        int bannerY = 49;
        int bannerHeight = height < 300 ? 50 : Math.max(74, Math.min(88, height / 5));
        ResourceLocation banner = available(FEATURE_COVER) ? FEATURE_COVER : CatalogTheme.COSMIC.background();
        if (banner != null) {
            blitCover(graphics, banner, MARGIN, bannerY, contentWidth, bannerHeight);
        } else {
            graphics.fill(MARGIN, bannerY, width - MARGIN, bannerY + bannerHeight, 0xFF152943);
        }
        graphics.fill(MARGIN, bannerY, width - MARGIN, bannerY + bannerHeight, 0x9B06101F);
        ComicUi.panel(graphics, MARGIN, bannerY, contentWidth, bannerHeight, CatalogTheme.fallback(), true);
        graphics.drawString(font, Component.literal("THE COMIC MULTIVERSE"), MARGIN + 12, bannerY + 9,
                0xFF8FBCFF, false);
        graphics.drawString(font, Component.literal("YOUR NEXT LEGEND STARTS HERE"),
                MARGIN + 12, bannerY + 27, 0xFFFFFFFF, false);
        if (bannerHeight < 60) {
            graphics.drawString(font, Component.literal("Packs, heróis, vilões e trajes"),
                    MARGIN + 12, bannerY + 36, 0xFFD1DDF0, false);
        } else {
            ComicUi.wrapped(graphics, font,
                    "Descubra universos, capas, trajes e packs criados para expandir o Ascendant.",
                    MARGIN + 12, bannerY + 44, contentWidth - 24, 2, 10, 0xFFD1DDF0);
        }

        int cardY = bannerY + bannerHeight + 8;
        int gap = 7;
        int cardWidth = (contentWidth - gap * 2) / 3;
        int cardHeight = height < 300 ? 54 : Math.max(85, Math.min(104, height / 4));
        drawUniverseCard(graphics, mouseX, mouseY, MARGIN, cardY, cardWidth, cardHeight,
                "DC", "DC UNIVERSE", "HERÓIS • VILÕES • EQUIPES", 0xFF3B83E6,
                "textures/gui/covers/universes/dc.png");
        drawUniverseCard(graphics, mouseX, mouseY, MARGIN + cardWidth + gap, cardY, cardWidth, cardHeight,
                "MARVEL", "MARVEL UNIVERSE", "HERÓIS • MUTANTES • CÓSMICOS", 0xFFD94B59,
                "textures/gui/covers/universes/marvel.png");
        drawUniverseCard(graphics, mouseX, mouseY, MARGIN + (cardWidth + gap) * 2, cardY, cardWidth, cardHeight,
                "OTHERS", "OTHER UNIVERSES", "COMICS • ANIME • COMMUNITY", 0xFF916EE7,
                "textures/gui/covers/universes/others.png");

        int communityY = cardY + cardHeight + 10;
        graphics.drawString(font, Component.literal("COMMUNITY SPOTLIGHT"),
                MARGIN + 2, communityY, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.literal("PACKS INSTALADOS / LOCAIS"),
                width - MARGIN - font.width("PACKS INSTALADOS / LOCAIS") - 2,
                communityY, 0xFF88ABDF, false);

        List<PackManifest> packs = PackManager.getInstalledPacks().stream()
                .sorted(Comparator.comparing(PackManifest::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int packY = communityY + 16;
        int packHeight = height < 300 ? Math.max(18, Math.min(26, height - packY - 40))
                : Math.max(60, height - packY - 43);
        if (packs.isEmpty()) {
            ComicUi.panel(graphics, MARGIN, packY, contentWidth, packHeight, CatalogTheme.fallback(), false);
            graphics.drawString(font, Component.literal(height < 300
                            ? "PACKS LOCAIS: " + packs.size() + "  •  ABRA TODOS PARA VER"
                            : "NENHUM PACK ASCENDANT LOCAL"),
                    MARGIN + 10, packY + (height < 300 ? 7 : 9), 0xFFFFFFFF, false);
            if (height >= 300) {
                ComicUi.wrapped(graphics, font,
                        "Os manifestos de packs instalados aparecem aqui. Para conteúdo comunitário, use arquivos .zip no formato Ascendant.",
                        MARGIN + 10, packY + 23, contentWidth - 20, 2, 10, 0xFF9EB0C9);
            }
        } else {
            int visible = Math.min(3, packs.size());
            int packGap = 6;
            int itemWidth = (contentWidth - packGap * (visible - 1)) / visible;
            for (int i = 0; i < visible; i++) {
                PackManifest pack = packs.get(i);
                int x = MARGIN + i * (itemWidth + packGap);
                boolean hover = contains(mouseX, mouseY, x, packY, itemWidth, packHeight);
                ComicUi.panel(graphics, x, packY, itemWidth, packHeight,
                        hover ? CatalogTheme.COSMIC : CatalogTheme.fallback(), hover);
                graphics.drawString(font, ComicUi.fit(font, pack.getName(), itemWidth - 16),
                        x + 8, packY + 8, 0xFFFFFFFF, false);
                graphics.drawString(font, "v" + pack.getVersion(), x + 8, packY + 21, 0xFF82B7FF, false);
                graphics.drawString(font, pack.getPowerCount() + " POWERS", x + 8, packY + 34,
                        0xFFB5C5DB, false);
                graphics.drawString(font, pack.getAbilityCount() + " ABILITIES", x + 8, packY + 45,
                        0xFFB5C5DB, false);
            }
        }
    }

    private void drawUniverseCard(GuiGraphics graphics, int mouseX, int mouseY,
                                  int x, int y, int cardWidth, int cardHeight,
                                  String id, String title, String subtitle, int accent, String coverPath) {
        ResourceLocation cover = new ResourceLocation("ascendant", coverPath);
        boolean hover = contains(mouseX, mouseY, x, y, cardWidth, cardHeight);
        graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xE60D1624);
        if (available(cover)) {
            blitCover(graphics, cover, x, y, cardWidth, cardHeight);
            graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xA8070D18);
        } else {
            graphics.fillGradient(x, y, x + cardWidth, y + cardHeight, ComicUi.withAlpha(accent, 0x55), 0xE609101B);
            graphics.fill(x + 6, y + 8, x + 8, y + cardHeight - 8, accent);
            // A restrained comic burst motif; kept decorative so cover text remains readable.
            for (int ray = 0; ray < 5; ray++) {
                int lineX = x + cardWidth - 14 - ray * 8;
                graphics.fill(lineX, y + 6, lineX + 1, y + cardHeight - 6,
                        ComicUi.withAlpha(accent, 0x23));
            }
        }
        if (hover) {
            int glow = 0x55 + (int) (Math.sin(System.currentTimeMillis() / 180.0) * 25.0 + 25.0);
            graphics.renderOutline(x, y, cardWidth, cardHeight, ComicUi.withAlpha(accent, glow));
        } else {
            graphics.renderOutline(x, y, cardWidth, cardHeight, ComicUi.withAlpha(accent, 0xCC));
        }
        graphics.fill(x, y + cardHeight - 34, x + cardWidth, y + cardHeight, 0xD9070B12);
        graphics.drawString(font, ComicUi.fit(font, title, cardWidth - 14),
                x + 7, y + cardHeight - 29, 0xFFFFFFFF, false);
        graphics.drawString(font, ComicUi.fit(font, subtitle, cardWidth - 14),
                x + 7, y + cardHeight - 15, ComicUi.withAlpha(accent, 0xFF), false);
        graphics.drawString(font, "OPEN →", x + cardWidth - font.width("OPEN →") - 7,
                y + 5, 0xFFFFFFFF, false);
    }

    private void renderUniverse(GuiGraphics graphics, int mouseX, int mouseY) {
        int accent = universeColor(universe);
        graphics.drawString(font, Component.literal(universe + " UNIVERSE  /  CHARACTER FILES"),
                MARGIN, 51, 0xFFFFFFFF, false);
        if (height >= 300) {
            graphics.drawString(font, Component.literal("ARQUIVO LOCAL • CAPAS PERSONALIZÁVEIS • PAGINAÇÃO"),
                    MARGIN, 65, 0xFF8FA5C4, false);
        }
        int gridTop = height < 300 ? 85 : 119;
        int columns = columns();
        int rows = rows();
        int gap = 6;
        int totalWidth = width - MARGIN * 2;
        int cardWidth = (totalWidth - gap * (columns - 1)) / columns;
        int availableHeight = height - gridTop - 43;
        int cardHeight = Math.max(48, Math.min(138, (availableHeight - gap * (rows - 1)) / rows));
        List<CharacterEntry> entries = filteredCharacters();
        int start = pageIndex * pageSize();
        int end = Math.min(entries.size(), start + pageSize());
        if (entries.isEmpty()) {
            ComicUi.panel(graphics, MARGIN, gridTop, totalWidth, 100, CatalogTheme.fallback(), false);
            graphics.drawString(font, Component.literal("NENHUM PERSONAGEM ENCONTRADO"),
                    MARGIN + 12, gridTop + 14, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.literal("Edite assets/ascendant/catalog/hero_archive.json para ampliar o arquivo."),
                    MARGIN + 12, gridTop + 31, 0xFF9EAFC7, false);
            return;
        }
        for (int index = start; index < end; index++) {
            CharacterEntry entry = entries.get(index);
            int localIndex = index - start;
            int col = localIndex % columns;
            int row = localIndex / columns;
            int x = MARGIN + col * (cardWidth + gap);
            int y = gridTop + row * (cardHeight + gap);
            boolean hover = contains(mouseX, mouseY, x, y, cardWidth, cardHeight);
            drawCharacterCard(graphics, entry, x, y, cardWidth, cardHeight, hover, accent);
        }

        if (height >= 300) {
            graphics.drawString(font, "REGISTROS: " + entries.size(),
                    MARGIN + 4, height - 42, 0xFF8EA5C5, false);
            String pageLabel = "PÁGINA " + (pageIndex + 1) + " / "
                    + Math.max(1, (entries.size() + pageSize() - 1) / pageSize());
            graphics.drawString(font, pageLabel,
                    width / 2 - font.width(pageLabel) / 2, height - 42, 0xFF8EA5C5, false);
        }
    }

    private void drawCharacterCard(GuiGraphics graphics, CharacterEntry entry, int x, int y,
                                   int cardWidth, int cardHeight, boolean hover, int fallbackAccent) {
        int border = entry.accent() == 0 ? fallbackAccent : entry.accent();
        graphics.fill(x, y, x + cardWidth, y + cardHeight, PANEL);
        int artHeight = Math.max(24, cardHeight - 27);
        if (entry.cover() != null && available(entry.cover())) {
            blitCover(graphics, entry.cover(), x + 1, y + 1, cardWidth - 2, artHeight);
            graphics.fill(x + 1, y + 1, x + cardWidth - 1, y + artHeight,
                    ComicUi.withAlpha(INK, 0x16));
        } else {
            graphics.fillGradient(x + 1, y + 1, x + cardWidth - 1, y + artHeight,
                    ComicUi.withAlpha(border, 0x99), 0xFF111826);
            graphics.fill(x + 6, y + 7, x + 8, y + artHeight - 5, border);
            String code = entry.name().isBlank() ? "?" : entry.name().substring(0, 1).toUpperCase(Locale.ROOT);
            graphics.drawCenteredString(font, Component.literal(code),
                    x + cardWidth / 2, y + artHeight / 2 - 12, 0xCCFFFFFF);
            graphics.drawCenteredString(font, Component.literal(ComicUi.fit(font, entry.name().toUpperCase(Locale.ROOT),
                            cardWidth - 12)),
                    x + cardWidth / 2, y + artHeight / 2 + 4, 0xFFFFFFFF);
        }
        graphics.fill(x + 1, y + artHeight, x + cardWidth - 1, y + cardHeight - 1, 0xF00B101A);
        graphics.drawString(font, ComicUi.fit(font, entry.name(), cardWidth - 12),
                x + 6, y + artHeight + 4, 0xFFFFFFFF, false);
        String role = entry.alignment().equalsIgnoreCase("VILLAIN") ? "VILÃO" : "HERÓI";
        graphics.drawString(font, role, x + 6, y + artHeight + 15, border, false);
        graphics.renderOutline(x, y, cardWidth, cardHeight,
                ComicUi.withAlpha(border, hover ? 0xFF : 0xAA));
        if (hover) {
            graphics.fill(x, y, x + cardWidth, y + 2, 0xFFFFFFFF);
        }
    }

    private void renderCharacter(GuiGraphics graphics) {
        CharacterEntry entry = selectedCharacter;
        if (entry == null) {
            graphics.drawCenteredString(font, Component.literal("Nenhum personagem selecionado"),
                    width / 2, height / 2, 0xFFFFFFFF);
            return;
        }
        int left = MARGIN;
        int top = 54;
        int panelWidth = Math.min(230, width / 3);
        int panelHeight = height - 96;
        ComicUi.panel(graphics, left, top, panelWidth, panelHeight,
                CatalogTheme.fallback(), true);
        if (entry.cover() != null && available(entry.cover())) {
            blitCover(graphics, entry.cover(), left + 7, top + 7, panelWidth - 14, panelHeight - 14);
        } else {
            graphics.fillGradient(left + 7, top + 7, left + panelWidth - 7, top + panelHeight - 7,
                    ComicUi.withAlpha(entry.accent(), 0xB0), 0xFF101725);
            graphics.drawCenteredString(font, Component.literal(entry.name().toUpperCase(Locale.ROOT)),
                    left + panelWidth / 2, top + panelHeight / 2 - 4, 0xFFFFFFFF);
        }

        int detailX = left + panelWidth + 12;
        int detailWidth = width - detailX - MARGIN;
        ComicUi.panel(graphics, detailX, top, detailWidth, panelHeight, CatalogTheme.fallback(), false);
        graphics.drawString(font, Component.literal("CHARACTER DOSSIER  //  " + entry.id().toUpperCase(Locale.ROOT)),
                detailX + 10, top + 10, 0xFF80B6FF, false);
        graphics.drawString(font, Component.literal(entry.name().toUpperCase(Locale.ROOT)),
                detailX + 10, top + 29, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.literal(entry.universe() + "  •  " + entry.alignment()
                + "  •  " + entry.era()), detailX + 10, top + 45, entry.accent(), false);
        graphics.drawString(font, Component.literal("EQUIPE: " + entry.team()),
                detailX + 10, top + 65, 0xFFD8E4F6, false);
        ComicUi.wrapped(graphics, font, entry.description(), detailX + 10, top + 84,
                detailWidth - 20, 5, 11, 0xFFB4C4DB);
        graphics.drawString(font, Component.literal("PODERES / CARACTERÍSTICAS"),
                detailX + 10, top + 145, 0xFF80B6FF, false);
        int powerY = top + 162;
        for (int i = 0; i < Math.min(9, entry.powers().size()); i++) {
            graphics.fill(detailX + 10, powerY + i * 13 + 3,
                    detailX + 14, powerY + i * 13 + 7, entry.accent());
            graphics.drawString(font, Component.literal(entry.powers().get(i)),
                    detailX + 20, powerY + i * 13, 0xFFD7E3F4, false);
        }
        graphics.drawString(font, Component.literal("Arte: " + (entry.cover() == null
                        ? "placeholder" : entry.cover().getPath())),
                detailX + 10, height - 42, 0xFF7E94B3, false);
    }

    private void renderCommunityPacks(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = 55;
        graphics.drawString(font, Component.literal("ASCENDANT COMMUNITY PACKS"),
                MARGIN, top, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.literal("Manifestos locais e pacotes que já estão disponíveis no diretório do mod."),
                MARGIN, top + 15, 0xFF9FB1CC, false);
        List<PackManifest> packs = PackManager.getInstalledPacks().stream()
                .sorted(Comparator.comparing(PackManifest::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int y = top + 40;
        int rowHeight = 60;
        int visibleCount = Math.max(1, (height - y - 45) / rowHeight);
        if (packs.isEmpty()) {
            ComicUi.panel(graphics, MARGIN, y, width - MARGIN * 2, 96,
                    CatalogTheme.fallback(), false);
            graphics.drawString(font, Component.literal("NENHUM PACK LOCAL ENCONTRADO"),
                    MARGIN + 10, y + 12, 0xFFFFFFFF, false);
            ComicUi.wrapped(graphics, font,
                    "Coloque packs .zip no diretório ascendant_packs para que os manifestos apareçam nesta lista. O marketplace não baixa executáveis ou mods arbitrários.",
                    MARGIN + 10, y + 31, width - MARGIN * 2 - 20, 3, 11, 0xFFB7C7DD);
            return;
        }
        int end = Math.min(packs.size(), visibleCount);
        for (int i = 0; i < end; i++) {
            PackManifest pack = packs.get(i);
            boolean hover = contains(mouseX, mouseY, MARGIN, y, width - MARGIN * 2, rowHeight - 5);
            ComicUi.panel(graphics, MARGIN, y, width - MARGIN * 2, rowHeight - 5,
                    hover ? CatalogTheme.COSMIC : CatalogTheme.fallback(), hover);
            graphics.drawString(font, ComicUi.fit(font, pack.getName(), width - 230),
                    MARGIN + 10, y + 7, 0xFFFFFFFF, false);
            graphics.drawString(font, pack.getAuthor() + "  •  v" + pack.getVersion(),
                    MARGIN + 10, y + 21, 0xFF9CB2D2, false);
            graphics.drawString(font, pack.getPowerCount() + " poderes  /  "
                    + pack.getAbilityCount() + " habilidades  /  " + pack.getSuitCount() + " trajes",
                    MARGIN + 10, y + 36, 0xFFB8C8DE, false);
            y += rowHeight;
        }
    }

    private ResourceLocation universeCover(String id) {
        return new ResourceLocation("ascendant", "textures/gui/covers/universes/"
                + id.toLowerCase(Locale.ROOT) + ".png");
    }

    private int universeColor(String id) {
        return switch (id.toUpperCase(Locale.ROOT)) {
            case "MARVEL" -> 0xFFD94B59;
            case "OTHERS" -> 0xFF916EE7;
            default -> BLUE;
        };
    }

    private boolean available(ResourceLocation location) {
        return location != null && Minecraft.getInstance().getResourceManager()
                .getResource(location).isPresent();
    }

    private static void blitCover(GuiGraphics graphics, ResourceLocation texture,
                                  int x, int y, int width, int height) {
        graphics.blit(texture, x, y, width, height, 0.0F, 0.0F, 256, 256, 256, 256);
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (page == Page.MARKETPLACE) {
                int contentWidth = width - MARGIN * 2;
                int bannerHeight = Math.max(74, Math.min(88, height / 5));
                int cardY = 49 + bannerHeight + 8;
                int gap = 7;
                int cardWidth = (contentWidth - gap * 2) / 3;
                int cardHeight = Math.max(85, Math.min(104, height / 4));
                if (contains(mouseX, mouseY, MARGIN, cardY, cardWidth, cardHeight)) {
                    openUniverse("DC");
                    return true;
                }
                if (contains(mouseX, mouseY, MARGIN + cardWidth + gap, cardY, cardWidth, cardHeight)) {
                    openUniverse("MARVEL");
                    return true;
                }
                if (contains(mouseX, mouseY, MARGIN + (cardWidth + gap) * 2, cardY, cardWidth, cardHeight)) {
                    openUniverse("OTHERS");
                    return true;
                }
            } else if (page == Page.UNIVERSE) {
                int gridTop = 119;
                int columns = columns();
                int gap = 6;
                int totalWidth = width - MARGIN * 2;
                int cardWidth = (totalWidth - gap * (columns - 1)) / columns;
                int availableHeight = height - gridTop - 43;
                int cardHeight = Math.max(68, Math.min(138, (availableHeight - gap * (rows() - 1)) / rows()));
                List<CharacterEntry> entries = filteredCharacters();
                int start = pageIndex * pageSize();
                int end = Math.min(entries.size(), start + pageSize());
                for (int index = start; index < end; index++) {
                    int localIndex = index - start;
                    int x = MARGIN + (localIndex % columns) * (cardWidth + gap);
                    int y = gridTop + (localIndex / columns) * (cardHeight + gap);
                    if (contains(mouseX, mouseY, x, y, cardWidth, cardHeight)) {
                        selectedCharacter = entries.get(index);
                        page = Page.CHARACTER;
                        init();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
