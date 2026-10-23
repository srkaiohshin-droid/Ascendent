package br.com.ascendant.power.runtime;

import javax.annotation.Nullable;

import br.com.ascendant.core.AbilityTrigger;
import br.com.ascendant.core.PowerType;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.power.component.ComponentContext;
import br.com.ascendant.power.component.IPowerComponent;
import br.com.ascendant.powers.IPlayerPowerData;
import br.com.ascendant.powers.PlayerPowerDataProvider;
import br.com.ascendant.suit.SuitManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative engine handling runtime ticks, cooldown tracking,
 * active toggles, damage hooks, and ability activations.
 */
public final class PowerRuntimeEngine {
    private static final Map<UUID, Map<ResourceLocation, Long>> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<ResourceLocation>> ACTIVE_TOGGLES = new ConcurrentHashMap<>();

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

    /**
     * Activates a power (for ACTIVE type powers) without toggling.
     * For TOGGLE/SUSTAINED powers, use togglePower instead.
     */
    public static boolean activatePower(ServerPlayer player, ResourceLocation powerId) {
        if (!hasPowerAccess(player, powerId)) {
            return false;
        }
        PowerDefinition power = PowerRegistry.get(powerId);
        if (power == null) {
            return false;
        }

        // For active powers, we can trigger their components immediately
        if (power.getType() == PowerType.ACTIVE) {
            if (isOnCooldown(player, powerId)) {
                return false;
            }
            setCooldown(player, powerId, power.getCooldownTicks());
            
            // Execute all active components
            for (IPowerComponent comp : power.getComponents()) {
                if (comp.isFunctional()) {
                    // Create a component context for execution
                    ComponentContext context = ComponentContext.of(player, powerId);
                    comp.execute(context);
                }
            }
            return true;
        }
        
        return false;
    }

    public static boolean togglePower(ServerPlayer player, ResourceLocation powerId, boolean enable) {
        if (!hasPowerAccess(player, powerId)) {
            return false;
        }
        PowerDefinition power = PowerRegistry.get(powerId);
        if (power == null) {
            return false;
        }

        Set<ResourceLocation> toggles = ACTIVE_TOGGLES.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        if (enable) {
            toggles.add(powerId);
        } else {
            toggles.remove(powerId);
        }

        for (IPowerComponent comp : power.getComponents()) {
            comp.onToggle(player, enable);
        }
        return true;
    }

    public static boolean activateAbility(ServerPlayer player, ResourceLocation abilityId) {
        AbilityDefinition ability = AbilityRegistry.get(abilityId);
        if (ability == null) {
            return false;
        }

        // Abilities with automatic triggers must not be manually activated.
        // Only KEY_PRESS and SNEAK_KEY abilities can be triggered by player input.
        AbilityTrigger trigger = ability.getTrigger();
        if (trigger != AbilityTrigger.KEY_PRESS && trigger != AbilityTrigger.SNEAK_KEY) {
            return false;
        }

        // Validate power ownership if ability is tied to a power
        if (!ability.isUniversal()) {
            if (!hasPowerAccess(player, ability.getPowerId())) {
                return false;
            }
        }

        // Check cooldown
        if (isOnCooldown(player, abilityId)) {
            return false;
        }

        ComponentContext context = ComponentContext.of(player, abilityId);
        boolean anyExecuted = false;

        for (IPowerComponent action : ability.getActions()) {
            if (action.isFunctional()) {
                boolean result = action.execute(context);
                if (result) {
                    anyExecuted = true;
                }
            }
        }

        if (anyExecuted && ability.getCooldownTicks() > 0) {
            setCooldown(player, abilityId, ability.getCooldownTicks());
        }

        return anyExecuted;
    }

    /**
     * Dispatches abilities whose trigger matches the given game event.
     * Called by the server-authoritative event hooks so automatic abilities
     * (HURT_EVENT, ATTACK_HIT, TICK) run without player input.
     */
    public static void dispatchTriggeredAbilities(ServerPlayer player, AbilityTrigger trigger) {
        if (!player.level().isClientSide && player.isAlive()) {
            for (AbilityDefinition ability : AbilityRegistry.getAll()) {
                if (!ability.isFunctional()) continue;
                if (ability.getTrigger() != trigger) continue;
                if (!ability.isUniversal()) {
                    if (!hasPowerAccess(player, ability.getPowerId())) continue;
                }
                if (isOnCooldown(player, ability.getId())) continue;
                ComponentContext context = ComponentContext.of(player, ability.getId());
                boolean anyExecuted = false;
                for (IPowerComponent action : ability.getActions()) {
                    if (action.isFunctional()) {
                        if (action.execute(context)) {
                            anyExecuted = true;
                        }
                    }
                }
                if (anyExecuted && ability.getCooldownTicks() > 0) {
                    setCooldown(player, ability.getId(), ability.getCooldownTicks());
                }
            }
        }
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
        if (trigger != AbilityTrigger.KEY_PRESS && trigger != AbilityTrigger.SNEAK_KEY) {
            return "gui.ascendant.catalog.ability_trigger_reason";
        }
        if (!ability.isUniversal() && ability.getPowerId() != null) {
            if (!hasPowerAccess(player, ability.getPowerId())) {
                return "gui.ascendant.catalog.ownership_note";
            }
        }
        if (isOnCooldown(player, abilityId)) {
            long remaining = getCooldownRemainingTicks(player, abilityId);
            return remaining > 0 ? "gui.ascendant.catalog.unavailable" : null;
        }
        if (!ability.isFunctional() || ability.getActions().isEmpty()
                || ability.getActions().stream().noneMatch(IPowerComponent::isFunctional)) {
            return "gui.ascendant.catalog.template_reason";
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
            // Dispatch tick-triggered abilities (passive monitoring, auras, etc.)
            dispatchTriggeredAbilities(player, AbilityTrigger.TICK);
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
            // Dispatch reactive abilities triggered by taking damage
            dispatchTriggeredAbilities(player, AbilityTrigger.HURT_EVENT);
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
            // Dispatch offensive abilities triggered by landing a hit
            dispatchTriggeredAbilities(attacker, AbilityTrigger.ATTACK_HIT);
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
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        COOLDOWNS.remove(uuid);
        ACTIVE_TOGGLES.remove(uuid);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        UUID uuid = event.getEntity().getUUID();
        ACTIVE_TOGGLES.remove(uuid);
    }
}
