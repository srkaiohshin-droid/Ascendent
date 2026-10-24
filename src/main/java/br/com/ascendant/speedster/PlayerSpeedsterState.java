package br.com.ascendant.speedster;

final class PlayerSpeedsterState implements SpeedsterState {
    private boolean active;
    private SpeedsterLevel level = MIN_LEVEL;
    private int inputMask;
    private long lastInputGameTick = Long.MIN_VALUE;
    private long lastControlGameTick = Long.MIN_VALUE;
    private double kineticEnergy;
    private int dashCooldown;
    private boolean dashing;
    private boolean perceptionActive;
    private int perceptionCooldown;
    private boolean overloadActive;
    private int overloadCooldown;

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            clearInput();
            setDashing(false);
        }
    }

    @Override
    public SpeedsterLevel getLevel() {
        return level;
    }

    @Override
    public void setLevel(SpeedsterLevel level) {
        this.level = level;
        if (level == SpeedsterLevel.NONE) {
            setKineticEnergy(0.0D);
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
    public double getKineticEnergy() {
        return kineticEnergy;
    }

    @Override
    public void setKineticEnergy(double kinetic) {
        this.kineticEnergy = Math.max(0.0D, Math.min(1.0D, kinetic));
    }

    @Override
    public int getDashCooldown() {
        return dashCooldown;
    }

    @Override
    public void setDashCooldown(int ticks) {
        this.dashCooldown = Math.max(0, ticks);
    }

    @Override
    public boolean isDashing() {
        return dashing;
    }

    @Override
    public void setDashing(boolean dashing) {
        this.dashing = dashing;
    }

    @Override
    public void clear() {
        active = false;
        level = MIN_LEVEL;
        kineticEnergy = 0.0D;
        dashCooldown = 0;
        dashing = false;
        perceptionActive = false;
        perceptionCooldown = 0;
        overloadActive = false;
        overloadCooldown = 0;
        clearInput();
        lastControlGameTick = Long.MIN_VALUE;
    }

    @Override
    public boolean isPerceptionActive() {
        return perceptionActive;
    }

    @Override
    public void setPerceptionActive(boolean active) {
        this.perceptionActive = active;
    }

    @Override
    public int getPerceptionCooldown() {
        return perceptionCooldown;
    }

    @Override
    public void setPerceptionCooldown(int ticks) {
        this.perceptionCooldown = Math.max(0, ticks);
    }

    @Override
    public boolean isOverloadActive() {
        return overloadActive;
    }

    @Override
    public void setOverloadActive(boolean active) {
        this.overloadActive = active;
    }

    @Override
    public int getOverloadCooldown() {
        return overloadCooldown;
    }

    @Override
    public void setOverloadCooldown(int ticks) {
        this.overloadCooldown = Math.max(0, ticks);
    }
}
