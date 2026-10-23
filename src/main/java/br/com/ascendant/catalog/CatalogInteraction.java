package br.com.ascendant.catalog;

import br.com.ascendant.core.AbilityTrigger;
import br.com.ascendant.core.PowerType;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.PowerDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/** Shared, client-independent catalog filtering and action classification. */
public final class CatalogInteraction {
    private CatalogInteraction() {
    }

    public enum ActionType {
        ACTIVATE_POWER,
        TOGGLE_POWER,
        ACTIVATE_ABILITY,
        EQUIP_SUIT,
        NONE
    }

    public record Action(ActionType type, ResourceLocation id) {
        public Action {
            if (type == null || id == null) {
                throw new IllegalArgumentException("Catalog actions require a type and identifier");
            }
        }
    }

    public static boolean matches(String query, String id, String name, String category) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();
        String searchSpace = (safe(id) + " " + safe(name) + " " + safe(category)).toLowerCase(Locale.ROOT);
        for (String term : normalizedQuery.split("\\s+")) {
            if (!searchSpace.contains(term)) {
                return false;
            }
        }
        return true;
    }

    public static boolean matchesCategory(String selectedCategory, String entryCategory) {
        return selectedCategory == null || selectedCategory.equalsIgnoreCase("all")
                || selectedCategory.equalsIgnoreCase(safe(entryCategory));
    }

    public static Action forPower(PowerDefinition power) {
        if (power == null || !power.isFunctional()) {
            return new Action(ActionType.NONE,
                    power == null ? new ResourceLocation("ascendant", "missing") : power.getId());
        }
        return switch (power.getType()) {
            case ACTIVE -> new Action(ActionType.ACTIVATE_POWER, power.getId());
            case TOGGLE, SUSTAINED -> new Action(ActionType.TOGGLE_POWER, power.getId());
            default -> new Action(ActionType.NONE, power.getId());
        };
    }

    public static Action forAbility(AbilityDefinition ability) {
        if (ability == null || !ability.isFunctional()) {
            return new Action(ActionType.NONE,
                    ability == null ? new ResourceLocation("ascendant", "missing") : ability.getId());
        }
        AbilityTrigger trigger = ability.getTrigger();
        if (trigger != AbilityTrigger.KEY_PRESS && trigger != AbilityTrigger.SNEAK_KEY
                && trigger != AbilityTrigger.TOGGLE) {
            return new Action(ActionType.NONE, ability.getId());
        }
        return new Action(ActionType.ACTIVATE_ABILITY, ability.getId());
    }

    public static Action forSuit(ResourceLocation suitId) {
        return new Action(ActionType.EQUIP_SUIT, suitId);
    }

    /** Returns a clockwise sector index starting at the top, or -1 inside the dead zone. */
    public static int radialSector(double x, double y, int sectorCount, double deadZone) {
        if (sectorCount < 1 || sectorCount > 8 || !Double.isFinite(x) || !Double.isFinite(y)
                || x * x + y * y < deadZone * deadZone) {
            return -1;
        }
        double angle = Math.atan2(y, x) + Math.PI / 2.0D;
        if (angle < 0.0D) {
            angle += Math.PI * 2.0D;
        }
        return (int) Math.floor(angle / (Math.PI * 2.0D / sectorCount) + 0.5D) % sectorCount;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}