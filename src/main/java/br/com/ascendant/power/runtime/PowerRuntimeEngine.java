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
    private static final Map<UUID, Set<ResourceLocation>> TOGGLED_ABILITIES = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<ResourceLocation>> ACTIVE_PASSIVES = new ConcurrentHashMap<>();

    private PowerRuntimeEngine() {
    }

    public static boolean isOnCooldown(ServerPlayer player, ResourceLocation id) {
        Map<ResourceLocation, Long> playerCooldowns = COOLDOWNS.get(player.getUUID());
        if (playerCooldowns == null) {
            return false;
        }
        Long expireTick = playerCooldowns.get(id);
        if (expireTick == null) {
            return false;
        }
        return player.level().getGameTime() < expireTick;
    }

    public static long getCooldownRemainingTicks(ServerPlayer player, ResourceLocation id) {
        Map<ResourceLocation, Long> playerCooldowns = COOLDOWNS.get(player.getUUID());
        if (playerCooldowns == null) {
            return 0L;
        }
        Long expireTick = playerCooldowns.get(id);
        if (expireTick == null) {
            return 0L;
        }
        long diff = expireTick - player.level().getGameTime();
        return Math.max(0L, diff);
    }

    public static void setCooldown(ServerPlayer player, ResourceLocation id, int ticks) {
        if (ticks <= 0) {
            return;
        }
        COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                .put(id, player.level().getGameTime() + ticks);
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
        Set<ResourceLocation> toggles = TOGGLED_ABILITIES.get(player.getUUID());
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

        Set<ResourceLocation> toggles = ACTIVE_TOGGLES.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        boolean changed;
        if (enable) {
            changed = toggles.add(powerId);
        } else {
            changed = toggles.remove(powerId);
        }

        if (changed) {
            for (IPowerComponent comp : power.getComponents()) {
                comp.onToggle(player, enable);
            }
            if (toggles.isEmpty()) {
                ACTIVE_TOGGLES.remove(player.getUUID(), toggles);
            }
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

        Map<ResourceLocation, Long> playerCooldowns = COOLDOWNS.get(player.getUUID());
        if (playerCooldowns != null) {
            playerCooldowns.remove(powerId);
        }
        Set<ResourceLocation> toggles = ACTIVE_TOGGLES.get(player.getUUID());
        if (toggles != null) {
            toggles.remove(powerId);
            if (toggles.isEmpty()) {
                ACTIVE_TOGGLES.remove(player.getUUID(), toggles);
            }
        }

        PowerDefinition power = PowerRegistry.get(powerId);
        if (power != null) {
            for (IPowerComponent component : power.getComponents()) {
                component.onToggle(player, false);
            }
        }

        // Abilities derived from this power lose access too unless another source still grants them.
        for (AbilityDefinition ability : AbilityRegistry.getAll()) {
            if (powerId.equals(ability.getPowerId())) {
                deactivateAbilityIfInaccessible(player, ability.getId());
            }
        }
        return true;
    }

    /**
     * Manually activates a key-triggered ability after validating functionality, ownership and cooldown.
     */
    public static boolean activateAbility(ServerPlayer player, ResourceLocation abilityId) {
        AbilityDefinition ability = AbilityRegistry.get(abilityId);
        if (ability == null || !ability.isFunctional() || !hasExecutableActions(ability)) {
            return false;
        }

        AbilityTrigger trigger = ability.getTrigger();
        if (trigger != AbilityTrigger.KEY_PRESS && trigger != AbilityTrigger.SNEAK_KEY) {
            return false;
        }

        if (!hasAbilityAccess(player, ability) || isOnCooldown(player, abilityId)) {
            return false;
        }

        ComponentContext context = ComponentContext.of(player, abilityId);
        boolean anyExecuted = false;
        for (IPowerComponent action : ability.getActions()) {
            if (action.isFunctional() && action.execute(context)) {
                anyExecuted = true;
            }
        }

        if (anyExecuted && ability.getCooldownTicks() > 0) {
            setCooldown(player, abilityId, ability.getCooldownTicks());
        }
        return anyExecuted;
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
     * Explicitly toggles a TOGGLE-triggered ability. Enabling applies the component toggle state;
     * disabling removes it and clears managed transient state.
     */
    public static boolean toggleAbility(ServerPlayer player, ResourceLocation abilityId) {
        AbilityDefinition ability = AbilityRegistry.get(abilityId);
        if (ability == null || !ability.isFunctional() || !hasExecutableActions(ability)
                || ability.getTrigger() != AbilityTrigger.TOGGLE || !hasAbilityAccess(player, ability)) {
            return false;
        }

        Set<ResourceLocation> toggles = TOGGLED_ABILITIES.computeIfAbsent(
                player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        boolean enable = !toggles.contains(abilityId);
        if (enable) {
            toggles.add(abilityId);
        } else {
            toggles.remove(abilityId);
            if (toggles.isEmpty()) {
                TOGGLED_ABILITIES.remove(player.getUUID(), toggles);
            }
        }
        for (IPowerComponent action : ability.getActions()) {
            if (action.isFunctional()) {
                action.onToggle(player, enable);
            }
        }
        return true;
    }

    /**
     * Cleans a suit/ability's transient state once its last access source disappears.
     */
    public static boolean deactivateAbilityIfInaccessible(ServerPlayer player, ResourceLocation abilityId) {
        AbilityDefinition ability = AbilityRegistry.get(abilityId);
        if (ability == null || hasAbilityAccess(player, ability)) {
            return false;
        }

        clearCooldown(player, abilityId);
        boolean cleaned = false;
        Set<ResourceLocation> toggles = TOGGLED_ABILITIES.get(player.getUUID());
        if (toggles != null && toggles.remove(abilityId)) {
            cleaned = true;
            if (toggles.isEmpty()) {
                TOGGLED_ABILITIES.remove(player.getUUID(), toggles);
            }
        }
        Set<ResourceLocation> passives = ACTIVE_PASSIVES.get(player.getUUID());
        if (passives != null && passives.remove(abilityId)) {
            cleaned = true;
            if (passives.isEmpty()) {
                ACTIVE_PASSIVES.remove(player.getUUID(), passives);
            }
        }

        if (cleaned) {
            for (IPowerComponent action : ability.getActions()) {
                action.onToggle(player, false);
            }
        }
        return cleaned;
    }

    public static boolean hasPowerAccess(ServerPlayer player, ResourceLocation powerId) {
        if (powerId == null) {
            return true;
        }
        boolean hasBase = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .map(data -> data.hasPower(powerId))
                .orElse(false);
        if (hasBase) {
            return true;
        }
        return SuitManager.grantsPower(player, powerId);
    }

    /**
     * A suit may grant an ability directly, so parent power ownership alone is not sufficient.
     */
    public static boolean hasAbilityAccess(ServerPlayer player, AbilityDefinition ability) {
        if (ability.isUniversal()) {
            return true;
        }
        return hasPowerAccess(player, ability.getPowerId())
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
        player.getCapability(PlayerPowerDataProvider.CAPABILITY).ifPresent(data -> powers.addAll(data.getUnlockedPowers()));
        powers.addAll(SuitManager.getEquippedSuitGrantedPowers(player));
        return powers;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isServer() && event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            Set<ResourceLocation> accessiblePowers = getAllActiveAndEquippedPowers(player);
            for (ResourceLocation pId : accessiblePowers) {
                PowerDefinition power = PowerRegistry.get(pId);
                if (power != null && power.isFunctional()) {
                    boolean isToggled = isPowerToggled(player, pId);
                    for (IPowerComponent comp : power.getComponents()) {
                        // Passive components tick continuously; toggle components tick if enabled
                        if (power.getType() == PowerType.PASSIVE || isToggled) {
                            comp.onPlayerTick(player);
                        }
                    }
                }
            }

            dispatchPassiveAbilities(player);
            dispatchToggledAbilities(player);
            dispatchTickAbilities(player);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // Damage mitigation & defense for player
        if (event.getEntity() instanceof ServerPlayer player) {
            Set<ResourceLocation> accessiblePowers = getAllActiveAndEquippedPowers(player);
            for (ResourceLocation pId : accessiblePowers) {
                PowerDefinition power = PowerRegistry.get(pId);
                if (power != null && power.isFunctional()) {
                    for (IPowerComponent comp : power.getComponents()) {
                        comp.onHurt(player, event);
                    }
                }
            }
            applyPassiveAbilityDamageHooks(player, event);
            dispatchHurtAbilities(player, event);
        }
        // Attack buffs / offensive triggers
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            Set<ResourceLocation> accessiblePowers = getAllActiveAndEquippedPowers(attacker);
            for (ResourceLocation pId : accessiblePowers) {
                PowerDefinition power = PowerRegistry.get(pId);
                if (power != null && power.isFunctional()) {
                    for (IPowerComponent comp : power.getComponents()) {
                        comp.onAttack(attacker, event);
                    }
                }
            }
            dispatchAttackAbilities(attacker, event);
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Set<ResourceLocation> accessiblePowers = getAllActiveAndEquippedPowers(player);
            for (ResourceLocation pId : accessiblePowers) {
                PowerDefinition power = PowerRegistry.get(pId);
                if (power != null && power.isFunctional()) {
                    for (IPowerComponent comp : power.getComponents()) {
                        comp.onFall(player, event);
                    }
                }
            }
            for (AbilityDefinition ability : getActivePassiveAbilities(player)) {
                for (IPowerComponent action : ability.getActions()) {
                    action.onFall(player, event);
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
                player.getUUID(), k -> ConcurrentHashMap.newKeySet());
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
        if (active.isEmpty()) {
            ACTIVE_PASSIVES.remove(player.getUUID(), active);
        }
    }

    public static void dispatchToggledAbilities(ServerPlayer player) {
        Set<ResourceLocation> toggles = TOGGLED_ABILITIES.get(player.getUUID());
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
                action.onPlayerTick(player);
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
            if (ability.getCooldownTicks() <= 0 || isOnCooldown(player, ability.getId())) {
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
            if (isOnCooldown(player, ability.getId())) {
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
            if (isOnCooldown(attacker, ability.getId())) {
                continue;
            }
            for (IPowerComponent action : ability.getActions()) {
                action.onAttack(attacker, event);
            }
            executeAbilityActions(attacker, ability, event.getEntity());
        }
    }

    private static void applyPassiveAbilityDamageHooks(ServerPlayer player, LivingHurtEvent event) {
        for (AbilityDefinition ability : getActivePassiveAbilities(player)) {
            for (IPowerComponent action : ability.getActions()) {
                action.onHurt(player, event);
            }
        }
    }

    private static List<AbilityDefinition> getActivePassiveAbilities(ServerPlayer player) {
        Set<ResourceLocation> active = ACTIVE_PASSIVES.get(player.getUUID());
        if (active == null || active.isEmpty()) {
            return List.of();
        }
        List<AbilityDefinition> abilities = new ArrayList<>(active.size());
        for (ResourceLocation abilityId : active) {
            AbilityDefinition ability = AbilityRegistry.get(abilityId);
            if (ability != null) {
                abilities.add(ability);
            }
        }
        return abilities;
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

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        COOLDOWNS.remove(uuid);
        ACTIVE_TOGGLES.remove(uuid);
        TOGGLED_ABILITIES.remove(uuid);
        ACTIVE_PASSIVES.remove(uuid);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        UUID uuid = event.getEntity().getUUID();
        ACTIVE_TOGGLES.remove(uuid);
        TOGGLED_ABILITIES.remove(uuid);
        ACTIVE_PASSIVES.remove(uuid);
    }
}
