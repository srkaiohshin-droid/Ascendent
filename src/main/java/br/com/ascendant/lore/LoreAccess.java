package br.com.ascendant.lore;

import net.minecraft.resources.ResourceLocation;

/**
 * Read-only view of what the player can currently access.
 *
 * <p>The server uses persistent power data and the equipped suit; the client
 * uses the last authoritative catalog snapshot. No lore state is stored
 * separately, so unlocking always reflects real gameplay access.</p>
 */
public interface LoreAccess {

    boolean hasPower(ResourceLocation powerId);

    boolean hasAbility(ResourceLocation abilityId);

    boolean hasSuit(ResourceLocation suitId);

    /** Access that owns nothing; useful for tests and offline previews. */
    static LoreAccess none() {
        return new LoreAccess() {
            @Override
            public boolean hasPower(ResourceLocation powerId) {
                return false;
            }

            @Override
            public boolean hasAbility(ResourceLocation abilityId) {
                return false;
            }

            @Override
            public boolean hasSuit(ResourceLocation suitId) {
                return false;
            }
        };
    }
}
