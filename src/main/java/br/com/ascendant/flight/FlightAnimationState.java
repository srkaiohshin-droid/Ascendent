package br.com.ascendant.flight;

/**
 * Discrete flight animation states. The state only selects a target pose; the
 * joints are produced by {@link FlightPoseLibrary} and blended over time by the
 * client animation controller.
 *
 * <p>The reference flight has no scripted trajectory. Hovering is an upright
 * pose that leans with the horizontal velocity, boosting is a horizontal pose,
 * and the maneuvre pose adds the server-authorised loop or roll on top of the
 * neutral flight silhouette.</p>
 */
public enum FlightAnimationState {
    /** Upright hover with arms down and a small velocity-driven lean. */
    HOVER,
    /** Sprint boost: nearly horizontal body, arms at the sides and legs trailing. */
    BOOST,
    /** A server-authorised loop or roll is being played. */
    MANEUVER,
    /** Flight has ended; the controller blends the pose back to vanilla. */
    EXIT
}
