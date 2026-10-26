package br.com.ascendant.flight;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-only presentation options. Nothing here changes flight authority or
 * physics; disabling the camera roll restores the vanilla third-person camera.
 */
public final class FlightClientTuning {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue DYNAMIC_CAMERA;
    public static final ForgeConfigSpec.DoubleValue CAMERA_STRENGTH;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("flightPresentation");
        ENABLED = builder.comment("Enable Ascendant's optional client-side flight presentation.")
                .define("enabled", true);
        DYNAMIC_CAMERA = builder.comment(
                        "Apply the reference boost camera roll while sprint-flying.")
                .define("dynamicCamera", true);
        CAMERA_STRENGTH = builder.comment("Multiplier for the reference boost camera roll.")
                .defineInRange("cameraStrength", 1.0D, 0.0D, 1.5D);
        builder.pop();
        SPEC = builder.build();
    }

    private FlightClientTuning() {
    }
}
