package br.com.ascendant.power.component;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ManagedEffectLease {
    private final MobEffect effect;
    private final boolean visible;
    private final boolean showIcon;
    private final Map<UUID, Long> expiresAt = new ConcurrentHashMap<>();

    ManagedEffectLease(MobEffect effect) {
        this(effect, false, false);
    }

    ManagedEffectLease(MobEffect effect, boolean visible, boolean showIcon) {
        this.effect = effect;
        this.visible = visible;
        this.showIcon = showIcon;
    }

    boolean apply(ServerPlayer player, int durationTicks) {
        int duration = Math.max(1, durationTicks);
        UUID playerId = player.getUUID();
        MobEffectInstance current = player.getEffect(effect);
        Long ownedExpiration = expiresAt.get(playerId);
        if (current != null) {
            if (ownedExpiration == null || !matchesOwned(current, ownedExpiration, player)) {
                expiresAt.remove(playerId);
                return false;
            }
        }
        player.addEffect(new MobEffectInstance(effect, duration, 0, false, visible, showIcon));
        expiresAt.put(playerId, player.level().getGameTime() + duration);
        return player.hasEffect(effect);
    }

    void remove(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Long ownedExpiration = expiresAt.remove(playerId);
        MobEffectInstance current = player.getEffect(effect);
        if (ownedExpiration != null && current != null && matchesOwned(current, ownedExpiration, player)) {
            player.removeEffect(effect);
        }
    }

    private boolean matchesOwned(MobEffectInstance current, long expiration, ServerPlayer player) {
        long expectedRemaining = Math.max(0L, expiration - player.level().getGameTime());
        return current.getAmplifier() == 0
                && current.isVisible() == visible
                && current.showIcon() == showIcon
                && Math.abs(current.getDuration() - expectedRemaining) <= 2L;
    }
}
