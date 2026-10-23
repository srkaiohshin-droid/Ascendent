package br.com.ascendant.speedster;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-shared tuning for the initial balanced speedster movement profile. */
public final class SpeedsterTuning {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue MIN_SPEED;
    public static final ForgeConfigSpec.DoubleValue MAX_SPEED;
    public static final ForgeConfigSpec.DoubleValue ACCELERATION;
    public static final ForgeConfigSpec.DoubleValue STEERING;
    public static final ForgeConfigSpec.DoubleValue COAST_DRAG;
    public static final ForgeConfigSpec.DoubleValue EMERGENCY_BRAKING;
    public static final ForgeConfigSpec.DoubleValue SPRINT_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("speedster");
        ENABLED = builder.comment("Allow players with the kinetic sprint power to use speedster movement.")
                .define("enabled", true);
        MIN_SPEED = builder.comment("Level 1 target horizontal speed in blocks per tick.")
                .defineInRange("minSpeed", 0.22D, 0.05D, 0.5D);
        MAX_SPEED = builder.comment("Level 10 target horizontal speed in blocks per tick.")
                .defineInRange("maxSpeed", 1.0D, 0.3D, 1.5D);
        ACCELERATION = builder.comment("Horizontal acceleration per tick.")
                .defineInRange("acceleration", 0.035D, 0.005D, 0.2D);
        STEERING = builder.comment("Velocity heading convergence per tick while steering.")
                .defineInRange("steering", 0.16D, 0.01D, 0.5D);
        COAST_DRAG = builder.comment("Horizontal momentum retained per tick without directional input.")
                .defineInRange("coastDrag", 0.94D, 0.7D, 0.999D);
        EMERGENCY_BRAKING = builder.comment("Horizontal speed removed per tick during emergency braking.")
                .defineInRange("emergencyBraking", 0.18D, 0.02D, 0.6D);
        SPRINT_MULTIPLIER = builder.comment("Maximum speed multiplier while the sprint input is held.")
                .defineInRange("sprintMultiplier", 1.12D, 1.0D, 1.5D);
        builder.pop();
        SPEC = builder.build();
    }

    private SpeedsterTuning() {
    }

    public static double minSpeed() {
        return MIN_SPEED.get();
    }

    public static double maxSpeed() {
        return Math.max(MIN_SPEED.get(), MAX_SPEED.get());
    }
}
