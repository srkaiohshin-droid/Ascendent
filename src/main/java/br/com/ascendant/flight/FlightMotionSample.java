package br.com.ascendant.flight;

/**
 * Immutable telemetry for one client tick of flight presentation.
 *
 * <p>The client builds this from the authoritative flight state plus the
 * player's real movement. The animation selector and the pose library only
 * consume this snapshot, which keeps state decisions deterministic, testable
 * and independent of the render frame rate.</p>
 *
 * <p>All speeds are blocks per tick and all angles are degrees.
 * {@code bodyForward} and {@code bodyRight} are the horizontal velocity
 * direction expressed in the player's yaw frame, so they are in
 * {@code [-1, 1]}. {@code viewPitch} follows Minecraft's convention: positive
 * values look downwards. {@code idlePhase} is the reference low-speed hover
 * loop in {@code [0, 1)}, derived from the player's tick counter.</p>
 */
public record FlightMotionSample(
        boolean active,
        int activeTicks,
        float speed,
        float horizontalSpeed,
        float verticalSpeed,
        float viewPitch,
        float bodyForward,
        float bodyRight,
        boolean boosting,
        boolean maneuvering,
        float idlePhase) {

    public FlightMotionSample {
        activeTicks = Math.max(0, activeTicks);
        speed = finite(speed);
        horizontalSpeed = Math.max(0.0F, finite(horizontalSpeed));
        verticalSpeed = finite(verticalSpeed);
        viewPitch = finite(viewPitch);
        bodyForward = clampUnit(bodyForward);
        bodyRight = clampUnit(bodyRight);
        idlePhase = wrapUnit(idlePhase);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static float clampUnit(float value) {
        return Math.max(-1.0F, Math.min(1.0F, finite(value)));
    }

    private static float wrapUnit(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        float wrapped = value % 1.0F;
        return wrapped < 0.0F ? wrapped + 1.0F : wrapped;
    }

    /** Small explicit builder; a sample has too many fields for positional calls. */
    public static final class Builder {
        private boolean active;
        private int activeTicks;
        private float speed;
        private float horizontalSpeed = Float.NaN;
        private float verticalSpeed;
        private float viewPitch;
        private float bodyForward;
        private float bodyRight;
        private boolean boosting;
        private boolean maneuvering;
        private float idlePhase;

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

        public Builder viewPitch(float viewPitch) {
            this.viewPitch = viewPitch;
            return this;
        }

        public Builder bodyForward(float bodyForward) {
            this.bodyForward = bodyForward;
            return this;
        }

        public Builder bodyRight(float bodyRight) {
            this.bodyRight = bodyRight;
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

        public Builder idlePhase(float idlePhase) {
            this.idlePhase = idlePhase;
            return this;
        }

        public FlightMotionSample build() {
            float resolvedSpeed = finite(speed);
            float resolvedVertical = finite(verticalSpeed);
            float resolvedHorizontal = Float.isNaN(horizontalSpeed)
                    ? (float) Math.sqrt(Math.max(0.0D,
                    resolvedSpeed * resolvedSpeed - resolvedVertical * resolvedVertical))
                    : Math.max(0.0F, finite(horizontalSpeed));
            return new FlightMotionSample(active, activeTicks, resolvedSpeed,
                    resolvedHorizontal, resolvedVertical, viewPitch,
                    bodyForward, bodyRight, boosting, maneuvering, idlePhase);
        }
    }
}
