package br.com.ascendant.speedster;

import net.minecraft.resources.ResourceLocation;

/**
 * Perfil de física e apresentação de um único nível de velocista.
 * Cada nível define seus próprios parâmetros de aceleração, controle,
 * energia cinética e efeitos visuais.
 */
public record SpeedsterProfile(
        SpeedsterLevel level,
        double minSpeed,
        double maxSpeed,
        double acceleration,
        double steering,
        double coastDrag,
        double emergencyBraking,
        double sprintMultiplier,
        double kineticBuildupRate,
        double kineticDecayRate,
        double dashCooldownTicks,
        double dashSpeed,
        boolean hasLightning,
        boolean hasTrails,
        boolean hasAfterimages,
        boolean hasShockwaves,
        boolean hasPerception,
        boolean canWallRun,
        boolean canWaterRun,
        double impactDamageMultiplier,
        ResourceLocation visualPalette
) {
    public static SpeedsterProfile none() {
        return new SpeedsterProfile(
                SpeedsterLevel.NONE, 0.22D, 0.22D, 0.0D, 0.0D, 1.0D, 0.0D, 1.0D,
                0.0D, 0.0D, 0.0D, 0.0D,
                false, false, false, false, false, false, false,
                0.0D, new ResourceLocation("ascendant", "speedster_palette_none"));
    }

    public static SpeedsterProfile reflexes() {
        return new SpeedsterProfile(
                SpeedsterLevel.REFLEXES, 0.22D, 0.42D, 0.055D, 0.22D, 0.92D, 0.20D, 1.08D,
                0.02D, 0.05D, 40.0D, 1.5D,
                false, true, false, false, false, false, false,
                1.0D, new ResourceLocation("ascendant", "speedster_palette_reflexes"));
    }

    public static SpeedsterProfile kinetic() {
        return new SpeedsterProfile(
                SpeedsterLevel.KINETIC, 0.22D, 0.62D, 0.045D, 0.19D, 0.90D, 0.22D, 1.15D,
                0.04D, 0.03D, 35.0D, 2.0D,
                true, true, false, false, false, true, false,
                1.2D, new ResourceLocation("ascendant", "speedster_palette_kinetic"));
    }

    public static SpeedsterProfile supersonic() {
        return new SpeedsterProfile(
                SpeedsterLevel.SUPERSONIC, 0.22D, 0.95D, 0.040D, 0.16D, 0.88D, 0.25D, 1.22D,
                0.06D, 0.02D, 30.0D, 2.8D,
                true, true, true, true, true, true, false,
                1.5D, new ResourceLocation("ascendant", "speedster_palette_supersonic"));
    }

    public static SpeedsterProfile electric() {
        return new SpeedsterProfile(
                SpeedsterLevel.ELECTRIC, 0.22D, 1.25D, 0.038D, 0.14D, 0.86D, 0.28D, 1.30D,
                0.08D, 0.015D, 25.0D, 3.6D,
                true, true, true, true, true, true, true,
                1.8D, new ResourceLocation("ascendant", "speedster_palette_electric"));
    }

    public static SpeedsterProfile extreme() {
        return new SpeedsterProfile(
                SpeedsterLevel.EXTREME, 0.22D, 1.70D, 0.036D, 0.12D, 0.84D, 0.32D, 1.40D,
                0.10D, 0.01D, 20.0D, 4.5D,
                true, true, true, true, true, true, true,
                2.2D, new ResourceLocation("ascendant", "speedster_palette_extreme"));
    }

    public static SpeedsterProfile transcendental() {
        return new SpeedsterProfile(
                SpeedsterLevel.TRANSCENDENTAL, 0.22D, 2.40D, 0.034D, 0.10D, 0.82D, 0.40D, 1.55D,
                0.12D, 0.005D, 15.0D, 6.0D,
                true, true, true, true, true, true, true,
                3.0D, new ResourceLocation("ascendant", "speedster_palette_transcendental"));
    }

    public static SpeedsterProfile forLevel(SpeedsterLevel level) {
        return switch (level) {
            case NONE -> none();
            case REFLEXES -> reflexes();
            case KINETIC -> kinetic();
            case SUPERSONIC -> supersonic();
            case ELECTRIC -> electric();
            case EXTREME -> extreme();
            case TRANSCENDENTAL -> transcendental();
        };
    }
}
