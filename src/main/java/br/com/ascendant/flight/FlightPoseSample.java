package br.com.ascendant.flight;

/**
 * One interpolated render sample.
 *
 * <p>The maneuver angles are kept outside {@link FlightPose} on purpose. They
 * are authoritative rotations derived from the server-synced maneuver ticks,
 * not posture targets that participate in pose blending. Keeping them separate
 * lets a 360-degree loop or roll use the full turn while ordinary poses keep
 * their small, readable joint range.</p>
 */
public record FlightPoseSample(FlightPose pose, float weight,
                               float maneuverPitchDegrees, float maneuverRollDegrees) {

    public FlightPoseSample(FlightPose pose, float weight) {
        this(pose, weight, 0.0F, 0.0F);
    }
}
