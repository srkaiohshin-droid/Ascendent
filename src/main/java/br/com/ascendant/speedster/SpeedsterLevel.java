package br.com.ascendant.speedster;

import net.minecraft.resources.ResourceLocation;

/**
 * Níveis de velocista, cada um com física, mecânicas e apresentação próprias.
 * O nível 0 representa o desligado (movimento vanilla sem resíduos).
 */
public enum SpeedsterLevel {
    NONE(0, "none", "message.ascendant.speedster.level.none", false),
    REFLEXES(1, "reflexes", "message.ascendant.speedster.level.reflexes", true),
    KINETIC(2, "kinetic", "message.ascendant.speedster.level.kinetic", true),
    SUPERSONIC(3, "supersonic", "message.ascendant.speedster.level.supersonic", true),
    ELECTRIC(4, "electric", "message.ascendant.speedster.level.electric", true),
    EXTREME(5, "extreme", "message.ascendant.speedster.level.extreme", true),
    TRANSCENDENTAL(6, "transcendental", "message.ascendant.speedster.level.transcendental", true);

    private final int id;
    private final String key;
    private final String translationKey;
    private final boolean hasEffects;

    SpeedsterLevel(int id, String key, String translationKey, boolean hasEffects) {
        this.id = id;
        this.key = key;
        this.translationKey = translationKey;
        this.hasEffects = hasEffects;
    }

    public int id() {
        return id;
    }

    public String key() {
        return key;
    }

    public String translationKey() {
        return translationKey;
    }

    public boolean hasEffects() {
        return hasEffects;
    }

    public static SpeedsterLevel byId(int id) {
        for (SpeedsterLevel level : values()) {
            if (level.id == id) {
                return level;
            }
        }
        if (id < 0) {
            return NONE;
        }
        // Legacy level range was 1-10; anything above TRANSCENDENTAL maps to the highest.
        return TRANSCENDENTAL;
    }

    public ResourceLocation asResourceLocation() {
        return new ResourceLocation("ascendant", "speedster_" + key);
    }
}
