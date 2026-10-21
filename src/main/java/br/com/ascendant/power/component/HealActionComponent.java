package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Heals the player and optionally cleanses harmful potion/status effects.
 */
public final class HealActionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:heal_action";
    private final float healAmount;
    private final boolean cleanseEffects;

    public HealActionComponent(float healAmount, boolean cleanseEffects) {
        this.healAmount = healAmount;
        this.cleanseEffects = cleanseEffects;
    }

    public HealActionComponent(float healAmount) {
        this(healAmount, false);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isFunctional() {
        return true;
    }

    @Override
    public boolean execute(ComponentContext context) {
        ServerPlayer player = context.getPlayer();
        ServerLevel level = context.getLevel();

        player.heal(healAmount);

        if (cleanseEffects) {
            List<MobEffectInstance> toRemove = new ArrayList<>();
            for (MobEffectInstance effect : player.getActiveEffects()) {
                if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                    toRemove.add(effect);
                }
            }
            for (MobEffectInstance effect : toRemove) {
                player.removeEffect(effect.getEffect());
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.6F);
        level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.2D, player.getZ(),
                10, 0.5D, 0.5D, 0.5D, 0.05D);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("healAmount", healAmount);
        obj.addProperty("cleanseEffects", cleanseEffects);
        return obj;
    }

    public static HealActionComponent fromJson(JsonObject json) {
        float amount = json.has("healAmount") ? json.get("healAmount").getAsFloat() : 6.0F;
        boolean cleanse = json.has("cleanseEffects") && json.get("cleanseEffects").getAsBoolean();
        return new HealActionComponent(amount, cleanse);
    }
}
