package br.com.ascendant.core;

/**
 * The activation mode of a power.
 */
public enum PowerType {
    /** Continuously active while unlocked or equipped; applies passive modifiers or tick effects. */
    PASSIVE,
    /** Triggered instantly upon command, key press, or ability execution, typically with a cooldown. */
    ACTIVE,
    /** Toggled on and off by the player (e.g. flight mode, thermal vision, force field aura). */
    TOGGLE,
    /** Held down to charge power level or intensity before release. */
    CHARGED,
    /** Continuously channeled with per-tick cost, cancelled when input ends or energy depletes. */
    SUSTAINED
}
