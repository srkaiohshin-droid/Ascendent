package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Phasing and intangibility component: negates projectile and kinetic impacts while active.
 */
public final class PhaseComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:phase";
    private final boolean projectileOnly;

    public PhaseComponent(boolean projectileOnly) {
        this.projectileOnly = projectileOnly;
    }

    public PhaseComponent() {
        this(true);
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
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0D, player.getZ(),
                15, 0.3D, 0.5D, 0.3D, 0.05D);
        return true;
    }

    @Override
    public void onHurt(ServerPlayer player, LivingHurtEvent event) {
        if (!projectileOnly || event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
            event.setCanceled(true);
        }
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("projectileOnly", projectileOnly);
        return obj;
    }

    public static PhaseComponent fromJson(JsonObject json) {
        boolean proj = !json.has("projectileOnly") || json.get("projectileOnly").getAsBoolean();
        return new PhaseComponent(proj);
    }
}
