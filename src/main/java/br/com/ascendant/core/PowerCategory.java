package br.com.ascendant.core;

/**
 * The 40 primary power categories as defined in the Ascendant Master specification.
 */
public enum PowerCategory {
    FLIGHT("ascendant.category.flight", "Voo e mobilidade aérea"),
    SUPERSPEED("ascendant.category.superspeed", "Supervelocidade e movimento cinético"),
    SUPERSTRENGTH("ascendant.category.superstrength", "Superforça e manipulação física"),
    RESISTANCE("ascendant.category.resistance", "Resistência, invulnerabilidade e regeneração"),
    TELEPORTATION("ascendant.category.teleportation", "Teletransporte, portais e deslocamento dimensional"),
    ENERGY("ascendant.category.energy", "Energia, lasers, rajadas e feixes"),
    FIRE("ascendant.category.fire", "Fogo, calor, plasma e combustão"),
    ICE("ascendant.category.ice", "Gelo, frio e criocinese"),
    ELECTRICITY("ascendant.category.electricity", "Eletricidade, magnetismo e campos eletromagnéticos"),
    WATER("ascendant.category.water", "Água, pressão, correntes e mobilidade aquática"),
    AIR("ascendant.category.air", "Ar, vento, vácuo e pressão atmosférica"),
    EARTH("ascendant.category.earth", "Terra, pedra, areia e minerais"),
    PLANTS("ascendant.category.plants", "Plantas, raízes, vinhas e esporos"),
    SOUND("ascendant.category.sound", "Som, vibração, ondas e ressonância"),
    LIGHT("ascendant.category.light", "Luz, invisibilidade e refração"),
    SHADOWS("ascendant.category.shadows", "Sombras, escuridão e ocultação"),
    TELEKINESIS("ascendant.category.telekinesis", "Telecinese e manipulação de objetos"),
    GRAVITY("ascendant.category.gravity", "Controle gravitacional e inércia"),
    SHIELDS("ascendant.category.shields", "Escudos, barreiras e campos protetores"),
    HEALING("ascendant.category.healing", "Cura, suporte e recuperação"),
    ENHANCED_SENSES("ascendant.category.enhanced_senses", "Sentidos aprimorados e percepção"),
    SPECIAL_VISION("ascendant.category.special_vision", "Visão térmica, visão noturna, visão telescópica"),
    CAMOUFLAGE("ascendant.category.camouflage", "Camuflagem, furtividade e disfarce"),
    METAMORPHOSIS("ascendant.category.metamorphosis", "Metamorfose e transformações"),
    CLONES("ascendant.category.clones", "Clones, duplicatas e ilusões"),
    SUMMONING("ascendant.category.summoning", "Invocação de criaturas e entidades"),
    TRAPS("ascendant.category.traps", "Armadilhas, minas e zonas de controle"),
    CROWD_CONTROL("ascendant.category.crowd_control", "Controle de multidões, empurrões e imobilização"),
    TECH("ascendant.category.tech", "Manipulação tecnológica e interfaces digitais"),
    COSMIC("ascendant.category.cosmic", "Habilidades cósmicas, dimensionais e energia avançada"),
    PSYCHIC("ascendant.category.psychic", "Poderes psíquicos, telepatia e percepção mental"),
    MELEE("ascendant.category.melee", "Poderes de combate corpo a corpo"),
    ENERGY_WEAPONS("ascendant.category.energy_weapons", "Poderes de projéteis e armas de energia"),
    REACTIVE_DEFENSE("ascendant.category.reactive_defense", "Poderes defensivos reativos"),
    ENVIRONMENTAL_PASSIVE("ascendant.category.environmental_passive", "Poderes passivos e adaptações ambientais"),
    EXPLORATION("ascendant.category.exploration", "Poderes de exploração e sobrevivência"),
    AQUATIC_SPECIALIZED("ascendant.category.aquatic_specialized", "Poderes aquáticos especializados"),
    BODY_TRANSFORMATION("ascendant.category.body_transformation", "Poderes de transformação corporal"),
    AREA_CONTROL("ascendant.category.area_control", "Poderes de controle territorial e construção"),
    HYBRID("ascendant.category.hybrid", "Poderes híbridos e combinações avançadas");

    private final String translationKey;
    private final String displayName;

    PowerCategory(String translationKey, String displayName) {
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
