package br.com.ascendant.flight;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-authoritative flight tuning. Values are shared with connected clients by Forge. */
public final class FlightTuning {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue MAX_HORIZONTAL_SPEED;
    public static final ForgeConfigSpec.DoubleValue MAX_VERTICAL_SPEED;
    public static final ForgeConfigSpec.DoubleValue HORIZONTAL_ACCELERATION;
    public static final ForgeConfigSpec.DoubleValue VERTICAL_ACCELERATION;
    public static final ForgeConfigSpec.DoubleValue BRAKING;
    public static final ForgeConfigSpec.DoubleValue HOVER_STABILITY;
    public static final ForgeConfigSpec.DoubleValue LANDING_SPEED;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("flight");
        ENABLED = builder.comment("Allow the unlocked flight power to be activated.")
                .define("enabled", true);
        MAX_HORIZONTAL_SPEED = builder.comment("Maximum horizontal flight speed in blocks per tick.")
                .defineInRange("maxHorizontalSpeed", 0.55D, 0.05D, 1.2D);
        MAX_VERTICAL_SPEED = builder.comment("Maximum upward or downward flight speed in blocks per tick.")
                .defineInRange("maxVerticalSpeed", 0.38D, 0.05D, 0.8D);
        HORIZONTAL_ACCELERATION = builder.comment("Horizontal velocity gained per tick while steering.")
                .defineInRange("horizontalAcceleration", 0.035D, 0.005D, 0.2D);
        VERTICAL_ACCELERATION = builder.comment("Vertical velocity gained per tick while climbing or descending.")
                .defineInRange("verticalAcceleration", 0.025D, 0.005D, 0.2D);
        BRAKING = builder.comment("Velocity removed per tick while the brake key is held.")
                .defineInRange("braking", 0.09D, 0.01D, 0.4D);
        HOVER_STABILITY = builder.comment("Vertical correction per tick when neither ascent nor descent is held.")
                .defineInRange("hoverStability", 0.16D, 0.02D, 0.8D);
        LANDING_SPEED = builder.comment("Maximum vertical speed for automatic shutdown after a flight landing.")
                .defineInRange("landingSpeed", 0.08D, 0.0D, 0.4D);
        builder.pop();
        SPEC = builder.build();
    }

    private FlightTuning() {
    }
}
