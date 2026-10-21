package br.com.ascendant.suit;

import net.minecraft.resources.ResourceLocation;

/**
 * Built-in superhero suit catalog entries inspired by the Armoured Workshop concept.
 */
public final class BuiltinSuitData {

    private BuiltinSuitData() {
    }

    public static void registerAll() {
        // 1. Classic Ascendant (Flagship Hero Suit)
        SuitRegistry.register(SuitDefinition.builder(new ResourceLocation("ascendant", "classic_ascendant"))
                .nameKey("suit.ascendant.classic_ascendant")
                .descriptionKey("suit.ascendant.classic_ascendant.desc")
                .universe("Ascendant Prime")
                .author("Ascendant Core")
                .rarity(SuitRarity.LEGENDARY)
                .addPower(new ResourceLocation("ascendant", "flight"))
                .addPower(new ResourceLocation("ascendant", "titan_strength"))
                .addPower(new ResourceLocation("ascendant", "kinetic_barrier"))
                .addAbility(new ResourceLocation("ascendant", "thunder_punch"))
                .addAbility(new ResourceLocation("ascendant", "kinetic_barrier_deploy"))
                .addAttributeModifier("armor", 8.0D)
                .modelInfo(SuitModelInfo.standard("ascendant:textures/models/suit/classic_ascendant.png", 0x1E40AF, 0xF59E0B))
                .build());

        // 2. Speedster Mk-I
        SuitRegistry.register(SuitDefinition.builder(new ResourceLocation("ascendant", "speedster_mk1"))
                .nameKey("suit.ascendant.speedster_mk1")
                .descriptionKey("suit.ascendant.speedster_mk1.desc")
                .universe("Kinetic Dynamics")
                .author("Dr. Harrison Wells")
                .rarity(SuitRarity.EPIC)
                .addPower(new ResourceLocation("ascendant", "kinetic_sprint"))
                .addPower(new ResourceLocation("ascendant", "mach_dash"))
                .addPower(new ResourceLocation("ascendant", "kinetic_dampener"))
                .addAbility(new ResourceLocation("ascendant", "sonic_thrust"))
                .addAttributeModifier("movement_speed", 0.08D)
                .modelInfo(SuitModelInfo.standard("ascendant:textures/models/suit/speedster_mk1.png", 0xDC2626, 0xFACC15))
                .build());

        // 3. Titan Exosuit
        SuitRegistry.register(SuitDefinition.builder(new ResourceLocation("ascendant", "titan_exosuit"))
                .nameKey("suit.ascendant.titan_exosuit")
                .descriptionKey("suit.ascendant.titan_exosuit.desc")
                .universe("Forge Tech")
                .author("Stark Laboratories")
                .rarity(SuitRarity.EPIC)
                .addPower(new ResourceLocation("ascendant", "diamond_skin"))
                .addPower(new ResourceLocation("ascendant", "seismic_slam"))
                .addPower(new ResourceLocation("ascendant", "colossal_leap"))
                .addAbility(new ResourceLocation("ascendant", "seismic_rupture"))
                .addAttributeModifier("armor", 12.0D)
                .addAttributeModifier("knockback_resistance", 0.6D)
                .modelInfo(SuitModelInfo.standard("ascendant:textures/models/suit/titan_exosuit.png", 0x374151, 0x10B981))
                .build());

        // 4. Atlantean Regalia
        SuitRegistry.register(SuitDefinition.builder(new ResourceLocation("ascendant", "atlantean_regalia"))
                .nameKey("suit.ascendant.atlantean_regalia")
                .descriptionKey("suit.ascendant.atlantean_regalia.desc")
                .universe("Abyssal Realm")
                .author("King of the Trench")
                .rarity(SuitRarity.RARE)
                .addPower(new ResourceLocation("ascendant", "aquatic_respiration"))
                .addPower(new ResourceLocation("ascendant", "tidal_wave"))
                .addPower(new ResourceLocation("ascendant", "water_dash"))
                .addAbility(new ResourceLocation("ascendant", "torrent_propel"))
                .addAttributeModifier("swim_speed", 1.2D)
                .modelInfo(SuitModelInfo.standard("ascendant:textures/models/suit/atlantean_regalia.png", 0x0D9488, 0xD97706))
                .build());

        // 5. Solar Aegis
        SuitRegistry.register(SuitDefinition.builder(new ResourceLocation("ascendant", "solar_aegis"))
                .nameKey("suit.ascendant.solar_aegis")
                .descriptionKey("suit.ascendant.solar_aegis.desc")
                .universe("Cosmic Vanguard")
                .author("Aethelgard Highguard")
                .rarity(SuitRarity.MYTHIC)
                .addPower(new ResourceLocation("ascendant", "solar_healing"))
                .addPower(new ResourceLocation("ascendant", "energy_blast"))
                .addPower(new ResourceLocation("ascendant", "supernova_blast"))
                .addAbility(new ResourceLocation("ascendant", "solar_rejuvenation"))
                .addAttributeModifier("max_health", 8.0D)
                .modelInfo(SuitModelInfo.standard("ascendant:textures/models/suit/solar_aegis.png", 0xF59E0B, 0xFFFFFF))
                .build());

        // 6. Shadow Infiltrator
        SuitRegistry.register(SuitDefinition.builder(new ResourceLocation("ascendant", "shadow_infiltrator"))
                .nameKey("suit.ascendant.shadow_infiltrator")
                .descriptionKey("suit.ascendant.shadow_infiltrator.desc")
                .universe("Stealth Ops")
                .author("The Syndicate")
                .rarity(SuitRarity.RARE)
                .addPower(new ResourceLocation("ascendant", "shadow_teleport"))
                .addPower(new ResourceLocation("ascendant", "stealth_strike"))
                .addPower(new ResourceLocation("ascendant", "night_vision_eyes"))
                .addAbility(new ResourceLocation("ascendant", "shadow_phase_step"))
                .addAttributeModifier("movement_speed", 0.05D)
                .modelInfo(SuitModelInfo.standard("ascendant:textures/models/suit/shadow_infiltrator.png", 0x111827, 0x6B7280))
                .build());
    }
}
