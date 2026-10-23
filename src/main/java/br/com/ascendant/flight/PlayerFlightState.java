package br.com.ascendant.flight;

final class PlayerFlightState implements FlightState {
    private boolean active;
    private int inputMask;
    private long lastInputGameTick = Long.MIN_VALUE;
    private boolean hasLeftGround;
    private boolean hadNoGravity;
    private int maneuverDirection;
    private int maneuverTicksRemaining;
    private int maneuverCooldownTicks;

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
            clearManeuver();
            maneuverCooldownTicks = 0;
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
    public int getManeuverDirection() {
        return maneuverDirection;
    }

    @Override
    public int getManeuverTicksRemaining() {
        return maneuverTicksRemaining;
    }

    @Override
    public int getManeuverCooldownTicks() {
        return maneuverCooldownTicks;
    }

    @Override
    public void setManeuverCooldownTicks(int ticks) {
        maneuverCooldownTicks = Math.max(0, ticks);
    }

    @Override
    public boolean startManeuver(int direction, int durationTicks) {
        if (direction != -1 && direction != 1) {
            return false;
        }
        if (maneuverTicksRemaining > 0 || maneuverCooldownTicks > 0 || durationTicks <= 0) {
            return false;
        }
        maneuverDirection = direction;
        maneuverTicksRemaining = durationTicks;
        return true;
    }

    @Override
    public void setManeuver(int direction, int ticksRemaining) {
        maneuverDirection = direction == -1 || direction == 1 ? direction : 0;
        maneuverTicksRemaining = Math.max(0, ticksRemaining);
        if (maneuverTicksRemaining == 0) {
            maneuverDirection = 0;
        }
    }

    @Override
    public void tickManeuver() {
        if (maneuverTicksRemaining > 0) {
            maneuverTicksRemaining--;
            if (maneuverTicksRemaining == 0) {
                maneuverDirection = 0;
            }
        }
        if (maneuverCooldownTicks > 0) {
            maneuverCooldownTicks--;
        }
    }

    @Override
    public void clearManeuver() {
        maneuverDirection = 0;
        maneuverTicksRemaining = 0;
    }

    @Override
    public void clear() {
        active = false;
        inputMask = 0;
        lastInputGameTick = Long.MIN_VALUE;
        hasLeftGround = false;
        hadNoGravity = false;
        maneuverDirection = 0;
        maneuverTicksRemaining = 0;
        maneuverCooldownTicks = 0;
    }
}
