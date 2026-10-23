package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Applies a bounded invisibility effect when the power is activated. */
public final class InvisibilityComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:invisibility";
    private final int durationTicks;

    public InvisibilityComponent(int durationTicks) {
        this.durationTicks = Math.max(1, durationTicks);
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
        if (player.hasEffect(MobEffects.INVISIBILITY)) {
            return false;
        }
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, durationTicks, 0, false, true, true));
        return player.hasEffect(MobEffects.INVISIBILITY);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("durationTicks", durationTicks);
        return json;
    }

    public static InvisibilityComponent fromJson(JsonObject json) {
        return new InvisibilityComponent(json.has("durationTicks") ? json.get("durationTicks").getAsInt() : 100);
    }
}
