package br.com.ascendant.flight;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-only presentation options. Nothing here changes flight authority or
 * physics; disabling every option restores the vanilla third-person camera.
 */
public final class FlightClientTuning {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue DYNAMIC_CAMERA;
    public static final ForgeConfigSpec.BooleanValue DYNAMIC_FOV;
    public static final ForgeConfigSpec.DoubleValue CAMERA_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue FOV_STRENGTH;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("flightPresentation");
        ENABLED = builder.comment("Enable Ascendant's optional client-side flight presentation.")
                .define("enabled", true);
        DYNAMIC_CAMERA = builder.comment("Bank and pitch the third-person camera with flight velocity.")
                .define("dynamicCamera", true);
        DYNAMIC_FOV = builder.comment("Widen the field of view slightly with flight speed.")
                .define("dynamicFov", true);
        CAMERA_STRENGTH = builder.comment("Multiplier for the dynamic camera bank and pitch.")
                .defineInRange("cameraStrength", 0.55D, 0.0D, 1.5D);
        FOV_STRENGTH = builder.comment("Maximum additional field of view in degrees at cruise speed.")
                .defineInRange("fovStrength", 8.0D, 0.0D, 24.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private FlightClientTuning() {
    }
}
