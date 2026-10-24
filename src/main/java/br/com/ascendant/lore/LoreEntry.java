package br.com.ascendant.lore;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * Immutable archive entry. Translation keys are derived from the id, which
 * keeps manifests, validation and the catalog in sync automatically:
 * {@code lore.<namespace>.<path>.title|summary|body}.
 */
public record LoreEntry(ResourceLocation id, LoreCategory category, LoreUnlock unlock,
                        List<ResourceLocation> relatedPowers, List<ResourceLocation> relatedSuits,
                        int order) {

    public LoreEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(unlock, "unlock");
        relatedPowers = List.copyOf(relatedPowers);
        relatedSuits = List.copyOf(relatedSuits);
        if (order < 0) {
            throw new IllegalArgumentException("order cannot be negative");
        }
    }

    public String getTitleKey() {
        return key("title");
    }

    public String getSummaryKey() {
        return key("summary");
    }

    public String getBodyKey() {
        return key("body");
    }

    public boolean isUnlocked(LoreAccess access) {
        return unlock.isSatisfied(access);
    }

    private String key(String suffix) {
        return "lore." + id.getNamespace() + "." + id.getPath() + "." + suffix;
    }
}
