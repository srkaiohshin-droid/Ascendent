package br.com.ascendant.power;

import br.com.ascendant.core.AbilityCategory;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Global server-authoritative registry of Ability definitions.
 */
public final class AbilityRegistry {
    private static final Map<ResourceLocation, AbilityDefinition> ABILITIES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, List<AbilityDefinition>> BY_POWER = new ConcurrentHashMap<>();
    private static volatile List<AbilityDefinition> UNIVERSAL_ABILITIES = List.of();

    private AbilityRegistry() {
    }

    public static synchronized void register(AbilityDefinition ability) {
        Objects.requireNonNull(ability, "ability");
        AbilityDefinition previous = ABILITIES.put(ability.getId(), ability);
        if (previous != null) {
            removeFromPowerIndex(previous);
        }
        addToPowerIndex(ability);
    }

    public static synchronized AbilityDefinition unregister(ResourceLocation id) {
        AbilityDefinition removed = ABILITIES.remove(id);
        if (removed != null) {
            removeFromPowerIndex(removed);
        }
        return removed;
    }

    public static AbilityDefinition get(ResourceLocation id) {
        return ABILITIES.get(id);
    }

    public static boolean contains(ResourceLocation id) {
        return ABILITIES.containsKey(id);
    }

    public static Collection<AbilityDefinition> getAll() {
        return Collections.unmodifiableCollection(ABILITIES.values());
    }

    public static List<AbilityDefinition> getByPower(ResourceLocation powerId) {
        return powerId == null ? UNIVERSAL_ABILITIES : BY_POWER.getOrDefault(powerId, List.of());
    }

    public static List<AbilityDefinition> getByCategory(AbilityCategory category) {
        return ABILITIES.values().stream()
                .filter(a -> a.getCategory() == category)
                .collect(Collectors.toList());
    }

    public static List<AbilityDefinition> getFunctionalAbilities() {
        return ABILITIES.values().stream()
                .filter(AbilityDefinition::isFunctional)
                .collect(Collectors.toList());
    }

    public static int size() {
        return ABILITIES.size();
    }

    public static int getFunctionalCount() {
        return (int) ABILITIES.values().stream().filter(AbilityDefinition::isFunctional).count();
    }

    public static synchronized void clear() {
        ABILITIES.clear();
        BY_POWER.clear();
        UNIVERSAL_ABILITIES = List.of();
    }

    private static void addToPowerIndex(AbilityDefinition ability) {
        ResourceLocation powerId = ability.getPowerId();
        if (powerId == null) {
            List<AbilityDefinition> updated = new ArrayList<>(UNIVERSAL_ABILITIES);
            updated.add(ability);
            UNIVERSAL_ABILITIES = List.copyOf(updated);
            return;
        }
        BY_POWER.compute(powerId, (ignored, current) -> {
            List<AbilityDefinition> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
            updated.add(ability);
            return List.copyOf(updated);
        });
    }

    private static void removeFromPowerIndex(AbilityDefinition ability) {
        ResourceLocation powerId = ability.getPowerId();
        if (powerId == null) {
            UNIVERSAL_ABILITIES = UNIVERSAL_ABILITIES.stream()
                    .filter(registered -> !registered.getId().equals(ability.getId()))
                    .toList();
            return;
        }
        BY_POWER.computeIfPresent(powerId, (ignored, current) -> {
            List<AbilityDefinition> updated = current.stream()
                    .filter(registered -> !registered.getId().equals(ability.getId()))
                    .toList();
            return updated.isEmpty() ? null : updated;
        });
    }
}
