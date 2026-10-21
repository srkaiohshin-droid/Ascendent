package br.com.ascendant.core;

/**
 * Functional categories for abilities, as defined in Section 4 of the specification.
 */
public enum AbilityCategory {
    OFFENSIVE("ascendant.ability.offensive", "Técnicas ofensivas"),
    DEFENSIVE("ascendant.ability.defensive", "Técnicas defensivas"),
    MOBILITY("ascendant.ability.mobility", "Mobilidade"),
    AREA_CONTROL("ascendant.ability.area_control", "Controle de área"),
    SUPPORT("ascendant.ability.support", "Suporte e recuperação"),
    EXPLORATION("ascendant.ability.exploration", "Exploração"),
    PERCEPTION("ascendant.ability.perception", "Percepção"),
    ENVIRONMENTAL("ascendant.ability.environmental", "Interação com o ambiente"),
    COMBO("ascendant.ability.combo", "Combos e finalizações"),
    PASSIVE("ascendant.ability.passive", "Técnicas passivas"),
    TRANSFORMATION("ascendant.ability.transformation", "Transformações"),
    CONDITIONAL("ascendant.ability.conditional", "Habilidades condicionais"),
    COOPERATIVE("ascendant.ability.cooperative", "Técnicas cooperativas"),
    SPECIALIZATION("ascendant.ability.specialization", "Técnicas de especialização");

    private final String translationKey;
    private final String displayName;

    AbilityCategory(String translationKey, String displayName) {
        this.translationKey = translationKey;
        this.displayName = displayName;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getDisplayName() {
        return displayName;
    }
}
