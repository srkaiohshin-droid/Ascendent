package br.com.ascendant.flight;

/**
 * Selects a flight animation state and produces the target pose for it.
 *
 * <p>The state only classifies the current flight situation. All nine visual
 * states share one pose model, so transitions are a single interpolation between
 * two {@link FlightPose} values instead of independent animation systems.</p>
 *
 * <p>The numbers are original values for the vanilla 1.20.1 player model. The
 * Fisk's Superheroes flight animations were used as a visual reference for the
 * body pitch, the arm thrust, the head lift and the leg stagger, but no asset or
 * code from that mod is copied.</p>
 */
public final class FlightPoseLibrary {
    /** Ticks spent blending from the launch lean into the heroic pose. */
    public static final int TAKEOFF_TICKS = 10;
    /** Ticks used to return to vanilla when flight stops. */
    public static final int EXIT_TICKS = 8;

    public static final float HOVER_SPEED = 0.07F;
    public static final float CRUISE_SPEED = 0.45F;
    public static final float TURN_THRESHOLD_DEGREES = 11.0F;
    public static final float CLIMB_VERTICAL_SPEED = 0.06F;
    public static final float DIVE_VERTICAL_SPEED = -0.08F;

    public static final float MIN_BODY_PITCH = -138.0F;
    public static final float MAX_BODY_PITCH = -5.0F;
    public static final float MAX_BODY_ROLL = 34.0F;
    /** Pivot around the torso centre, in the pre-flip frame used by setupRotations. */
    public static final float BODY_PIVOT_Y = 6.0F / 16.0F;

    private FlightPoseLibrary() {
    }

    public static FlightAnimationState selectState(boolean active, int activeTicks,
                                                   float speed, float verticalSpeed,
                                                   float viewPitch, boolean braking,
                                                   float turnDegrees) {
        if (!active) {
            return FlightAnimationState.EXIT;
        }
        if (activeTicks < TAKEOFF_TICKS) {
            return FlightAnimationState.TAKEOFF;
        }
        if (braking) {
            return FlightAnimationState.BRAKING;
        }

        boolean moving = speed > 0.12F;
        if (moving && (verticalSpeed <= DIVE_VERTICAL_SPEED || viewPitch > 28.0F)) {
            return FlightAnimationState.DIVE;
        }
        if (moving && (verticalSpeed >= CLIMB_VERTICAL_SPEED || viewPitch < -24.0F)) {
            return FlightAnimationState.CLIMB;
        }
        if (speed < HOVER_SPEED) {
            return FlightAnimationState.HOVER;
        }
        if (Math.abs(turnDegrees) >= TURN_THRESHOLD_DEGREES) {
            return FlightAnimationState.TURNING;
        }
        if (speed >= CRUISE_SPEED && Math.abs(verticalSpeed) < 0.05F) {
            return FlightAnimationState.CRUISE;
        }
        return FlightAnimationState.HEROIC_FLIGHT;
    }

    public static FlightPose target(FlightAnimationState state, int activeTicks,
                                    float speed, float verticalSpeed,
                                    float viewPitch, float turnDegrees) {
        return switch (state) {
            case HOVER -> hover(turnDegrees);
            case TAKEOFF -> takeoff(activeTicks, speed, viewPitch, turnDegrees);
            case BRAKING -> braking(turnDegrees);
            case DIVE -> dive(verticalSpeed, viewPitch, turnDegrees);
            case CLIMB -> climb(verticalSpeed, viewPitch, turnDegrees);
            case TURNING -> turning(speed, viewPitch, turnDegrees);
            case CRUISE -> cruise(speed, viewPitch, turnDegrees);
            case HEROIC_FLIGHT -> heroic(speed, viewPitch, turnDegrees);
            case EXIT -> FlightPose.VANILLA;
        };
    }

    private static FlightPose hover(float turnDegrees) {
        return new FlightPose(
                -12.0F, turnRoll(turnDegrees, 0.20F, 7.0F), 0.0F, -14.0F,
                -6.0F, 4.0F, 10.0F,
                -6.0F, -4.0F, -10.0F,
                2.0F, 2.0F, 2.0F,
                4.0F, -2.0F, -2.0F);
    }

    private static FlightPose takeoff(int activeTicks, float speed, float viewPitch, float turnDegrees) {
        float progress = smoothstep(clamp(activeTicks / (float) TAKEOFF_TICKS, 0.0F, 1.0F));
        FlightPose launch = new FlightPose(
                -35.0F, turnRoll(turnDegrees, 0.30F, 12.0F), 0.0F, -37.0F,
                -22.0F, 10.0F, 8.0F,
                -22.0F, -10.0F, -8.0F,
                -6.0F, 3.0F, -4.0F,
                0.0F, -3.0F, 4.0F);
        return FlightPose.lerp(launch, heroic(speed, viewPitch, turnDegrees), progress);
    }

    private static FlightPose heroic(float speed, float viewPitch, float turnDegrees) {
        // The reference flight animation rests slightly head-up instead of
        // perfectly level; this keeps the face and the forward arms readable from
        // a third-person camera above the body without extreme joint angles.
        float pitch = clamp(-78.0F - clamp(viewPitch, -90.0F, 90.0F) * 0.35F,
                MIN_BODY_PITCH, -60.0F);
        float roll = turnRoll(turnDegrees, 0.55F + 0.15F * speedFactor(speed), 26.0F);
        return new FlightPose(
                pitch, roll, 0.12F, pitch - 2.0F,
                -170.0F, 14.0F, -8.0F,
                -166.0F, -14.0F, 8.0F,
                4.0F, 6.0F, -6.0F,
                12.0F, -6.0F, 6.0F);
    }

    private static FlightPose cruise(float speed, float viewPitch, float turnDegrees) {
        FlightPose heroic = heroic(speed, viewPitch, turnDegrees);
        return new FlightPose(
                heroic.bodyPitchDegrees,
                turnRoll(turnDegrees, 0.40F, 20.0F),
                heroic.bodyLift,
                heroic.headXRotDegrees,
                heroic.rightArmXRot, heroic.rightArmYRot, heroic.rightArmZRot,
                heroic.leftArmXRot, heroic.leftArmYRot, heroic.leftArmZRot,
                heroic.rightLegXRot, heroic.rightLegYRot, heroic.rightLegZRot,
                heroic.leftLegXRot, heroic.leftLegYRot, heroic.leftLegZRot);
    }

    private static FlightPose turning(float speed, float viewPitch, float turnDegrees) {
        FlightPose heroic = heroic(speed, viewPitch, 0.0F);
        return new FlightPose(
                heroic.bodyPitchDegrees,
                turnRoll(turnDegrees, 0.95F, MAX_BODY_ROLL),
                heroic.bodyLift,
                heroic.headXRotDegrees,
                heroic.rightArmXRot, heroic.rightArmYRot + 3.0F, heroic.rightArmZRot - 2.0F,
                heroic.leftArmXRot, heroic.leftArmYRot - 3.0F, heroic.leftArmZRot + 2.0F,
                heroic.rightLegXRot, heroic.rightLegYRot, heroic.rightLegZRot,
                heroic.leftLegXRot, heroic.leftLegYRot, heroic.leftLegZRot);
    }

    private static FlightPose climb(float verticalSpeed, float viewPitch, float turnDegrees) {
        float climb = clamp(verticalSpeed / 0.75F, 0.0F, 1.0F);
        float pitch = clamp(-78.0F + 20.0F * climb - clamp(viewPitch, -90.0F, 90.0F) * 0.25F,
                -112.0F, -52.0F);
        return new FlightPose(
                pitch, turnRoll(turnDegrees, 0.50F, 24.0F), 0.10F, pitch - 8.0F,
                -160.0F, 13.0F, -8.0F,
                -156.0F, -13.0F, 8.0F,
                8.0F, 5.0F, -6.0F,
                14.0F, -5.0F, 6.0F);
    }

    private static FlightPose dive(float verticalSpeed, float viewPitch, float turnDegrees) {
        float dive = clamp(-verticalSpeed / 0.9F, 0.0F, 1.0F);
        float pitch = clamp(-78.0F - 22.0F * dive - clamp(viewPitch, -90.0F, 90.0F) * 0.40F,
                MIN_BODY_PITCH, -74.0F);
        return new FlightPose(
                pitch, turnRoll(turnDegrees, 0.50F, 24.0F), 0.10F, pitch + 6.0F,
                -182.0F, 12.0F, -8.0F,
                -178.0F, -12.0F, 8.0F,
                -4.0F, 4.0F, -6.0F,
                2.0F, -4.0F, 6.0F);
    }

    private static FlightPose braking(float turnDegrees) {
        return new FlightPose(
                -66.0F, turnRoll(turnDegrees, 0.35F, 14.0F), 0.08F, -70.0F,
                -10.0F, 14.0F, 18.0F,
                -10.0F, -14.0F, -18.0F,
                -4.0F, 4.0F, 3.0F,
                2.0F, -4.0F, -3.0F);
    }

    private static float turnRoll(float turnDegrees, float gain, float max) {
        return clamp(turnDegrees * gain, -max, max);
    }

    private static float speedFactor(float speed) {
        return clamp(speed / 0.65F, 0.0F, 1.0F);
    }

    private static float smoothstep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    /** Package-visible for tests. */
    static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
