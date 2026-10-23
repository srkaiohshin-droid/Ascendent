package br.com.ascendant.ui.client;

import br.com.ascendant.catalog.CatalogInteraction;
import br.com.ascendant.core.PowerCategory;
import br.com.ascendant.network.AscendantNetwork;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Quick-use radial. The same themed visual language as the catalog keeps the
 * identity consistent, while every slot still maps to a server-validated
 * power or ability action.
 */
@OnlyIn(Dist.CLIENT)
public final class RadialMenuScreen extends Screen {
    private static final int SLOTS_PER_PAGE = 8;
    private static final double RADIUS = 118.0D;
    private static final double DEAD_ZONE = 32.0D;
    private static final int SLOT_WIDTH = 112;
    private static final int SLOT_HEIGHT = 38;

    private record Option(CatalogInteraction.Action action, String label, String kind, int accent) {
    }

    private final Screen parent;
    private final List<Option> options = new ArrayList<>();
    private int page;
    private int selectedSlot = -1;
    private String feedback = "";

    public RadialMenuScreen(Screen parent) {
        super(Component.translatable("gui.ascendant.radial.title"));
        this.parent = parent;
        collectOptions();
        AscendantNetwork.requestCatalogState();
    }

    private void collectOptions() {
        PowerRegistry.getAll().stream()
                .filter(power -> CatalogInteraction.forPower(power).type() != CatalogInteraction.ActionType.NONE)
                .sorted(Comparator.comparing(power -> power.getId().toString()))
                .forEach(power -> options.add(new Option(CatalogInteraction.forPower(power),
                        displayName(power.getNameKey(), power.getId()), power.getType().name(),
                        powerAccent(power))));
        AbilityRegistry.getAll().stream()
                .filter(ability -> CatalogInteraction.forAbility(ability).type()
                        == CatalogInteraction.ActionType.ACTIVATE_ABILITY
                        || CatalogInteraction.forAbility(ability).type()
                        == CatalogInteraction.ActionType.TOGGLE_ABILITY)
                .sorted(Comparator.comparing(ability -> ability.getId().toString()))
                .forEach(ability -> options.add(new Option(CatalogInteraction.forAbility(ability),
                        displayName(ability.getNameKey(), ability.getId()), ability.getTrigger().name(),
                        0xFF7CE3C0)));
    }

    public void nextPage(int direction) {
        int pageCount = pageCount();
        if (pageCount <= 1) return;
        page = Math.floorMod(page + direction, pageCount);
        selectedSlot = -1;
    }

    public void confirmSelection() {
        dispatchSelected(null);
    }

    public void activateSelected() {
        dispatchSelected(Boolean.FALSE);
    }

    public void toggleSelected() {
        dispatchSelected(Boolean.TRUE);
    }

    private void dispatchSelected(Boolean toggleOnly) {
        if (selectedSlot < 0) return;
        int index = page * SLOTS_PER_PAGE + selectedSlot;
        if (index < 0 || index >= options.size()) return;
        CatalogInteraction.Action action = options.get(index).action();
        boolean isToggle = action.type() == CatalogInteraction.ActionType.TOGGLE_POWER
                || action.type() == CatalogInteraction.ActionType.TOGGLE_ABILITY;
        if (toggleOnly != null && toggleOnly != isToggle) return;
        if (!isAuthorizedAndReady(action)) {
            feedback = runtimeStatus(action);
            return;
        }
        dispatch(action);
        Minecraft.getInstance().setScreen(parent);
    }

    private void dispatch(CatalogInteraction.Action action) {
        switch (action.type()) {
            case ACTIVATE_POWER -> AscendantNetwork.sendActivatePower(action.id());
            case TOGGLE_POWER -> AscendantNetwork.sendTogglePowerRequest(action.id());
            case ACTIVATE_ABILITY -> AscendantNetwork.sendActivateAbility(action.id());
            case TOGGLE_ABILITY -> AscendantNetwork.sendToggleAbilityRequest(action.id());
            default -> {
                // The radial model only collects executable powers and key-triggered abilities.
            }
        }
    }

    private boolean isAuthorizedAndReady(CatalogInteraction.Action action) {
        if (!CatalogClientState.isInitialized()) {
            return false;
        }
        boolean authorized = action.type() == CatalogInteraction.ActionType.ACTIVATE_POWER
                || action.type() == CatalogInteraction.ActionType.TOGGLE_POWER
                ? CatalogClientState.hasPower(action.id())
                : CatalogClientState.hasAbility(action.id());
        if (!authorized) {
            return false;
        }
        if (isToggle(action.type()) && isToggled(action)) {
            return true;
        }
        return CatalogClientState.getCooldown(action.id()) == 0;
    }

    private static boolean isToggle(CatalogInteraction.ActionType type) {
        return type == CatalogInteraction.ActionType.TOGGLE_POWER
                || type == CatalogInteraction.ActionType.TOGGLE_ABILITY;
    }

    private static boolean isToggled(CatalogInteraction.Action action) {
        return action.type() == CatalogInteraction.ActionType.TOGGLE_POWER
                ? CatalogClientState.isPowerToggled(action.id())
                : action.type() == CatalogInteraction.ActionType.TOGGLE_ABILITY
                && CatalogClientState.isAbilityToggled(action.id());
    }

    private String runtimeStatus(CatalogInteraction.Action action) {
        if (!CatalogClientState.isInitialized()) {
            return I18n.get("gui.ascendant.catalog.loading_state");
        }
        boolean authorized = action.type() == CatalogInteraction.ActionType.ACTIVATE_POWER
                || action.type() == CatalogInteraction.ActionType.TOGGLE_POWER
                ? CatalogClientState.hasPower(action.id())
                : CatalogClientState.hasAbility(action.id());
        if (!authorized) {
            return I18n.get("gui.ascendant.catalog.not_owned");
        }
        if (isToggle(action.type()) && isToggled(action)) {
            return I18n.get("gui.ascendant.catalog.active");
        }
        int cooldown = CatalogClientState.getCooldown(action.id());
        if (cooldown > 0) {
            return I18n.get("gui.ascendant.catalog.cooldown_remaining").formatted(cooldown);
        }
        return I18n.get("gui.ascendant.catalog.ready");
    }

    private int pageCount() {
        return Math.max(1, (options.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
    }

    private void selectAt(double mouseX, double mouseY) {
        double centerX = width / 2.0D;
        double centerY = height / 2.0D;
        int slot = CatalogInteraction.radialSector(mouseX - centerX, mouseY - centerY,
                SLOTS_PER_PAGE, DEAD_ZONE);
        int index = page * SLOTS_PER_PAGE + slot;
        selectedSlot = slot >= 0 && index < options.size() ? slot : -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CatalogTheme theme = CatalogTheme.current();
        ComicUi.backdrop(graphics, theme, width, height);
        graphics.fill(0, 0, width, height, 0x66070A10);

        double centerX = width / 2.0D;
        double centerY = height / 2.0D;
        int first = page * SLOTS_PER_PAGE;
        String pageText = I18n.get("gui.ascendant.radial.page")
                .formatted(page + 1, pageCount());

        ComicUi.badge(graphics, font, I18n.get("gui.ascendant.radial.title"),
                (int) centerX - 52, 12, ComicUi.withAlpha(theme.accent(), 0x44), theme.accentBright());
        graphics.drawString(font, pageText, width - 92, 16, theme.muted(), false);
        String hint = I18n.get("gui.ascendant.radial.key_hint");
        graphics.drawString(font, ComicUi.fit(font, hint, width - 24), 12, height - 18, theme.muted(), false);

        if (!feedback.isEmpty()) {
            ComicUi.wrapped(graphics, font, feedback, 12, height - 38, width - 24, 2, 10, 0xFFFFC060);
        }

        renderConnectors(graphics, theme, centerX, centerY, first);
        renderHub(graphics, theme, centerX, centerY, first);

        for (int slot = 0; slot < SLOTS_PER_PAGE; slot++) {
            int optionIndex = first + slot;
            if (optionIndex >= options.size()) continue;
            renderSlot(graphics, theme, options.get(optionIndex), slot, centerX, centerY);
        }

        if (options.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.ascendant.radial.empty"),
                    width / 2, (int) centerY + 52, 0xFFFFC060);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderConnectors(GuiGraphics graphics, CatalogTheme theme,
                                  double centerX, double centerY, int first) {
        for (int slot = 0; slot < SLOTS_PER_PAGE; slot++) {
            int optionIndex = first + slot;
            if (optionIndex >= options.size()) continue;
            double angle = -Math.PI / 2.0D + slot * (Math.PI * 2.0D / SLOTS_PER_PAGE);
            int innerX = (int) Math.round(centerX + Math.cos(angle) * 48.0D);
            int innerY = (int) Math.round(centerY + Math.sin(angle) * 48.0D);
            int outerX = (int) Math.round(centerX + Math.cos(angle) * (RADIUS - SLOT_WIDTH * 0.35D));
            int outerY = (int) Math.round(centerY + Math.sin(angle) * (RADIUS - SLOT_HEIGHT * 0.35D));
            int color = slot == selectedSlot ? theme.accentBright()
                    : ComicUi.withAlpha(theme.muted(), 0x66);
            drawLine(graphics, innerX, innerY, outerX, outerY, color);
        }
    }

    private void renderHub(GuiGraphics graphics, CatalogTheme theme, double centerX, double centerY,
                           int first) {
        Option selected = selectedSlot < 0 || first + selectedSlot >= options.size()
                ? null : options.get(first + selectedSlot);
        int hubWidth = 132;
        int hubHeight = 56;
        ComicUi.panel(graphics, (int) centerX - hubWidth / 2, (int) centerY - hubHeight / 2,
                hubWidth, hubHeight, theme, true);
        if (selected == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.ascendant.radial.choose"),
                    (int) centerX, (int) centerY - 8, theme.ink());
            graphics.drawCenteredString(font, I18n.get("gui.ascendant.radial.choose_hint"),
                    (int) centerX, (int) centerY + 5, theme.muted());
            return;
        }
        graphics.drawCenteredString(font, ComicUi.fit(font, selected.label(), hubWidth - 12),
                (int) centerX, (int) centerY - 12, theme.ink());
        graphics.drawCenteredString(font, ComicUi.fit(font, runtimeStatus(selected.action()), hubWidth - 12),
                (int) centerX, (int) centerY, stateColor(selected.action(), theme));
        String actionHint = selected.action().type() == CatalogInteraction.ActionType.TOGGLE_POWER
                || selected.action().type() == CatalogInteraction.ActionType.TOGGLE_ABILITY
                ? I18n.get("gui.ascendant.radial.toggle_hint")
                : I18n.get("gui.ascendant.radial.use_hint");
        graphics.drawCenteredString(font, ComicUi.fit(font, actionHint, hubWidth - 12),
                (int) centerX, (int) centerY + 12, theme.muted());
    }

    private void renderSlot(GuiGraphics graphics, CatalogTheme theme, Option option, int slot,
                            double centerX, double centerY) {
        double angle = -Math.PI / 2.0D + slot * (Math.PI * 2.0D / SLOTS_PER_PAGE);
        int x = (int) Math.round(centerX + Math.cos(angle) * RADIUS);
        int y = (int) Math.round(centerY + Math.sin(angle) * RADIUS);
        boolean highlighted = slot == selectedSlot;
        boolean usable = isAuthorizedAndReady(option.action());
        boolean active = isToggled(option.action());

        int left = x - SLOT_WIDTH / 2;
        int top = y - SLOT_HEIGHT / 2;
        int fill = !usable ? 0xD0301E24 : highlighted
                ? ComicUi.withAlpha(option.accent(), 0x66) : 0xD0141A26;
        graphics.fill(left, top, left + SLOT_WIDTH, top + SLOT_HEIGHT, fill);
        graphics.renderOutline(left, top, SLOT_WIDTH, SLOT_HEIGHT,
                !usable ? 0xFFFF8060 : highlighted ? theme.accentBright()
                        : ComicUi.withAlpha(option.accent(), 0x88));
        graphics.fill(left, top, left + 4, top + SLOT_HEIGHT, option.accent());

        graphics.drawString(font, ComicUi.fit(font, option.label(), SLOT_WIDTH - 16),
                left + 9, top + 6, theme.ink(), false);
        String state = runtimeStatus(option.action());
        graphics.drawString(font, ComicUi.fit(font, state, SLOT_WIDTH - 16),
                left + 9, top + 17, stateColor(option.action(), theme), false);
        String kind = localizedKind(option.kind());
        graphics.drawString(font, ComicUi.fit(font, kind, SLOT_WIDTH - 16),
                left + 9, top + 27, theme.muted(), false);
        if (active) {
            graphics.fill(left + SLOT_WIDTH - 8, top + 4, left + SLOT_WIDTH - 4, top + 8,
                    theme.accentBright());
        }
    }

    private int stateColor(CatalogInteraction.Action action, CatalogTheme theme) {
        if (!CatalogClientState.isInitialized() || !isAuthorized(action)) {
            return 0xFFFFA080;
        }
        if (isToggled(action)) {
            return theme.accentBright();
        }
        if (CatalogClientState.getCooldown(action.id()) > 0) {
            return 0xFFFFC060;
        }
        return 0xFF7CE38B;
    }

    private static boolean isAuthorized(CatalogInteraction.Action action) {
        return action.type() == CatalogInteraction.ActionType.ACTIVATE_POWER
                || action.type() == CatalogInteraction.ActionType.TOGGLE_POWER
                ? CatalogClientState.hasPower(action.id())
                : CatalogClientState.hasAbility(action.id());
    }

    private static String localizedKind(String kind) {
        String key = "gui.ascendant.radial.kind." + kind;
        return I18n.exists(key) ? I18n.get(key) : humanize(kind);
    }

    private static int powerAccent(PowerDefinition power) {
        PowerCategory category = power.getCategory();
        return switch (category) {
            case FLIGHT -> 0xFF74C7FF;
            case SUPERSPEED -> 0xFFFFD24A;
            case SUPERSTRENGTH -> 0xFFFF8C5A;
            case ENERGY -> 0xFF9C8CFF;
            case TELEPORTATION -> 0xFFDA8CFF;
            default -> 0xFF58D7FF;
        };
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        int steps = 0;
        while (steps++ < 512) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int doubled = error * 2;
            if (doubled >= dy) {
                error += dy;
                x0 += sx;
            }
            if (doubled <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        selectAt(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            onClose();
            return true;
        }
        if (button == 0) {
            selectAt(mouseX, mouseY);
            if (selectedSlot >= 0) confirmSelection();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0.0D) nextPage(delta < 0.0D ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            confirmSelection();
            return true;
        }
        if (keyCode == 262 || keyCode == 264) {
            nextPage(1);
            return true;
        }
        if (keyCode == 263 || keyCode == 265) {
            nextPage(-1);
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

    private static String displayName(String key, ResourceLocation id) {
        return I18n.exists(key) ? I18n.get(key) : humanize(id.getPath());
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
}
