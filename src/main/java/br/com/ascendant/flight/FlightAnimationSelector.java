package br.com.ascendant.flight;

/**
 * Deterministic flight animation classifier.
 *
 * <p>The selector consumes one {@link FlightMotionSample} per client tick and
 * keeps the previous state plus the number of ticks already spent in it. The
 * hysteresis bands and the minimum dwell prevent the pose from flickering when
 * the speed, the trajectory pitch or the turn rate sits exactly on a threshold.
 * The class is pure so it can be exercised by server-side GameTests and does not
 * depend on any client class.</p>
 */
public final class FlightAnimationSelector {
    /** A non-critical state must survive this many ticks before it can be replaced. */
    public static final int MIN_DWELL_TICKS = 3;

    /** Speed bands in blocks per tick. The second value is the exit threshold. */
    public static final float HOVER_ENTER_SPEED = 0.075F;
    public static final float HOVER_EXIT_SPEED = 0.120F;
    public static final float SLOW_ENTER_SPEED = 0.220F;
    public static final float SLOW_EXIT_SPEED = 0.160F;
    public static final float CRUISE_ENTER_SPEED = 0.500F;
    public static final float CRUISE_EXIT_SPEED = 0.440F;

    /** Degrees of real heading change per tick. */
    public static final float TURN_ENTER_DEGREES = 5.0F;
    public static final float TURN_EXIT_DEGREES = 2.5F;
    /** Trajectory pitch in degrees, positive when climbing. */
    public static final float CLIMB_ENTER_DEGREES = 9.0F;
    public static final float CLIMB_EXIT_DEGREES = 5.0F;
    public static final float DIVE_ENTER_DEGREES = -9.0F;
    public static final float DIVE_EXIT_DEGREES = -5.0F;

    /** Minimum longitudinal acceleration before the accel/decel poses engage. */
    public static final float ACCELERATION_ENTER = 0.008F;
    public static final float DECELERATION_ENTER = -0.008F;

    /**
     * Minimum vertical speed for a pitch-based climb/dive classification.
     * The idle hover bob reaches 0.025 blocks per tick, so anything at or
     * below that band must never turn a stationary hover into a dive.
     */
    public static final float VERTICAL_CLASSIFICATION_SPEED = 0.05F;
    /** Minimum horizontal speed before a trajectory pitch is meaningful. */
    public static final float TRAJECTORY_HORIZONTAL_SPEED = 0.10F;

    private FlightAnimationSelector() {
    }

    /**
     * Selects the state for this tick.
     *
     * @param previous    state selected on the previous tick, or {@code null} on
     *                    the first tick
     * @param ticksInState ticks already spent in {@code previous}
     * @param sample      current authoritative motion telemetry
     */
    public static FlightAnimationState select(FlightAnimationState previous, int ticksInState,
                                              FlightMotionSample sample) {
        if (sample == null || !sample.active()) {
            return FlightAnimationState.EXIT;
        }
        if (sample.maneuvering()) {
            return FlightAnimationState.MANEUVER;
        }
        if (sample.activeTicks() < takeoffTicks(sample)) {
            return FlightAnimationState.TAKEOFF;
        }
        if (sample.landingProgress() > 0.0F) {
            return FlightAnimationState.LANDING;
        }
        if (sample.braking()) {
            return FlightAnimationState.BRAKING;
        }

        FlightAnimationState desired = desired(previous, sample);
        if (previous == null || previous == FlightAnimationState.EXIT) {
            return desired;
        }
        if (desired == previous) {
            return previous;
        }
        // Critical states may interrupt immediately. Ordinary states must respect
        // the dwell so a value hovering on a threshold does not stutter.
        if (ticksInState < MIN_DWELL_TICKS && priority(desired) <= priority(previous)) {
            return previous;
        }
        return desired;
    }

    /** Number of ticks the takeoff pose owns after activation. */
    public static int takeoffTicks(FlightMotionSample sample) {
        return sample.launchedFromGround() ? FlightPoseLibrary.TAKEOFF_GROUND_TICKS
                : FlightPoseLibrary.TAKEOFF_AIR_TICKS;
    }

    private static FlightAnimationState desired(FlightAnimationState previous, FlightMotionSample sample) {
        if (sample.boosting()) {
            return FlightAnimationState.ACCELERATING;
        }
        if (isClimbing(previous, sample)) {
            return FlightAnimationState.CLIMB;
        }
        if (isDiving(previous, sample)) {
            return FlightAnimationState.DIVE;
        }
        if (isTurning(previous, sample)) {
            return FlightAnimationState.TURNING;
        }

        float speed = sample.speed();
        if (speed < (previous == FlightAnimationState.HOVER ? HOVER_EXIT_SPEED : HOVER_ENTER_SPEED)) {
            return FlightAnimationState.HOVER;
        }
        if (speed < (previous == FlightAnimationState.SLOW_FLIGHT ? SLOW_EXIT_SPEED : SLOW_ENTER_SPEED)) {
            return FlightAnimationState.SLOW_FLIGHT;
        }
        if (sample.accelerationPerTick() >= ACCELERATION_ENTER) {
            return FlightAnimationState.ACCELERATING;
        }
        if (sample.accelerationPerTick() <= DECELERATION_ENTER) {
            return FlightAnimationState.DECELERATING;
        }
        if (speed >= (previous == FlightAnimationState.CRUISE ? CRUISE_EXIT_SPEED : CRUISE_ENTER_SPEED)
                && Math.abs(sample.verticalSpeed()) < 0.05F
                && Math.abs(sample.trajectoryPitchDegrees()) < CLIMB_EXIT_DEGREES) {
            return FlightAnimationState.CRUISE;
        }
        return FlightAnimationState.HEROIC_FLIGHT;
    }

    private static boolean isClimbing(FlightAnimationState previous, FlightMotionSample sample) {
        float threshold = previous == FlightAnimationState.CLIMB
                ? CLIMB_EXIT_DEGREES : CLIMB_ENTER_DEGREES;
        if (sample.verticalSpeed() >= 0.060F) {
            return true;
        }
        // A near-zero horizontal speed with a small vertical bob reports a
        // trajectory pitch of 90 degrees. Requiring both a real vertical rate
        // and a real horizontal trajectory keeps hover out of the climb pose.
        return sample.verticalSpeed() >= VERTICAL_CLASSIFICATION_SPEED
                && sample.horizontalSpeed() >= TRAJECTORY_HORIZONTAL_SPEED
                && sample.trajectoryPitchDegrees() >= threshold;
    }

    private static boolean isDiving(FlightAnimationState previous, FlightMotionSample sample) {
        float threshold = previous == FlightAnimationState.DIVE
                ? DIVE_EXIT_DEGREES : DIVE_ENTER_DEGREES;
        if (sample.verticalSpeed() <= -0.080F) {
            return true;
        }
        return sample.verticalSpeed() <= -VERTICAL_CLASSIFICATION_SPEED
                && sample.horizontalSpeed() >= TRAJECTORY_HORIZONTAL_SPEED
                && sample.trajectoryPitchDegrees() <= threshold;
    }

    private static boolean isTurning(FlightAnimationState previous, FlightMotionSample sample) {
        float threshold = previous == FlightAnimationState.TURNING
                ? TURN_EXIT_DEGREES : TURN_ENTER_DEGREES;
        return Math.abs(sample.turnRateDegrees()) >= threshold;
    }

    private static int priority(FlightAnimationState state) {
        return switch (state) {
            case MANEUVER -> 120;
            case LANDING -> 110;
            case TAKEOFF -> 100;
            case BRAKING -> 90;
            case ACCELERATING -> 80;
            case CLIMB, DIVE -> 70;
            case TURNING -> 60;
            case DECELERATING -> 50;
            case HEROIC_FLIGHT -> 40;
            case CRUISE -> 35;
            case SLOW_FLIGHT -> 30;
            case HOVER -> 20;
            case EXIT -> 0;
        };
    }
}
