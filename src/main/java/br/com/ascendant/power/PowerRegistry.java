package br.com.ascendant.power;

import br.com.ascendant.core.PowerCategory;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Global server-authoritative registry of Superpower definitions.
 */
public final class PowerRegistry {
    private static final Map<ResourceLocation, PowerDefinition> POWERS = new ConcurrentHashMap<>();

    private PowerRegistry() {
    }

    public static synchronized void register(PowerDefinition power) {
        Objects.requireNonNull(power, "power");
        POWERS.put(power.getId(), power);
    }

    public static PowerDefinition get(ResourceLocation id) {
        return POWERS.get(id);
    }

    public static boolean contains(ResourceLocation id) {
        return POWERS.containsKey(id);
    }

    public static Collection<PowerDefinition> getAll() {
        return Collections.unmodifiableCollection(POWERS.values());
    }

    public static List<PowerDefinition> getByCategory(PowerCategory category) {
        return POWERS.values().stream()
                .filter(p -> p.getCategory() == category)
                .collect(Collectors.toList());
    }

    public static List<PowerDefinition> getFunctionalPowers() {
        return POWERS.values().stream()
                .filter(PowerDefinition::isFunctional)
                .collect(Collectors.toList());
    }

    public static int size() {
        return POWERS.size();
    }

    public static int getFunctionalCount() {
        return (int) POWERS.values().stream().filter(PowerDefinition::isFunctional).count();
    }

    public static synchronized void clear() {
        POWERS.clear();
    }
}
