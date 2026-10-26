package br.com.ascendant.flight;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server-authoritative flight tuning, shared with connected clients by Forge.
 *
 * <p>The defaults follow the reference constants. {@code speed} is the fixed
 * horizontal impulse applied while hovering and {@code boostSpeed} is the
 * impulse along the full look vector while sprinting. The steady-state speeds
 * are derived from the impulse and drag, not capped. The boost impulse is
 * trimmed slightly from the reference value for control: {@code 0.185} instead
 * of {@code 0.20}, which lowers the terminal speed from about 4.0 to about
 * 3.69 blocks per tick.</p>
 */
public final class FlightTuning {
    /** Gameplay default for the boost impulse (reference: 0.20). */
    public static final double DEFAULT_BOOST_SPEED = 0.185D;

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue SPEED;
    public static final ForgeConfigSpec.DoubleValue BOOST_SPEED;
    public static final ForgeConfigSpec.DoubleValue HOVER_DRAG;
    public static final ForgeConfigSpec.DoubleValue HOVER_GRAVITY;
    public static final ForgeConfigSpec.DoubleValue HOVER_BOB;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("flight");
        ENABLED = builder.comment("Allow the unlocked flight power to be activated.")
                .define("enabled", true);
        SPEED = builder.comment("Hover impulse in blocks per tick (reference: 0.10). "
                        + "The steady hover speed is speed * 0.91 / (1 - 0.91).")
                .defineInRange("speed", 0.10D, 0.01D, 0.50D);
        BOOST_SPEED = builder.comment("Boost impulse along the look vector in blocks per tick "
                        + "(Ascendant: 0.185, reference: 0.20). The steady boost speed is "
                        + "about 3.69 blocks per tick.")
                .defineInRange("boostSpeed", DEFAULT_BOOST_SPEED, 0.02D, 1.00D);
        HOVER_DRAG = builder.comment("Horizontal velocity retained per hover tick (reference: 0.91).")
                .defineInRange("hoverDrag", 0.91D, 0.50D, 0.995D);
        HOVER_GRAVITY = builder.comment("Hover gravity and forward lift per tick (reference: 0.08).")
                .defineInRange("hoverGravity", 0.08D, 0.0D, 0.20D);
        HOVER_BOB = builder.comment("Amplitude in blocks per tick of the idle hover bob (reference: 0.025).")
                .defineInRange("hoverBob", 0.025D, 0.0D, 0.10D);
        builder.pop();
        SPEC = builder.build();
    }

    private FlightTuning() {
    }
}
