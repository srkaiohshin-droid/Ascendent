package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.world.effect.MobEffects;

/** Grants a short, server-side fire-resistance window when activated. */
public final class FireWardComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:fire_ward";
    private final int durationTicks;
    private final ManagedEffectLease effectLease = new ManagedEffectLease(MobEffects.FIRE_RESISTANCE);

    public FireWardComponent(int durationTicks) {
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
        return effectLease.apply(context.getPlayer(), durationTicks);
    }

    @Override
    public void onToggle(net.minecraft.server.level.ServerPlayer player, boolean enabled) {
        if (!enabled) {
            effectLease.remove(player);
        }
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("durationTicks", durationTicks);
        return json;
    }

    public static FireWardComponent fromJson(JsonObject json) {
        int duration = json.has("durationTicks") ? json.get("durationTicks").getAsInt() : 100;
        return new FireWardComponent(duration);
    }
}
