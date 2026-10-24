package br.com.ascendant.lore;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Globally registered lore archive. Content is static and shared by client and server. */
public final class LoreRegistry {
    private static final Map<ResourceLocation, LoreEntry> ENTRIES = new ConcurrentHashMap<>();

    private LoreRegistry() {
    }

    public static synchronized void register(LoreEntry entry) {
        Objects.requireNonNull(entry, "entry");
        ENTRIES.put(entry.id(), entry);
    }

    public static LoreEntry get(ResourceLocation id) {
        return ENTRIES.get(id);
    }

    public static boolean contains(ResourceLocation id) {
        return ENTRIES.containsKey(id);
    }

    public static Collection<LoreEntry> getAll() {
        return List.copyOf(ENTRIES.values());
    }

    public static List<LoreEntry> getAllSorted() {
        return ENTRIES.values().stream()
                .sorted(Comparator.comparingInt(LoreEntry::order).thenComparing(entry -> entry.id().toString()))
                .toList();
    }

    public static List<LoreEntry> getByCategory(LoreCategory category) {
        return ENTRIES.values().stream()
                .filter(entry -> entry.category() == category)
                .sorted(Comparator.comparingInt(LoreEntry::order))
                .toList();
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static synchronized void clear() {
        ENTRIES.clear();
    }
}
