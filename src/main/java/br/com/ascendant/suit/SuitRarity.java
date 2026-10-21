package br.com.ascendant.suit;

public enum SuitRarity {
    COMMON("ascendant.rarity.common", 0xFFFFFF),
    UNCOMMON("ascendant.rarity.uncommon", 0x55FF55),
    RARE("ascendant.rarity.rare", 0x55FFFF),
    EPIC("ascendant.rarity.epic", 0xAA00AA),
    LEGENDARY("ascendant.rarity.legendary", 0xFFAA00),
    MYTHIC("ascendant.rarity.mythic", 0xFF5555);

    private final String translationKey;
    private final int colorHex;

    SuitRarity(String translationKey, int colorHex) {
        this.translationKey = translationKey;
        this.colorHex = colorHex;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public int getColorHex() {
        return colorHex;
    }
}
