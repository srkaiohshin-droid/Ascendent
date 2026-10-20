package br.com.ascendant.flight;

final class PlayerFlightState implements FlightState {
    private boolean active;
    private int inputMask;
    private long lastInputGameTick = Long.MIN_VALUE;
    private long lastToggleGameTick = Long.MIN_VALUE;
    private boolean hasLeftGround;
    private boolean hadNoGravity;

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        if (active && !this.active) {
            inputMask = 0;
            lastInputGameTick = Long.MIN_VALUE;
        }
        this.active = active;
        if (!active) {
            inputMask = 0;
            hasLeftGround = false;
        }
    }

    @Override
    public int getInputMask() {
        return inputMask;
    }

    @Override
    public void setInputMask(int inputMask) {
        this.inputMask = inputMask & VALID_INPUT_MASK;
    }

    @Override
    public boolean acceptInput(int inputMask, long gameTick) {
        if ((inputMask & ~VALID_INPUT_MASK) != 0 || gameTick == lastInputGameTick) {
            return false;
        }
        this.inputMask = inputMask;
        lastInputGameTick = gameTick;
        return true;
    }

    @Override
    public long getLastInputGameTick() {
        return lastInputGameTick;
    }

    @Override
    public void clearInput() {
        inputMask = 0;
    }

    @Override
    public long getLastToggleGameTick() {
        return lastToggleGameTick;
    }

    @Override
    public void setLastToggleGameTick(long gameTick) {
        this.lastToggleGameTick = gameTick;
    }

    @Override
    public boolean hasLeftGround() {
        return hasLeftGround;
    }

    @Override
    public void setHasLeftGround(boolean hasLeftGround) {
        this.hasLeftGround = hasLeftGround;
    }

    @Override
    public boolean hadNoGravity() {
        return hadNoGravity;
    }

    @Override
    public void setHadNoGravity(boolean hadNoGravity) {
        this.hadNoGravity = hadNoGravity;
    }

    @Override
    public void clear() {
        active = false;
        inputMask = 0;
        lastInputGameTick = Long.MIN_VALUE;
        lastToggleGameTick = Long.MIN_VALUE;
        hasLeftGround = false;
        hadNoGravity = false;
    }
}
