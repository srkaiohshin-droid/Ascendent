package br.com.ascendant.catalog;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Pure, client-independent classification of a catalog entry.
 *
 * <p>The catalog screen and the tests share this model so a card, its badge, its
 * detail panel and the execute button all agree on why an entry can or cannot be
 * used. Keeping it free of Minecraft client classes also lets GameTests verify
 * the refusal reasons without opening a screen.</p>
 */
public final class CatalogEntryState {

    private CatalogEntryState() {
    }

    public enum ContentKind {
        POWER, ABILITY, SUIT, PACK
    }

    public enum State {
        /** Pack metadata, never a runtime action. */
        METADATA,
        /** Suit loadout that is already equipped. */
        EQUIPPED,
        /** Suit loadout available to equip. */
        AVAILABLE,
        /** Authoritative server state has not arrived yet. */
        LOADING,
        /** Registered definition without an executable mechanism. */
        TEMPLATE,
        /** Functional entry the player does not currently own or receive from a suit. */
        LOCKED,
        /** Toggle currently enabled. */
        ACTIVE,
        /** Manual action waiting for its cooldown. */
        COOLDOWN,
        /** Manual action available right now. */
        READY,
        /** Passive power; there is nothing to press. */
        PASSIVE,
        /** Ability driven by an automatic trigger rather than a key. */
        AUTOMATIC
    }

    /**
     * Facts needed to classify an entry. {@code manualAction} is true when the
     * definition maps to a real server request (activate/toggle), and
     * {@code owned} means the player currently holds the power/ability.
     */
    public record Facts(ContentKind kind, boolean functional, boolean initialized, boolean owned,
                        boolean toggled, int cooldownTicks, boolean manualAction, boolean equipped,
                        boolean packLoaded) {
        public Facts {
            Objects.requireNonNull(kind, "kind");
            if (cooldownTicks < 0) {
                throw new IllegalArgumentException("cooldownTicks cannot be negative");
            }
        }
    }

    /**
     * @param reasonKey  translation key explaining a refusal, or {@code null} when usable
     * @param reasonArg  integer argument for the reason (used by cooldown messages)
     */
    public record Assessment(State state, String labelKey, String labelFallback,
                             @Nullable String reasonKey, int reasonArg, boolean actionable) {
        public Assessment {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(labelKey, "labelKey");
            Objects.requireNonNull(labelFallback, "labelFallback");
        }

        public boolean hasReason() {
            return reasonKey != null;
        }
    }

    public static Assessment assess(Facts facts) {
        Objects.requireNonNull(facts, "facts");
        if (facts.kind() == ContentKind.PACK) {
            return new Assessment(State.METADATA,
                    "gui.ascendant.catalog.metadata_short", "Metadata",
                    "gui.ascendant.catalog.pack_not_actionable", 0, false);
        }
        if (facts.kind() == ContentKind.SUIT) {
            return facts.equipped()
                    ? new Assessment(State.EQUIPPED,
                    "gui.ascendant.catalog.suit_equipped", "Equipped",
                    "gui.ascendant.catalog.req.equipped", 0, false)
                    : new Assessment(State.AVAILABLE,
                    "gui.ascendant.catalog.suit_available", "Available loadout",
                    null, 0, true);
        }
        if (!facts.functional()) {
            return new Assessment(State.TEMPLATE,
                    "gui.ascendant.catalog.template_short", "Template",
                    "gui.ascendant.catalog.template_reason", 0, false);
        }
        if (!facts.initialized()) {
            return new Assessment(State.LOADING,
                    "gui.ascendant.catalog.loading_short", "Syncing",
                    "gui.ascendant.catalog.loading_state", 0, false);
        }
        if (!facts.owned()) {
            return new Assessment(State.LOCKED,
                    "gui.ascendant.catalog.locked_short", "Locked",
                    "gui.ascendant.catalog.not_owned", 0, false);
        }
        if (facts.toggled()) {
            return new Assessment(State.ACTIVE,
                    "gui.ascendant.catalog.active", "Active",
                    null, 0, true);
        }
        if (facts.cooldownTicks() > 0) {
            return new Assessment(State.COOLDOWN,
                    "gui.ascendant.catalog.cooldown_short_label", "Cooldown",
                    "gui.ascendant.catalog.cooldown_remaining", facts.cooldownTicks(), false);
        }
        if (!facts.manualAction()) {
            return facts.kind() == ContentKind.ABILITY
                    ? new Assessment(State.AUTOMATIC,
                    "gui.ascendant.catalog.automatic_short", "Automatic",
                    "gui.ascendant.catalog.ability_trigger_reason", 0, false)
                    : new Assessment(State.PASSIVE,
                    "gui.ascendant.catalog.passive_short", "Passive",
                    "gui.ascendant.catalog.power_type_reason", 0, false);
        }
        return new Assessment(State.READY,
                "gui.ascendant.catalog.ready_short", "Ready",
                null, 0, true);
    }
}
