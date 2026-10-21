package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Visual and auditory effect component to accompany ability actions.
 */
public final class SoundParticleComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:sound_particle";
    private final String soundKey;
    private final String particleKey;

    public SoundParticleComponent(String soundKey, String particleKey) {
        this.soundKey = soundKey;
        this.particleKey = particleKey;
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

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0D, player.getZ(),
                12, 0.4D, 0.4D, 0.4D, 0.1D);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("soundKey", soundKey);
        obj.addProperty("particleKey", particleKey);
        return obj;
    }

    public static SoundParticleComponent fromJson(JsonObject json) {
        String snd = json.has("soundKey") ? json.get("soundKey").getAsString() : "sweep";
        String part = json.has("particleKey") ? json.get("particleKey").getAsString() : "crit";
        return new SoundParticleComponent(snd, part);
    }
}
