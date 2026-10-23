package br.com.ascendant.ui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class CatalogClientState {
    private static boolean initialized;
    private static Set<ResourceLocation> powers = Set.of();
    private static Set<ResourceLocation> abilities = Set.of();
    private static Set<ResourceLocation> toggledPowers = Set.of();
    private static Set<ResourceLocation> toggledAbilities = Set.of();
    private static Map<ResourceLocation, Long> cooldownExpirations = Map.of();
    private static ResourceLocation equippedSuit;

    private CatalogClientState() {
    }

    public static void beginRequest() {
        initialized = false;
    }

    public static void update(Set<ResourceLocation> availablePowers,
                              Set<ResourceLocation> availableAbilities,
                              Set<ResourceLocation> activePowers,
                              Set<ResourceLocation> activeAbilities,
                              Map<ResourceLocation, Integer> activeCooldowns,
                              ResourceLocation suitId) {
        powers = Set.copyOf(availablePowers);
        abilities = Set.copyOf(availableAbilities);
        toggledPowers = Set.copyOf(activePowers);
        toggledAbilities = Set.copyOf(activeAbilities);
        long now = currentGameTime();
        Map<ResourceLocation, Long> expirations = new HashMap<>(activeCooldowns.size());
        activeCooldowns.forEach((id, ticks) -> {
            if (ticks > 0) {
                expirations.put(id, now + ticks);
            }
        });
        cooldownExpirations = Map.copyOf(expirations);
        equippedSuit = suitId;
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean hasPower(ResourceLocation id) {
        return powers.contains(id);
    }

    public static boolean hasAbility(ResourceLocation id) {
        return abilities.contains(id);
    }

    public static boolean isPowerToggled(ResourceLocation id) {
        return toggledPowers.contains(id);
    }

    public static boolean isAbilityToggled(ResourceLocation id) {
        return toggledAbilities.contains(id);
    }

    public static int getCooldown(ResourceLocation id) {
        Long expiresAt = cooldownExpirations.get(id);
        if (expiresAt == null) {
            return 0;
        }
        long remaining = expiresAt - currentGameTime();
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, remaining));
    }

    public static ResourceLocation getEquippedSuit() {
        return equippedSuit;
    }

    private static long currentGameTime() {
        return Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
    }
}
