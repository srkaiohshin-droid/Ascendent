package br.com.ascendant.lore;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Objects;

/**
 * Declarative unlock rule for a lore entry. It points at real registered
 * content instead of a separate unlock counter, so the archive cannot claim an
 * entry is available without the matching power, ability or equipped suit.
 */
public record LoreUnlock(Type type, @Nullable ResourceLocation id) {

    public enum Type {
        ALWAYS, POWER, ABILITY, SUIT
    }

    public LoreUnlock {
        Objects.requireNonNull(type, "type");
        if (type == Type.ALWAYS && id != null) {
            throw new IllegalArgumentException("ALWAYS unlocks cannot carry a requirement id");
        }
        if (type != Type.ALWAYS && id == null) {
            throw new IllegalArgumentException(type + " unlocks require a requirement id");
        }
    }

    public static LoreUnlock always() {
        return new LoreUnlock(Type.ALWAYS, null);
    }

    public static LoreUnlock power(ResourceLocation powerId) {
        return new LoreUnlock(Type.POWER, powerId);
    }

    public static LoreUnlock ability(ResourceLocation abilityId) {
        return new LoreUnlock(Type.ABILITY, abilityId);
    }

    public static LoreUnlock suit(ResourceLocation suitId) {
        return new LoreUnlock(Type.SUIT, suitId);
    }

    public boolean isSatisfied(LoreAccess access) {
        Objects.requireNonNull(access, "access");
        return switch (type) {
            case ALWAYS -> true;
            case POWER -> access.hasPower(Objects.requireNonNull(id));
            case ABILITY -> access.hasAbility(Objects.requireNonNull(id));
            case SUIT -> access.hasSuit(Objects.requireNonNull(id));
        };
    }

    public String requirementKey() {
        return "lore.ascendant.requires." + type.name().toLowerCase(Locale.ROOT);
    }
}
