package br.com.ascendant.suit;

import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry of all available suit definitions in the Ascendant Armoured Workshop system.
 */
public final class SuitRegistry {
    private static final Map<ResourceLocation, SuitDefinition> SUITS = new ConcurrentHashMap<>();

    private SuitRegistry() {
    }

    public static synchronized void register(SuitDefinition suit) {
        Objects.requireNonNull(suit, "suit");
        SUITS.put(suit.getId(), suit);
    }

    public static SuitDefinition get(ResourceLocation id) {
        return SUITS.get(id);
    }

    public static boolean contains(ResourceLocation id) {
        return SUITS.containsKey(id);
    }

    public static Collection<SuitDefinition> getAll() {
        return Collections.unmodifiableCollection(SUITS.values());
    }

    public static List<SuitDefinition> getByUniverse(String universe) {
        return SUITS.values().stream()
                .filter(s -> s.getUniverse().equalsIgnoreCase(universe))
                .collect(Collectors.toList());
    }

    public static List<SuitDefinition> getByRarity(SuitRarity rarity) {
        return SUITS.values().stream()
                .filter(s -> s.getRarity() == rarity)
                .collect(Collectors.toList());
    }

    public static int size() {
        return SUITS.size();
    }

    public static synchronized void clear() {
        SUITS.clear();
    }
}
