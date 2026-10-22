package br.com.ascendant.speedster;

final class PlayerSpeedsterState implements SpeedsterState {
    private boolean active;
    private int level = MIN_LEVEL;
    private int inputMask;
    private long lastInputGameTick = Long.MIN_VALUE;
    private long lastControlGameTick = Long.MIN_VALUE;

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            clearInput();
        }
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public void setLevel(int level) {
        this.level = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
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
        if ((inputMask & ~VALID_INPUT_MASK) != 0 || gameTick <= lastInputGameTick) {
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
    public boolean canAcceptControl(long gameTick) {
        return gameTick > lastControlGameTick;
    }

    @Override
    public void markControlAccepted(long gameTick) {
        lastControlGameTick = gameTick;
    }

    @Override
    public void clearInput() {
        inputMask = 0;
        lastInputGameTick = Long.MIN_VALUE;
    }

    @Override
    public void clear() {
        active = false;
        level = MIN_LEVEL;
        clearInput();
        lastControlGameTick = Long.MIN_VALUE;
    }
}
