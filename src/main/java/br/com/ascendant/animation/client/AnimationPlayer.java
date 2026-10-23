package br.com.ascendant.animation.client;

import br.com.ascendant.animation.AnimationProfile;
import br.com.ascendant.animation.AnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only animation playback for powers and abilities.
 * <p>
 * This class is isolated to the client via {@link OnlyIn(Dist.CLIENT)} so that
 * dedicated servers can load the mod without referencing any client classes.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "ascendant", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AnimationPlayer {
    private static final Map<Player, ActiveAnimation> ACTIVE = new ConcurrentHashMap<>();

    private AnimationPlayer() {
    }

    public static void start(Player player, ResourceLocation powerOrAbilityId) {
        AnimationProfile profile = AnimationRegistry.get(powerOrAbilityId);
        if (profile == null) {
            return;
        }
        ACTIVE.put(player, new ActiveAnimation(player, powerOrAbilityId, profile));
    }

    public static void stop(Player player) {
        ACTIVE.remove(player);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            ACTIVE.clear();
            return;
        }
        ActiveAnimation active = ACTIVE.get(player);
        if (active == null) {
            return;
        }
        active.tick();
    }

    private static final class ActiveAnimation {
        private final Player player;
        private final ResourceLocation id;
        private final AnimationProfile profile;
        private int ticksRemaining;

        ActiveAnimation(Player player, ResourceLocation id, AnimationProfile profile) {
            this.player = player;
            this.id = id;
            this.profile = profile;
            this.ticksRemaining = 40;
            playLayers(profile.getLayers());
        }

        private void playLayers(List<AnimationProfile.EffectLayer> layers) {
            for (AnimationProfile.EffectLayer layer : layers) {
                playLayer(layer);
            }
        }

        private void playLayer(AnimationProfile.EffectLayer layer) {
            String type = layer.getType();
            Map<String, String> p = layer.getParameters();
            switch (type) {
                case AnimationProfile.TYPE_PARTICLES -> playParticles(p);
                case AnimationProfile.TYPE_SOUND -> playSound(p);
                default -> {
                    // Pose, aura, impact layers handled by dedicated renderers
                }
            }
        }

        private void playParticles(Map<String, String> p) {
            double x = player.getX();
            double y = player.getY() + 1.0D;
            double z = player.getZ();
            int count = parseInt(p.get("count"), 10);
            ParticleOptions type = parseParticleType(p.get("type"), ParticleTypes.CAMPFIRE_COSY_SMOKE);
            for (int i = 0; i < count; i++) {
                player.level().addParticle(type, x, y, z, 0, 0.3D, 0);
            }
        }

        private void playSound(Map<String, String> p) {
            // Sound playback deferred to dedicated sound system; particles are the
            // primary visual feedback in this initial animation layer implementation.
        }

        private int parseInt(String value, int def) {
            try { return value == null ? def : Integer.parseInt(value); } catch (NumberFormatException e) { return def; }
        }

        private double parseDouble(String value, double def) {
            try { return value == null ? def : Double.parseDouble(value); } catch (NumberFormatException e) { return def; }
        }

        private float parseFloat(String value, float def) {
            try { return value == null ? def : Float.parseFloat(value); } catch (NumberFormatException e) { return def; }
        }

        private ParticleOptions parseParticleType(String value, ParticleOptions def) {
            if (value == null) {
                return def;
            }
            switch (value.toLowerCase(Locale.ROOT)) {
                case "smoke" -> { return ParticleTypes.CAMPFIRE_COSY_SMOKE; }
                case "heart" -> { return ParticleTypes.HEART; }
                case "portal" -> { return ParticleTypes.PORTAL; }
                case "soul_fire" -> { return ParticleTypes.SOUL_FIRE_FLAME; }
                case "reverse_portal" -> { return ParticleTypes.REVERSE_PORTAL; }
                case "electric_spark" -> { return ParticleTypes.ELECTRIC_SPARK; }
                case "dust" -> { return ParticleTypes.COMPOSTER; }
                default -> { return def; }
            }
        }

        void tick() {
            if (ticksRemaining-- <= 0) {
                ACTIVE.remove(player);
            }
        }
    }
}
