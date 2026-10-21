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

    private AbilityRegistry() {
    }

    public static synchronized void register(AbilityDefinition ability) {
        Objects.requireNonNull(ability, "ability");
        ABILITIES.put(ability.getId(), ability);
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
        return ABILITIES.values().stream()
                .filter(a -> Objects.equals(a.getPowerId(), powerId))
                .collect(Collectors.toList());
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
    }
}
