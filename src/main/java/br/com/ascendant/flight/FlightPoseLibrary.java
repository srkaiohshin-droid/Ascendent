package br.com.ascendant.flight;

/**
 * Turns flight telemetry into a target pose and classifies the animation state.
 *
 * <p>All angles are in degrees and all distances in model/render units, matching
 * the vanilla 1.20.1 humanoid joints. The numbers are original values for that
 * model. The only external visual reference is the general superhero silhouette
 * (horizontal torso, arms leading, head lifted); no asset or animation code from
 * another mod is copied.</p>
 *
 * <p>The head is deliberately <strong>not</strong> rotated by the same local
 * angle as the torso. Vanilla elytra flight uses a horizontal body with a local
 * head pitch near -45 degrees; copying the body angle into the neck folds the
 * head into the chest and produces the displacement that this revision fixes.
 * The helpers below derive a smaller local pitch from the body pitch and clamp
 * it to a safe neck range.</p>
 */
public final class FlightPoseLibrary {
    /** Ticks spent in the launch pose when the player leaves the ground. */
    public static final int TAKEOFF_GROUND_TICKS = 14;
    /** Ticks spent in the launch pose when flight starts already airborne. */
    public static final int TAKEOFF_AIR_TICKS = 8;
    /** Backwards-compatible name for the ground launch duration. */
    public static final int TAKEOFF_TICKS = TAKEOFF_GROUND_TICKS;
    /** Ticks used to return to vanilla when flight stops. */
    public static final int EXIT_TICKS = 8;
    /** Ticks in one full 360-degree aerial roll. */
    public static final int MANEUVER_TICKS = 14;
    /** Ticks of contact/recovery after a landed flight ends. */
    public static final int LANDING_RECOVERY_TICKS = 12;

    public static final float HOVER_SPEED = FlightAnimationSelector.HOVER_ENTER_SPEED;
    public static final float CRUISE_SPEED = FlightAnimationSelector.CRUISE_ENTER_SPEED;
    public static final float TURN_THRESHOLD_DEGREES = FlightAnimationSelector.TURN_ENTER_DEGREES;
    public static final float CLIMB_VERTICAL_SPEED = 0.06F;
    public static final float DIVE_VERTICAL_SPEED = -0.08F;
    /** Minimum trajectory pitch for the climb/dive classification. */
    public static final float TRAJECTORY_PITCH_THRESHOLD =
            FlightAnimationSelector.CLIMB_ENTER_DEGREES;

    public static final float MIN_BODY_PITCH = -138.0F;
    public static final float MAX_BODY_PITCH = -5.0F;
    public static final float MAX_BODY_ROLL = 34.0F;

    /** Local neck limits. The head never copies the full torso rotation. */
    public static final float MIN_HEAD_PITCH = -75.0F;
    public static final float MAX_HEAD_PITCH = 0.0F;
    public static final float MAX_HEAD_YAW = 18.0F;
    public static final float MAX_HEAD_ROLL = 12.0F;
    /** Head pitch as a fraction of the body pitch (vanilla horizontal reference ≈ 0.5). */
    public static final float HEAD_PITCH_FRACTION = 0.52F;

    /** Pivot used by the running lean, kept at the legacy knee height. */
    public static final float BODY_PIVOT_Y = 6.0F / 16.0F;
    /**
     * Flight pivot in the pre-flip {@code setupRotations} frame. The vanilla
     * torso spans roughly 0.7..1.4 blocks above the feet; 1.0 is its centre.
     * Rotating here keeps the chest and hips together instead of swinging the
     * whole body around the knees.
     */
    public static final float FLIGHT_BODY_PIVOT_Y = 1.0F;

    private static final float HOVER_CYCLE_SPEED = 0.085F;

    private FlightPoseLibrary() {
    }

    /** Stateless classification, kept for callers that do not track hysteresis. */
    public static FlightAnimationState selectState(boolean active, int activeTicks,
                                                   float speed, float verticalSpeed,
                                                   float viewPitch, boolean braking,
                                                   float turnDegrees) {
        return selectState(active, activeTicks, speed, verticalSpeed, viewPitch, braking,
                turnDegrees, false);
    }

    /** Stateless classification, kept for callers that do not track hysteresis. */
    public static FlightAnimationState selectState(boolean active, int activeTicks,
                                                   float speed, float verticalSpeed,
                                                   float viewPitch, boolean braking,
                                                   float turnDegrees, boolean maneuvering) {
        FlightMotionSample sample = FlightMotionSample.builder()
                .active(active)
                .activeTicks(activeTicks)
                .launchedFromGround(true)
                .speed(speed)
                .verticalSpeed(verticalSpeed)
                .turnRate(turnDegrees)
                .bank(turnRoll(turnDegrees, 0.9F, MAX_BODY_ROLL))
                .braking(braking)
                .maneuvering(maneuvering)
                .build();
        return FlightAnimationSelector.select(null, 0, sample);
    }

    /**
     * Hysteresis-aware classification. The client controller owns
     * {@code previous} and {@code ticksInState}; tests can drive it directly.
     */
    public static FlightAnimationState selectState(FlightAnimationState previous, int ticksInState,
                                                   FlightMotionSample sample) {
        return FlightAnimationSelector.select(previous, ticksInState, sample);
    }

    /** Backwards-compatible target builder that derives a telemetry sample. */
    public static FlightPose target(FlightAnimationState state, int activeTicks,
                                    float speed, float verticalSpeed,
                                    float viewPitch, float turnDegrees) {
        FlightMotionSample sample = FlightMotionSample.builder()
                .active(state != FlightAnimationState.EXIT)
                .activeTicks(activeTicks)
                .launchedFromGround(true)
                .speed(speed)
                .verticalSpeed(verticalSpeed)
                .turnRate(turnDegrees)
                .bank(turnRoll(turnDegrees, 0.9F, MAX_BODY_ROLL))
                .build();
        return target(state, sample);
    }

    /** Main pose entry point. */
    public static FlightPose target(FlightAnimationState state, FlightMotionSample sample) {
        return switch (state) {
            case HOVER -> hover(sample);
            case SLOW_FLIGHT -> slowFlight(sample);
            case TAKEOFF -> takeoff(sample);
            case ACCELERATING -> sample.boosting() ? boost(sample) : accelerating(sample);
            case DECELERATING -> decelerating(sample);
            case BRAKING -> braking(sample);
            case DIVE -> dive(sample);
            case CLIMB -> climb(sample);
            case TURNING -> turning(sample);
            case CRUISE -> cruise(sample);
            case HEROIC_FLIGHT -> heroic(sample);
            case MANEUVER -> maneuver(sample);
            case LANDING -> landing(sample);
            case EXIT -> FlightPose.VANILLA;
        };
    }

    /**
     * Local neck pitch for a body pitch. At a horizontal body (-90) this stays
     * near the vanilla elytra neck angle instead of copying the torso. The
     * result is always inside {@link #MIN_HEAD_PITCH}..{@link #MAX_HEAD_PITCH}.
     */
    public static float headPitchForBody(float bodyPitchDegrees) {
        return clamp(bodyPitchDegrees * HEAD_PITCH_FRACTION, MIN_HEAD_PITCH, MAX_HEAD_PITCH);
    }

    /**
     * Full 360-degree roll offset for an aerial maneuver. Progress is derived
     * from the server-synced ticks remaining, so every client sees the same
     * rotation without any client-side authority over the maneuver.
     */
    public static float maneuverRoll(int direction, int ticksRemaining) {
        if (direction != -1 && direction != 1) {
            return 0.0F;
        }
        float progress = 1.0F - clamp(ticksRemaining / (float) MANEUVER_TICKS, 0.0F, 1.0F);
        return direction * 360.0F * smoothstep(progress);
    }

    private static FlightPose hover(FlightMotionSample sample) {
        float phase = sample.activeTicks() * HOVER_CYCLE_SPEED;
        float breathing = (float) Math.sin(phase);
        float settle = (float) Math.sin(phase * 0.63F + 1.2F);

        float bodyPitch = -9.0F + settle * 1.8F;
        float bodyRoll = clamp(sample.bankDegrees() * 0.25F + breathing * 2.2F,
                -MAX_BODY_ROLL, MAX_BODY_ROLL);
        float headPitch = headPitchForBody(bodyPitch) - 2.0F + settle * 1.0F;
        float headYaw = (float) Math.sin(phase * 0.51F + 0.8F) * 2.8F;

        return new FlightPose(
                bodyPitch, bodyRoll, breathing * 0.010F,
                headPitch, headYaw, -bodyRoll * 0.35F,
                -12.0F + (float) Math.sin(phase + 0.4F) * 4.0F, 8.0F, 10.0F,
                -10.0F + (float) Math.sin(phase + 2.2F) * 4.0F, -8.0F, -10.0F,
                3.0F + (float) Math.sin(phase + 1.0F) * 2.0F, 1.5F, 2.0F,
                -2.0F + (float) Math.sin(phase + 2.6F) * 2.0F, -1.5F, -2.0F);
    }

    private static FlightPose slowFlight(FlightMotionSample sample) {
        float factor = smoothstep(clamp(
                (sample.speed() - FlightAnimationSelector.HOVER_EXIT_SPEED)
                        / (FlightAnimationSelector.SLOW_ENTER_SPEED
                        - FlightAnimationSelector.HOVER_EXIT_SPEED), 0.0F, 1.0F));
        return FlightPose.lerp(hover(sample), heroic(sample), factor);
    }

    private static FlightPose takeoff(FlightMotionSample sample) {
        int duration = sample.launchedFromGround() ? TAKEOFF_GROUND_TICKS : TAKEOFF_AIR_TICKS;
        float progress = smoothstep(clamp(sample.activeTicks() / (float) duration, 0.0F, 1.0F));
        FlightPose extended = extension(sample);
        if (!sample.launchedFromGround()) {
            return FlightPose.lerp(extended, heroic(sample), progress);
        }

        FlightPose anticipation = anticipation(sample);
        float launchProgress = smoothstep(clamp(progress / 0.24F, 0.0F, 1.0F));
        FlightPose launched = FlightPose.lerp(anticipation, extended, launchProgress);
        float stabilize = smoothstep(clamp((progress - 0.24F) / 0.76F, 0.0F, 1.0F));
        return FlightPose.lerp(launched, heroic(sample), stabilize);
    }

    private static FlightPose anticipation(FlightMotionSample sample) {
        float headPitch = headPitchForBody(-8.0F) - 1.0F;
        return new FlightPose(
                -8.0F, bank(sample) * 0.20F, 0.055F,
                headPitch, 0.0F, 0.0F,
                26.0F, 10.0F, 14.0F,
                26.0F, -10.0F, -14.0F,
                -18.0F, 3.0F, 4.0F,
                -18.0F, -3.0F, -4.0F);
    }

    private static FlightPose extension(FlightMotionSample sample) {
        float bodyPitch = -58.0F;
        float headPitch = headPitchForBody(bodyPitch) - 3.0F;
        return new FlightPose(
                bodyPitch, bank(sample) * 0.55F, 0.035F,
                headPitch, 0.0F, -bank(sample) * 0.25F,
                -132.0F, 13.0F, -8.0F,
                -128.0F, -13.0F, 8.0F,
                14.0F, 5.0F, -5.0F,
                10.0F, -5.0F, 5.0F);
    }

    private static FlightPose heroic(FlightMotionSample sample) {
        float bodyPitch = clamp(-78.0F + sample.trajectoryPitchDegrees() * 0.45F,
                MIN_BODY_PITCH, -58.0F);
        float bodyRoll = bank(sample);
        float headPitch = headPitchForBody(bodyPitch) - 2.0F;

        return new FlightPose(
                bodyPitch, bodyRoll, 0.020F,
                headPitch, headYaw(sample), headRoll(bodyRoll),
                -168.0F, 14.0F, -8.0F,
                -164.0F, -14.0F, 8.0F,
                10.0F, 6.0F, -6.0F,
                6.0F, -6.0F, 6.0F);
    }

    private static FlightPose cruise(FlightMotionSample sample) {
        FlightPose heroic = heroic(sample);
        float bodyRoll = bank(sample) * 0.85F;
        return new FlightPose(
                heroic.bodyPitchDegrees, bodyRoll, heroic.bodyLift,
                heroic.headXRotDegrees, headYaw(sample) * 0.7F, headRoll(bodyRoll),
                -170.0F, 12.0F, -6.0F,
                -166.0F, -12.0F, 6.0F,
                8.0F, 5.0F, -5.0F,
                5.0F, -5.0F, 5.0F);
    }

    private static FlightPose turning(FlightMotionSample sample) {
        float bodyPitch = heroic(sample).bodyPitchDegrees;
        float bodyRoll = bank(sample);
        float turn = clamp(sample.turnRateDegrees() * 0.5F, -10.0F, 10.0F);
        return new FlightPose(
                bodyPitch, bodyRoll, 0.018F,
                headPitchForBody(bodyPitch) - 1.0F, headYaw(sample) + turn * 0.5F,
                headRoll(bodyRoll),
                -164.0F, 14.0F + turn * 0.35F, -8.0F,
                -160.0F, -14.0F + turn * 0.35F, 8.0F,
                12.0F, 6.0F, -6.0F - turn * 0.25F,
                8.0F, -6.0F, 6.0F - turn * 0.25F);
    }

    private static FlightPose climb(FlightMotionSample sample) {
        float climb = clamp(sample.verticalSpeed() / 0.75F, 0.0F, 1.0F);
        float bodyPitch = clamp(-78.0F + 22.0F * climb
                        + sample.trajectoryPitchDegrees() * 0.25F,
                -112.0F, -52.0F);
        float bodyRoll = bank(sample);
        return new FlightPose(
                bodyPitch, bodyRoll, 0.016F,
                headPitchForBody(bodyPitch) - 6.0F, headYaw(sample) * 0.8F,
                headRoll(bodyRoll),
                -154.0F, 13.0F, -8.0F,
                -150.0F, -13.0F, 8.0F,
                16.0F, 5.0F, -6.0F,
                12.0F, -5.0F, 6.0F);
    }

    private static FlightPose dive(FlightMotionSample sample) {
        float dive = clamp(-sample.verticalSpeed() / 0.90F, 0.0F, 1.0F);
        float bodyPitch = clamp(-78.0F - 26.0F * dive
                        + sample.trajectoryPitchDegrees() * 0.30F,
                MIN_BODY_PITCH, -74.0F);
        float bodyRoll = bank(sample);
        return new FlightPose(
                bodyPitch, bodyRoll, 0.016F,
                headPitchForBody(bodyPitch) + 4.0F, headYaw(sample) * 0.8F,
                headRoll(bodyRoll),
                -182.0F, 12.0F, -8.0F,
                -178.0F, -12.0F, 8.0F,
                -4.0F, 4.0F, -6.0F,
                2.0F, -4.0F, 6.0F);
    }

    private static FlightPose accelerating(FlightMotionSample sample) {
        float bodyPitch = -82.0F;
        float bodyRoll = bank(sample);
        return new FlightPose(
                bodyPitch, bodyRoll, 0.012F,
                headPitchForBody(bodyPitch) - 6.0F, headYaw(sample) * 0.7F,
                headRoll(bodyRoll),
                -174.0F, 10.0F, -5.0F,
                -170.0F, -10.0F, 5.0F,
                7.0F, 4.0F, -4.0F,
                4.0F, -4.0F, 4.0F);
    }

    private static FlightPose boost(FlightMotionSample sample) {
        float bodyPitch = -86.0F;
        float bodyRoll = bank(sample) * 0.90F;
        return new FlightPose(
                bodyPitch, bodyRoll, 0.008F,
                headPitchForBody(bodyPitch) - 8.0F, headYaw(sample) * 0.55F,
                headRoll(bodyRoll),
                -10.0F, 5.0F, 7.0F,
                -6.0F, -5.0F, -7.0F,
                1.0F, 2.0F, -2.0F,
                -1.0F, -2.0F, 2.0F);
    }

    private static FlightPose decelerating(FlightMotionSample sample) {
        float bodyPitch = -64.0F;
        float bodyRoll = bank(sample) * 0.65F;
        return new FlightPose(
                bodyPitch, bodyRoll, 0.030F,
                headPitchForBody(bodyPitch) - 2.0F, headYaw(sample) * 0.8F,
                headRoll(bodyRoll),
                -30.0F, 32.0F, 22.0F,
                -26.0F, -32.0F, -22.0F,
                -12.0F, 6.0F, 5.0F,
                -8.0F, -6.0F, -5.0F);
    }

    private static FlightPose braking(FlightMotionSample sample) {
        float bodyPitch = -66.0F;
        float bodyRoll = bank(sample) * 0.40F;
        return new FlightPose(
                bodyPitch, bodyRoll, 0.025F,
                headPitchForBody(bodyPitch) - 1.0F, headYaw(sample) * 0.6F,
                headRoll(bodyRoll),
                -8.0F, 14.0F, 20.0F,
                -8.0F, -14.0F, -20.0F,
                -5.0F, 4.0F, 3.0F,
                1.0F, -4.0F, -3.0F);
    }

    private static FlightPose maneuver(FlightMotionSample sample) {
        float bodyPitch = -80.0F;
        return new FlightPose(
                bodyPitch, 0.0F, 0.010F,
                headPitchForBody(bodyPitch) - 2.0F, 0.0F, 0.0F,
                -176.0F, 8.0F, -5.0F,
                -172.0F, -8.0F, 5.0F,
                4.0F, 2.0F, -2.0F,
                2.0F, -2.0F, 2.0F);
    }

    private static FlightPose landing(FlightMotionSample sample) {
        float impact = clamp(sample.landingImpact(), 0.0F, 1.0F);
        FlightPose contact = landingContact(impact);
        if (sample.recoveryProgress() > 0.0F) {
            return FlightPose.lerp(contact, FlightPose.VANILLA,
                    smoothstep(clamp(sample.recoveryProgress(), 0.0F, 1.0F)));
        }

        float progress = smoothstep(clamp(sample.landingProgress(), 0.0F, 1.0F));
        return FlightPose.lerp(landingApproach(sample, impact), contact, progress);
    }

    private static FlightPose landingApproach(FlightMotionSample sample, float impact) {
        float bodyPitch = -28.0F + 12.0F * impact;
        float bodyRoll = bank(sample) * 0.45F;
        return new FlightPose(
                bodyPitch, bodyRoll, 0.015F,
                headPitchForBody(bodyPitch) - 1.0F, headYaw(sample) * 0.5F,
                headRoll(bodyRoll),
                -58.0F, 24.0F, 26.0F,
                -54.0F, -24.0F, -26.0F,
                -10.0F, 5.0F, 4.0F,
                6.0F, -5.0F, -4.0F);
    }

    private static FlightPose landingContact(float impact) {
        float bodyPitch = -8.0F + 4.0F * impact;
        float kneeBend = -20.0F - 14.0F * impact;
        return new FlightPose(
                bodyPitch, 0.0F, 0.045F + 0.025F * impact,
                headPitchForBody(bodyPitch) - 2.0F, 0.0F, 0.0F,
                20.0F, 12.0F, 16.0F,
                18.0F, -12.0F, -16.0F,
                kneeBend, 4.0F, 4.0F,
                kneeBend - 4.0F, -4.0F, -4.0F);
    }

    private static float bank(FlightMotionSample sample) {
        return clamp(sample.bankDegrees(), -MAX_BODY_ROLL, MAX_BODY_ROLL);
    }

    private static float headYaw(FlightMotionSample sample) {
        // aimError is positive when the crosshair is left of the trajectory and
        // head yaw follows the same sign in the vanilla model.
        float combined = sample.aimErrorDegrees() * 0.55F + sample.turnRateDegrees() * 0.12F;
        return clamp(combined, -MAX_HEAD_YAW, MAX_HEAD_YAW);
    }

    private static float headRoll(float bodyRoll) {
        return clamp(-bodyRoll * 0.55F, -MAX_HEAD_ROLL, MAX_HEAD_ROLL);
    }

    private static float turnRoll(float turnDegrees, float gain, float max) {
        return clamp(turnDegrees * gain, -max, max);
    }

    private static float smoothstep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    /** Package-visible for tests. */
    static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
