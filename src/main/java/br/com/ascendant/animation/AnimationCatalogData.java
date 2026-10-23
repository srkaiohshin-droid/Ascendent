package br.com.ascendant.animation;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Registers default animation profiles for functional powers and abilities.
 * <p>
 * This class is separate from {@code PowerCatalogData} and {@code AbilityCatalogData}
 * so it can be maintained independently. Profiles are visual-only and contain
 * no gameplay logic.
 */
public final class AnimationCatalogData {

    private AnimationCatalogData() {
    }

    public static void registerAll() {
        registerFlightAnimations();
        registerMovementAnimations();
        registerCombatAnimations();
        registerDefenseAnimations();
        registerUtilityAnimations();
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("ascendant", path);
    }

    private static void register(ResourceLocation id, String name, List<AnimationProfile.EffectLayer> layers) {
        AnimationRegistry.register(id, new AnimationProfile(id, name, layers));
    }

    private static void registerFlightAnimations() {
        register(id("flight"), "Voo", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "smoke", "count", "8")),
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_SOUND,
                        Map.of("sound", "ascendant:flight_start", "volume", "0.8", "pitch", "1.2"))
        ));
        register(id("aerobatic_dash"), "Investida Aerobatica", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "electric_spark", "count", "15"))
        ));
        register(id("aerial_launch"), "Lancamento Aereo", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "smoke", "count", "12"))
        ));
    }

    private static void registerMovementAnimations() {
        register(id("mach_dash"), "Mach Dash", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "electric_spark", "count", "20"))
        ));
        register(id("blink_step"), "Piscar", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "portal", "count", "10"))
        ));
        register(id("colossal_leap"), "Salto Colossal", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "smoke", "count", "10"))
        ));
    }

    private static void registerCombatAnimations() {
        register(id("titan_strength"), "Forca Titania", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "dust", "count", "12"))
        ));
        register(id("thunder_punch"), "Soco de Trovão", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "electric_spark", "count", "15"))
        ));
        register(id("seismic_slam"), "Impacto Seismico", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "dust", "count", "25"))
        ));
    }

    private static void registerDefenseAnimations() {
        register(id("kinetic_barrier"), "Barreira Cinetica", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_AURA,
                        Map.of("color", "0x00aaff", "radius", "2.0"))
        ));
        register(id("diamond_skin"), "Pele de Diamante", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_AURA,
                        Map.of("color", "0xaaddff", "radius", "1.8"))
        ));
        register(id("shield_action"), "Escudo", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_IMPACT,
                        Map.of("color", "0x00aaff", "radius", "1.5"))
        ));
    }

    private static void registerUtilityAnimations() {
        register(id("invisibility"), "Invisibilidade", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "portal", "count", "8"))
        ));
        register(id("healing_touch"), "Toque Curativo", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "heart", "count", "12"))
        ));
        register(id("night_vision_eyes"), "Visao Noturna", List.of(
                new AnimationProfile.EffectLayer(AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "soul_fire", "count", "6"))
        ));
    }

}
