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

    long getLastToggleGameTick();

    void setLastToggleGameTick(long gameTick);

    boolean hasLeftGround();

    void setHasLeftGround(boolean hasLeftGround);

    boolean hadNoGravity();

    void setHadNoGravity(boolean hadNoGravity);

    void clear();
}
