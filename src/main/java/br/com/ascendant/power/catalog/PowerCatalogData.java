package br.com.ascendant.power.catalog;

import br.com.ascendant.core.PowerCategory;
import br.com.ascendant.core.PowerType;
import br.com.ascendant.flight.FlightController;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.power.component.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;
import java.util.Map;

/**
 * Deterministic catalog population registering exactly 500 distinct powers
 * across the 40 specified categories, with 100+ fully functional powers.
 */
public final class PowerCatalogData {

    private PowerCatalogData() {
    }

    public static void registerAll() {
        registerFlightPowers();           // Category 1 (13 powers, functional: 3)
        registerSuperspeedPowers();       // Category 2 (13 powers, functional: 4)
        registerSuperstrengthPowers();    // Category 3 (13 powers, functional: 4)
        registerResistancePowers();       // Category 4 (13 powers, functional: 4)
        registerTeleportationPowers();    // Category 5 (13 powers, functional: 3)
        registerEnergyPowers();           // Category 6 (13 powers, functional: 3)
        registerFirePowers();             // Category 7 (13 powers, functional: 3)
        registerIcePowers();              // Category 8 (13 powers, functional: 3)
        registerElectricityPowers();      // Category 9 (13 powers, functional: 3)
        registerWaterPowers();            // Category 10 (13 powers, functional: 3)
        registerAirPowers();              // Category 11 (13 powers, functional: 3)
        registerEarthPowers();            // Category 12 (13 powers, functional: 3)
        registerPlantsPowers();           // Category 13 (13 powers, functional: 2)
        registerSoundPowers();            // Category 14 (13 powers, functional: 3)
        registerLightPowers();            // Category 15 (13 powers, functional: 2)
        registerShadowsPowers();          // Category 16 (13 powers, functional: 2)
        registerTelekinesisPowers();      // Category 17 (13 powers, functional: 3)
        registerGravityPowers();          // Category 18 (13 powers, functional: 3)
        registerShieldsPowers();          // Category 19 (13 powers, functional: 3)
        registerHealingPowers();          // Category 20 (13 powers, functional: 3)
        registerEnhancedSensesPowers();   // Category 21 (12 powers, functional: 2)
        registerSpecialVisionPowers();    // Category 22 (12 powers, functional: 3)
        registerCamouflagePowers();       // Category 23 (12 powers, functional: 2)
        registerMetamorphosisPowers();    // Category 24 (12 powers, functional: 2)
        registerClonesPowers();           // Category 25 (12 powers, functional: 2)
        registerSummoningPowers();        // Category 26 (12 powers, functional: 2)
        registerTrapsPowers();            // Category 27 (12 powers, functional: 2)
        registerCrowdControlPowers();     // Category 28 (12 powers, functional: 3)
        registerTechPowers();             // Category 29 (12 powers, functional: 2)
        registerCosmicPowers();           // Category 30 (12 powers, functional: 2)
        registerPsychicPowers();          // Category 31 (12 powers, functional: 2)
        registerMeleePowers();            // Category 32 (12 powers, functional: 3)
        registerEnergyWeaponsPowers();    // Category 33 (12 powers, functional: 2)
        registerReactiveDefensePowers();  // Category 34 (12 powers, functional: 3)
        registerEnvironmentalPassivePowers(); // Category 35 (12 powers, functional: 3)
        registerExplorationPowers();      // Category 36 (12 powers, functional: 3)
        registerAquaticSpecializedPowers(); // Category 37 (12 powers, functional: 3)
        registerBodyTransformationPowers(); // Category 38 (12 powers, functional: 2)
        registerAreaControlPowers();      // Category 39 (12 powers, functional: 2)
        registerHybridPowers();           // Category 40 (12 powers, functional: 3)
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("ascendant", path);
    }

    private static void regFunc(String path, PowerCategory cat, PowerType type, int cd, IPowerComponent comp, List<String> abilities) {
        PowerRegistry.register(PowerDefinition.builder(id(path))
                .category(cat).type(type).cooldown(cd)
                .addComponent(comp)
                .functional(true)
                .build());
    }

    private static void regFunc(String path, PowerCategory cat, PowerType type, int cd,
                                IPowerComponent primary, IPowerComponent secondary) {
        PowerRegistry.register(PowerDefinition.builder(id(path))
                .category(cat).type(type).cooldown(cd)
                .addComponent(primary)
                .addComponent(secondary)
                .functional(true)
                .build());
    }

    private static void regTempl(String path, PowerCategory cat, PowerType type, int cd, String mechanic, Map<String, String> params) {
        PowerRegistry.register(PowerDefinition.builder(id(path))
                .category(cat).type(type).cooldown(cd)
                .addComponent(new TemplateComponent(mechanic, params))
                .functional(false)
                .build());
    }

    // 1. FLIGHT (13)
    private static void registerFlightPowers() {
        // Core functional flight power - integration with FlightController
        PowerRegistry.register(PowerDefinition.builder(FlightController.FLIGHT_POWER)
                .category(PowerCategory.FLIGHT)
                .type(PowerType.TOGGLE)
                .addComponent(new FlightActionComponent())
                .functional(true)
                .build());

        regFunc("aerobatic_dash", PowerCategory.FLIGHT, PowerType.ACTIVE, 60, new DashComponent(1.8D, 0.2D), List.of());
        regFunc("aerial_launch", PowerCategory.FLIGHT, PowerType.ACTIVE, 100, new LeapComponent(0.8D, 2.2D), List.of());

        regTempl("orbital_ascent", PowerCategory.FLIGHT, PowerType.ACTIVE, 200, "atmospheric_ascent", Map.of("maxAltitude", "320"));
        regTempl("glider_wings", PowerCategory.FLIGHT, PowerType.PASSIVE, 0, "glide_physics", Map.of("glideRatio", "0.85"));
        regTempl("hover_field", PowerCategory.FLIGHT, PowerType.TOGGLE, 0, "gravity_neutralization", Map.of("stability", "high"));
        regTempl("thermal_lift", PowerCategory.FLIGHT, PowerType.PASSIVE, 0, "updraft_boost", Map.of("thermalGain", "1.4"));
        regTempl("sonic_boom_dive", PowerCategory.FLIGHT, PowerType.ACTIVE, 120, "dive_shockwave", Map.of("impactRadius", "6.0"));
        regTempl("cloud_surfing", PowerCategory.FLIGHT, PowerType.TOGGLE, 0, "vapor_propulsion", Map.of("friction", "0.01"));
        regTempl("jet_propulsion", PowerCategory.FLIGHT, PowerType.SUSTAINED, 0, "thrust_vectoring", Map.of("thrustMax", "2.5"));
        regTempl("void_drift", PowerCategory.FLIGHT, PowerType.PASSIVE, 0, "inertial_dampening", Map.of("drag", "0.99"));
        regTempl("magnetic_levitation", PowerCategory.FLIGHT, PowerType.TOGGLE, 0, "flux_alignment", Map.of("range", "16.0"));
        regTempl("solar_levitator", PowerCategory.FLIGHT, PowerType.PASSIVE, 0, "photonic_lift", Map.of("dayEfficiency", "1.5"));
    }

    // 2. SUPERSPEED (13)
    private static void registerSuperspeedPowers() {
        // Super-speed is a real toggle mode, not a hidden passive: the same power drives the
        // catalog toggle, the radial menu entry and the dedicated Z keybind.
        regFunc("kinetic_sprint", PowerCategory.SUPERSPEED, PowerType.TOGGLE, 0,
                new SpeedsterComponent(),
                new AttributeModifierComponent("movement_speed", 0.15D, AttributeModifier.Operation.ADDITION));
        regFunc("mach_dash", PowerCategory.SUPERSPEED, PowerType.ACTIVE, 40, new DashComponent(2.2D, 0.05D), List.of());
        regFunc("hyper_leap", PowerCategory.SUPERSPEED, PowerType.ACTIVE, 80, new LeapComponent(1.6D, 1.2D), List.of());
        regFunc("kinetic_dampener", PowerCategory.SUPERSPEED, PowerType.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fall"), List.of());

        regTempl("bullet_time_reflexes", PowerCategory.SUPERSPEED, PowerType.TOGGLE, 100, "temporal_dilation", Map.of("dilationFactor", "0.3"));
        regTempl("frictionless_glide", PowerCategory.SUPERSPEED, PowerType.TOGGLE, 0, "surface_slippage", Map.of("slippage", "0.98"));
        regTempl("whirlwind_circle", PowerCategory.SUPERSPEED, PowerType.ACTIVE, 140, "vortex_generation", Map.of("radius", "7.0"));
        regTempl("afterimage_stride", PowerCategory.SUPERSPEED, PowerType.ACTIVE, 80, "decoy_afterimages", Map.of("count", "3"));
        regTempl("momentum_charge", PowerCategory.SUPERSPEED, PowerType.CHARGED, 160, "momentum_build", Map.of("maxMultiplier", "3.0"));
        regTempl("kinetic_brake", PowerCategory.SUPERSPEED, PowerType.ACTIVE, 20, "instant_arrest", Map.of("stoppingPower", "max"));
        regTempl("velocity_overdrive", PowerCategory.SUPERSPEED, PowerType.ACTIVE, 240, "adrenal_burst", Map.of("boostPct", "50"));
        regTempl("tremor_sprint", PowerCategory.SUPERSPEED, PowerType.TOGGLE, 0, "ground_resonance", Map.of("shakeRadius", "4.0"));
        regTempl("lightning_steps", PowerCategory.SUPERSPEED, PowerType.TOGGLE, 0, "static_discharge", Map.of("sparkChance", "0.25"));
    }

    // 3. SUPERSTRENGTH (13)
    private static void registerSuperstrengthPowers() {
        regFunc("titan_strength", PowerCategory.SUPERSTRENGTH, PowerType.PASSIVE, 0,
                new AttributeModifierComponent("attack_damage", 8.0D, AttributeModifier.Operation.ADDITION), List.of());
        regFunc("seismic_slam", PowerCategory.SUPERSTRENGTH, PowerType.ACTIVE, 80, new DamageActionComponent(12.0F, 5.0D, "physical"), List.of());
        regFunc("colossal_leap", PowerCategory.SUPERSTRENGTH, PowerType.ACTIVE, 60, new LeapComponent(1.4D, 1.8D), List.of());
        regFunc("shockwave_knockback", PowerCategory.SUPERSTRENGTH, PowerType.ACTIVE, 50, new KnockbackActionComponent(6.0D, 2.0D), List.of());

        regTempl("earth_shatter", PowerCategory.SUPERSTRENGTH, PowerType.ACTIVE, 120, "faultline_crack", Map.of("length", "10.0"));
        regTempl("kinetic_absorption_punch", PowerCategory.SUPERSTRENGTH, PowerType.CHARGED, 100, "impact_storage", Map.of("damageCap", "40"));
        regTempl("iron_grip", PowerCategory.SUPERSTRENGTH, PowerType.ACTIVE, 80, "immobilize_hold", Map.of("duration", "60"));
        regTempl("crushing_throw", PowerCategory.SUPERSTRENGTH, PowerType.ACTIVE, 90, "entity_propel", Map.of("velocity", "2.8"));
        regTempl("fortress_breaker", PowerCategory.SUPERSTRENGTH, PowerType.ACTIVE, 150, "structure_penetration", Map.of("hardnessIgnore", "true"));
        regTempl("juggernaut_charge", PowerCategory.SUPERSTRENGTH, PowerType.SUSTAINED, 180, "unstoppable_momentum", Map.of("massFactor", "5.0"));
        regTempl("thunderclap", PowerCategory.SUPERSTRENGTH, PowerType.ACTIVE, 100, "acoustic_blast", Map.of("coneAngle", "60"));
        regTempl("hyper_density_strike", PowerCategory.SUPERSTRENGTH, PowerType.ACTIVE, 70, "armor_pierce", Map.of("pierceRatio", "0.75"));
        regTempl("seismic_stomp", PowerCategory.SUPERSTRENGTH, PowerType.ACTIVE, 60, "ground_stomp", Map.of("stunDuration", "40"));
    }

    // 4. RESISTANCE (13)
    private static void registerResistancePowers() {
        regFunc("diamond_skin", PowerCategory.RESISTANCE, PowerType.PASSIVE, 0,
                new AttributeModifierComponent("armor", 10.0D, AttributeModifier.Operation.ADDITION), List.of());
        regFunc("kinetic_mitigation", PowerCategory.RESISTANCE, PowerType.PASSIVE, 0, new DamageMitigationComponent(0.4F, "all"), List.of());
        regFunc("fire_immunity", PowerCategory.RESISTANCE, PowerType.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fire"), List.of());
        regFunc("nanite_regeneration", PowerCategory.RESISTANCE, PowerType.ACTIVE, 80, new HealActionComponent(8.0F, true), List.of());

        regTempl("cellular_rejuvenation", PowerCategory.RESISTANCE, PowerType.PASSIVE, 0, "passive_regen_tick", Map.of("rate", "1hp/2s"));
        regTempl("absolute_invulnerability", PowerCategory.RESISTANCE, PowerType.ACTIVE, 400, "invulnerable_window", Map.of("duration", "80"));
        regTempl("vitality_surge", PowerCategory.RESISTANCE, PowerType.ACTIVE, 160, "overheal_surge", Map.of("tempHearts", "6"));
        regTempl("pain_suppression", PowerCategory.RESISTANCE, PowerType.TOGGLE, 0, "knockback_immunity", Map.of("status", "unstaggerable"));
        regTempl("blast_armor", PowerCategory.RESISTANCE, PowerType.PASSIVE, 0, "explosion_dampening", Map.of("absorbRatio", "0.8"));
        regTempl("thermal_absorption", PowerCategory.RESISTANCE, PowerType.PASSIVE, 0, "heat_conversion", Map.of("heatToEnergy", "true"));
        regTempl("reactive_plating", PowerCategory.RESISTANCE, PowerType.PASSIVE, 0, "stacking_resilience", Map.of("maxStacks", "5"));
        regTempl("enduring_will", PowerCategory.RESISTANCE, PowerType.PASSIVE, 0, "death_defy", Map.of("threshold", "1hp"));
        regTempl("subatomic_hardening", PowerCategory.RESISTANCE, PowerType.ACTIVE, 200, "density_shift", Map.of("defenseBoost", "100%"));
    }

    // 5. TELEPORTATION (13)
    private static void registerTeleportationPowers() {
        regFunc("blink_step", PowerCategory.TELEPORTATION, PowerType.ACTIVE, 40, new BlinkComponent(16.0D), List.of());
        regFunc("spatial_leap", PowerCategory.TELEPORTATION, PowerType.ACTIVE, 70, new BlinkComponent(32.0D), List.of());
        regFunc("phase_dash", PowerCategory.TELEPORTATION, PowerType.ACTIVE, 50, new DashComponent(2.0D, 0.0D), List.of());

        regTempl("dimensional_rift", PowerCategory.TELEPORTATION, PowerType.ACTIVE, 300, "portal_tear", Map.of("duration", "200"));
        regTempl("shadow_jaunt", PowerCategory.TELEPORTATION, PowerType.ACTIVE, 60, "darkness_teleport", Map.of("maxLight", "7"));
        regTempl("quantum_swap", PowerCategory.TELEPORTATION, PowerType.ACTIVE, 120, "position_exchange", Map.of("range", "24.0"));
        regTempl("teleport_beacon", PowerCategory.TELEPORTATION, PowerType.ACTIVE, 200, "recall_anchor", Map.of("recallDelay", "0"));
        regTempl("micro_warp", PowerCategory.TELEPORTATION, PowerType.ACTIVE, 20, "short_step", Map.of("distance", "6.0"));
        regFunc("void_step", PowerCategory.TELEPORTATION, PowerType.ACTIVE, 90, new PhaseComponent(20.0D), List.of());
        regTempl("spatial_anchor", PowerCategory.TELEPORTATION, PowerType.TOGGLE, 0, "teleport_lock", Map.of("antiTeleportRadius", "16.0"));
        regTempl("wormhole_gate", PowerCategory.TELEPORTATION, PowerType.CHARGED, 400, "two_way_gate", Map.of("gateLifetime", "400"));
        regTempl("distant_traverse", PowerCategory.TELEPORTATION, PowerType.CHARGED, 600, "long_range_warp", Map.of("maxKm", "1.0"));
        regTempl("phase_retreat", PowerCategory.TELEPORTATION, PowerType.ACTIVE, 100, "backward_blink", Map.of("distance", "12.0"));
    }

    // 6. ENERGY (13)
    private static void registerEnergyPowers() {
        regFunc("energy_blast", PowerCategory.ENERGY, PowerType.ACTIVE, 30, new DamageActionComponent(10.0F, 6.0D, "magic"), List.of());
        regFunc("energy_burst", PowerCategory.ENERGY, PowerType.ACTIVE, 50, new ExplosionActionComponent(2.5F, false, false), List.of());
        regFunc("kinetic_pulse", PowerCategory.ENERGY, PowerType.ACTIVE, 40, new KnockbackActionComponent(7.0D, 1.8D), List.of());

        regTempl("plasma_beam", PowerCategory.ENERGY, PowerType.SUSTAINED, 0, "continuous_ray", Map.of("dps", "14"));
        regTempl("overcharge_sphere", PowerCategory.ENERGY, PowerType.CHARGED, 120, "accumulating_orb", Map.of("maxRadius", "4.0"));
        regTempl("photon_volley", PowerCategory.ENERGY, PowerType.ACTIVE, 60, "multi_projectile", Map.of("count", "5"));
        regTempl("emp_shockwave", PowerCategory.ENERGY, PowerType.ACTIVE, 180, "tech_drain", Map.of("radius", "12.0"));
        regTempl("energy_siphon", PowerCategory.ENERGY, PowerType.ACTIVE, 90, "drain_target", Map.of("drainAmount", "10"));
        regFunc("radiant_nova", PowerCategory.ENERGY, PowerType.ACTIVE, 240,
                new ShockwaveComponent(10.0D, 14.0F, 1.6D, 24), List.of());
        regFunc("plasma_cutter", PowerCategory.ENERGY, PowerType.ACTIVE, 50,
                new EnergyBeamComponent(10.0D, 9.0F, 2, "fire"), List.of());
        regTempl("prismatic_shield", PowerCategory.ENERGY, PowerType.TOGGLE, 0, "reflection_matrix", Map.of("reflectPct", "35"));
        regTempl("quantum_cascade", PowerCategory.ENERGY, PowerType.CHARGED, 300, "chain_detonation", Map.of("cascades", "4"));
        regFunc("tachyon_pulse", PowerCategory.ENERGY, PowerType.ACTIVE, 70,
                new EnergyBeamComponent(48.0D, 12.0F, 8, "magic"), List.of());
    }

    // 7. FIRE (13)
    private static void registerFirePowers() {
        regFunc("pyroclastic_burst", PowerCategory.FIRE, PowerType.ACTIVE, 40, new DamageActionComponent(8.0F, 5.0D, "fire"), List.of());
        regFunc("fireball_shot", PowerCategory.FIRE, PowerType.ACTIVE, 50, new ExplosionActionComponent(2.0F, false, true), List.of());
        regFunc("inferno_resistance", PowerCategory.FIRE, PowerType.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fire"), List.of());

        regTempl("flame_trail", PowerCategory.FIRE, PowerType.TOGGLE, 0, "ground_fire_path", Map.of("burnDuration", "60"));
        regTempl("firestorm_cyclone", PowerCategory.FIRE, PowerType.SUSTAINED, 180, "fire_vortex", Map.of("radius", "8.0"));
        regTempl("cauterize_wound", PowerCategory.FIRE, PowerType.ACTIVE, 100, "self_heal_fire", Map.of("healAmount", "6"));
        regTempl("magma_fist", PowerCategory.FIRE, PowerType.ACTIVE, 40, "ignite_punch", Map.of("fireDuration", "100"));
        regTempl("solar_flare_eruption", PowerCategory.FIRE, PowerType.ACTIVE, 220, "flare_burst", Map.of("blindDuration", "80"));
        regTempl("molten_armor", PowerCategory.FIRE, PowerType.TOGGLE, 0, "thorns_fire", Map.of("recoilDamage", "4"));
        regTempl("combustion_wave", PowerCategory.FIRE, PowerType.ACTIVE, 80, "radial_heat", Map.of("waveDistance", "8.0"));
        regTempl("ember_teleport", PowerCategory.FIRE, PowerType.ACTIVE, 70, "smoke_relocation", Map.of("distance", "14.0"));
        regTempl("plasma_lance", PowerCategory.FIRE, PowerType.ACTIVE, 60, "pierce_fire_ray", Map.of("length", "12.0"));
        regTempl("phoenix_rebirth", PowerCategory.FIRE, PowerType.PASSIVE, 1200, "resurrection_fire", Map.of("healPercent", "50"));
    }

    // 8. ICE (13)
    private static void registerIcePowers() {
        regFunc("cryo_blast", PowerCategory.ICE, PowerType.ACTIVE, 50, new AreaEffectActionComponent("freeze", 140, 1, 6.0D), List.of());
        regFunc("frost_bite_touch", PowerCategory.ICE, PowerType.ACTIVE, 40, new DamageActionComponent(7.0F, 4.0D, "magic"), List.of());
        regFunc("subzero_burst", PowerCategory.ICE, PowerType.ACTIVE, 60, new KnockbackActionComponent(5.0D, 1.4D), List.of());

        regTempl("glacial_wall", PowerCategory.ICE, PowerType.ACTIVE, 120, "temporary_ice_blocks", Map.of("width", "5", "height", "3"));
        regTempl("ice_skate_stride", PowerCategory.ICE, PowerType.TOGGLE, 0, "frost_walker_movement", Map.of("speedGain", "0.2"));
        regTempl("blizzard_vortex", PowerCategory.ICE, PowerType.SUSTAINED, 200, "storm_slowness", Map.of("radius", "10.0"));
        regTempl("ice_shards_volley", PowerCategory.ICE, PowerType.ACTIVE, 40, "projectile_icicles", Map.of("shardCount", "6"));
        regTempl("absolute_zero_cage", PowerCategory.ICE, PowerType.ACTIVE, 260, "deep_freeze", Map.of("freezeTicks", "200"));
        regTempl("permafrost_skin", PowerCategory.ICE, PowerType.TOGGLE, 0, "armor_toughness_ice", Map.of("toughnessBonus", "6"));
        regTempl("cryo_healing", PowerCategory.ICE, PowerType.ACTIVE, 140, "stasis_regen", Map.of("regenDuration", "100"));
        regTempl("frost_nova", PowerCategory.ICE, PowerType.ACTIVE, 80, "ground_freeze_wave", Map.of("novaRadius", "8.0"));
        regTempl("avalanche_rush", PowerCategory.ICE, PowerType.ACTIVE, 90, "snow_dash", Map.of("distance", "14.0"));
        regTempl("ice_clone", PowerCategory.ICE, PowerType.ACTIVE, 160, "brittle_decoy", Map.of("shatterDamage", "10"));
    }

    // 9. ELECTRICITY (13)
    private static void registerElectricityPowers() {
        regFunc("thunderbolt_strike", PowerCategory.ELECTRICITY, PowerType.ACTIVE, 80, new LightningActionComponent(28.0D), List.of());
        regFunc("static_shock", PowerCategory.ELECTRICITY, PowerType.ACTIVE, 30, new DamageActionComponent(6.0F, 5.0D, "lightning"), List.of());
        regFunc("electromagnetic_pulse", PowerCategory.ELECTRICITY, PowerType.ACTIVE, 60, new KnockbackActionComponent(6.0D, 1.6D), List.of());

        regTempl("chain_lightning", PowerCategory.ELECTRICITY, PowerType.ACTIVE, 100, "chain_damage", Map.of("maxJumps", "4"));
        regTempl("lightning_rod_aura", PowerCategory.ELECTRICITY, PowerType.TOGGLE, 0, "absorb_lightning", Map.of("chargePerHit", "20"));
        regTempl("volt_dash", PowerCategory.ELECTRICITY, PowerType.ACTIVE, 40, "instant_spark_dash", Map.of("dashSpeed", "2.5"));
        regTempl("magnetic_repulsion", PowerCategory.ELECTRICITY, PowerType.TOGGLE, 0, "projectile_deflect", Map.of("deflectChance", "0.7"));
        regTempl("arc_field", PowerCategory.ELECTRICITY, PowerType.SUSTAINED, 140, "continuous_sparks", Map.of("dps", "8"));
        regFunc("taser_touch", PowerCategory.ELECTRICITY, PowerType.ACTIVE, 50,
                new StunComponent(3.5D, 60, 4), List.of());
        regTempl("plasma_railgun", PowerCategory.ELECTRICITY, PowerType.CHARGED, 180, "hypervelocity_bolt", Map.of("pierceDepth", "10"));
        regTempl("ball_lightning", PowerCategory.ELECTRICITY, PowerType.ACTIVE, 120, "slow_moving_orb", Map.of("orbLifetime", "100"));
        regTempl("overload_capacitors", PowerCategory.ELECTRICITY, PowerType.ACTIVE, 240, "stat_burst", Map.of("duration", "160"));
        regTempl("electromagnetic_levitate", PowerCategory.ELECTRICITY, PowerType.TOGGLE, 0, "metal_float", Map.of("height", "4.0"));
    }

    // 10. WATER (13)
    private static void registerWaterPowers() {
        regFunc("aquatic_respiration", PowerCategory.WATER, PowerType.PASSIVE, 0, new WaterAdaptationComponent(true, true), List.of());
        regFunc("tidal_wave", PowerCategory.WATER, PowerType.ACTIVE, 60, new KnockbackActionComponent(8.0D, 2.2D), List.of());
        regFunc("water_dash", PowerCategory.WATER, PowerType.ACTIVE, 40, new DashComponent(1.7D, 0.1D), List.of());

        regTempl("hydro_cutter", PowerCategory.WATER, PowerType.ACTIVE, 40, "high_pressure_stream", Map.of("range", "16.0"));
        regTempl("bubble_prison", PowerCategory.WATER, PowerType.ACTIVE, 140, "water_sphere_trap", Map.of("holdTicks", "100"));
        regTempl("healing_rain", PowerCategory.WATER, PowerType.ACTIVE, 200, "area_shower_regen", Map.of("radius", "8.0"));
        regFunc("vortex_maelstrom", PowerCategory.WATER, PowerType.SUSTAINED, 180, new VortexActionComponent(10.0D, 0.08D), List.of());
        regTempl("liquid_form", PowerCategory.WATER, PowerType.TOGGLE, 0, "fluid_phase", Map.of("damageReduction", "0.6"));
        regTempl("geyser_erupt", PowerCategory.WATER, PowerType.ACTIVE, 90, "vertical_water_thrust", Map.of("launchPower", "2.0"));
        regTempl("mist_veil", PowerCategory.WATER, PowerType.ACTIVE, 120, "evasion_fog", Map.of("radius", "6.0"));
        regTempl("frost_water_conversion", PowerCategory.WATER, PowerType.ACTIVE, 80, "temperature_shift", Map.of("mode", "freeze_or_melt"));
        regTempl("tsunami_charge", PowerCategory.WATER, PowerType.CHARGED, 260, "massive_wave", Map.of("waveWidth", "12.0"));
        regTempl("abyssal_pressure", PowerCategory.WATER, PowerType.ACTIVE, 110, "crushing_gravity_water", Map.of("pressureDmg", "14"));
    }

    // 11. AIR (13)
    private static void registerAirPowers() {
        regFunc("gale_blast", PowerCategory.AIR, PowerType.ACTIVE, 40, new KnockbackActionComponent(7.0D, 1.9D), List.of());
        regFunc("cyclone_leap", PowerCategory.AIR, PowerType.ACTIVE, 50, new LeapComponent(1.2D, 1.8D), List.of());
        regFunc("wind_cushion", PowerCategory.AIR, PowerType.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fall"), List.of());

        regTempl("vacuum_implosion", PowerCategory.AIR, PowerType.ACTIVE, 100, "inward_suction", Map.of("pullRadius", "8.0"));
        regTempl("tornado_column", PowerCategory.AIR, PowerType.SUSTAINED, 180, "whirlwind_lift", Map.of("liftHeight", "12.0"));
        regTempl("air_blade", PowerCategory.AIR, PowerType.ACTIVE, 30, "compressed_wind_slice", Map.of("damage", "9"));
        regTempl("suffocation_bubble", PowerCategory.AIR, PowerType.ACTIVE, 160, "oxygen_removal", Map.of("duration", "80"));
        regTempl("aero_shield", PowerCategory.AIR, PowerType.TOGGLE, 0, "wind_deflection", Map.of("deflectChance", "0.6"));
        regTempl("feather_step", PowerCategory.AIR, PowerType.PASSIVE, 0, "silent_light_movement", Map.of("vibrationSuppressed", "true"));
        regTempl("pressure_pulse", PowerCategory.AIR, PowerType.ACTIVE, 70, "atmospheric_burst", Map.of("stunTicks", "30"));
        regTempl("tailwind_aura", PowerCategory.AIR, PowerType.TOGGLE, 0, "ally_speed_buff", Map.of("speedGain", "0.15"));
        regTempl("storm_glide", PowerCategory.AIR, PowerType.TOGGLE, 0, "aerial_horizontal_drift", Map.of("maxSpeed", "1.5"));
        regTempl("soundless_strike", PowerCategory.AIR, PowerType.ACTIVE, 60, "zero_noise_attack", Map.of("critMult", "2.0"));
    }

    // 12. EARTH (13)
    private static void registerEarthPowers() {
        regFunc("stone_skin_plate", PowerCategory.EARTH, PowerType.PASSIVE, 0,
                new AttributeModifierComponent("armor", 8.0D, AttributeModifier.Operation.ADDITION), List.of());
        regFunc("tremor_shock", PowerCategory.EARTH, PowerType.ACTIVE, 60, new DamageActionComponent(9.0F, 5.0D, "physical"), List.of());
        regFunc("boulder_knockback", PowerCategory.EARTH, PowerType.ACTIVE, 50, new KnockbackActionComponent(6.0D, 1.7D), List.of());

        regTempl("earthen_pillar", PowerCategory.EARTH, PowerType.ACTIVE, 80, "block_pillar_summon", Map.of("pillarHeight", "4"));
        regTempl("quicksand_trap", PowerCategory.EARTH, PowerType.ACTIVE, 120, "sinkhole_slowness", Map.of("slowLevel", "4"));
        regTempl("subterranean_burrow", PowerCategory.EARTH, PowerType.TOGGLE, 0, "underground_tunneling", Map.of("digSpeed", "fast"));
        regTempl("rock_armor_harden", PowerCategory.EARTH, PowerType.ACTIVE, 140, "temp_defense_spike", Map.of("armorBoost", "12"));
        regTempl("sandstorm_shroud", PowerCategory.EARTH, PowerType.ACTIVE, 100, "blindness_particle_zone", Map.of("radius", "7.0"));
        regTempl("crystal_spikes", PowerCategory.EARTH, PowerType.ACTIVE, 70, "ground_puncture", Map.of("spikeDamage", "12"));
        regTempl("tectonic_fault", PowerCategory.EARTH, PowerType.CHARGED, 240, "crevasse_fissure", Map.of("depth", "6"));
        regTempl("mineral_magnetism", PowerCategory.EARTH, PowerType.ACTIVE, 90, "ore_locate_pulse", Map.of("radius", "16.0"));
        regTempl("granite_fortress", PowerCategory.EARTH, PowerType.ACTIVE, 300, "dome_creation", Map.of("domeRadius", "4.0"));
        regTempl("petrifying_touch", PowerCategory.EARTH, PowerType.ACTIVE, 180, "turn_to_stone_slow", Map.of("duration", "100"));
    }

    // 13. PLANTS (13)
    private static void registerPlantsPowers() {
        regFunc("vine_pull", PowerCategory.PLANTS, PowerType.ACTIVE, 40, new PullActionComponent(14.0D, 1.4D), List.of());
        regFunc("spore_cloud", PowerCategory.PLANTS, PowerType.ACTIVE, 60, new AreaEffectActionComponent("slowness", 100, 2, 5.0D), List.of());

        regTempl("thorn_armor", PowerCategory.PLANTS, PowerType.PASSIVE, 0, "thorns_reflect", Map.of("damage", "4"));
        regTempl("root_entangle", PowerCategory.PLANTS, PowerType.ACTIVE, 80, "immobilize_roots", Map.of("duration", "60"));
        regTempl("chlorophyll_healing", PowerCategory.PLANTS, PowerType.PASSIVE, 0, "sunlight_regen", Map.of("rate", "1hp/3s"));
        regTempl("bramble_barrier", PowerCategory.PLANTS, PowerType.ACTIVE, 100, "dense_hedge_wall", Map.of("width", "6"));
        regTempl("poison_ivy_coating", PowerCategory.PLANTS, PowerType.TOGGLE, 0, "poison_melee", Map.of("poisonTicks", "60"));
        regTempl("forest_communion", PowerCategory.PLANTS, PowerType.ACTIVE, 120, "biome_sense", Map.of("radius", "32.0"));
        regTempl("bloom_burst", PowerCategory.PLANTS, PowerType.ACTIVE, 70, "flower_pollen_aoe", Map.of("radius", "6.0"));
        regTempl("bark_reinforce", PowerCategory.PLANTS, PowerType.ACTIVE, 140, "sturdy_tree_hide", Map.of("knockbackResistance", "0.8"));
        regTempl("carnivorous_flora", PowerCategory.PLANTS, PowerType.ACTIVE, 200, "snapdragon_summon", Map.of("biteDmg", "14"));
        regTempl("spore_revival", PowerCategory.PLANTS, PowerType.PASSIVE, 800, "plant_rebirth", Map.of("reviveHealth", "6"));
        regTempl("vine_swing", PowerCategory.PLANTS, PowerType.ACTIVE, 50, "grapple_swing", Map.of("distance", "18.0"));
    }

    // 14. SOUND (13)
    private static void registerSoundPowers() {
        regFunc("sonic_screech", PowerCategory.SOUND, PowerType.ACTIVE, 50, new DamageActionComponent(8.0F, 5.0D, "magic"), List.of());
        regFunc("acoustic_shockwave", PowerCategory.SOUND, PowerType.ACTIVE, 40, new KnockbackActionComponent(8.0D, 2.0D), List.of());
        regFunc("sonic_boost_leap", PowerCategory.SOUND, PowerType.ACTIVE, 60, new LeapComponent(1.5D, 1.4D), List.of());

        regTempl("echolocation_pulse", PowerCategory.SOUND, PowerType.ACTIVE, 60, "sonar_highlight", Map.of("range", "32.0"));
        regTempl("resonance_shatter", PowerCategory.SOUND, PowerType.ACTIVE, 90, "vibration_break", Map.of("damageGlass", "true"));
        regTempl("silence_bubble", PowerCategory.SOUND, PowerType.ACTIVE, 160, "dampen_all_audio", Map.of("radius", "10.0"));
        regTempl("hypersonic_blast", PowerCategory.SOUND, PowerType.CHARGED, 140, "cone_stun", Map.of("stunSeconds", "3"));
        regTempl("tuning_fork_strike", PowerCategory.SOUND, PowerType.ACTIVE, 70, "harmonic_vibe", Map.of("extraDamage", "6"));
        regTempl("auditory_hallucination", PowerCategory.SOUND, PowerType.ACTIVE, 120, "phantom_noises", Map.of("scareMobs", "true"));
        regTempl("decibel_cannon", PowerCategory.SOUND, PowerType.ACTIVE, 110, "directional_sound_shot", Map.of("force", "2.5"));
        regTempl("dampening_frequency", PowerCategory.SOUND, PowerType.TOGGLE, 0, "negate_kinetic_vibrations", Map.of("absorption", "0.3"));
        regTempl("bass_drop_rupture", PowerCategory.SOUND, PowerType.ACTIVE, 150, "low_freq_ground_quake", Map.of("radius", "8.0"));
        regTempl("siren_lullaby", PowerCategory.SOUND, PowerType.ACTIVE, 200, "pacify_entities", Map.of("duration", "120"));
    }

    // 15. LIGHT (13)
    private static void registerLightPowers() {
        regFunc("solar_healing", PowerCategory.LIGHT, PowerType.ACTIVE, 100, new HealActionComponent(6.0F, true), List.of());
        regFunc("photonic_dash", PowerCategory.LIGHT, PowerType.ACTIVE, 40, new DashComponent(2.0D, 0.1D), List.of());

        regTempl("blinding_flash", PowerCategory.LIGHT, PowerType.ACTIVE, 80, "radiant_blind", Map.of("radius", "8.0"));
        regTempl("laser_refraction", PowerCategory.LIGHT, PowerType.SUSTAINED, 0, "beam_channel", Map.of("dps", "12"));
        regTempl("invisibility_cloak", PowerCategory.LIGHT, PowerType.TOGGLE, 0, "bend_photons", Map.of("stealth", "true"));
        regTempl("holographic_projection", PowerCategory.LIGHT, PowerType.ACTIVE, 120, "light_decoy", Map.of("count", "1"));
        regTempl("dawn_shield", PowerCategory.LIGHT, PowerType.ACTIVE, 140, "radiant_barrier", Map.of("absorbHearts", "4"));
        regTempl("prism_barrage", PowerCategory.LIGHT, PowerType.ACTIVE, 60, "rainbow_lasers", Map.of("beams", "7"));
        regTempl("sun_spear", PowerCategory.LIGHT, PowerType.CHARGED, 110, "charged_javelin_light", Map.of("damage", "18"));
        regTempl("purifying_glow", PowerCategory.LIGHT, PowerType.ACTIVE, 160, "cleanse_undead_burn", Map.of("undeadDamage", "16"));
        regTempl("ambient_illumination", PowerCategory.LIGHT, PowerType.TOGGLE, 0, "personal_light_source", Map.of("level", "15"));
        regTempl("aurora_canopy", PowerCategory.LIGHT, PowerType.ACTIVE, 240, "sky_color_buff", Map.of("regenBuff", "true"));
        regTempl("solar_flare_burst", PowerCategory.LIGHT, PowerType.ACTIVE, 150, "nova_light", Map.of("radius", "10.0"));
    }

    // 16. SHADOWS (13)
    private static void registerShadowsPowers() {
        regFunc("shadow_teleport", PowerCategory.SHADOWS, PowerType.ACTIVE, 50, new BlinkComponent(14.0D), List.of());
        regFunc("shadow_strike", PowerCategory.SHADOWS, PowerType.ACTIVE, 40, new DamageActionComponent(9.0F, 4.0D, "magic"), List.of());

        regTempl("shroud_of_night", PowerCategory.SHADOWS, PowerType.TOGGLE, 0, "light_absorption", Map.of("reduceMobAggro", "true"));
        regTempl("darkness_tendrils", PowerCategory.SHADOWS, PowerType.ACTIVE, 80, "shadow_arms_pull", Map.of("targetCount", "3"));
        regTempl("shadow_puppet_clone", PowerCategory.SHADOWS, PowerType.ACTIVE, 140, "dark_replica", Map.of("replicaHp", "20"));
        regTempl("void_pocket", PowerCategory.SHADOWS, PowerType.ACTIVE, 100, "banish_target_briefly", Map.of("duration", "40"));
        regTempl("umbral_armor", PowerCategory.SHADOWS, PowerType.TOGGLE, 0, "damage_absorb_in_dark", Map.of("darkReduction", "0.5"));
        regTempl("blind_eclipse", PowerCategory.SHADOWS, PowerType.ACTIVE, 200, "area_darkness_fog", Map.of("radius", "12.0"));
        regTempl("shadow_meld", PowerCategory.SHADOWS, PowerType.ACTIVE, 90, "wall_shadow_crawl", Map.of("duration", "80"));
        regTempl("nocturnal_vigor", PowerCategory.SHADOWS, PowerType.PASSIVE, 0, "night_stat_boost", Map.of("speedBoost", "0.1"));
        regTempl("abyssal_grasp", PowerCategory.SHADOWS, PowerType.ACTIVE, 70, "ground_arms_slow", Map.of("slowness", "3"));
        regTempl("dark_consumption", PowerCategory.SHADOWS, PowerType.ACTIVE, 160, "absorb_mob_soul", Map.of("healOnKill", "true"));
        regTempl("nightfall_eruption", PowerCategory.SHADOWS, PowerType.ACTIVE, 220, "blackout_pulse", Map.of("radius", "14.0"));
    }

    // 17. TELEKINESIS (13)
    private static void registerTelekinesisPowers() {
        regFunc("kinetic_pull", PowerCategory.TELEKINESIS, PowerType.ACTIVE, 30, new PullActionComponent(16.0D, 1.6D), List.of());
        regFunc("kinetic_push", PowerCategory.TELEKINESIS, PowerType.ACTIVE, 30, new KnockbackActionComponent(7.0D, 1.8D), List.of());
        regFunc("psychic_slam", PowerCategory.TELEKINESIS, PowerType.ACTIVE, 60, new DamageActionComponent(8.0F, 5.0D, "physical"), List.of());

        regFunc("telekinetic_levitate", PowerCategory.TELEKINESIS, PowerType.SUSTAINED, 0, new LevitationComponent(16.0D, 0.14D), List.of());
        regTempl("object_throw", PowerCategory.TELEKINESIS, PowerType.ACTIVE, 40, "hurl_nearby_debris", Map.of("speed", "2.0"));
        regTempl("debris_orbit_shield", PowerCategory.TELEKINESIS, PowerType.TOGGLE, 0, "revolving_debris_ring", Map.of("ringCount", "4"));
        regTempl("crush_grip", PowerCategory.TELEKINESIS, PowerType.CHARGED, 120, "implosive_squeeze", Map.of("maxDmg", "20"));
        regTempl("mass_suspension", PowerCategory.TELEKINESIS, PowerType.ACTIVE, 180, "lift_all_in_radius", Map.of("radius", "8.0"));
        regTempl("kinetic_deflection", PowerCategory.TELEKINESIS, PowerType.ACTIVE, 50, "reverse_incoming_arrow", Map.of("deflectSpeed", "2.2"));
        regTempl("remote_interaction", PowerCategory.TELEKINESIS, PowerType.ACTIVE, 20, "distant_block_use", Map.of("reachDistance", "12.0"));
        regTempl("vector_barrier", PowerCategory.TELEKINESIS, PowerType.ACTIVE, 140, "force_wall", Map.of("size", "4x4"));
        regTempl("spatial_snare", PowerCategory.TELEKINESIS, PowerType.ACTIVE, 90, "lock_entity_in_air", Map.of("duration", "60"));
        regTempl("telekinetic_flight", PowerCategory.TELEKINESIS, PowerType.TOGGLE, 0, "self_levitation_move", Map.of("speed", "0.8"));
    }

    // 18. GRAVITY (13)
    private static void registerGravityPowers() {
        regFunc("gravity_leap", PowerCategory.GRAVITY, PowerType.ACTIVE, 50, new LeapComponent(1.2D, 2.0D), List.of());
        regFunc("gravitational_slam", PowerCategory.GRAVITY, PowerType.ACTIVE, 60, new DamageActionComponent(10.0F, 6.0D, "physical"), List.of());
        regFunc("weightlessness_cushion", PowerCategory.GRAVITY, PowerType.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fall"), List.of());

        regFunc("zero_gravity_field", PowerCategory.GRAVITY, PowerType.SUSTAINED, 120,
                new GravityWellComponent(8.0D, 0.12D, GravityWellComponent.MODE_PUSH, 16), List.of());
        regFunc("micro_black_hole", PowerCategory.GRAVITY, PowerType.SUSTAINED, 260,
                new GravityWellComponent(12.0D, 0.22D, GravityWellComponent.MODE_PULL, 24), List.of());
        regTempl("heavy_gravity_zone", PowerCategory.GRAVITY, PowerType.ACTIVE, 100, "pin_down_entities", Map.of("fallSpeedBonus", "2.0"));
        regTempl("gravity_well", PowerCategory.GRAVITY, PowerType.ACTIVE, 80, "target_attract_orb", Map.of("duration", "80"));
        regTempl("invert_gravity", PowerCategory.GRAVITY, PowerType.ACTIVE, 140, "reverse_gravity_target", Map.of("duration", "60"));
        regTempl("repulsor_cushion", PowerCategory.GRAVITY, PowerType.TOGGLE, 0, "fall_damage_to_zero", Map.of("active", "true"));
        regTempl("graviton_crush", PowerCategory.GRAVITY, PowerType.ACTIVE, 150, "downward_force_burst", Map.of("damage", "14"));
        regTempl("orbital_slingshot", PowerCategory.GRAVITY, PowerType.ACTIVE, 70, "high_speed_launch", Map.of("impulse", "3.0"));
        regTempl("density_flux", PowerCategory.GRAVITY, PowerType.TOGGLE, 0, "mass_multiplier", Map.of("multiplier", "3.0"));
        regFunc("event_horizon_pulse", PowerCategory.GRAVITY, PowerType.ACTIVE, 220,
                new GravityWellComponent(10.0D, 0.3D, GravityWellComponent.MODE_PUSH, 24), List.of());
    }

    // 19. SHIELDS (13)
    private static void registerShieldsPowers() {
        regFunc("kinetic_barrier", PowerCategory.SHIELDS, PowerType.ACTIVE, 60, new ShieldActionComponent(8.0F, 160), List.of());
        regFunc("energy_aegis", PowerCategory.SHIELDS, PowerType.ACTIVE, 100, new ShieldActionComponent(14.0F, 240), List.of());
        regFunc("reactive_deflection", PowerCategory.SHIELDS, PowerType.ACTIVE, 40, new KnockbackActionComponent(6.0D, 1.8D), List.of());

        regTempl("dome_shield", PowerCategory.SHIELDS, PowerType.ACTIVE, 200, "stationary_bubble", Map.of("radius", "5.0", "duration", "160"));
        regTempl("directional_riot_shield", PowerCategory.SHIELDS, PowerType.TOGGLE, 0, "frontal_block_cone", Map.of("angle", "90"));
        regTempl("tethered_shield", PowerCategory.SHIELDS, PowerType.ACTIVE, 120, "shield_target_ally", Map.of("heartsGranted", "6"));
        regTempl("plasma_buckler", PowerCategory.SHIELDS, PowerType.ACTIVE, 50, "parry_incoming_hit", Map.of("parryWindowTicks", "15"));
        regTempl("adaptive_matrix", PowerCategory.SHIELDS, PowerType.PASSIVE, 0, "learns_last_damage_type", Map.of("resistanceBoost", "0.5"));
        regTempl("invulnerable_bulwark", PowerCategory.SHIELDS, PowerType.ACTIVE, 400, "100_percent_block", Map.of("durationTicks", "60"));
        regTempl("reflect_prism", PowerCategory.SHIELDS, PowerType.ACTIVE, 80, "bounce_magic_projectiles", Map.of("reflectRange", "16.0"));
        regTempl("fortified_hull", PowerCategory.SHIELDS, PowerType.PASSIVE, 0, "armor_toughness_mod", Map.of("toughness", "8"));
        regTempl("barrier_overload", PowerCategory.SHIELDS, PowerType.ACTIVE, 140, "detonate_shield_for_aoe", Map.of("radius", "6.0"));
        regTempl("hexagonal_mesh", PowerCategory.SHIELDS, PowerType.TOGGLE, 0, "layered_shielding", Map.of("layers", "3"));
    }

    // 20. HEALING (13)
    private static void registerHealingPowers() {
        regFunc("healing_touch", PowerCategory.HEALING, PowerType.ACTIVE, 60, new HealActionComponent(8.0F, true), List.of());
        regFunc("regeneration_aura", PowerCategory.HEALING, PowerType.ACTIVE, 120, new HealActionComponent(14.0F, false), List.of());
        regFunc("restorative_surge", PowerCategory.HEALING, PowerType.ACTIVE, 80, new HealActionComponent(10.0F, true), List.of());

        regTempl("purifying_cleanse", PowerCategory.HEALING, PowerType.ACTIVE, 40, "remove_all_debuffs", Map.of("cleanse", "all"));
        regTempl("tether_vitality", PowerCategory.HEALING, PowerType.SUSTAINED, 0, "beam_heal_ally", Map.of("healPerSecond", "4"));
        regTempl("resuscitation_burst", PowerCategory.HEALING, PowerType.PASSIVE, 1200, "prevent_fatal_blow", Map.of("reviveHealth", "8"));
        regTempl("cellular_overclock", PowerCategory.HEALING, PowerType.ACTIVE, 140, "supercharged_regen", Map.of("duration", "100"));
        regTempl("font_of_life", PowerCategory.HEALING, PowerType.ACTIVE, 200, "totem_spring_heal", Map.of("radius", "6.0"));
        regTempl("metabolic_repair", PowerCategory.HEALING, PowerType.PASSIVE, 0, "hunger_saturation_buff", Map.of("saturationRate", "slow"));
        regTempl("empathic_transfusion", PowerCategory.HEALING, PowerType.ACTIVE, 70, "transfer_health_to_target", Map.of("amount", "6"));
        regTempl("sanctuary_ground", PowerCategory.HEALING, PowerType.ACTIVE, 240, "holy_healing_zone", Map.of("duration", "160"));
        regTempl("rejuvenating_mist", PowerCategory.HEALING, PowerType.ACTIVE, 100, "spray_healing_mist", Map.of("coneAngle", "45"));
        regTempl("spirit_salve", PowerCategory.HEALING, PowerType.ACTIVE, 50, "instant_small_heal", Map.of("amount", "4"));
    }

    // 21. ENHANCED SENSES (12)
    private static void registerEnhancedSensesPowers() {
        regFunc("sonar_pulse", PowerCategory.ENHANCED_SENSES, PowerType.ACTIVE, 40, new ThermalVisionComponent(28.0D, 120), List.of());
        regFunc("reflex_boost", PowerCategory.ENHANCED_SENSES, PowerType.PASSIVE, 0,
                new AttributeModifierComponent("movement_speed", 0.08D, AttributeModifier.Operation.ADDITION), List.of());

        regTempl("heartbeat_sensor", PowerCategory.ENHANCED_SENSES, PowerType.TOGGLE, 0, "detect_living_mobs", Map.of("range", "32.0"));
        regTempl("danger_precognition", PowerCategory.ENHANCED_SENSES, PowerType.PASSIVE, 0, "warning_before_incoming_hit", Map.of("dodgeChance", "0.25"));
        regTempl("olfactory_tracker", PowerCategory.ENHANCED_SENSES, PowerType.ACTIVE, 80, "scent_trail_to_target", Map.of("maxTrackingDistance", "64.0"));
        regTempl("vibration_detection", PowerCategory.ENHANCED_SENSES, PowerType.TOGGLE, 0, "warden_style_vibrations", Map.of("range", "24.0"));
        regTempl("weak_point_analysis", PowerCategory.ENHANCED_SENSES, PowerType.PASSIVE, 0, "crit_rate_increase", Map.of("critBonus", "0.3"));
        regTempl("frequency_tuning", PowerCategory.ENHANCED_SENSES, PowerType.ACTIVE, 60, "hear_stealthed_entities", Map.of("revealDistance", "16.0"));
        regTempl("accelerated_cognition", PowerCategory.ENHANCED_SENSES, PowerType.ACTIVE, 120, "bullet_time_reaction", Map.of("duration", "60"));
        regTempl("environmental_gauge", PowerCategory.ENHANCED_SENSES, PowerType.PASSIVE, 0, "hud_elevation_temperature", Map.of("display", "true"));
        regTempl("pressure_change_alert", PowerCategory.ENHANCED_SENSES, PowerType.PASSIVE, 0, "weather_forecast_alert", Map.of("earlyTicks", "1200"));
        regTempl("neural_overclock", PowerCategory.ENHANCED_SENSES, PowerType.ACTIVE, 180, "all_senses_max", Map.of("duration", "100"));
    }

    // 22. SPECIAL VISION (12)
    private static void registerSpecialVisionPowers() {
        regFunc("night_vision_eyes", PowerCategory.SPECIAL_VISION, PowerType.PASSIVE, 0, new NightVisionComponent(300), List.of());
        regFunc("thermal_scan", PowerCategory.SPECIAL_VISION, PowerType.ACTIVE, 60, new ThermalVisionComponent(36.0D, 180), List.of());
        regFunc("infrared_toggle", PowerCategory.SPECIAL_VISION, PowerType.TOGGLE, 0, new NightVisionComponent(400), List.of());

        regTempl("telescopic_zoom", PowerCategory.SPECIAL_VISION, PowerType.TOGGLE, 0, "fov_zoom_magnify", Map.of("zoomLevel", "4x"));
        regTempl("xray_spectral_gaze", PowerCategory.SPECIAL_VISION, PowerType.TOGGLE, 0, "ore_block_translucency", Map.of("depth", "8"));
        regTempl("ultraviolet_spectrum", PowerCategory.SPECIAL_VISION, PowerType.TOGGLE, 0, "reveal_invisible_and_footprints", Map.of("active", "true"));
        regTempl("electromagnetic_sight", PowerCategory.SPECIAL_VISION, PowerType.TOGGLE, 0, "see_redstone_circuits", Map.of("range", "20.0"));
        regTempl("astral_sight", PowerCategory.SPECIAL_VISION, PowerType.TOGGLE, 0, "see_spirits_and_phantoms", Map.of("active", "true"));
        regTempl("microscopic_inspection", PowerCategory.SPECIAL_VISION, PowerType.ACTIVE, 40, "inspect_target_nbt", Map.of("inspect", "health_armor"));
        regTempl("true_sight", PowerCategory.SPECIAL_VISION, PowerType.ACTIVE, 160, "pierce_all_illusions", Map.of("duration", "200"));
        regTempl("predator_hud", PowerCategory.SPECIAL_VISION, PowerType.TOGGLE, 0, "target_lock_crosshair", Map.of("autoTrack", "true"));
        regTempl("chronological_sight", PowerCategory.SPECIAL_VISION, PowerType.ACTIVE, 240, "see_past_movements", Map.of("trailLengthSeconds", "10"));
    }

    // 23. CAMOUFLAGE (12)
    private static void registerCamouflagePowers() {
        regFunc("chameleon_dash", PowerCategory.CAMOUFLAGE, PowerType.ACTIVE, 50, new DashComponent(1.6D, 0.05D), List.of());
        regFunc("stealth_strike", PowerCategory.CAMOUFLAGE, PowerType.ACTIVE, 40, new DamageActionComponent(10.0F, 3.5D, "physical"), List.of());

        regTempl("adaptive_skin_camouflage", PowerCategory.CAMOUFLAGE, PowerType.TOGGLE, 0, "blend_into_background_block", Map.of("standStill", "true"));
        regFunc("refractive_invisibility", PowerCategory.CAMOUFLAGE, PowerType.ACTIVE, 120, new InvisibilityComponent(100), List.of());
        regTempl("smoke_screen", PowerCategory.CAMOUFLAGE, PowerType.ACTIVE, 80, "thick_smoke_drop", Map.of("radius", "6.0"));
        regTempl("silhouette_mimic", PowerCategory.CAMOUFLAGE, PowerType.ACTIVE, 160, "appear_as_neutral_mob", Map.of("mobType", "villager"));
        regTempl("heat_masking", PowerCategory.CAMOUFLAGE, PowerType.PASSIVE, 0, "hidden_from_thermal_sight", Map.of("masked", "true"));
        regTempl("scent_suppressor", PowerCategory.CAMOUFLAGE, PowerType.PASSIVE, 0, "undetectable_by_canines", Map.of("masked", "true"));
        regTempl("shadow_blend", PowerCategory.CAMOUFLAGE, PowerType.TOGGLE, 0, "invisible_in_darkness", Map.of("lightThreshold", "5"));
        regTempl("cardboard_disguise", PowerCategory.CAMOUFLAGE, PowerType.TOGGLE, 0, "box_props_disguise", Map.of("sneaking", "true"));
        regTempl("flash_escape", PowerCategory.CAMOUFLAGE, PowerType.ACTIVE, 70, "flash_and_vanish", Map.of("distance", "10.0"));
        regTempl("quantum_cloaking", PowerCategory.CAMOUFLAGE, PowerType.SUSTAINED, 0, "total_optical_invisibility", Map.of("costPerTick", "medium"));
    }

    // 24. METAMORPHOSIS (12)
    private static void registerMetamorphosisPowers() {
        regFunc("beast_leap", PowerCategory.METAMORPHOSIS, PowerType.ACTIVE, 40, new LeapComponent(1.6D, 1.4D), List.of());
        regFunc("feral_swipe", PowerCategory.METAMORPHOSIS, PowerType.ACTIVE, 30, new DamageActionComponent(9.0F, 4.0D, "physical"), List.of());

        regTempl("lycan_form", PowerCategory.METAMORPHOSIS, PowerType.TOGGLE, 300, "wolf_transformation", Map.of("statBonus", "high"));
        regTempl("avian_metamorphosis", PowerCategory.METAMORPHOSIS, PowerType.TOGGLE, 200, "hawk_form_flight", Map.of("speed", "1.4"));
        regTempl("aquatic_chimera", PowerCategory.METAMORPHOSIS, PowerType.TOGGLE, 0, "gills_and_fins", Map.of("swimSpeed", "1.8"));
        regTempl("ursine_resilience", PowerCategory.METAMORPHOSIS, PowerType.TOGGLE, 200, "bear_health_pool", Map.of("tempHealth", "20"));
        regTempl("serpentine_slither", PowerCategory.METAMORPHOSIS, PowerType.TOGGLE, 0, "crawl_through_1block", Map.of("hitboxHeight", "0.6"));
        regTempl("insectoid_carapace", PowerCategory.METAMORPHOSIS, PowerType.TOGGLE, 0, "chitinous_plating", Map.of("armor", "12"));
        regTempl("draconic_awakening", PowerCategory.METAMORPHOSIS, PowerType.ACTIVE, 600, "dragon_hybrid_form", Map.of("duration", "400"));
        regTempl("arachnid_limbs", PowerCategory.METAMORPHOSIS, PowerType.TOGGLE, 0, "wall_climbing_spider", Map.of("climbWalls", "true"));
        regTempl("amphibian_spring", PowerCategory.METAMORPHOSIS, PowerType.PASSIVE, 0, "high_jump_frog", Map.of("jumpBoost", "2.0"));
        regTempl("polymorphic_reversion", PowerCategory.METAMORPHOSIS, PowerType.ACTIVE, 20, "return_to_human", Map.of("cleanse", "true"));
    }

    // 25. CLONES (12)
    private static void registerClonesPowers() {
        regFunc("clone_dash_decoy", PowerCategory.CLONES, PowerType.ACTIVE, 60, new DashComponent(1.8D, 0.1D), List.of());
        regFunc("decoy_burst", PowerCategory.CLONES, PowerType.ACTIVE, 50, new KnockbackActionComponent(6.0D, 1.5D), List.of());

        regTempl("mirror_duplication", PowerCategory.CLONES, PowerType.ACTIVE, 120, "spawn_decoy_mob", Map.of("decoyCount", "2"));
        regTempl("shadow_army", PowerCategory.CLONES, PowerType.CHARGED, 360, "multiple_shadow_fighters", Map.of("armySize", "5"));
        regTempl("swap_with_clone", PowerCategory.CLONES, PowerType.ACTIVE, 40, "instant_clone_swap", Map.of("range", "24.0"));
        regTempl("exploding_clone", PowerCategory.CLONES, PowerType.ACTIVE, 100, "clone_detonation_on_death", Map.of("blastRadius", "3.0"));
        regTempl("hologram_mimicry", PowerCategory.CLONES, PowerType.ACTIVE, 80, "stationary_visual_ghost", Map.of("duration", "160"));
        regTempl("echo_strikes", PowerCategory.CLONES, PowerType.PASSIVE, 0, "after_image_follows_attack", Map.of("extraHitDmg", "4"));
        regTempl("quantum_twinning", PowerCategory.CLONES, PowerType.ACTIVE, 240, "independent_ai_twin", Map.of("twinHp", "30"));
        regTempl("clone_shield_wall", PowerCategory.CLONES, PowerType.ACTIVE, 150, "line_of_decoys_blocking", Map.of("decoyCount", "4"));
        regTempl("mirage_feint", PowerCategory.CLONES, PowerType.ACTIVE, 30, "fake_dash_animation", Map.of("confuseMobs", "true"));
        regTempl("fractal_multiplication", PowerCategory.CLONES, PowerType.ACTIVE, 300, "exponential_split", Map.of("splits", "3"));
    }

    // 26. SUMMONING (12)
    private static void registerSummoningPowers() {
        regFunc("lightning_elemental_strike", PowerCategory.SUMMONING, PowerType.ACTIVE, 80, new LightningActionComponent(24.0D), List.of());
        regFunc("healing_wisp", PowerCategory.SUMMONING, PowerType.ACTIVE, 90, new HealActionComponent(8.0F, false), List.of());

        regTempl("summon_golem_sentinel", PowerCategory.SUMMONING, PowerType.ACTIVE, 300, "spawn_allied_golem", Map.of("golemType", "iron"));
        regFunc("spectral_wolf_pack", PowerCategory.SUMMONING, PowerType.ACTIVE, 180, new SummonEntityComponent(3, 2.5D), List.of());
        regTempl("flame_elemental", PowerCategory.SUMMONING, PowerType.ACTIVE, 200, "spawn_fire_spirit", Map.of("burnEnemies", "true"));
        regTempl("undead_minion_raise", PowerCategory.SUMMONING, PowerType.ACTIVE, 140, "resurrect_skeleton", Map.of("lifespan", "300"));
        regTempl("astral_familiar", PowerCategory.SUMMONING, PowerType.TOGGLE, 0, "permanent_pet_scout", Map.of("petType", "raven"));
        regTempl("swarm_of_locusts", PowerCategory.SUMMONING, PowerType.ACTIVE, 120, "locust_cloud_drain", Map.of("dps", "5"));
        regTempl("crystal_warden", PowerCategory.SUMMONING, PowerType.ACTIVE, 240, "spawn_defensive_warden", Map.of("barrierRadius", "6.0"));
        regTempl("shadow_stalker_summon", PowerCategory.SUMMONING, PowerType.ACTIVE, 160, "spawn_assassin_shadow", Map.of("targetPrioritize", "low_hp"));
        regTempl("storm_herald", PowerCategory.SUMMONING, PowerType.ACTIVE, 280, "thunder_djinn", Map.of("chainBolts", "true"));
        regTempl("dismiss_all_summons", PowerCategory.SUMMONING, PowerType.ACTIVE, 10, "despawn_allied_creatures", Map.of("instant", "true"));
    }

    // 27. TRAPS (12)
    private static void registerTrapsPowers() {
        regFunc("kinetic_mine", PowerCategory.TRAPS, PowerType.ACTIVE, 50, new ExplosionActionComponent(3.0F, false, false), List.of());
        regFunc("frost_trap_burst", PowerCategory.TRAPS, PowerType.ACTIVE, 60, new AreaEffectActionComponent("freeze", 120, 1, 6.0D), List.of());

        regTempl("gravity_snare_trap", PowerCategory.TRAPS, PowerType.ACTIVE, 70, "place_invisible_snare", Map.of("rootTicks", "80"));
        regTempl("shock_wire", PowerCategory.TRAPS, PowerType.ACTIVE, 80, "tripwire_lightning", Map.of("lineLength", "6"));
        regTempl("spike_carpet", PowerCategory.TRAPS, PowerType.ACTIVE, 60, "ground_puncture_trap", Map.of("bleedDmg", "8"));
        regTempl("paralytic_gas_mine", PowerCategory.TRAPS, PowerType.ACTIVE, 90, "aoe_sleep_powder", Map.of("radius", "5.0"));
        regTempl("spatial_teleport_trap", PowerCategory.TRAPS, PowerType.ACTIVE, 120, "relocate_stepper", Map.of("teleportDistance", "20.0"));
        regTempl("flame_rune", PowerCategory.TRAPS, PowerType.ACTIVE, 60, "inscribed_fire_glyph", Map.of("fireDuration", "120"));
        regTempl("emp_mine", PowerCategory.TRAPS, PowerType.ACTIVE, 100, "tech_disable_trap", Map.of("stunTech", "true"));
        regTempl("tether_anchor_trap", PowerCategory.TRAPS, PowerType.ACTIVE, 80, "leash_stepper_to_block", Map.of("leashRadius", "4.0"));
        regTempl("disorient_flash_trap", PowerCategory.TRAPS, PowerType.ACTIVE, 50, "blind_and_nausea", Map.of("duration", "100"));
        regTempl("proximity_sensor_trap", PowerCategory.TRAPS, PowerType.ACTIVE, 40, "sound_alert_when_triggered", Map.of("soundAlert", "true"));
    }

    // 28. CROWD CONTROL (12)
    private static void registerCrowdControlPowers() {
        regFunc("staggering_wave", PowerCategory.CROWD_CONTROL, PowerType.ACTIVE, 40, new KnockbackActionComponent(7.0D, 2.0D), List.of());
        regFunc("cryo_lockdown", PowerCategory.CROWD_CONTROL, PowerType.ACTIVE, 60, new AreaEffectActionComponent("freeze", 140, 2, 6.0D), List.of());
        regFunc("sonic_repulsion", PowerCategory.CROWD_CONTROL, PowerType.ACTIVE, 50, new KnockbackActionComponent(9.0D, 2.2D), List.of());

        regTempl("mass_tether", PowerCategory.CROWD_CONTROL, PowerType.ACTIVE, 100, "rope_all_nearby_mobs", Map.of("radius", "8.0"));
        regTempl("gravity_well_hold", PowerCategory.CROWD_CONTROL, PowerType.ACTIVE, 120, "implosive_containment", Map.of("radius", "6.0"));
        regFunc("concussion_flash", PowerCategory.CROWD_CONTROL, PowerType.ACTIVE, 70,
                new StunComponent(8.0D, 40, 12), List.of());
        regTempl("whirlwind_lift", PowerCategory.CROWD_CONTROL, PowerType.ACTIVE, 90, "levitate_group", Map.of("height", "8.0"));
        regTempl("stone_entombment", PowerCategory.CROWD_CONTROL, PowerType.ACTIVE, 150, "encase_in_rock", Map.of("duration", "100"));
        regTempl("mind_pacification", PowerCategory.CROWD_CONTROL, PowerType.ACTIVE, 160, "halt_all_aggro", Map.of("duration", "120"));
        regTempl("time_slow_bubble", PowerCategory.CROWD_CONTROL, PowerType.ACTIVE, 200, "slow_motion_dome", Map.of("slowPct", "75"));
        regTempl("magnetic_disarm", PowerCategory.CROWD_CONTROL, PowerType.ACTIVE, 80, "drop_weapons", Map.of("dropChance", "0.5"));
        regTempl("chain_immobilize", PowerCategory.CROWD_CONTROL, PowerType.ACTIVE, 110, "shackles_jump_to_allies", Map.of("maxChains", "5"));
    }

    // 29. TECH (12)
    private static void registerTechPowers() {
        regFunc("cybernetic_speed", PowerCategory.TECH, PowerType.PASSIVE, 0,
                new AttributeModifierComponent("movement_speed", 0.12D, AttributeModifier.Operation.ADDITION), List.of());
        regFunc("nanite_heal_injection", PowerCategory.TECH, PowerType.ACTIVE, 60, new HealActionComponent(8.0F, true), List.of());

        regTempl("overclock_servos", PowerCategory.TECH, PowerType.ACTIVE, 100, "temporary_speed_boost", Map.of("boost", "50%"));
        regTempl("hud_target_locking", PowerCategory.TECH, PowerType.TOGGLE, 0, "auto_aim_crosshair", Map.of("lockRange", "24.0"));
        regTempl("emp_pulse_emitter", PowerCategory.TECH, PowerType.ACTIVE, 120, "disable_electronics", Map.of("radius", "10.0"));
        regTempl("drone_recon", PowerCategory.TECH, PowerType.ACTIVE, 150, "summon_scout_drone", Map.of("droneSpeed", "1.2"));
        regTempl("integrated_repulsor", PowerCategory.TECH, PowerType.ACTIVE, 40, "hand_repulsor_blast", Map.of("damage", "8"));
        regTempl("energy_shield_generator", PowerCategory.TECH, PowerType.ACTIVE, 120, "hardlight_shield", Map.of("absorption", "10"));
        regTempl("jetpack_stabilizer", PowerCategory.TECH, PowerType.TOGGLE, 0, "fuel_hover_glide", Map.of("hoverSpeed", "0.6"));
        regTempl("railgun_overcharge", PowerCategory.TECH, PowerType.CHARGED, 180, "piercing_energy_slug", Map.of("pierceBlocks", "3"));
        regTempl("nanite_swarm_deploy", PowerCategory.TECH, PowerType.ACTIVE, 200, "corrosive_nanite_cloud", Map.of("dps", "7"));
        regTempl("system_reboot", PowerCategory.TECH, PowerType.ACTIVE, 300, "clear_all_cooldowns_small", Map.of("reduction", "30%"));
    }

    // 30. COSMIC (12)
    private static void registerCosmicPowers() {
        regFunc("stellar_dash", PowerCategory.COSMIC, PowerType.ACTIVE, 40, new DashComponent(2.0D, 0.15D), List.of());
        regFunc("supernova_blast", PowerCategory.COSMIC, PowerType.ACTIVE, 80, new DamageActionComponent(14.0F, 7.0D, "magic"), List.of());

        regTempl("starlight_teleport", PowerCategory.COSMIC, PowerType.ACTIVE, 70, "cosmic_warp", Map.of("distance", "25.0"));
        regTempl("black_hole_seed", PowerCategory.COSMIC, PowerType.CHARGED, 300, "gravitational_collapse", Map.of("radius", "14.0"));
        regTempl("solar_radiation_aura", PowerCategory.COSMIC, PowerType.TOGGLE, 0, "continuous_cosmic_burn", Map.of("burnDps", "5"));
        regTempl("cosmic_awareness", PowerCategory.COSMIC, PowerType.TOGGLE, 0, "dimension_wide_player_sense", Map.of("active", "true"));
        regTempl("meteor_call", PowerCategory.COSMIC, PowerType.ACTIVE, 240, "strike_meteor_from_sky", Map.of("blastRadius", "6.0"));
        regTempl("nebula_shield", PowerCategory.COSMIC, PowerType.ACTIVE, 160, "absorb_cosmic_energy", Map.of("hearts", "12"));
        regTempl("pulsar_beam", PowerCategory.COSMIC, PowerType.SUSTAINED, 0, "rapid_cosmic_ray", Map.of("pulseRate", "0.2s"));
        regTempl("antimatter_touch", PowerCategory.COSMIC, PowerType.ACTIVE, 180, "heavy_disintegration", Map.of("damage", "30"));
        regTempl("cosmic_flight_drift", PowerCategory.COSMIC, PowerType.TOGGLE, 0, "weightless_interstellar_drift", Map.of("speed", "1.6"));
        regTempl("quantum_annihilation", PowerCategory.COSMIC, PowerType.CHARGED, 400, "obliterate_target", Map.of("executeThreshold", "25%"));
    }

    // 31. PSYCHIC (12)
    private static void registerPsychicPowers() {
        regFunc("mind_blast", PowerCategory.PSYCHIC, PowerType.ACTIVE, 40, new DamageActionComponent(9.0F, 5.0D, "magic"), List.of());
        regFunc("psychic_push", PowerCategory.PSYCHIC, PowerType.ACTIVE, 30, new KnockbackActionComponent(7.0D, 1.8D), List.of());

        regTempl("telepathic_link", PowerCategory.PSYCHIC, PowerType.ACTIVE, 60, "communicate_silently", Map.of("range", "128.0"));
        regTempl("mind_control_feint", PowerCategory.PSYCHIC, PowerType.ACTIVE, 160, "confuse_hostile_mob", Map.of("targetAllies", "true"));
        regTempl("psionic_barrier", PowerCategory.PSYCHIC, PowerType.ACTIVE, 100, "mental_forcefield", Map.of("absorption", "8"));
        regTempl("memory_wipe", PowerCategory.PSYCHIC, PowerType.ACTIVE, 140, "mob_forgets_player", Map.of("clearTarget", "true"));
        regTempl("hallucination_trap", PowerCategory.PSYCHIC, PowerType.ACTIVE, 120, "phantom_attacker_illusions", Map.of("count", "3"));
        regTempl("psionic_blade", PowerCategory.PSYCHIC, PowerType.ACTIVE, 50, "mental_sword_slash", Map.of("ignoreArmor", "true"));
        regTempl("clairvoyance_scout", PowerCategory.PSYCHIC, PowerType.ACTIVE, 200, "astral_camera_scout", Map.of("duration", "160"));
        regTempl("neural_shock", PowerCategory.PSYCHIC, PowerType.ACTIVE, 80, "paralyze_brain", Map.of("stunTicks", "50"));
        regTempl("empathic_shield", PowerCategory.PSYCHIC, PowerType.TOGGLE, 0, "reflect_status_effects", Map.of("reflectDebuffs", "true"));
        regTempl("psychic_drain", PowerCategory.PSYCHIC, PowerType.SUSTAINED, 0, "drain_hunger_and_health", Map.of("rate", "2/s"));
    }

    // 32. MELEE (12)
    private static void registerMeleePowers() {
        regFunc("brawler_strength", PowerCategory.MELEE, PowerType.PASSIVE, 0,
                new AttributeModifierComponent("attack_damage", 6.0D, AttributeModifier.Operation.ADDITION), List.of());
        regFunc("haymaker_punch", PowerCategory.MELEE, PowerType.ACTIVE, 40, new DamageActionComponent(11.0F, 3.5D, "physical"), List.of());
        regFunc("uppercut_leap", PowerCategory.MELEE, PowerType.ACTIVE, 50, new LeapComponent(0.6D, 1.7D), List.of());

        regTempl("flurry_of_blows", PowerCategory.MELEE, PowerType.ACTIVE, 60, "rapid_fist_strikes", Map.of("hitCount", "5"));
        regTempl("sweep_kick", PowerCategory.MELEE, PowerType.ACTIVE, 30, "trip_multiple_targets", Map.of("knockdownTicks", "30"));
        regTempl("parry_counter", PowerCategory.MELEE, PowerType.ACTIVE, 50, "block_and_counter_punch", Map.of("counterMult", "2.0"));
        regTempl("armored_knuckles", PowerCategory.MELEE, PowerType.TOGGLE, 0, "fist_armor_bonus", Map.of("punchBonus", "4"));
        regTempl("grapple_takedown", PowerCategory.MELEE, PowerType.ACTIVE, 80, "pin_target_to_ground", Map.of("duration", "40"));
        regTempl("roundhouse_blast", PowerCategory.MELEE, PowerType.ACTIVE, 40, "360_degree_kick", Map.of("knockback", "2.2"));
        regTempl("iron_body_stance", PowerCategory.MELEE, PowerType.TOGGLE, 0, "immovable_stance", Map.of("knockbackImmune", "true"));
        regTempl("critical_focus", PowerCategory.MELEE, PowerType.ACTIVE, 100, "guaranteed_crit_window", Map.of("critDuration", "80"));
        regTempl("shattering_elbow", PowerCategory.MELEE, PowerType.ACTIVE, 60, "armor_reduction_blow", Map.of("armorDownPct", "40"));
    }

    // 33. ENERGY WEAPONS (12)
    private static void registerEnergyWeaponsPowers() {
        regFunc("energy_shot", PowerCategory.ENERGY_WEAPONS, PowerType.ACTIVE, 30, new DamageActionComponent(9.0F, 8.0D, "magic"), List.of());
        regFunc("plasma_grenade", PowerCategory.ENERGY_WEAPONS, PowerType.ACTIVE, 60, new ExplosionActionComponent(2.8F, false, false), List.of());

        regTempl("photon_carbine", PowerCategory.ENERGY_WEAPONS, PowerType.SUSTAINED, 0, "rapid_energy_bolts", Map.of("rateOfFire", "10/s"));
        regTempl("plasma_shotgun", PowerCategory.ENERGY_WEAPONS, PowerType.ACTIVE, 40, "spread_pellet_energy", Map.of("pellets", "8"));
        regTempl("arc_cannon", PowerCategory.ENERGY_WEAPONS, PowerType.CHARGED, 120, "charged_shock_sphere", Map.of("chargeMax", "3s"));
        regTempl("disruptor_pistol", PowerCategory.ENERGY_WEAPONS, PowerType.ACTIVE, 25, "shield_piercing_beam", Map.of("pierceShields", "true"));
        regTempl("thermal_lance", PowerCategory.ENERGY_WEAPONS, PowerType.SUSTAINED, 0, "continuous_cutting_laser", Map.of("dps", "12"));
        regTempl("singularity_launcher", PowerCategory.ENERGY_WEAPONS, PowerType.ACTIVE, 220, "gravity_projectile", Map.of("pullRadius", "6.0"));
        regTempl("laser_sniper_rifle", PowerCategory.ENERGY_WEAPONS, PowerType.CHARGED, 140, "hitscan_high_damage", Map.of("damage", "24"));
        regTempl("plasma_sword_blade", PowerCategory.ENERGY_WEAPONS, PowerType.TOGGLE, 0, "summon_energy_blade", Map.of("bladeDamage", "12"));
        regTempl("seeking_missiles", PowerCategory.ENERGY_WEAPONS, PowerType.ACTIVE, 100, "homing_energy_rockets", Map.of("missileCount", "4"));
        regTempl("flak_burst_cannon", PowerCategory.ENERGY_WEAPONS, PowerType.ACTIVE, 70, "airburst_shrapnel", Map.of("burstRadius", "7.0"));
    }

    // 34. REACTIVE DEFENSE (12)
    private static void registerReactiveDefensePowers() {
        regFunc("reactive_shield", PowerCategory.REACTIVE_DEFENSE, PowerType.ACTIVE, 40, new ShieldActionComponent(8.0F, 120), List.of());
        regFunc("thorns_retaliation", PowerCategory.REACTIVE_DEFENSE, PowerType.PASSIVE, 0, new DamageMitigationComponent(0.3F, "all"), List.of());
        regFunc("emergency_teleport", PowerCategory.REACTIVE_DEFENSE, PowerType.ACTIVE, 80, new BlinkComponent(14.0D), List.of());

        regTempl("impact_absorption_barrier", PowerCategory.REACTIVE_DEFENSE, PowerType.PASSIVE, 0, "store_damage_as_power", Map.of("ratio", "0.5"));
        regTempl("flash_parry", PowerCategory.REACTIVE_DEFENSE, PowerType.ACTIVE, 30, "stun_attacker_on_timed_block", Map.of("window", "10 ticks"));
        regTempl("kinetic_recoil_blast", PowerCategory.REACTIVE_DEFENSE, PowerType.ACTIVE, 50, "blast_away_on_hit", Map.of("knockback", "2.0"));
        regTempl("smoke_auto_deploy", PowerCategory.REACTIVE_DEFENSE, PowerType.PASSIVE, 160, "drop_smoke_when_low_hp", Map.of("hpThreshold", "6"));
        regTempl("nanite_instant_patch", PowerCategory.REACTIVE_DEFENSE, PowerType.PASSIVE, 240, "heal_instantly_on_fatal", Map.of("healHp", "8"));
        regTempl("temporal_rewind_dodge", PowerCategory.REACTIVE_DEFENSE, PowerType.ACTIVE, 120, "rewind_position_3s", Map.of("seconds", "3"));
        regTempl("deflective_carapace", PowerCategory.REACTIVE_DEFENSE, PowerType.PASSIVE, 0, "ricochet_arrows", Map.of("ricochetPct", "50"));
        regTempl("static_retaliation", PowerCategory.REACTIVE_DEFENSE, PowerType.PASSIVE, 0, "shock_attacker_with_lightning", Map.of("chance", "0.3"));
        regTempl("ice_cocoon_emergency", PowerCategory.REACTIVE_DEFENSE, PowerType.ACTIVE, 300, "invulnerable_ice_shell", Map.of("duration", "80"));
    }

    // 35. ENVIRONMENTAL PASSIVE (12)
    private static void registerEnvironmentalPassivePowers() {
        regFunc("feather_fall_passive", PowerCategory.ENVIRONMENTAL_PASSIVE, PowerType.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fall"), List.of());
        regFunc("lava_skin", PowerCategory.ENVIRONMENTAL_PASSIVE, PowerType.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fire"), List.of());
        regFunc("abyssal_lung", PowerCategory.ENVIRONMENTAL_PASSIVE, PowerType.PASSIVE, 0, new WaterAdaptationComponent(true, false), List.of());

        regTempl("freezing_adaptation", PowerCategory.ENVIRONMENTAL_PASSIVE, PowerType.PASSIVE, 0, "immune_to_powder_snow", Map.of("powderSnowImmune", "true"));
        regTempl("radiation_filter", PowerCategory.ENVIRONMENTAL_PASSIVE, PowerType.PASSIVE, 0, "immune_to_wither_and_poison", Map.of("cleanse", "true"));
        regTempl("atmospheric_equalizer", PowerCategory.ENVIRONMENTAL_PASSIVE, PowerType.PASSIVE, 0, "breathe_in_vacuum", Map.of("spaceBreath", "true"));
        regTempl("kinetic_grounding", PowerCategory.ENVIRONMENTAL_PASSIVE, PowerType.PASSIVE, 0, "immune_to_lightning_damage", Map.of("grounded", "true"));
        regTempl("toxic_neutralizer", PowerCategory.ENVIRONMENTAL_PASSIVE, PowerType.PASSIVE, 0, "convert_poisons_to_healing", Map.of("poisonToRegen", "true"));
        regTempl("dense_gravity_adaptation", PowerCategory.ENVIRONMENTAL_PASSIVE, PowerType.PASSIVE, 0, "no_slowness_from_heavy_weight", Map.of("active", "true"));
        regTempl("heatwave_resilience", PowerCategory.ENVIRONMENTAL_PASSIVE, PowerType.PASSIVE, 0, "desert_heat_resistance", Map.of("waterLoss", "zero"));
        regTempl("deep_dark_silence", PowerCategory.ENVIRONMENTAL_PASSIVE, PowerType.PASSIVE, 0, "warden_blind_to_player", Map.of("vibrationFree", "true"));
        regTempl("super_metabolism", PowerCategory.ENVIRONMENTAL_PASSIVE, PowerType.PASSIVE, 0, "infinite_food_efficiency", Map.of("hungerDrainRatio", "0.2"));
    }

    // 36. EXPLORATION (12)
    private static void registerExplorationPowers() {
        regFunc("mountaineer_leap", PowerCategory.EXPLORATION, PowerType.ACTIVE, 40, new LeapComponent(1.4D, 1.8D), List.of());
        regFunc("expedition_dash", PowerCategory.EXPLORATION, PowerType.ACTIVE, 30, new DashComponent(1.8D, 0.1D), List.of());
        regFunc("night_explorer", PowerCategory.EXPLORATION, PowerType.PASSIVE, 0, new NightVisionComponent(300), List.of());

        regTempl("step_assist_legs", PowerCategory.EXPLORATION, PowerType.PASSIVE, 0, "step_up_1_block", Map.of("stepHeight", "1.25"));
        regTempl("dowsing_sense", PowerCategory.EXPLORATION, PowerType.ACTIVE, 80, "locate_water_and_lava", Map.of("range", "32.0"));
        regTempl("ore_resonance", PowerCategory.EXPLORATION, PowerType.ACTIVE, 100, "chime_near_valuable_ores", Map.of("range", "16.0"));
        regTempl("structure_compass", PowerCategory.EXPLORATION, PowerType.ACTIVE, 200, "point_to_nearest_dungeon", Map.of("searchRadius", "500"));
        regTempl("grappling_hook_power", PowerCategory.EXPLORATION, PowerType.ACTIVE, 50, "pull_player_to_block", Map.of("maxCable", "24.0"));
        regTempl("pathfinder_stride", PowerCategory.EXPLORATION, PowerType.TOGGLE, 0, "no_slowness_from_webs_mud", Map.of("ignoreTerrainSlow", "true"));
        regTempl("camp_beacon", PowerCategory.EXPLORATION, PowerType.ACTIVE, 300, "summon_rest_aura", Map.of("duration", "400"));
        regTempl("elytra_propulsion", PowerCategory.EXPLORATION, PowerType.ACTIVE, 60, "infinite_firework_boost", Map.of("boostStrength", "1.5"));
        regTempl("geological_scan", PowerCategory.EXPLORATION, PowerType.ACTIVE, 120, "display_cave_hollows", Map.of("scanRadius", "24.0"));
    }

    // 37. AQUATIC SPECIALIZED (12)
    private static void registerAquaticSpecializedPowers() {
        regFunc("atlantean_sovereignty", PowerCategory.AQUATIC_SPECIALIZED, PowerType.PASSIVE, 0, new WaterAdaptationComponent(true, true), List.of());
        regFunc("hydro_torpedo", PowerCategory.AQUATIC_SPECIALIZED, PowerType.ACTIVE, 40, new DashComponent(2.4D, 0.2D), List.of());
        regFunc("abyssal_pulse", PowerCategory.AQUATIC_SPECIALIZED, PowerType.ACTIVE, 50, new KnockbackActionComponent(8.0D, 2.0D), List.of());

        regTempl("sub_surface_sonar", PowerCategory.AQUATIC_SPECIALIZED, PowerType.ACTIVE, 60, "detect_underwater_mobs", Map.of("range", "48.0"));
        regTempl("trident_mastery", PowerCategory.AQUATIC_SPECIALIZED, PowerType.PASSIVE, 0, "riptide_boost_in_any_water", Map.of("bonus", "true"));
        regTempl("crushing_depths", PowerCategory.AQUATIC_SPECIALIZED, PowerType.ACTIVE, 90, "water_vortex_damage", Map.of("damage", "15"));
        regTempl("conduit_beacon_link", PowerCategory.AQUATIC_SPECIALIZED, PowerType.PASSIVE, 0, "permanent_conduit_anywhere", Map.of("global", "true"));
        regTempl("aqua_jet", PowerCategory.AQUATIC_SPECIALIZED, PowerType.ACTIVE, 30, "fast_water_leap", Map.of("exitWaterSpeed", "2.0"));
        regTempl("boiling_sea", PowerCategory.AQUATIC_SPECIALIZED, PowerType.ACTIVE, 140, "heat_surrounding_water", Map.of("scaldRadius", "8.0"));
        regTempl("iceberg_creation", PowerCategory.AQUATIC_SPECIALIZED, PowerType.ACTIVE, 120, "freeze_ocean_surface", Map.of("radius", "10.0"));
        regTempl("siren_call", PowerCategory.AQUATIC_SPECIALIZED, PowerType.ACTIVE, 160, "lure_marine_life", Map.of("attractRadius", "20.0"));
        regTempl("water_skin_shield", PowerCategory.AQUATIC_SPECIALIZED, PowerType.TOGGLE, 0, "physical_damage_dampening_water", Map.of("reduction", "0.4"));
    }

    // 38. BODY TRANSFORMATION (12)
    private static void registerBodyTransformationPowers() {
        regFunc("titan_mass_form", PowerCategory.BODY_TRANSFORMATION, PowerType.TOGGLE, 100,
                new AttributeModifierComponent("attack_damage", 10.0D, AttributeModifier.Operation.ADDITION), List.of());
        regFunc("graphene_hardening", PowerCategory.BODY_TRANSFORMATION, PowerType.PASSIVE, 0,
                new AttributeModifierComponent("armor", 12.0D, AttributeModifier.Operation.ADDITION), List.of());

        regTempl("elastic_stretch", PowerCategory.BODY_TRANSFORMATION, PowerType.ACTIVE, 40, "extended_reach_punch", Map.of("reachBonus", "4.0"));
        regTempl("metal_skin_heavy", PowerCategory.BODY_TRANSFORMATION, PowerType.TOGGLE, 0, "iron_body_form", Map.of("armor", "15", "speed", "-0.05"));
        regTempl("liquid_state", PowerCategory.BODY_TRANSFORMATION, PowerType.TOGGLE, 0, "immune_to_projectiles", Map.of("projectileImmune", "true"));
        regTempl("gaseous_cloud", PowerCategory.BODY_TRANSFORMATION, PowerType.TOGGLE, 0, "fly_through_slits", Map.of("density", "zero"));
        regTempl("bone_spikes", PowerCategory.BODY_TRANSFORMATION, PowerType.ACTIVE, 50, "protrude_skeletal_spikes", Map.of("damage", "8"));
        regTempl("dense_musculature", PowerCategory.BODY_TRANSFORMATION, PowerType.PASSIVE, 0, "carry_capacity_and_knockback_res", Map.of("knockbackRes", "1.0"));
        regTempl("cellular_shrinkage", PowerCategory.BODY_TRANSFORMATION, PowerType.TOGGLE, 0, "ant_size_transformation", Map.of("scale", "0.2"));
        regTempl("gigantism_growth", PowerCategory.BODY_TRANSFORMATION, PowerType.TOGGLE, 0, "giant_size_transformation", Map.of("scale", "3.0"));
        regTempl("shadow_flesh", PowerCategory.BODY_TRANSFORMATION, PowerType.TOGGLE, 0, "semi_intangible_form", Map.of("phaseBlocks", "true"));
        regTempl("crystal_lattice_form", PowerCategory.BODY_TRANSFORMATION, PowerType.ACTIVE, 180, "light_refracting_skin", Map.of("reflectLasers", "true"));
    }

    // 39. AREA CONTROL (12)
    private static void registerAreaControlPowers() {
        regFunc("seismic_disruption", PowerCategory.AREA_CONTROL, PowerType.ACTIVE, 80, new DamageActionComponent(10.0F, 8.0D, "physical"), List.of());
        regFunc("concussive_pulse_dome", PowerCategory.AREA_CONTROL, PowerType.ACTIVE, 60, new KnockbackActionComponent(9.0D, 2.2D), List.of());

        regTempl("sanctuary_ward", PowerCategory.AREA_CONTROL, PowerType.ACTIVE, 200, "prevent_mob_entry_zone", Map.of("radius", "10.0"));
        regTempl("toxic_fumes_zone", PowerCategory.AREA_CONTROL, PowerType.ACTIVE, 120, "poison_cloud_ground", Map.of("dps", "4"));
        regTempl("gravity_well_center", PowerCategory.AREA_CONTROL, PowerType.ACTIVE, 140, "draw_all_towards_center", Map.of("radius", "12.0"));
        regTempl("consecrated_ground", PowerCategory.AREA_CONTROL, PowerType.ACTIVE, 180, "damage_undead_in_zone", Map.of("holyDmg", "8"));
        regTempl("nullification_field", PowerCategory.AREA_CONTROL, PowerType.TOGGLE, 0, "suppress_powers_in_area", Map.of("radius", "8.0"));
        regTempl("wind_vortex_zone", PowerCategory.AREA_CONTROL, PowerType.ACTIVE, 100, "spinning_projectile_deflector", Map.of("radius", "7.0"));
        regTempl("flash_freeze_lake", PowerCategory.AREA_CONTROL, PowerType.ACTIVE, 110, "freeze_water_radius", Map.of("radius", "16.0"));
        regTempl("quarantine_dome", PowerCategory.AREA_CONTROL, PowerType.ACTIVE, 300, "impenetrable_dome_wall", Map.of("radius", "6.0"));
        regTempl("healing_spring_zone", PowerCategory.AREA_CONTROL, PowerType.ACTIVE, 160, "regen_ground_circle", Map.of("regenAmount", "6"));
        regTempl("thunderstorm_territory", PowerCategory.AREA_CONTROL, PowerType.ACTIVE, 260, "random_lightning_in_area", Map.of("strikes", "8"));
    }

    // 40. HYBRID (12)
    private static void registerHybridPowers() {
        regFunc("kinetic_thunder_strike", PowerCategory.HYBRID, PowerType.ACTIVE, 70, new LightningActionComponent(22.0D), List.of());
        regFunc("blazing_dash", PowerCategory.HYBRID, PowerType.ACTIVE, 40, new DashComponent(2.0D, 0.1D), List.of());
        regFunc("glacial_shield", PowerCategory.HYBRID, PowerType.ACTIVE, 60, new ShieldActionComponent(10.0F, 160), List.of());

        regTempl("plasma_flight", PowerCategory.HYBRID, PowerType.TOGGLE, 0, "fiery_aeronautics", Map.of("speed", "1.8", "burnsTrail", "true"));
        regTempl("frost_lightning", PowerCategory.HYBRID, PowerType.ACTIVE, 80, "freezing_electric_bolt", Map.of("freezeTicks", "80", "damage", "12"));
        regTempl("seismic_flight", PowerCategory.HYBRID, PowerType.TOGGLE, 0, "earth_platform_surfer", Map.of("speed", "1.2"));
        regTempl("shadow_flame_burst", PowerCategory.HYBRID, PowerType.ACTIVE, 60, "dark_fire_explosion", Map.of("damage", "14"));
        regTempl("aquatic_cyclone", PowerCategory.HYBRID, PowerType.ACTIVE, 120, "water_spout_tornado", Map.of("radius", "8.0"));
        regTempl("radiant_gravity_well", PowerCategory.HYBRID, PowerType.ACTIVE, 160, "pull_and_blind", Map.of("radius", "10.0"));
        regTempl("cyber_shifter", PowerCategory.HYBRID, PowerType.TOGGLE, 0, "nanite_shape_morph", Map.of("morphSpeed", "instant"));
        regTempl("nature_psionics", PowerCategory.HYBRID, PowerType.ACTIVE, 90, "mind_controlled_vines", Map.of("targets", "4"));
        regTempl("cosmic_vampirism", PowerCategory.HYBRID, PowerType.ACTIVE, 180, "life_drain_black_hole", Map.of("drainMax", "16"));
    }
}
