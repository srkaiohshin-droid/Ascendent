package br.com.ascendant.flight;

/**
 * One interpolated render sample.
 *
 * <p>The maneuvre angles are kept outside {@link FlightPose} on purpose. They
 * are authoritative rotations derived from the server-synced maneuvre ticks,
 * not posture targets that participate in pose blending. Keeping them separate
 * lets a 360-degree loop or roll use the full turn while ordinary poses keep
 * their small, readable joint range.</p>
 *
 * <p>{@code rollOverlay} is the additive barrel-roll limb/body-pitch layer. It
 * is interpolated separately from the ordinary pose so the reference accents
 * survive the client's per-tick pose smoothing.</p>
 */
public record FlightPoseSample(FlightPose pose, float weight,
                               float maneuverPitchDegrees, float maneuverRollDegrees,
                               FlightPose rollOverlay) {

    public FlightPoseSample(FlightPose pose, float weight) {
        this(pose, weight, 0.0F, 0.0F, FlightPose.VANILLA);
    }

    public FlightPoseSample(FlightPose pose, float weight,
                            float maneuverPitchDegrees, float maneuverRollDegrees) {
        this(pose, weight, maneuverPitchDegrees, maneuverRollDegrees, FlightPose.VANILLA);
    }
}
