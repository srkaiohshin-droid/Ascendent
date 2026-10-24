package br.com.ascendant.flight;

/**
 * Immutable telemetry for one client tick of flight presentation.
 *
 * <p>The client builds this from the authoritative flight state plus the
 * player's real movement. The animation selector and the pose library only
 * consume this snapshot, which keeps state decisions deterministic, testable
 * and independent of the render frame rate.</p>
 *
 * <p>All speeds are blocks per tick. Angles are degrees. {@code bankDegrees}
 * is the already smoothed bank around the body's longitudinal axis;
 * {@code turnRateDegrees} is the actual horizontal heading change for the
 * tick, not the camera yaw. Keeping both values separate makes it possible to
 * bank into a curve while the velocity is still catching up with the look
 * direction.</p>
 */
public record FlightMotionSample(
        boolean active,
        int activeTicks,
        boolean launchedFromGround,
        float speed,
        float horizontalSpeed,
        float verticalSpeed,
        float trajectoryPitchDegrees,
        float accelerationPerTick,
        float turnRateDegrees,
        float aimErrorDegrees,
        float bankDegrees,
        boolean braking,
        boolean boosting,
        boolean maneuvering,
        float landingProgress,
        float landingImpact,
        float recoveryProgress) {

    public FlightMotionSample {
        activeTicks = Math.max(0, activeTicks);
        speed = finite(speed);
        horizontalSpeed = Math.max(0.0F, finite(horizontalSpeed));
        verticalSpeed = finite(verticalSpeed);
        trajectoryPitchDegrees = finite(trajectoryPitchDegrees);
        accelerationPerTick = finite(accelerationPerTick);
        turnRateDegrees = finite(turnRateDegrees);
        aimErrorDegrees = finite(aimErrorDegrees);
        bankDegrees = finite(bankDegrees);
        landingProgress = clamp01(landingProgress);
        landingImpact = clamp01(landingImpact);
        recoveryProgress = clamp01(recoveryProgress);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Returns a copy with the smoothed body/camera bank replaced. */
    public FlightMotionSample withBank(float bank) {
        return new FlightMotionSample(active, activeTicks, launchedFromGround, speed,
                horizontalSpeed, verticalSpeed, trajectoryPitchDegrees, accelerationPerTick,
                turnRateDegrees, aimErrorDegrees, bank, braking, boosting, maneuvering,
                landingProgress, landingImpact, recoveryProgress);
    }

    /** Returns a copy carrying the ground-approach progress for landing poses. */
    public FlightMotionSample withLanding(float progress, float impact) {
        return new FlightMotionSample(active, activeTicks, launchedFromGround, speed,
                horizontalSpeed, verticalSpeed, trajectoryPitchDegrees, accelerationPerTick,
                turnRateDegrees, aimErrorDegrees, bankDegrees, braking, boosting, maneuvering,
                progress, impact, recoveryProgress);
    }

    /** Returns a copy carrying the post-contact recovery progress. */
    public FlightMotionSample withRecovery(float progress) {
        return new FlightMotionSample(active, activeTicks, launchedFromGround, speed,
                horizontalSpeed, verticalSpeed, trajectoryPitchDegrees, accelerationPerTick,
                turnRateDegrees, aimErrorDegrees, bankDegrees, braking, boosting, maneuvering,
                landingProgress, landingImpact, progress);
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, finite(value)));
    }

    /**
     * Small explicit builder. A sample has enough independent telemetry that
     * positional construction would make call sites and tests error-prone.
     */
    public static final class Builder {
        private boolean active;
        private int activeTicks;
        private boolean launchedFromGround;
        private float speed;
        private float horizontalSpeed = Float.NaN;
        private float verticalSpeed;
        private float trajectoryPitchDegrees = Float.NaN;
        private float accelerationPerTick;
        private float turnRateDegrees;
        private float aimErrorDegrees;
        private float bankDegrees;
        private boolean braking;
        private boolean boosting;
        private boolean maneuvering;
        private float landingProgress;
        private float landingImpact;
        private float recoveryProgress;

        private Builder() {
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder activeTicks(int activeTicks) {
            this.activeTicks = activeTicks;
            return this;
        }

        public Builder launchedFromGround(boolean launchedFromGround) {
            this.launchedFromGround = launchedFromGround;
            return this;
        }

        public Builder speed(float speed) {
            this.speed = speed;
            return this;
        }

        public Builder horizontalSpeed(float horizontalSpeed) {
            this.horizontalSpeed = horizontalSpeed;
            return this;
        }

        public Builder verticalSpeed(float verticalSpeed) {
            this.verticalSpeed = verticalSpeed;
            return this;
        }

        public Builder trajectoryPitch(float pitchDegrees) {
            this.trajectoryPitchDegrees = pitchDegrees;
            return this;
        }

        public Builder acceleration(float accelerationPerTick) {
            this.accelerationPerTick = accelerationPerTick;
            return this;
        }

        public Builder turnRate(float turnRateDegrees) {
            this.turnRateDegrees = turnRateDegrees;
            return this;
        }

        public Builder aimError(float aimErrorDegrees) {
            this.aimErrorDegrees = aimErrorDegrees;
            return this;
        }

        public Builder bank(float bankDegrees) {
            this.bankDegrees = bankDegrees;
            return this;
        }

        public Builder braking(boolean braking) {
            this.braking = braking;
            return this;
        }

        public Builder boosting(boolean boosting) {
            this.boosting = boosting;
            return this;
        }

        public Builder maneuvering(boolean maneuvering) {
            this.maneuvering = maneuvering;
            return this;
        }

        public Builder landing(float progress, float impact) {
            this.landingProgress = progress;
            this.landingImpact = impact;
            return this;
        }

        public Builder recovery(float progress) {
            this.recoveryProgress = progress;
            return this;
        }

        public FlightMotionSample build() {
            float resolvedSpeed = finite(speed);
            float resolvedVertical = finite(verticalSpeed);
            float resolvedHorizontal = Float.isNaN(horizontalSpeed)
                    ? (float) Math.sqrt(Math.max(0.0D,
                    resolvedSpeed * resolvedSpeed - resolvedVertical * resolvedVertical))
                    : Math.max(0.0F, finite(horizontalSpeed));
            float trajectory = Float.isNaN(trajectoryPitchDegrees)
                    ? trajectoryPitch(resolvedVertical, resolvedHorizontal)
                    : trajectoryPitchDegrees;
            return new FlightMotionSample(active, activeTicks, launchedFromGround, resolvedSpeed,
                    resolvedHorizontal, resolvedVertical, trajectory, accelerationPerTick,
                    turnRateDegrees, aimErrorDegrees, bankDegrees, braking, boosting,
                    maneuvering, landingProgress, landingImpact, recoveryProgress);
        }

        private static float trajectoryPitch(float verticalSpeed, float horizontalSpeed) {
            if (horizontalSpeed <= 0.02F) {
                if (verticalSpeed > 0.02F) {
                    return 90.0F;
                }
                if (verticalSpeed < -0.02F) {
                    return -90.0F;
                }
                return 0.0F;
            }
            return (float) Math.toDegrees(Math.atan2(verticalSpeed, horizontalSpeed));
        }
    }
}
