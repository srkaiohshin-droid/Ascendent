package br.com.ascendant.core;

/**
 * Triggers that can activate an ability.
 */
public enum AbilityTrigger {
    /** Activated via key press or hotkey slot. */
    KEY_PRESS,
    /** Activated when pressing key while sneaking. */
    SNEAK_KEY,
    /** Triggered automatically on melee attack hit. */
    ATTACK_HIT,
    /** Triggered reactively when taking damage. */
    HURT_EVENT,
    /** Triggered on every game tick (e.g. passive monitoring or aura). */
    TICK,
    /** Triggered to toggle state on/off. */
    TOGGLE,
    /** Always active passively without input. */
    PASSIVE
}
