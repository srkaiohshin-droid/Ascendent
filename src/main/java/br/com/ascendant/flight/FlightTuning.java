package br.com.ascendant.flight;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-authoritative flight tuning. Values are shared with connected clients by Forge. */
public final class FlightTuning {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue MAX_CRUISE_SPEED;
    public static final ForgeConfigSpec.DoubleValue MAX_BOOST_SPEED;
    public static final ForgeConfigSpec.DoubleValue ACCELERATION;
    public static final ForgeConfigSpec.DoubleValue BOOST_ACCELERATION;
    public static final ForgeConfigSpec.DoubleValue STEERING_FACTOR;
    public static final ForgeConfigSpec.DoubleValue DRAG;
    public static final ForgeConfigSpec.DoubleValue BRAKING;
    public static final ForgeConfigSpec.DoubleValue HOVER_STABILITY;
    public static final ForgeConfigSpec.DoubleValue LANDING_SPEED;
    public static final ForgeConfigSpec.DoubleValue TAKEOFF_IMPULSE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("flight");
        ENABLED = builder.comment("Allow the unlocked flight power to be activated.")
                .define("enabled", true);
        MAX_CRUISE_SPEED = builder.comment("Maximum normal cruise flight speed in blocks per tick.")
                .defineInRange("maxCruiseSpeed", 0.65D, 0.1D, 2.0D);
        MAX_BOOST_SPEED = builder.comment("Maximum boosted / sprinting flight speed in blocks per tick.")
                .defineInRange("maxBoostSpeed", 1.25D, 0.2D, 3.0D);
        ACCELERATION = builder.comment("Normal flight acceleration gained per tick.")
                .defineInRange("acceleration", 0.045D, 0.005D, 0.3D);
        BOOST_ACCELERATION = builder.comment("Boost / sprinting flight acceleration gained per tick.")
                .defineInRange("boostAcceleration", 0.080D, 0.01D, 0.5D);
        STEERING_FACTOR = builder.comment("Aerodynamic steering rate at which flight trajectory curves toward crosshair.")
                .defineInRange("steeringFactor", 0.12D, 0.01D, 0.5D);
        DRAG = builder.comment("Inertial velocity retained per tick when releasing propulsion (glide retention).")
                .defineInRange("drag", 0.94D, 0.70D, 0.99D);
        BRAKING = builder.comment("Velocity removed per tick while the brake key is held.")
                .defineInRange("braking", 0.12D, 0.02D, 0.4D);
        HOVER_STABILITY = builder.comment("Vertical correction per tick when hovering.")
                .defineInRange("hoverStability", 0.12D, 0.02D, 0.5D);
        LANDING_SPEED = builder.comment("Maximum vertical speed for automatic shutdown after a flight landing.")
                .defineInRange("landingSpeed", 0.12D, 0.01D, 0.4D);
        TAKEOFF_IMPULSE = builder.comment("Initial server-authoritative upward impulse when flight starts from the ground, in blocks per tick.")
                .defineInRange("takeoffImpulse", 0.32D, 0.0D, 0.8D);
        builder.pop();
        SPEC = builder.build();
    }

    private FlightTuning() {
    }
}
