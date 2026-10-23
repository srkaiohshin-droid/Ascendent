package br.com.ascendant.animation;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server- and client-safe registry of animation profiles.
 * <p>
 * Profiles are registered by power/ability ID and contain only visual data.
 * No client classes are referenced here, so dedicated servers can load this
 * registry without crashing.
 */
public final class AnimationRegistry {
    private static final Map<ResourceLocation, AnimationProfile> PROFILES = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> REGISTERED_POWER_IDS = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> REGISTERED_ABILITY_IDS = ConcurrentHashMap.newKeySet();

    private AnimationRegistry() {
    }

    public static void register(ResourceLocation powerOrAbilityId, AnimationProfile profile) {
        Objects.requireNonNull(powerOrAbilityId, "powerOrAbilityId");
        Objects.requireNonNull(profile, "profile");
        AnimationProfile existing = PROFILES.putIfAbsent(powerOrAbilityId, profile);
        if (existing != null && !existing.equals(profile)) {
            throw new IllegalStateException(
                    "Animation profile already registered for " + powerOrAbilityId
                            + ". Existing: " + existing.getName() + ", new: " + profile.getName()
            );
        }
    }

    public static void registerPower(ResourceLocation powerId) {
        REGISTERED_POWER_IDS.add(Objects.requireNonNull(powerId, "powerId"));
    }

    public static void registerAbility(ResourceLocation abilityId) {
        REGISTERED_ABILITY_IDS.add(Objects.requireNonNull(abilityId, "abilityId"));
    }

    @Nullable
    public static AnimationProfile get(ResourceLocation id) {
        return PROFILES.get(id);
    }

    public static boolean hasProfile(ResourceLocation id) {
        return PROFILES.containsKey(id);
    }

    public static Collection<AnimationProfile> getAll() {
        return Collections.unmodifiableCollection(PROFILES.values());
    }

    public static int size() {
        return PROFILES.size();
    }

    public static int registeredPowerCount() {
        return REGISTERED_POWER_IDS.size();
    }

    public static int registeredAbilityCount() {
        return REGISTERED_ABILITY_IDS.size();
    }

    public static Set<ResourceLocation> getRegisteredPowerIds() {
        return Collections.unmodifiableSet(REGISTERED_POWER_IDS);
    }

    public static Set<ResourceLocation> getRegisteredAbilityIds() {
        return Collections.unmodifiableSet(REGISTERED_ABILITY_IDS);
    }

    public static synchronized void clear() {
        PROFILES.clear();
        REGISTERED_POWER_IDS.clear();
        REGISTERED_ABILITY_IDS.clear();
    }
}
