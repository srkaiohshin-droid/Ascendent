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
    /** Vertical loop: one complete rotation in the vertical travel plane. */
    int MANEUVER_LOOP = 2;
    /** Half aileron roll: ends with the body inverted until the command is repeated. */
    int MANEUVER_ROLL_180 = 3;

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
     * @param kind one of {@link #MANEUVER_ROLL_360}, {@link #MANEUVER_LOOP} or
     *             {@link #MANEUVER_ROLL_180}
     */
    boolean startManeuver(int kind, int direction, int durationTicks);

    /** Sets the synced maneuver state without applying cooldown or validation. */
    void setManeuver(int kind, int direction, int ticksRemaining);

    /**
     * Stores the launch basis of a loop. The loop must keep flying along the
     * direction it started with, even after the body has rotated past the top of
     * the circle where the instantaneous velocity points backwards.
     */
    void setManeuverBasis(float yawDegrees, float speed);

    float getManeuverYaw();

    float getManeuverSpeed();

    /** Advances the maneuver and cooldown by one tick. */
    void tickManeuver();

    void clearManeuver();

    void clear();
}
