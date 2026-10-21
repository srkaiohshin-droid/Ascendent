package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Grants continuous or activated night vision.
 */
public final class NightVisionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:night_vision";
    private final int durationTicks;

    public NightVisionComponent(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    public NightVisionComponent() {
        this(300);
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
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, durationTicks, 0, false, false, true));
        return true;
    }

    @Override
    public void onPlayerTick(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false, false));
    }

    @Override
    public void onToggle(ServerPlayer player, boolean enabled) {
        if (!enabled) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, false));
        }
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("durationTicks", durationTicks);
        return obj;
    }

    public static NightVisionComponent fromJson(JsonObject json) {
        int dur = json.has("durationTicks") ? json.get("durationTicks").getAsInt() : 300;
        return new NightVisionComponent(dur);
    }
}
