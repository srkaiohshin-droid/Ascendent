package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * Bounded stun for a small group of visible hostiles.
 *
 * <p>Stun is implemented with vanilla status effects plus AI reset, so it is observable,
 * expires on its own and cannot leave a mob in a custom frozen state. It is deliberately
 * capped in radius, duration and target count to keep it safe in multiplayer.</p>
 */
public final class StunComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:stun";
    private static final int MIN_DURATION = 10;
    private static final int MAX_DURATION = 240;

    private final double radius;
    private final int durationTicks;
    private final int maxTargets;

    public StunComponent(double radius, int durationTicks, int maxTargets) {
        this.radius = Math.max(1.0D, Math.min(16.0D, radius));
        this.durationTicks = Math.max(MIN_DURATION, Math.min(MAX_DURATION, durationTicks));
        this.maxTargets = Math.max(1, Math.min(CombatTargeting.MAX_TARGETS, maxTargets));
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isFunctional() {
        return true;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    @Override
    public boolean execute(ComponentContext context) {
        ServerPlayer player = context.getPlayer();
        ServerLevel level = context.getLevel();
        List<LivingEntity> targets = CombatTargeting.visibleHostiles(player, radius, maxTargets);
        if (targets.isEmpty()) {
            return false;
        }

        for (LivingEntity target : targets) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, 3,
                    false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, durationTicks, 1,
                    false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, durationTicks, 2,
                    false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, Math.max(40, durationTicks / 2), 0,
                    false, true, true));
            if (target instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            target.hurtMarked = true;
        }

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0D,
                player.getZ(), 30, radius * 0.5D, 0.6D, radius * 0.5D, 0.08D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 1.4F);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("radius", radius);
        json.addProperty("durationTicks", durationTicks);
        json.addProperty("maxTargets", maxTargets);
        return json;
    }

    public static StunComponent fromJson(JsonObject json) {
        return new StunComponent(
                json.has("radius") ? json.get("radius").getAsDouble() : 6.0D,
                json.has("durationTicks") ? json.get("durationTicks").getAsInt() : 60,
                json.has("maxTargets") ? json.get("maxTargets").getAsInt() : 8);
    }
}
