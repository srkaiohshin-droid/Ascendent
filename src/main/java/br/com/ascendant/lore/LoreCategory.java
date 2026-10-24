package br.com.ascendant.lore;

import java.util.Locale;

/** Narrative grouping used by the lore archive tab and its filters. */
public enum LoreCategory {
    ORIGIN("Origins"),
    FACTION("Factions"),
    THREAT("Threats"),
    ARCHIVE("Archives"),
    DOSSIER("Dossiers");

    private final String displayName;

    LoreCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTranslationKey() {
        return "lore.ascendant.category." + name().toLowerCase(Locale.ROOT);
    }
}
