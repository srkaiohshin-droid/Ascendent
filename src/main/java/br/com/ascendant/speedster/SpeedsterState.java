package br.com.ascendant.speedster;

import net.minecraftforge.common.capabilities.AutoRegisterCapability;

/**
 * Transient speedster state; movement and activation remain server-authoritative.
 * Carries the current level profile, kinetic energy buildup, dash cooldown and
 * maneuver state.
 */
@AutoRegisterCapability
public interface SpeedsterState {
    int FORWARD = 1;
    int BACK = 1 << 1;
    int LEFT = 1 << 2;
    int RIGHT = 1 << 3;
    int BRAKE = 1 << 4;
    int BOOST = 1 << 5;
    int ATTACK = 1 << 6;
    int VALID_INPUT_MASK = FORWARD | BACK | LEFT | RIGHT | BRAKE | BOOST | ATTACK;
    SpeedsterLevel MIN_LEVEL = SpeedsterLevel.NONE;
    SpeedsterLevel MAX_LEVEL = SpeedsterLevel.TRANSCENDENTAL;

    boolean isActive();

    void setActive(boolean active);

    SpeedsterLevel getLevel();

    void setLevel(SpeedsterLevel level);

    int getInputMask();

    void setInputMask(int inputMask);

    boolean acceptInput(int inputMask, long gameTick);

    long getLastInputGameTick();

    boolean canAcceptControl(long gameTick);

    void markControlAccepted(long gameTick);

    void clearInput();

    /**
     * Current kinetic energy buildup (0.0 to 1.0). Accumulates while sprinting,
     * decays when idle. Higher levels build and retain more kinetic energy.
     */
    double getKineticEnergy();

    void setKineticEnergy(double kinetic);

    /**
     * Ticks remaining until the next dash is available. 0 means ready.
     */
    int getDashCooldown();

    void setDashCooldown(int ticks);

    /**
     * Whether the player is currently performing a dash maneuver.
     */
    boolean isDashing();

    void setDashing(boolean dashing);

    void clear();

    /**
     * Returns the physics profile for the current level.
     */
    default SpeedsterProfile profile() {
        return SpeedsterProfile.forLevel(getLevel());
    }

    /**
     * Percepção acelerada: destaca ameaças próximas e reage a projéteis.
     * Ativo por janelas curtas, com cooldown. Nunca pausa o servidor.
     */
    boolean isPerceptionActive();

    void setPerceptionActive(boolean active);

    int getPerceptionCooldown();

    void setPerceptionCooldown(int ticks);

    /**
     * Sobrecarga de velocidade: aumento temporário de potência com custo de
     * energia cinética e cooldown depois do uso.
     */
    boolean isOverloadActive();

    void setOverloadActive(boolean active);

    int getOverloadCooldown();

    void setOverloadCooldown(int ticks);
}
