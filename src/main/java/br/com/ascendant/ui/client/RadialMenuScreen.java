package br.com.ascendant.ui.client;

import br.com.ascendant.catalog.CatalogInteraction;
import br.com.ascendant.network.AscendantNetwork;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class RadialMenuScreen extends Screen {
    private static final int SLOTS_PER_PAGE = 8;
    private static final double RADIUS = 118.0D;
    private static final double DEAD_ZONE = 32.0D;

    private record Option(CatalogInteraction.Action action, String label, String kind) {
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
                        displayName(power.getNameKey(), power.getId()), power.getType().name())));
        AbilityRegistry.getAll().stream()
                .filter(ability -> CatalogInteraction.forAbility(ability).type()
                        == CatalogInteraction.ActionType.ACTIVATE_ABILITY
                        || CatalogInteraction.forAbility(ability).type()
                        == CatalogInteraction.ActionType.TOGGLE_ABILITY)
                .sorted(Comparator.comparing(ability -> ability.getId().toString()))
                .forEach(ability -> options.add(new Option(CatalogInteraction.forAbility(ability),
                        displayName(ability.getNameKey(), ability.getId()), ability.getTrigger().name())));
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
            return net.minecraft.client.resources.language.I18n.get("gui.ascendant.catalog.loading_state");
        }
        boolean authorized = action.type() == CatalogInteraction.ActionType.ACTIVATE_POWER
                || action.type() == CatalogInteraction.ActionType.TOGGLE_POWER
                ? CatalogClientState.hasPower(action.id())
                : CatalogClientState.hasAbility(action.id());
        if (!authorized) {
            return net.minecraft.client.resources.language.I18n.get("gui.ascendant.catalog.not_owned");
        }
        int cooldown = CatalogClientState.getCooldown(action.id());
        if (cooldown > 0) {
            return net.minecraft.client.resources.language.I18n.get("gui.ascendant.catalog.cooldown_remaining")
                    .formatted(cooldown);
        }
        return net.minecraft.client.resources.language.I18n.get("gui.ascendant.catalog.ready");
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
        graphics.fill(0, 0, width, height, 0xB0000000);
        double centerX = width / 2.0D;
        double centerY = height / 2.0D;
        int first = page * SLOTS_PER_PAGE;
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("gui.ascendant.radial.page", page + 1, pageCount()),
                width / 2, height - 26, 0xFFCCCCCC);
        if (!feedback.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal(feedback), width / 2, height - 40, 0xFFFFC060);
        }

        graphics.fill((int) centerX - 40, (int) centerY - 24, (int) centerX + 40, (int) centerY + 24,
                0xE0282838);
        graphics.renderOutline((int) centerX - 40, (int) centerY - 24, 80, 48, 0xFF8090B0);
        Option selected = selectedSlot < 0 || first + selectedSlot >= options.size()
                ? null : options.get(first + selectedSlot);
        graphics.drawCenteredString(font,
                selected == null ? Component.translatable("gui.ascendant.radial.choose")
                        : Component.literal(font.plainSubstrByWidth(selected.label(), 72)),
                (int) centerX, (int) centerY - 4, 0xFFFFFFFF);

        for (int slot = 0; slot < SLOTS_PER_PAGE; slot++) {
            int optionIndex = first + slot;
            double angle = -Math.PI / 2.0D + slot * (Math.PI * 2.0D / SLOTS_PER_PAGE);
            int x = (int) Math.round(centerX + Math.cos(angle) * RADIUS);
            int y = (int) Math.round(centerY + Math.sin(angle) * RADIUS);
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, centerY, 0);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) (angle - Math.PI / 2.0D)));
            graphics.fill(-1, (int) RADIUS - 1, 1, (int) RADIUS + 1, 0xFF8090B0);
            graphics.pose().popPose();
            if (optionIndex >= options.size()) continue;

            Option option = options.get(optionIndex);
            boolean highlighted = slot == selectedSlot;
            boolean usable = isAuthorizedAndReady(option.action());
            int boxWidth = 104;
            int boxHeight = 32;
            graphics.fill(x - boxWidth / 2, y - boxHeight / 2, x + boxWidth / 2, y + boxHeight / 2,
                    !usable ? 0xD0382830 : highlighted ? 0xF06088B0 : 0xD0303848);
            graphics.renderOutline(x - boxWidth / 2, y - boxHeight / 2, boxWidth, boxHeight,
                    !usable ? 0xFFFF8060 : highlighted ? 0xFFFFFF80 : 0xFF8090B0);
            graphics.drawCenteredString(font, font.plainSubstrByWidth(option.label(), boxWidth - 8),
                    x, y - 7, 0xFFFFFFFF);
            String state = runtimeStatus(option.action());
            graphics.drawCenteredString(font, font.plainSubstrByWidth(state, boxWidth - 8), x, y + 4,
                    !usable ? 0xFFFFA080
                            : option.action().type() == CatalogInteraction.ActionType.TOGGLE_POWER
                            || option.action().type() == CatalogInteraction.ActionType.TOGGLE_ABILITY
                            ? 0xFF80D0FF : 0xFFA0E0A0);
        }

        if (options.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.ascendant.radial.empty"),
                    width / 2, height / 2 + 52, 0xFFFFC060);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
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
        return net.minecraft.client.resources.language.I18n.exists(key)
                ? net.minecraft.client.resources.language.I18n.get(key) : humanize(id.getPath());
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
