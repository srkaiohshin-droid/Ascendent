package br.com.ascendant.power.runtime;

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
                        if (power.getType() == br.com.ascendant.core.PowerType.PASSIVE || isToggled) {
                            comp.onPlayerTick(player);
                        }
                    }
                }
            }
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
