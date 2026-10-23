package br.com.ascendant.power.catalog;

import br.com.ascendant.core.AbilityCategory;
import br.com.ascendant.core.AbilityTrigger;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.component.*;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Deterministic catalog population registering exactly 500 distinct abilities
 * across the 14 specified categories, with 100+ fully functional abilities.
 */
public final class AbilityCatalogData {

    private AbilityCatalogData() {
    }

    public static void registerAll() {
        registerOffensiveAbilities();      // 36 abilities (functional: 10)
        registerDefensiveAbilities();      // 36 abilities (functional: 10)
        registerMobilityAbilities();       // 36 abilities (functional: 10)
        registerAreaControlAbilities();    // 36 abilities (functional: 9)
        registerSupportAbilities();        // 36 abilities (functional: 9)
        registerExplorationAbilities();    // 36 abilities (functional: 8)
        registerPerceptionAbilities();     // 36 abilities (functional: 8)
        registerEnvironmentalAbilities();  // 36 abilities (functional: 8)
        registerComboAbilities();          // 36 abilities (functional: 8)
        registerPassiveAbilities();        // 36 abilities (functional: 8)
        registerTransformationAbilities(); // 35 abilities (functional: 6)
        registerConditionalAbilities();    // 35 abilities (functional: 6)
        registerCooperativeAbilities();    // 35 abilities (functional: 5)
        registerSpecializationAbilities(); // 35 abilities (functional: 6)
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("ascendant", path);
    }

    private static void regFunc(String path, String powerId, AbilityCategory cat, AbilityTrigger trigger, int cd, IPowerComponent action) {
        regFunc(path, powerId, cat, trigger, cd, action, 1.0F);
    }

    private static void regFunc(String path, String powerId, AbilityCategory cat, AbilityTrigger trigger,
                                int cd, IPowerComponent action, float healthThreshold) {
        ResourceLocation pId = powerId != null ? id(powerId) : null;
        AbilityRegistry.register(AbilityDefinition.builder(id(path))
                .powerId(pId)
                .category(cat)
                .trigger(trigger)
                .cooldown(cd)
                .healthThreshold(healthThreshold)
                .addAction(action)
                .functional(true)
                .build());
    }

    private static void regTempl(String path, String powerId, AbilityCategory cat, AbilityTrigger trigger, int cd, String mechanic, Map<String, String> params) {
        ResourceLocation pId = powerId != null ? id(powerId) : null;
        AbilityRegistry.register(AbilityDefinition.builder(id(path))
                .powerId(pId)
                .category(cat)
                .trigger(trigger)
                .cooldown(cd)
                .addAction(new TemplateComponent(mechanic, params))
                .functional(false)
                .build());
    }

    // 1. OFFENSIVE (36)
    private static void registerOffensiveAbilities() {
        regFunc("thunder_punch", "titan_strength", AbilityCategory.OFFENSIVE, AbilityTrigger.KEY_PRESS, 40, new DamageActionComponent(12.0F, 4.0D, "physical"));
        regFunc("kinetic_burst", "mach_dash", AbilityCategory.OFFENSIVE, AbilityTrigger.KEY_PRESS, 30, new DamageActionComponent(8.0F, 5.0D, "physical"));
        regFunc("pyro_lance", "pyroclastic_burst", AbilityCategory.OFFENSIVE, AbilityTrigger.KEY_PRESS, 50, new DamageActionComponent(10.0F, 6.0D, "fire"));
        regFunc("cryo_slash", "frost_bite_touch", AbilityCategory.OFFENSIVE, AbilityTrigger.KEY_PRESS, 40, new DamageActionComponent(8.0F, 4.5D, "magic"));
        regFunc("lightning_javelin", "thunderbolt_strike", AbilityCategory.OFFENSIVE, AbilityTrigger.KEY_PRESS, 60, new LightningActionComponent(24.0D));
        regFunc("sonic_rupture", "sonic_screech", AbilityCategory.OFFENSIVE, AbilityTrigger.KEY_PRESS, 50, new DamageActionComponent(9.0F, 5.0D, "magic"));
        regFunc("gravity_strike", "gravitational_slam", AbilityCategory.OFFENSIVE, AbilityTrigger.KEY_PRESS, 60, new DamageActionComponent(11.0F, 5.5D, "physical"));
        regFunc("energy_revolver", "energy_blast", AbilityCategory.OFFENSIVE, AbilityTrigger.KEY_PRESS, 25, new DamageActionComponent(7.0F, 7.0D, "magic"));
        regFunc("haymaker_finisher", "haymaker_punch", AbilityCategory.OFFENSIVE, AbilityTrigger.KEY_PRESS, 50, new DamageActionComponent(14.0F, 3.5D, "physical"));
        regFunc("psionic_shred", "mind_blast", AbilityCategory.OFFENSIVE, AbilityTrigger.KEY_PRESS, 45, new DamageActionComponent(9.0F, 5.0D, "magic"));

        for (int i = 11; i <= 36; i++) {
            regTempl("offensive_tech_" + i, null, AbilityCategory.OFFENSIVE, AbilityTrigger.KEY_PRESS, 40 + (i * 2), "offensive_strike_" + i, Map.of("powerLevel", String.valueOf(i)));
        }
    }

    // 2. DEFENSIVE (36)
    private static void registerDefensiveAbilities() {
        regFunc("kinetic_barrier_deploy", "kinetic_barrier", AbilityCategory.DEFENSIVE, AbilityTrigger.KEY_PRESS, 60, new ShieldActionComponent(8.0F, 160));
        regFunc("diamond_guard", "diamond_skin", AbilityCategory.DEFENSIVE, AbilityTrigger.KEY_PRESS, 80, new ShieldActionComponent(12.0F, 200));
        regFunc("fire_ward", "fire_immunity", AbilityCategory.DEFENSIVE, AbilityTrigger.KEY_PRESS, 40,
                new FireWardComponent(100));
        regFunc("repulsion_wave", "energy_aegis", AbilityCategory.DEFENSIVE, AbilityTrigger.KEY_PRESS, 50, new KnockbackActionComponent(7.0D, 1.8D));
        regFunc("flash_parry_stance", "reactive_shield", AbilityCategory.DEFENSIVE, AbilityTrigger.KEY_PRESS, 35, new ShieldActionComponent(6.0F, 80));
        regFunc("emergency_blink", "emergency_teleport", AbilityCategory.DEFENSIVE, AbilityTrigger.KEY_PRESS, 70, new BlinkComponent(14.0D));
        regFunc("water_aegis", "atlantean_sovereignty", AbilityCategory.DEFENSIVE, AbilityTrigger.KEY_PRESS, 60, new ShieldActionComponent(10.0F, 140));
        regFunc("earthen_bulwark", "stone_skin_plate", AbilityCategory.DEFENSIVE, AbilityTrigger.KEY_PRESS, 90, new ShieldActionComponent(14.0F, 220));
        regFunc("feather_descent", "feather_fall_passive", AbilityCategory.DEFENSIVE, AbilityTrigger.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fall"));
        regFunc("frost_armor_coat", "glacial_shield", AbilityCategory.DEFENSIVE, AbilityTrigger.KEY_PRESS, 60, new ShieldActionComponent(10.0F, 180));

        for (int i = 11; i <= 36; i++) {
            regTempl("defensive_tech_" + i, null, AbilityCategory.DEFENSIVE, AbilityTrigger.KEY_PRESS, 50 + (i * 2), "defensive_barrier_" + i, Map.of("mitigationLevel", String.valueOf(i)));
        }
    }

    // 3. MOBILITY (36)
    private static void registerMobilityAbilities() {
        regFunc("sonic_thrust", "mach_dash", AbilityCategory.MOBILITY, AbilityTrigger.KEY_PRESS, 30, new DashComponent(2.2D, 0.1D));
        regFunc("warp_jump", "blink_step", AbilityCategory.MOBILITY, AbilityTrigger.KEY_PRESS, 40, new BlinkComponent(16.0D));
        regFunc("skyward_bound", "colossal_leap", AbilityCategory.MOBILITY, AbilityTrigger.KEY_PRESS, 50, new LeapComponent(1.2D, 1.8D));
        regFunc("cyclone_burst_dash", "aerobatic_dash", AbilityCategory.MOBILITY, AbilityTrigger.KEY_PRESS, 35, new DashComponent(1.9D, 0.15D));
        regFunc("torrent_propel", "water_dash", AbilityCategory.MOBILITY, AbilityTrigger.KEY_PRESS, 30, new DashComponent(2.0D, 0.1D));
        regFunc("shadow_phase_step", "shadow_teleport", AbilityCategory.MOBILITY, AbilityTrigger.KEY_PRESS, 45, new BlinkComponent(14.0D));
        regFunc("stellar_surge", "stellar_dash", AbilityCategory.MOBILITY, AbilityTrigger.KEY_PRESS, 40, new DashComponent(2.1D, 0.2D));
        regFunc("high_altitude_leap", "aerial_launch", AbilityCategory.MOBILITY, AbilityTrigger.KEY_PRESS, 60, new LeapComponent(0.8D, 2.4D));
        regFunc("kinetic_vault", "hyper_leap", AbilityCategory.MOBILITY, AbilityTrigger.KEY_PRESS, 45, new LeapComponent(1.5D, 1.3D));
        regFunc("hydro_sprint_kick", "hydro_torpedo", AbilityCategory.MOBILITY, AbilityTrigger.KEY_PRESS, 35, new DashComponent(2.3D, 0.15D));

        for (int i = 11; i <= 36; i++) {
            regTempl("mobility_action_" + i, null, AbilityCategory.MOBILITY, AbilityTrigger.KEY_PRESS, 30 + (i * 2), "mobility_maneuver_" + i, Map.of("speedMultiplier", String.valueOf(1.0 + (i * 0.05))));
        }
    }

    // 4. AREA CONTROL (36)
    private static void registerAreaControlAbilities() {
        regFunc("seismic_rupture", "seismic_slam", AbilityCategory.AREA_CONTROL, AbilityTrigger.KEY_PRESS, 70, new DamageActionComponent(11.0F, 6.0D, "physical"));
        regFunc("permafrost_surge", "cryo_blast", AbilityCategory.AREA_CONTROL, AbilityTrigger.KEY_PRESS, 60, new AreaEffectActionComponent("freeze", 160, 2, 7.0D));
        regFunc("concussive_burst", "concussive_pulse_dome", AbilityCategory.AREA_CONTROL, AbilityTrigger.KEY_PRESS, 50, new KnockbackActionComponent(8.0D, 2.2D));
        regFunc("spore_cloud_burst", "spore_cloud", AbilityCategory.AREA_CONTROL, AbilityTrigger.KEY_PRESS, 60, new AreaEffectActionComponent("slowness", 120, 2, 6.0D));
        regFunc("shockwave_detonation", "shockwave_knockback", AbilityCategory.AREA_CONTROL, AbilityTrigger.KEY_PRESS, 50, new KnockbackActionComponent(9.0D, 2.0D));
        regFunc("gale_cyclone", "gale_blast", AbilityCategory.AREA_CONTROL, AbilityTrigger.KEY_PRESS, 40, new KnockbackActionComponent(8.0D, 2.0D));
        regFunc("infernal_eruption", "pyroclastic_burst", AbilityCategory.AREA_CONTROL, AbilityTrigger.KEY_PRESS, 70, new ExplosionActionComponent(2.8F, false, true));
        regFunc("gravity_crush_wave", "gravitational_slam", AbilityCategory.AREA_CONTROL, AbilityTrigger.KEY_PRESS, 65, new KnockbackActionComponent(7.0D, 1.8D));
        regFunc("stagger_flash", "staggering_wave", AbilityCategory.AREA_CONTROL, AbilityTrigger.KEY_PRESS, 45, new KnockbackActionComponent(8.0D, 2.0D));

        for (int i = 10; i <= 36; i++) {
            regTempl("area_control_action_" + i, null, AbilityCategory.AREA_CONTROL, AbilityTrigger.KEY_PRESS, 50 + (i * 2), "zone_dominance_" + i, Map.of("radius", String.valueOf(5 + (i * 0.2))));
        }
    }

    // 5. SUPPORT (36)
    private static void registerSupportAbilities() {
        regFunc("first_aid_touch", "healing_touch", AbilityCategory.SUPPORT, AbilityTrigger.KEY_PRESS, 50, new HealActionComponent(6.0F, true));
        regFunc("vitality_infusion", "regeneration_aura", AbilityCategory.SUPPORT, AbilityTrigger.KEY_PRESS, 100, new HealActionComponent(12.0F, false));
        regFunc("cleansing_surge", "restorative_surge", AbilityCategory.SUPPORT, AbilityTrigger.KEY_PRESS, 70, new HealActionComponent(8.0F, true));
        regFunc("solar_rejuvenation", "solar_healing", AbilityCategory.SUPPORT, AbilityTrigger.KEY_PRESS, 80, new HealActionComponent(8.0F, true));
        regFunc("nanite_regeneration_boost", "nanite_regeneration", AbilityCategory.SUPPORT, AbilityTrigger.KEY_PRESS, 60, new HealActionComponent(7.0F, true));
        regFunc("healing_wisp_send", "healing_wisp", AbilityCategory.SUPPORT, AbilityTrigger.KEY_PRESS, 90, new HealActionComponent(10.0F, false));
        regFunc("cyber_patch", "nanite_heal_injection", AbilityCategory.SUPPORT, AbilityTrigger.KEY_PRESS, 60, new HealActionComponent(8.0F, true));
        regFunc("protective_ward", "kinetic_barrier", AbilityCategory.SUPPORT, AbilityTrigger.KEY_PRESS, 70, new ShieldActionComponent(8.0F, 160));
        regFunc("restoration_pulse", "healing_touch", AbilityCategory.SUPPORT, AbilityTrigger.KEY_PRESS, 60, new HealActionComponent(8.0F, true));

        for (int i = 10; i <= 36; i++) {
            regTempl("support_skill_" + i, null, AbilityCategory.SUPPORT, AbilityTrigger.KEY_PRESS, 60 + (i * 2), "support_aura_" + i, Map.of("healingOutput", String.valueOf(4 + (i * 0.3))));
        }
    }

    // 6. EXPLORATION (36)
    private static void registerExplorationAbilities() {
        regFunc("mountain_bound", "mountaineer_leap", AbilityCategory.EXPLORATION, AbilityTrigger.KEY_PRESS, 40, new LeapComponent(1.3D, 1.8D));
        regFunc("recon_dash", "expedition_dash", AbilityCategory.EXPLORATION, AbilityTrigger.KEY_PRESS, 30, new DashComponent(1.8D, 0.1D));
        regFunc("night_lens", "night_explorer", AbilityCategory.EXPLORATION, AbilityTrigger.PASSIVE, 0, new NightVisionComponent(300));
        regFunc("thermal_ping", "thermal_scan", AbilityCategory.EXPLORATION, AbilityTrigger.KEY_PRESS, 50, new ThermalVisionComponent(32.0D, 160));
        regFunc("abyssal_swim", "aquatic_respiration", AbilityCategory.EXPLORATION, AbilityTrigger.PASSIVE, 0, new WaterAdaptationComponent(true, true));
        regFunc("safe_landing", "feather_fall_passive", AbilityCategory.EXPLORATION, AbilityTrigger.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fall"));
        regFunc("dark_vision_pulse", "night_vision_eyes", AbilityCategory.EXPLORATION, AbilityTrigger.KEY_PRESS, 40, new NightVisionComponent(400));
        regFunc("sonar_echo", "sonar_pulse", AbilityCategory.EXPLORATION, AbilityTrigger.KEY_PRESS, 40, new ThermalVisionComponent(30.0D, 140));

        for (int i = 9; i <= 36; i++) {
            regTempl("exploration_utility_" + i, null, AbilityCategory.EXPLORATION, AbilityTrigger.KEY_PRESS, 40 + i, "explorer_perk_" + i, Map.of("utilityTier", String.valueOf(i)));
        }
    }

    // 7. PERCEPTION (36)
    private static void registerPerceptionAbilities() {
        regFunc("radar_pulse", "sonar_pulse", AbilityCategory.PERCEPTION, AbilityTrigger.KEY_PRESS, 40, new ThermalVisionComponent(36.0D, 180));
        regFunc("night_gaze", "night_vision_eyes", AbilityCategory.PERCEPTION, AbilityTrigger.PASSIVE, 0, new NightVisionComponent(300));
        regFunc("thermal_focus", "thermal_scan", AbilityCategory.PERCEPTION, AbilityTrigger.KEY_PRESS, 50, new ThermalVisionComponent(40.0D, 200));
        regFunc("infrared_sweep", "infrared_toggle", AbilityCategory.PERCEPTION, AbilityTrigger.TOGGLE, 0, new NightVisionComponent(400));
        regFunc("motion_sense_alert", "sonar_pulse", AbilityCategory.PERCEPTION, AbilityTrigger.KEY_PRESS, 40, new ThermalVisionComponent(28.0D, 120));
        regFunc("life_scan", "thermal_scan", AbilityCategory.PERCEPTION, AbilityTrigger.KEY_PRESS, 60, new ThermalVisionComponent(48.0D, 160));
        regFunc("predator_sight", "night_vision_eyes", AbilityCategory.PERCEPTION, AbilityTrigger.PASSIVE, 0, new NightVisionComponent(300));
        regFunc("vital_sign_locator", "sonar_pulse", AbilityCategory.PERCEPTION, AbilityTrigger.KEY_PRESS, 50, new ThermalVisionComponent(32.0D, 140));

        for (int i = 9; i <= 36; i++) {
            regTempl("perception_sense_" + i, null, AbilityCategory.PERCEPTION, AbilityTrigger.KEY_PRESS, 40 + i, "sense_perk_" + i, Map.of("detectionRange", String.valueOf(20 + i)));
        }
    }

    // 8. ENVIRONMENTAL (36)
    private static void registerEnvironmentalAbilities() {
        regFunc("lava_diver", "lava_skin", AbilityCategory.ENVIRONMENTAL, AbilityTrigger.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fire"));
        regFunc("oceanic_breathing", "abyssal_lung", AbilityCategory.ENVIRONMENTAL, AbilityTrigger.PASSIVE, 0, new WaterAdaptationComponent(true, false));
        regFunc("gravity_nullify", "weightlessness_cushion", AbilityCategory.ENVIRONMENTAL, AbilityTrigger.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fall"));
        regFunc("aquatic_stride", "aquatic_respiration", AbilityCategory.ENVIRONMENTAL, AbilityTrigger.PASSIVE, 0, new WaterAdaptationComponent(true, true));
        regFunc("flame_immunity_stance", "fire_immunity", AbilityCategory.ENVIRONMENTAL, AbilityTrigger.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fire"));
        regFunc("fall_absorption", "kinetic_dampener", AbilityCategory.ENVIRONMENTAL, AbilityTrigger.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fall"));
        regFunc("deep_sea_mastery", "atlantean_sovereignty", AbilityCategory.ENVIRONMENTAL, AbilityTrigger.PASSIVE, 0, new WaterAdaptationComponent(true, true));
        regFunc("thermal_ward", "lava_skin", AbilityCategory.ENVIRONMENTAL, AbilityTrigger.PASSIVE, 0, new DamageMitigationComponent(1.0F, "fire"));

        for (int i = 9; i <= 36; i++) {
            regTempl("environmental_adaptation_" + i, null, AbilityCategory.ENVIRONMENTAL, AbilityTrigger.PASSIVE, 0, "env_perk_" + i, Map.of("resilienceType", "elemental_" + i));
        }
    }

    // 9. COMBO (36)
    private static void registerComboAbilities() {
        regFunc("dive_kick", "aerobatic_dash", AbilityCategory.COMBO, AbilityTrigger.KEY_PRESS, 40, new DashComponent(2.0D, -0.4D));
        regFunc("uppercut_launch", "uppercut_leap", AbilityCategory.COMBO, AbilityTrigger.KEY_PRESS, 40, new LeapComponent(0.5D, 1.9D));
        regFunc("thunder_slam_combo", "kinetic_thunder_strike", AbilityCategory.COMBO, AbilityTrigger.KEY_PRESS, 60, new LightningActionComponent(20.0D));
        regFunc("flame_dash_combo", "blazing_dash", AbilityCategory.COMBO, AbilityTrigger.KEY_PRESS, 40, new DashComponent(2.2D, 0.1D));
        regFunc("frost_buckler_counter", "glacial_shield", AbilityCategory.COMBO, AbilityTrigger.KEY_PRESS, 50, new ShieldActionComponent(8.0F, 140));
        regFunc("warp_kick", "blink_step", AbilityCategory.COMBO, AbilityTrigger.KEY_PRESS, 40, new BlinkComponent(12.0D));
        regFunc("seismic_uppercut", "seismic_slam", AbilityCategory.COMBO, AbilityTrigger.KEY_PRESS, 50, new DamageActionComponent(12.0F, 4.0D, "physical"));
        regFunc("sonic_tackle", "sonic_boost_leap", AbilityCategory.COMBO, AbilityTrigger.KEY_PRESS, 45, new DashComponent(2.1D, 0.1D));

        for (int i = 9; i <= 36; i++) {
            regTempl("combo_chain_" + i, null, AbilityCategory.COMBO, AbilityTrigger.ATTACK_HIT, 30 + i, "combo_finisher_" + i, Map.of("comboHits", String.valueOf(3 + (i % 4))));
        }
    }

    // 10. PASSIVE (36)
    private static void registerPassiveAbilities() {
        regFunc("titan_muscle_passive", "titan_strength", AbilityCategory.PASSIVE, AbilityTrigger.PASSIVE, 0,
                new AttributeModifierComponent("attack_damage", 8.0D));
        regFunc("speedster_legs_passive", "kinetic_sprint", AbilityCategory.PASSIVE, AbilityTrigger.PASSIVE, 0,
                new AttributeModifierComponent("movement_speed", 0.15D));
        regFunc("diamond_skin_passive", "diamond_skin", AbilityCategory.PASSIVE, AbilityTrigger.PASSIVE, 0,
                new AttributeModifierComponent("armor", 10.0D));
        regFunc("fall_dampening_passive", "feather_fall_passive", AbilityCategory.PASSIVE, AbilityTrigger.PASSIVE, 0,
                new DamageMitigationComponent(1.0F, "fall"));
        regFunc("fire_ward_passive", "fire_immunity", AbilityCategory.PASSIVE, AbilityTrigger.PASSIVE, 0,
                new DamageMitigationComponent(1.0F, "fire"));
        regFunc("atlantean_lungs_passive", "aquatic_respiration", AbilityCategory.PASSIVE, AbilityTrigger.PASSIVE, 0,
                new WaterAdaptationComponent(true, true));
        regFunc("night_vision_passive", "night_vision_eyes", AbilityCategory.PASSIVE, AbilityTrigger.PASSIVE, 0,
                new NightVisionComponent(300));
        regFunc("brawler_power_passive", "brawler_strength", AbilityCategory.PASSIVE, AbilityTrigger.PASSIVE, 0,
                new AttributeModifierComponent("attack_damage", 6.0D));

        for (int i = 9; i <= 36; i++) {
            regTempl("passive_mastery_" + i, null, AbilityCategory.PASSIVE, AbilityTrigger.PASSIVE, 0, "passive_stat_" + i, Map.of("perkMultiplier", String.valueOf(1.0 + (i * 0.02))));
        }
    }

    // 11. TRANSFORMATION (35)
    private static void registerTransformationAbilities() {
        regFunc("beast_lunge", "beast_leap", AbilityCategory.TRANSFORMATION, AbilityTrigger.KEY_PRESS, 40, new LeapComponent(1.5D, 1.4D));
        regFunc("feral_claw_slash", "feral_swipe", AbilityCategory.TRANSFORMATION, AbilityTrigger.KEY_PRESS, 30, new DamageActionComponent(9.0F, 4.0D, "physical"));
        regFunc("titan_growth_boost", "titan_mass_form", AbilityCategory.TRANSFORMATION, AbilityTrigger.TOGGLE, 80,
                new AttributeModifierComponent("attack_damage", 10.0D));
        regFunc("graphene_coat", "graphene_hardening", AbilityCategory.TRANSFORMATION, AbilityTrigger.PASSIVE, 0,
                new AttributeModifierComponent("armor", 12.0D));
        regFunc("reptilian_dash", "chameleon_dash", AbilityCategory.TRANSFORMATION, AbilityTrigger.KEY_PRESS, 40, new DashComponent(1.7D, 0.1D));
        regFunc("predator_strike", "stealth_strike", AbilityCategory.TRANSFORMATION, AbilityTrigger.KEY_PRESS, 35, new DamageActionComponent(10.0F, 3.5D, "physical"));

        for (int i = 7; i <= 35; i++) {
            regTempl("transformation_form_" + i, null, AbilityCategory.TRANSFORMATION, AbilityTrigger.TOGGLE, 100 + i, "morph_variant_" + i, Map.of("formIndex", String.valueOf(i)));
        }
    }

    // 12. CONDITIONAL (35)
    private static void registerConditionalAbilities() {
        regFunc("retaliation_blast", "reactive_shield", AbilityCategory.CONDITIONAL, AbilityTrigger.HURT_EVENT, 50, new KnockbackActionComponent(6.0D, 1.8D));
        regFunc("low_hp_emergency_warp", "emergency_teleport", AbilityCategory.CONDITIONAL,
                AbilityTrigger.HURT_EVENT, 120, new BlinkComponent(16.0D), 0.25F);
        regFunc("water_speed_surge", "aquatic_respiration", AbilityCategory.CONDITIONAL, AbilityTrigger.TICK, 0, new WaterAdaptationComponent(true, true));
        regFunc("fire_contact_absorb", "fire_immunity", AbilityCategory.CONDITIONAL, AbilityTrigger.HURT_EVENT, 0, new DamageMitigationComponent(1.0F, "fire"));
        regFunc("fall_impact_absorb", "feather_fall_passive", AbilityCategory.CONDITIONAL, AbilityTrigger.HURT_EVENT, 0, new DamageMitigationComponent(1.0F, "fall"));
        regFunc("low_hp_shield", "kinetic_barrier", AbilityCategory.CONDITIONAL,
                AbilityTrigger.HURT_EVENT, 140, new ShieldActionComponent(12.0F, 180), 0.25F);

        for (int i = 7; i <= 35; i++) {
            regTempl("conditional_trigger_" + i, null, AbilityCategory.CONDITIONAL, AbilityTrigger.HURT_EVENT, 60 + i, "trigger_condition_" + i, Map.of("conditionType", "reactive_" + i));
        }
    }

    // 13. COOPERATIVE (35)
    private static void registerCooperativeAbilities() {
        regFunc("ally_heal_pulse", "healing_touch", AbilityCategory.COOPERATIVE, AbilityTrigger.KEY_PRESS, 70, new HealActionComponent(8.0F, true));
        regFunc("team_shield_boost", "kinetic_barrier", AbilityCategory.COOPERATIVE, AbilityTrigger.KEY_PRESS, 80, new ShieldActionComponent(8.0F, 200));
        regFunc("team_repulsion", "reactive_deflection", AbilityCategory.COOPERATIVE, AbilityTrigger.KEY_PRESS, 50, new KnockbackActionComponent(8.0D, 2.0D));
        regFunc("thermal_ally_ping", "thermal_scan", AbilityCategory.COOPERATIVE, AbilityTrigger.KEY_PRESS, 60, new ThermalVisionComponent(40.0D, 180));
        regFunc("purifying_cleanse_team", "restorative_surge", AbilityCategory.COOPERATIVE, AbilityTrigger.KEY_PRESS, 80, new HealActionComponent(10.0F, true));

        for (int i = 6; i <= 35; i++) {
            regTempl("cooperative_maneuver_" + i, null, AbilityCategory.COOPERATIVE, AbilityTrigger.KEY_PRESS, 80 + i, "team_tactic_" + i, Map.of("allyRadius", String.valueOf(8 + (i % 6))));
        }
    }

    // 14. SPECIALIZATION (35)
    private static void registerSpecializationAbilities() {
        regFunc("specialist_mach_dash", "mach_dash", AbilityCategory.SPECIALIZATION, AbilityTrigger.KEY_PRESS, 25, new DashComponent(2.5D, 0.05D));
        regFunc("specialist_titan_strike", "titan_strength", AbilityCategory.SPECIALIZATION, AbilityTrigger.KEY_PRESS, 35, new DamageActionComponent(16.0F, 4.0D, "physical"));
        regFunc("specialist_high_leap", "colossal_leap", AbilityCategory.SPECIALIZATION, AbilityTrigger.KEY_PRESS, 45, new LeapComponent(1.5D, 2.2D));
        regFunc("specialist_thunder_bolt", "thunderbolt_strike", AbilityCategory.SPECIALIZATION, AbilityTrigger.KEY_PRESS, 60, new LightningActionComponent(32.0D));
        regFunc("specialist_blink", "blink_step", AbilityCategory.SPECIALIZATION, AbilityTrigger.KEY_PRESS, 30, new BlinkComponent(20.0D));
        regFunc("specialist_frost_blast", "cryo_blast", AbilityCategory.SPECIALIZATION, AbilityTrigger.KEY_PRESS, 45, new AreaEffectActionComponent("freeze", 180, 2, 8.0D));

        for (int i = 7; i <= 35; i++) {
            regTempl("specialization_technique_" + i, null, AbilityCategory.SPECIALIZATION, AbilityTrigger.KEY_PRESS, 50 + i, "expert_doctrine_" + i, Map.of("masteryTier", String.valueOf((i % 5) + 1)));
        }
    }
}
