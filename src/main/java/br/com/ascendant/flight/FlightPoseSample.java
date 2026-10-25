package br.com.ascendant.flight;

/**
 * One interpolated render sample.
 *
 * <p>The maneuver angles are kept outside {@link FlightPose} on purpose. They
 * are authoritative rotations derived from the server-synced maneuver ticks,
 * not posture targets that participate in pose blending. Keeping them separate
 * lets a 360-degree loop or roll use the full turn while ordinary poses keep
 * their small, readable joint range.</p>
 *
 * <p>{@code bodyYawOffsetDegrees} is a presentation-only yaw around the world
 * vertical axis. It lets the body keep facing the trajectory while the camera
 * turns, so a free 180-degree turn reads as a manoeuvre instead of the model
 * snapping rigidly to the crosshair.</p>
 */
public record FlightPoseSample(FlightPose pose, float weight,
                               float bodyYawOffsetDegrees,
                               float maneuverPitchDegrees, float maneuverRollDegrees) {

    public FlightPoseSample(FlightPose pose, float weight) {
        this(pose, weight, 0.0F, 0.0F, 0.0F);
    }

    public FlightPoseSample(FlightPose pose, float weight,
                            float maneuverPitchDegrees, float maneuverRollDegrees) {
        this(pose, weight, 0.0F, maneuverPitchDegrees, maneuverRollDegrees);
    }
}
