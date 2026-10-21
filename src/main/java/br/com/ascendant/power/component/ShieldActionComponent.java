package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Creates an energy or kinetic shield, granting temporary absorption hearts and damage resistance.
 */
public final class ShieldActionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:shield_action";
    private final float absorptionAmount;
    private final int durationTicks;

    public ShieldActionComponent(float absorptionAmount, int durationTicks) {
        this.absorptionAmount = absorptionAmount;
        this.durationTicks = durationTicks;
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

        float currentAbsorption = player.getAbsorptionAmount();
        player.setAbsorptionAmount(Math.min(currentAbsorption + absorptionAmount, absorptionAmount * 2.0F));

        if (durationTicks > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1, false, true, true));
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.0D, player.getZ(),
                20, 0.5D, 0.8D, 0.5D, 0.05D);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("absorptionAmount", absorptionAmount);
        obj.addProperty("durationTicks", durationTicks);
        return obj;
    }

    public static ShieldActionComponent fromJson(JsonObject json) {
        float abs = json.has("absorptionAmount") ? json.get("absorptionAmount").getAsFloat() : 8.0F;
        int dur = json.has("durationTicks") ? json.get("durationTicks").getAsInt() : 200;
        return new ShieldActionComponent(abs, dur);
    }
}
