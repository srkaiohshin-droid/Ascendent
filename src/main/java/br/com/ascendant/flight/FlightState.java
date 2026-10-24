package br.com.ascendant.flight;

import net.minecraftforge.common.capabilities.AutoRegisterCapability;

/** Runtime-only flight state. This state is deliberately not written to NBT. */
@AutoRegisterCapability
public interface FlightState {
    int FORWARD = 1;
    int BACK = 1 << 1;
    /** Aircraft-style bank / turn to the left while flying. */
    int LEFT = 1 << 2;
    /** Aircraft-style bank / turn to the right while flying. */
    int RIGHT = 1 << 3;
    /** Nose-up pitch control while flying (also the classic ascend request). */
    int ASCEND = 1 << 4;
    /** Nose-down pitch control while flying (also the classic descend request). */
    int DESCEND = 1 << 5;
    int BRAKE = 1 << 6;
    int VALID_INPUT_MASK = FORWARD | BACK | LEFT | RIGHT | ASCEND | DESCEND | BRAKE;

    int MANEUVER_NONE = 0;
    /** Aileron roll: one complete rotation about the longitudinal (travel) axis. */
    int MANEUVER_ROLL_360 = 1;
    /**
     * Vertical pull-up loop. The server owns a collision-checked circular
     * trajectory, so this is a real flight path and not a render-only effect.
     */
    int MANEUVER_LOOP = 2;
    /**
     * Reserved half-roll. It is kept as a distinct kind so it is never confused
     * with the 180-degree heading reversal below.
     */
    int MANEUVER_ROLL_180 = 3;
    /**
     * Horizontal 180-degree U-turn. Like {@link #MANEUVER_LOOP}, the heading
     * change comes from a collision-checked server trajectory.
     */
    int MANEUVER_REVERSAL = 4;

    /** Why the last flight session ended. Presentation uses this to pick landing or exit. */
    int END_NONE = 0;
    int END_LANDED = 1;
    int END_MANUAL = 2;
    int END_REVOKED = 3;
    int END_LIFECYCLE = 4;

    boolean isActive();

    void setActive(boolean active);

    int getInputMask();

    void setInputMask(int inputMask);

    /** Accepts only a bounded input mask and at most one packet per server tick. */
    boolean acceptInput(int inputMask, long gameTick);

    long getLastInputGameTick();

    void clearInput();

    boolean hasLeftGround();

    void setHasLeftGround(boolean hasLeftGround);

    /** True when this flight session started from a grounded activation. */
    boolean wasLaunchedFromGround();

    void setLaunchedFromGround(boolean launchedFromGround);

    /** One of the {@code END_*} constants. */
    int getLastEndReason();

    void setLastEndReason(int reason);

    boolean hadNoGravity();

    void setHadNoGravity(boolean hadNoGravity);

    /** One of the {@code MANEUVER_*} constants. */
    int getManeuverKind();

    /** -1 for a left-hand roll, 1 for a right-hand roll, 0 when idle. */
    int getManeuverDirection();

    /** Ticks left in the current aerial maneuver; zero means no maneuver. */
    int getManeuverTicksRemaining();

    int getManeuverCooldownTicks();

    void setManeuverCooldownTicks(int ticks);

    /** True while the body is deliberately flying upside down after a half roll. */
    boolean isInverted();

    void setInverted(boolean inverted);

    /**
     * Starts a full-body aerial maneuver. The server owns the duration and
     * validates that no maneuver is already running and the cooldown elapsed.
     */
    boolean startManeuver(int direction, int durationTicks);

    /**
     * Starts a typed aerial maneuver.
     *
     * @param kind one of {@link #MANEUVER_ROLL_360}, {@link #MANEUVER_LOOP},
     *             {@link #MANEUVER_ROLL_180} or {@link #MANEUVER_REVERSAL}
     */
    boolean startManeuver(int kind, int direction, int durationTicks);

    /** Sets the synced maneuver state without applying cooldown or validation. */
    void setManeuver(int kind, int direction, int ticksRemaining);

    /**
     * True when the last trajectory maneuver was stopped by the safety sweep
     * instead of finishing its planned path. Clients use it to end playback
     * immediately; a normal completion lets playback run to its final pose.
     */
    boolean wasManeuverAborted();

    void setManeuverAborted(boolean aborted);

    /**
     * Stores the entry heading and speed shared by trajectory maneuvers. The
     * analytic path is built from this basis, so the server, the owning client
     * and the animation all compute the same curve.
     */
    void setManeuverBasis(float yawDegrees, float speed);

    float getManeuverYaw();

    float getManeuverSpeed();

    /** Advances the maneuver and cooldown by one tick. */
    void tickManeuver();

    void clearManeuver();

    void clear();
}
