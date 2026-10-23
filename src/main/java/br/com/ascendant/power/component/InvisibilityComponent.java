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
 * Grants tactical invisibility and suppresses footstep vibrations.
 */
public final class InvisibilityComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:invisibility";
    private final int durationTicks;

    public InvisibilityComponent(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    public InvisibilityComponent() {
        this(200);
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

        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, durationTicks, 0, false, false, true));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.4F);
        level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0D, player.getZ(),
                15, 0.3D, 0.5D, 0.3D, 0.05D);
        return true;
    }

    @Override
    public void onPlayerTick(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
    }

    @Override
    public void onToggle(ServerPlayer player, boolean enabled) {
        if (!enabled) {
            player.removeEffect(MobEffects.INVISIBILITY);
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, false, false, false));
        }
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("durationTicks", durationTicks);
        return obj;
    }

    public static InvisibilityComponent fromJson(JsonObject json) {
        int dur = json.has("durationTicks") ? json.get("durationTicks").getAsInt() : 200;
        return new InvisibilityComponent(dur);
    }
}
