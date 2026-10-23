package br.com.ascendant.power.runtime;

import br.com.ascendant.core.AbilityTrigger;
import br.com.ascendant.core.PowerType;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.power.component.ComponentContext;
import br.com.ascendant.power.component.IPowerComponent;
import br.com.ascendant.powers.PlayerPowerDataProvider;
import br.com.ascendant.speedster.SpeedsterController;
import br.com.ascendant.suit.SuitManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative engine handling runtime ticks, cooldown tracking,
 * active toggles, damage hooks, and ability activations.
 */
public final class PowerRuntimeEngine {
    private static final Map<UUID, Map<ResourceLocation, Long>> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<ResourceLocation>> ACTIVE_TOGGLES = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<ResourceLocation>> ACTIVE_ABILITY_TOGGLES = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<ResourceLocation>> ACTIVE_PASSIVES = new ConcurrentHashMap<>();
    private static final Set<AbilityExecution> EXECUTING_ABILITIES = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, RuntimeSnapshot> PENDING_RESPAWN_CLEANUP = new ConcurrentHashMap<>();

    private PowerRuntimeEngine() {
    }

    public enum AbilityResult {
        ACTIVATED("message.ascendant.ability.activated", true),
        ENABLED("message.ascendant.ability.enabled", true),
        DISABLED("message.ascendant.ability.disabled", true),
        UNKNOWN("message.ascendant.ability.unknown", false),
        NOT_FUNCTIONAL("message.ascendant.ability.not_functional", false),
        WRONG_TRIGGER("message.ascendant.ability.wrong_trigger", false),
        NOT_AUTHORIZED("message.ascendant.ability.not_authorized", false),
        SNEAK_REQUIRED("message.ascendant.ability.sneak_required", false),
        COOLDOWN("message.ascendant.ability.cooldown", false),
        CONDITIONS_NOT_MET("message.ascendant.ability.conditions_not_met", false);

        private final String translationKey;
        private final boolean successful;

        AbilityResult(String translationKey, boolean successful) {
            this.translationKey = translationKey;
            this.successful = successful;
        }

        public String translationKey() {
            return translationKey;
        }

        public boolean successful() {
            return successful;
        }
    }

    private record AbilityExecution(UUID playerId, ResourceLocation abilityId) {
    }

    private record RuntimeSnapshot(Set<ResourceLocation> powers, Set<ResourceLocation> abilities,
                                   Set<ResourceLocation> passives) {
    }

    public static boolean isOnCooldown(ServerPlayer player, ResourceLocation id) {
        Map<ResourceLocation, Long> playerCooldowns = COOLDOWNS.get(player.getUUID());
        if (playerCooldowns == null) {
            return false;
        }
        Long expireTick = playerCooldowns.get(id);
        return expireTick != null && player.level().getGameTime() < expireTick;
    }

    public static long getCooldownRemainingTicks(ServerPlayer player, ResourceLocation id) {
        Map<ResourceLocation, Long> playerCooldowns = COOLDOWNS.get(player.getUUID());
        if (playerCooldowns == null) {
            return 0L;
        }
        Long expireTick = playerCooldowns.get(id);
        return expireTick == null ? 0L : Math.max(0L, expireTick - player.level().getGameTime());
    }

    public static void setCooldown(ServerPlayer player, ResourceLocation id, int ticks) {
        if (ticks > 0) {
            COOLDOWNS.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>())
                    .put(id, player.level().getGameTime() + ticks);
        }
    }

    public static void clearCooldown(ServerPlayer player, ResourceLocation id) {
        Map<ResourceLocation, Long> map = COOLDOWNS.get(player.getUUID());
        if (map != null) {
            map.remove(id);
        }
    }

    public static boolean isPowerToggled(ServerPlayer player, ResourceLocation powerId) {
        Set<ResourceLocation> toggles = ACTIVE_TOGGLES.get(player.getUUID());
        return toggles != null && toggles.contains(powerId);
    }

    public static boolean isAbilityToggled(ServerPlayer player, ResourceLocation abilityId) {
        Set<ResourceLocation> toggles = ACTIVE_ABILITY_TOGGLES.get(player.getUUID());
        return toggles != null && toggles.contains(abilityId);
    }

    public static boolean togglePower(ServerPlayer player, ResourceLocation powerId, boolean enable) {
        if (!hasPowerAccess(player, powerId)) {
            return false;
        }
        PowerDefinition power = PowerRegistry.get(powerId);
        if (power == null || !power.isFunctional()
                || (power.getType() != PowerType.TOGGLE && power.getType() != PowerType.SUSTAINED)) {
            return false;
        }
        if (enable && !isPowerToggled(player, powerId) && isOnCooldown(player, powerId)) {
            return false;
        }

        Set<ResourceLocation> toggles = ACTIVE_TOGGLES.computeIfAbsent(
                player.getUUID(), ignored -> ConcurrentHashMap.newKeySet());
        boolean changed = enable ? toggles.add(powerId) : toggles.remove(powerId);
        if (changed) {
            for (IPowerComponent component : power.getComponents()) {
                component.onToggle(player, enable);
            }
            if (enable && power.getCooldownTicks() > 0) {
                setCooldown(player, powerId, power.getCooldownTicks());
            }
            removeEmptySet(ACTIVE_TOGGLES, player.getUUID(), toggles);
        }
        return true;
    }

    public static boolean activatePower(ServerPlayer player, ResourceLocation powerId) {
        PowerDefinition power = PowerRegistry.get(powerId);
        if (power == null || !power.isFunctional() || power.getType() != PowerType.ACTIVE
                || !hasPowerAccess(player, powerId) || isOnCooldown(player, powerId)) {
            return false;
        }

        ComponentContext context = ComponentContext.of(player, powerId);
        boolean executed = false;
        for (IPowerComponent component : power.getComponents()) {
            if (component.isFunctional() && component.execute(context)) {
                executed = true;
            }
        }
        if (executed && power.getCooldownTicks() > 0) {
            setCooldown(player, powerId, power.getCooldownTicks());
        }
        return executed;
    }

    public static boolean activateAbility(ServerPlayer player, ResourceLocation abilityId) {
        return activateAbilityWithResult(player, abilityId).successful();
    }

    public static AbilityResult activateAbilityWithResult(ServerPlayer player, ResourceLocation abilityId) {
        AbilityDefinition ability = AbilityRegistry.get(abilityId);
        if (ability == null) {
            return AbilityResult.UNKNOWN;
        }
        if (!ability.isFunctional() || !hasExecutableActions(ability)) {
            return AbilityResult.NOT_FUNCTIONAL;
        }
        AbilityTrigger trigger = ability.getTrigger();
        if (trigger != AbilityTrigger.KEY_PRESS && trigger != AbilityTrigger.SNEAK_KEY) {
            return AbilityResult.WRONG_TRIGGER;
        }
        if (!hasAbilityAccess(player, ability)) {
            return AbilityResult.NOT_AUTHORIZED;
        }
        if (trigger == AbilityTrigger.SNEAK_KEY && !player.isCrouching()) {
            return AbilityResult.SNEAK_REQUIRED;
        }
        if (isOnCooldown(player, abilityId)) {
            return AbilityResult.COOLDOWN;
        }
        return executeAbility(player, ability) ? AbilityResult.ACTIVATED : AbilityResult.CONDITIONS_NOT_MET;
    }

    public static AbilityResult toggleAbility(ServerPlayer player, ResourceLocation abilityId, boolean enable) {
        AbilityDefinition ability = AbilityRegistry.get(abilityId);
        if (ability == null) {
            return AbilityResult.UNKNOWN;
        }
        if (!ability.isFunctional() || !hasExecutableActions(ability)) {
            return AbilityResult.NOT_FUNCTIONAL;
        }
        if (ability.getTrigger() != AbilityTrigger.TOGGLE) {
            return AbilityResult.WRONG_TRIGGER;
        }
        if (!hasAbilityAccess(player, ability)) {
            return AbilityResult.NOT_AUTHORIZED;
        }
        boolean alreadyEnabled = isAbilityToggled(player, abilityId);
        if (enable == alreadyEnabled) {
            return enable ? AbilityResult.ENABLED : AbilityResult.DISABLED;
        }
        if (enable && isOnCooldown(player, abilityId)) {
            return AbilityResult.COOLDOWN;
        }

        Set<ResourceLocation> toggles = ACTIVE_ABILITY_TOGGLES.computeIfAbsent(
                player.getUUID(), ignored -> ConcurrentHashMap.newKeySet());
        boolean changed = enable ? toggles.add(abilityId) : toggles.remove(abilityId);
        if (changed) {
            for (IPowerComponent action : ability.getActions()) {
                if (action.isFunctional()) {
                    action.onToggle(player, enable);
                }
            }
            if (enable && ability.getCooldownTicks() > 0) {
                setCooldown(player, abilityId, ability.getCooldownTicks());
            }
            removeEmptySet(ACTIVE_ABILITY_TOGGLES, player.getUUID(), toggles);
        }
        return enable ? AbilityResult.ENABLED : AbilityResult.DISABLED;
    }

    /**
     * Convenience overload used by the catalog and older callers. It returns whether the
     * requested state change was accepted, not the resulting toggle state.
     */
    public static boolean toggleAbility(ServerPlayer player, ResourceLocation abilityId) {
        boolean enable = !isAbilityToggled(player, abilityId);
        return toggleAbility(player, abilityId, enable).successful();
    }

    private static boolean executeAbility(ServerPlayer player, AbilityDefinition ability) {
        AbilityExecution execution = new AbilityExecution(player.getUUID(), ability.getId());
        if (!EXECUTING_ABILITIES.add(execution)) {
            return false;
        }
        try {
            ComponentContext context = ComponentContext.of(player, ability.getId());
            boolean executed = false;
            for (IPowerComponent action : ability.getActions()) {
                if (action.isFunctional() && action.execute(context)) {
                    executed = true;
                }
            }
            if (executed && ability.getCooldownTicks() > 0) {
                setCooldown(player, ability.getId(), ability.getCooldownTicks());
            }
            return executed;
        } finally {
            EXECUTING_ABILITIES.remove(execution);
        }
    }

    /**
     * Server-side activation entry point used by the network layer. Reports the real failure
     * reason to the requesting player instead of assuming success.
     */
    public static boolean activateAbilityWithFeedback(ServerPlayer player, ResourceLocation abilityId) {
        String failureReason = getActivationFailureReason(player, abilityId);
        if (failureReason != null) {
            player.displayClientMessage(Component.translatable(failureReason), true);
            return false;
        }
        boolean activated = activateAbility(player, abilityId);
        if (!activated) {
            player.displayClientMessage(
                    Component.translatable("gui.ascendant.catalog.activation_failed"), true);
        }
        return activated;
    }

    /**
     * Cleans an ability's transient state once its last access source disappears.
     * Component-owned effects and modifiers are lease-scoped, so this cannot remove an
     * effect supplied by an unrelated source.
     */
    public static boolean deactivateAbilityIfInaccessible(ServerPlayer player, ResourceLocation abilityId) {
        AbilityDefinition ability = AbilityRegistry.get(abilityId);
        if (ability != null && hasAbilityAccess(player, ability)) {
            return false;
        }
        Set<ResourceLocation> toggles = ACTIVE_ABILITY_TOGGLES.get(player.getUUID());
        if (toggles != null) {
            toggles.remove(abilityId);
            removeEmptySet(ACTIVE_ABILITY_TOGGLES, player.getUUID(), toggles);
        }
        Set<ResourceLocation> passives = ACTIVE_PASSIVES.get(player.getUUID());
        if (passives != null) {
            passives.remove(abilityId);
            removeEmptySet(ACTIVE_PASSIVES, player.getUUID(), passives);
        }
        clearCooldown(player, abilityId);
        if (ability != null) {
            for (IPowerComponent action : ability.getActions()) {
                action.onToggle(player, false);
            }
        }
        return true;
    }

    /**
     * Removes a power's transient runtime state after its final ownership source is lost.
     * This deliberately bypasses togglePower's access check, which must reject client activation.
     */
    public static boolean deactivatePowerIfInaccessible(ServerPlayer player, ResourceLocation powerId) {
        if (hasPowerAccess(player, powerId)) {
            return false;
        }
        if (SpeedsterController.REQUIRED_POWER.equals(powerId)) {
            SpeedsterController.deactivateIfPowerWasRevoked(player);
        }

        clearCooldown(player, powerId);
        Set<ResourceLocation> toggles = ACTIVE_TOGGLES.get(player.getUUID());
        if (toggles != null) {
            toggles.remove(powerId);
            removeEmptySet(ACTIVE_TOGGLES, player.getUUID(), toggles);
        }
        PowerDefinition power = PowerRegistry.get(powerId);
        if (power != null) {
            for (IPowerComponent component : power.getComponents()) {
                component.onToggle(player, false);
            }
        }
        for (AbilityDefinition ability : AbilityRegistry.getByPower(powerId)) {
            deactivateAbilityIfInaccessible(player, ability.getId());
        }
        return true;
    }

    public static boolean hasPowerAccess(ServerPlayer player, ResourceLocation powerId) {
        if (powerId == null) {
            return true;
        }
        boolean hasBase = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .map(data -> data.hasPower(powerId))
                .orElse(false);
        return hasBase || SuitManager.grantsPower(player, powerId);
    }

    /**
     * A suit may grant an ability directly, so parent power ownership alone is not sufficient.
     */
    public static boolean hasAbilityAccess(ServerPlayer player, AbilityDefinition ability) {
        if (ability == null) {
            return false;
        }
        return ability.isUniversal() || hasPowerAccess(player, ability.getPowerId())
                || SuitManager.grantsAbility(player, ability.getId());
    }

    /**
     * Returns a human-readable reason why an ability cannot be activated, or null when activation is allowed.
     * Used for client feedback so the player understands why an entry is blocked.
     */
    @Nullable
    public static String getActivationFailureReason(ServerPlayer player, ResourceLocation abilityId) {
        AbilityDefinition ability = AbilityRegistry.get(abilityId);
        if (ability == null) {
            return "gui.ascendant.catalog.unavailable_reason";
        }
        AbilityTrigger trigger = ability.getTrigger();
        if (trigger != AbilityTrigger.KEY_PRESS && trigger != AbilityTrigger.SNEAK_KEY
                && trigger != AbilityTrigger.TOGGLE) {
            return "gui.ascendant.catalog.ability_trigger_reason";
        }
        if (!ability.isFunctional() || !hasExecutableActions(ability)) {
            return "gui.ascendant.catalog.template_reason";
        }
        if (!hasAbilityAccess(player, ability)) {
            return "gui.ascendant.catalog.ownership_note";
        }
        if (isOnCooldown(player, abilityId)) {
            long remaining = getCooldownRemainingTicks(player, abilityId);
            return remaining > 0 ? "gui.ascendant.catalog.unavailable" : null;
        }
        return null;
    }

    public static Set<ResourceLocation> getAllActiveAndEquippedPowers(ServerPlayer player) {
        Set<ResourceLocation> powers = new HashSet<>();
        player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .ifPresent(data -> powers.addAll(data.getUnlockedPowers()));
        powers.addAll(SuitManager.getEquippedSuitGrantedPowers(player));
        return powers;
    }

    public static Set<AbilityDefinition> getAccessibleAbilities(ServerPlayer player) {
        Set<AbilityDefinition> abilities = new HashSet<>(AbilityRegistry.getByPower(null));
        for (ResourceLocation powerId : getAllActiveAndEquippedPowers(player)) {
            abilities.addAll(AbilityRegistry.getByPower(powerId));
        }
        for (ResourceLocation abilityId : SuitManager.getEquippedSuitGrantedAbilities(player)) {
            AbilityDefinition ability = AbilityRegistry.get(abilityId);
            if (ability != null) {
                abilities.add(ability);
            }
        }
        abilities.removeIf(ability -> !ability.isFunctional() || !hasAbilityAccess(player, ability));
        return abilities;
    }

    /**
     * Dispatches all automatic ability lifecycles. Public because the server tick and tests
     * must exercise the same code path.
     */
    public static void tickAbilities(ServerPlayer player) {
        dispatchPassiveAbilities(player);
        dispatchToggledAbilities(player);
        dispatchTickAbilities(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!event.side.isServer() || event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        for (ResourceLocation powerId : getAllActiveAndEquippedPowers(player)) {
            PowerDefinition power = PowerRegistry.get(powerId);
            if (power != null && power.isFunctional()) {
                boolean isToggled = isPowerToggled(player, powerId);
                for (IPowerComponent component : power.getComponents()) {
                    if (power.getType() == PowerType.PASSIVE || isToggled) {
                        component.onPlayerTick(player);
                    }
                }
            }
        }
        tickAbilities(player);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (ResourceLocation powerId : getAllActiveAndEquippedPowers(player)) {
                PowerDefinition power = PowerRegistry.get(powerId);
                if (power != null && power.isFunctional()) {
                    for (IPowerComponent component : power.getComponents()) {
                        component.onHurt(player, event);
                    }
                }
            }
            dispatchAlwaysOnDamageHooks(player, event, false);
            dispatchHurtAbilities(player, event);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            for (ResourceLocation powerId : getAllActiveAndEquippedPowers(attacker)) {
                PowerDefinition power = PowerRegistry.get(powerId);
                if (power != null && power.isFunctional()) {
                    for (IPowerComponent component : power.getComponents()) {
                        component.onAttack(attacker, event);
                    }
                }
            }
            dispatchAlwaysOnDamageHooks(attacker, event, true);
            dispatchAttackAbilities(attacker, event);
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (ResourceLocation powerId : getAllActiveAndEquippedPowers(player)) {
                PowerDefinition power = PowerRegistry.get(powerId);
                if (power != null && power.isFunctional()) {
                    for (IPowerComponent component : power.getComponents()) {
                        component.onFall(player, event);
                    }
                }
            }
            for (AbilityDefinition ability : getAccessibleAbilities(player)) {
                if (isOngoingAbility(player, ability) || ability.getTrigger() == AbilityTrigger.HURT_EVENT) {
                    for (IPowerComponent action : ability.getActions()) {
                        if (action.isFunctional()) {
                            action.onFall(player, event);
                        }
                    }
                }
            }
        }
    }

    /**
     * PASSIVE abilities are applied once, maintained idempotently, and cleaned when access ends.
     * They must never be executed as a one-shot action every tick.
     */
    public static void dispatchPassiveAbilities(ServerPlayer player) {
        Set<ResourceLocation> active = ACTIVE_PASSIVES.computeIfAbsent(
                player.getUUID(), ignored -> ConcurrentHashMap.newKeySet());
        Set<ResourceLocation> accessible = new HashSet<>();
        for (AbilityDefinition ability : AbilityRegistry.getAll()) {
            if (ability.getTrigger() != AbilityTrigger.PASSIVE || !ability.isFunctional()
                    || !hasExecutableActions(ability) || !hasAbilityAccess(player, ability)) {
                continue;
            }
            accessible.add(ability.getId());
            if (active.add(ability.getId())) {
                ComponentContext context = ComponentContext.of(player, ability.getId());
                for (IPowerComponent action : ability.getActions()) {
                    if (action.isFunctional()) {
                        action.execute(context);
                    }
                }
            }
            for (IPowerComponent action : ability.getActions()) {
                action.onPlayerTick(player);
            }
        }

        for (ResourceLocation abilityId : new HashSet<>(active)) {
            if (accessible.contains(abilityId)) {
                continue;
            }
            active.remove(abilityId);
            AbilityDefinition ability = AbilityRegistry.get(abilityId);
            if (ability != null) {
                for (IPowerComponent action : ability.getActions()) {
                    action.onToggle(player, false);
                }
            }
        }
        removeEmptySet(ACTIVE_PASSIVES, player.getUUID(), active);
    }

    public static void dispatchToggledAbilities(ServerPlayer player) {
        Set<ResourceLocation> toggles = ACTIVE_ABILITY_TOGGLES.get(player.getUUID());
        if (toggles == null) {
            return;
        }
        for (ResourceLocation abilityId : new HashSet<>(toggles)) {
            AbilityDefinition ability = AbilityRegistry.get(abilityId);
            if (ability == null || !ability.isFunctional() || !hasAbilityAccess(player, ability)) {
                deactivateAbilityIfInaccessible(player, abilityId);
                continue;
            }
            for (IPowerComponent action : ability.getActions()) {
                if (action.isFunctional()) {
                    action.onPlayerTick(player);
                }
            }
        }
    }

    /**
     * TICK abilities receive periodic maintenance through onPlayerTick. One-shot execution is
     * throttled by the ability cooldown so it cannot run indiscriminately every tick.
     */
    public static void dispatchTickAbilities(ServerPlayer player) {
        for (AbilityDefinition ability : AbilityRegistry.getAll()) {
            if (ability.getTrigger() != AbilityTrigger.TICK || !ability.isFunctional()
                    || !hasExecutableActions(ability) || !hasAbilityAccess(player, ability)) {
                continue;
            }
            for (IPowerComponent action : ability.getActions()) {
                action.onPlayerTick(player);
            }
            if (ability.getCooldownTicks() > 0 && isOnCooldown(player, ability.getId())) {
                continue;
            }
            executeAbilityActions(player, ability, null);
        }
    }

    public static void dispatchHurtAbilities(ServerPlayer player, LivingHurtEvent event) {
        Entity attacker = event.getSource().getEntity();
        for (AbilityDefinition ability : AbilityRegistry.getAll()) {
            if (ability.getTrigger() != AbilityTrigger.HURT_EVENT || !ability.isFunctional()
                    || !hasExecutableActions(ability) || !hasAbilityAccess(player, ability)) {
                continue;
            }
            if (isOnCooldown(player, ability.getId()) || !meetsHealthThreshold(player, event, ability)) {
                continue;
            }
            for (IPowerComponent action : ability.getActions()) {
                action.onHurt(player, event);
            }
            executeAbilityActions(player, ability, attacker);
        }
    }

    public static void dispatchAttackAbilities(ServerPlayer attacker, LivingHurtEvent event) {
        for (AbilityDefinition ability : AbilityRegistry.getAll()) {
            if (ability.getTrigger() != AbilityTrigger.ATTACK_HIT || !ability.isFunctional()
                    || !hasExecutableActions(ability) || !hasAbilityAccess(attacker, ability)) {
                continue;
            }
            if (isOnCooldown(attacker, ability.getId()) || !meetsHealthThreshold(attacker, event, ability)) {
                continue;
            }
            for (IPowerComponent action : ability.getActions()) {
                action.onAttack(attacker, event);
            }
            executeAbilityActions(attacker, ability, event.getEntity());
        }
    }

    private static void dispatchAlwaysOnDamageHooks(ServerPlayer player, LivingHurtEvent event,
                                                    boolean attacker) {
        for (AbilityDefinition ability : getAccessibleAbilities(player)) {
            if (!isOngoingAbility(player, ability)) {
                continue;
            }
            for (IPowerComponent action : ability.getActions()) {
                if (!action.isFunctional()) {
                    continue;
                }
                if (attacker) {
                    action.onAttack(player, event);
                } else {
                    action.onHurt(player, event);
                }
            }
        }
    }

    private static boolean isOngoingAbility(ServerPlayer player, AbilityDefinition ability) {
        return ability.getTrigger() == AbilityTrigger.PASSIVE || ability.getTrigger() == AbilityTrigger.TICK
                || ability.getTrigger() == AbilityTrigger.TOGGLE && isAbilityToggled(player, ability.getId());
    }

    private static boolean meetsHealthThreshold(ServerPlayer player, LivingHurtEvent event,
                                                AbilityDefinition ability) {
        float threshold = ability.getHealthThreshold();
        if (threshold >= 1.0F) {
            return true;
        }
        double maxHealth = Math.max(1.0D, player.getMaxHealth());
        double projectedHealth = Math.max(0.0D, player.getHealth() - Math.max(0.0F, event.getAmount()));
        return projectedHealth / maxHealth <= threshold;
    }

    private static boolean executeAbilityActions(ServerPlayer player, AbilityDefinition ability,
                                                 @Nullable Entity target) {
        ComponentContext context = target == null
                ? ComponentContext.of(player, ability.getId())
                : ComponentContext.of(player, ability.getId(), target);
        boolean executed = false;
        for (IPowerComponent action : ability.getActions()) {
            if (action.isFunctional() && action.execute(context)) {
                executed = true;
            }
        }
        if (executed && ability.getCooldownTicks() > 0) {
            setCooldown(player, ability.getId(), ability.getCooldownTicks());
        }
        return executed;
    }

    private static boolean hasExecutableActions(AbilityDefinition ability) {
        return ability.getActions().stream().anyMatch(IPowerComponent::isFunctional);
    }

    public static void clearActiveToggles(ServerPlayer player) {
        RuntimeSnapshot snapshot = takeRuntimeState(player);
        cleanupRuntimeState(player, snapshot);
    }

    private static RuntimeSnapshot takeRuntimeState(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Set<ResourceLocation> powers = ACTIVE_TOGGLES.remove(uuid);
        Set<ResourceLocation> abilities = ACTIVE_ABILITY_TOGGLES.remove(uuid);
        Set<ResourceLocation> passives = ACTIVE_PASSIVES.remove(uuid);
        return new RuntimeSnapshot(
                powers == null ? Set.of() : Set.copyOf(powers),
                abilities == null ? Set.of() : Set.copyOf(abilities),
                passives == null ? Set.of() : Set.copyOf(passives));
    }

    private static void cleanupRuntimeState(ServerPlayer player, RuntimeSnapshot snapshot) {
        for (ResourceLocation powerId : snapshot.powers()) {
            PowerDefinition power = PowerRegistry.get(powerId);
            if (power != null) {
                power.getComponents().forEach(component -> component.onToggle(player, false));
            }
        }
        for (ResourceLocation abilityId : snapshot.abilities()) {
            AbilityDefinition ability = AbilityRegistry.get(abilityId);
            if (ability != null) {
                ability.getActions().forEach(action -> action.onToggle(player, false));
            }
        }
        for (ResourceLocation abilityId : snapshot.passives()) {
            AbilityDefinition ability = AbilityRegistry.get(abilityId);
            if (ability != null) {
                ability.getActions().forEach(action -> action.onToggle(player, false));
            }
        }
    }

    private static void cleanupRegisteredComponents(ServerPlayer player) {
        for (PowerDefinition power : PowerRegistry.getAll()) {
            power.getComponents().forEach(component -> component.onToggle(player, false));
        }
        for (AbilityDefinition ability : AbilityRegistry.getAll()) {
            ability.getActions().forEach(action -> action.onToggle(player, false));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RuntimeSnapshot snapshot = takeRuntimeState(player);
            cleanupRuntimeState(player, snapshot);
            cleanupRegisteredComponents(player);
            COOLDOWNS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original) {
            PENDING_RESPAWN_CLEANUP.put(original.getUUID(), takeRuntimeState(original));
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RuntimeSnapshot pending = PENDING_RESPAWN_CLEANUP.remove(player.getUUID());
            if (pending != null) {
                cleanupRuntimeState(player, pending);
            }
            RuntimeSnapshot snapshot = takeRuntimeState(player);
            cleanupRuntimeState(player, snapshot);
            cleanupRegisteredComponents(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RuntimeSnapshot snapshot = takeRuntimeState(player);
            cleanupRuntimeState(player, snapshot);
            cleanupRegisteredComponents(player);
        }
    }

    private static void removeEmptySet(Map<UUID, Set<ResourceLocation>> states, UUID uuid,
                                       Set<ResourceLocation> values) {
        if (values.isEmpty()) {
            states.remove(uuid, values);
        }
    }
}
