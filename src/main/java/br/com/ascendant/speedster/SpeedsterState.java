package br.com.ascendant.speedster;

import net.minecraftforge.common.capabilities.AutoRegisterCapability;

/** Transient speedster state; movement and activation remain server-authoritative. */
@AutoRegisterCapability
public interface SpeedsterState {
    int FORWARD = 1;
    int BACK = 1 << 1;
    int LEFT = 1 << 2;
    int RIGHT = 1 << 3;
    int BRAKE = 1 << 4;
    int BOOST = 1 << 5;
    int VALID_INPUT_MASK = FORWARD | BACK | LEFT | RIGHT | BRAKE | BOOST;
    int MIN_LEVEL = 1;
    int MAX_LEVEL = 10;

    boolean isActive();

    void setActive(boolean active);

    int getLevel();

    void setLevel(int level);

    int getInputMask();

    void setInputMask(int inputMask);

    boolean acceptInput(int inputMask, long gameTick);

    long getLastInputGameTick();

    boolean canAcceptControl(long gameTick);

    void markControlAccepted(long gameTick);

    void clearInput();

    void clear();
}
