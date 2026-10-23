package br.com.ascendant.flight;

import net.minecraftforge.common.capabilities.AutoRegisterCapability;

/** Runtime-only flight state. This state is deliberately not written to NBT. */
@AutoRegisterCapability
public interface FlightState {
    int FORWARD = 1;
    int BACK = 1 << 1;
    int LEFT = 1 << 2;
    int RIGHT = 1 << 3;
    int ASCEND = 1 << 4;
    int DESCEND = 1 << 5;
    int BRAKE = 1 << 6;
    int VALID_INPUT_MASK = FORWARD | BACK | LEFT | RIGHT | ASCEND | DESCEND | BRAKE;

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

    /** -1 for a left-hand roll, 1 for a right-hand roll, 0 when idle. */
    int getManeuverDirection();

    /** Ticks left in the current aerial maneuver; zero means no maneuver. */
    int getManeuverTicksRemaining();

    int getManeuverCooldownTicks();

    void setManeuverCooldownTicks(int ticks);

    /**
     * Starts a full-body aerial roll. The server owns the duration and validates
     * that no maneuver is already running and the cooldown has elapsed.
     */
    boolean startManeuver(int direction, int durationTicks);

    /** Sets the synced maneuver state without applying cooldown or validation. */
    void setManeuver(int direction, int ticksRemaining);

    /** Advances the maneuver and cooldown by one tick. */
    void tickManeuver();

    void clearManeuver();

    void clear();
}
