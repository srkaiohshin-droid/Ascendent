package br.com.ascendant.suit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative manager for superhero suits equipped by players.
 */
public final class SuitManager {
    private static final Map<UUID, ResourceLocation> EQUIPPED_SUITS = new ConcurrentHashMap<>();

    private SuitManager() {
    }

    public static boolean equipSuit(ServerPlayer player, ResourceLocation suitId) {
        SuitDefinition suit = SuitRegistry.get(suitId);
        if (suit == null) {
            return false;
        }
        EQUIPPED_SUITS.put(player.getUUID(), suitId);
        return true;
    }

    public static boolean unequipSuit(ServerPlayer player) {
        return EQUIPPED_SUITS.remove(player.getUUID()) != null;
    }

    public static ResourceLocation getEquippedSuitId(ServerPlayer player) {
        return EQUIPPED_SUITS.get(player.getUUID());
    }

    public static SuitDefinition getEquippedSuit(ServerPlayer player) {
        ResourceLocation suitId = EQUIPPED_SUITS.get(player.getUUID());
        return suitId != null ? SuitRegistry.get(suitId) : null;
    }

    public static boolean grantsPower(ServerPlayer player, ResourceLocation powerId) {
        SuitDefinition suit = getEquippedSuit(player);
        return suit != null && suit.getGrantedPowers().contains(powerId);
    }

    public static List<ResourceLocation> getEquippedSuitGrantedPowers(ServerPlayer player) {
        SuitDefinition suit = getEquippedSuit(player);
        return suit != null ? suit.getGrantedPowers() : Collections.emptyList();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EQUIPPED_SUITS.remove(event.getEntity().getUUID());
    }
}
