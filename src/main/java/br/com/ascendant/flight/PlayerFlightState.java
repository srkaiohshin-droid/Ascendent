package br.com.ascendant.flight;

final class PlayerFlightState implements FlightState {
    private boolean active;
    private int inputMask;
    private long lastInputGameTick = Long.MIN_VALUE;
    private boolean hasLeftGround;
    private boolean hadNoGravity;
    private boolean launchedFromGround;
    private int lastEndReason = END_NONE;
    private int maneuverKind = MANEUVER_NONE;
    private int maneuverDirection;
    private int maneuverTicksRemaining;
    private int maneuverCooldownTicks;
    private boolean maneuverAborted;
    private boolean inverted;
    private float maneuverYaw;
    private float maneuverSpeed;

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        if (active && !this.active) {
            inputMask = 0;
            lastInputGameTick = Long.MIN_VALUE;
            launchedFromGround = false;
            lastEndReason = END_NONE;
        }
        this.active = active;
        if (!active) {
            inputMask = 0;
            hasLeftGround = false;
            clearManeuver();
            maneuverCooldownTicks = 0;
            maneuverAborted = false;
            inverted = false;
            maneuverYaw = 0.0F;
            maneuverSpeed = 0.0F;
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
        // Opposing strafe directions cancel instead of both being held down.
        if ((inputMask & LEFT) != 0 && (inputMask & RIGHT) != 0) {
            inputMask &= ~(LEFT | RIGHT);
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
    public boolean wasLaunchedFromGround() {
        return launchedFromGround;
    }

    @Override
    public void setLaunchedFromGround(boolean launchedFromGround) {
        this.launchedFromGround = launchedFromGround;
    }

    @Override
    public int getLastEndReason() {
        return lastEndReason;
    }

    @Override
    public void setLastEndReason(int reason) {
        lastEndReason = isValidEndReason(reason) ? reason : END_NONE;
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
    public int getManeuverKind() {
        return maneuverKind;
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
    public boolean isInverted() {
        return inverted;
    }

    @Override
    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }

    @Override
    public boolean startManeuver(int direction, int durationTicks) {
        return startManeuver(MANEUVER_ROLL_360, direction, durationTicks);
    }

    @Override
    public boolean startManeuver(int kind, int direction, int durationTicks) {
        if (!isValidKind(kind) || direction != -1 && direction != 1) {
            return false;
        }
        if (maneuverTicksRemaining > 0 || maneuverCooldownTicks > 0 || durationTicks <= 0) {
            return false;
        }
        maneuverKind = kind;
        maneuverDirection = direction;
        maneuverTicksRemaining = durationTicks;
        maneuverAborted = false;
        return true;
    }

    @Override
    public boolean wasManeuverAborted() {
        return maneuverAborted;
    }

    @Override
    public void setManeuverAborted(boolean aborted) {
        maneuverAborted = aborted;
    }

    @Override
    public void setManeuverBasis(float yawDegrees, float speed) {
        maneuverYaw = yawDegrees;
        maneuverSpeed = Math.max(0.0F, speed);
    }

    @Override
    public float getManeuverYaw() {
        return maneuverYaw;
    }

    @Override
    public float getManeuverSpeed() {
        return maneuverSpeed;
    }

    @Override
    public void setManeuver(int kind, int direction, int ticksRemaining) {
        maneuverKind = isValidKind(kind) ? kind : MANEUVER_NONE;
        maneuverDirection = direction == -1 || direction == 1 ? direction : 0;
        maneuverTicksRemaining = Math.max(0, ticksRemaining);
        if (maneuverTicksRemaining == 0) {
            maneuverKind = MANEUVER_NONE;
            maneuverDirection = 0;
        }
    }

    @Override
    public void tickManeuver() {
        if (maneuverTicksRemaining > 0) {
            maneuverTicksRemaining--;
            if (maneuverTicksRemaining == 0) {
                if (maneuverKind == MANEUVER_ROLL_180) {
                    inverted = !inverted;
                }
                maneuverKind = MANEUVER_NONE;
                maneuverDirection = 0;
            }
        }
        if (maneuverKind == MANEUVER_NONE && maneuverTicksRemaining == 0) {
            maneuverAborted = false;
        }
        if (maneuverCooldownTicks > 0) {
            maneuverCooldownTicks--;
        }
    }

    @Override
    public void clearManeuver() {
        maneuverKind = MANEUVER_NONE;
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
        launchedFromGround = false;
        lastEndReason = END_NONE;
        inverted = false;
        clearManeuver();
        maneuverCooldownTicks = 0;
        maneuverAborted = false;
        maneuverYaw = 0.0F;
        maneuverSpeed = 0.0F;
    }

    private static boolean isValidKind(int kind) {
        return kind == MANEUVER_ROLL_360 || kind == MANEUVER_LOOP
                || kind == MANEUVER_ROLL_180;
    }

    private static boolean isValidEndReason(int reason) {
        return reason >= END_NONE && reason <= END_LIFECYCLE;
    }
}
