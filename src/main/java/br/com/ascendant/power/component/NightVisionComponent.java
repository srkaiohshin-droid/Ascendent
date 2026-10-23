package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

/**
 * Grants continuous or activated night vision.
 */
public final class NightVisionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:night_vision";
    private final int durationTicks;
    private final ManagedEffectLease effectLease = new ManagedEffectLease(MobEffects.NIGHT_VISION);

    public NightVisionComponent(int durationTicks) {
        this.durationTicks = Math.max(1, durationTicks);
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
        return effectLease.apply(context.getPlayer(), durationTicks);
    }

    @Override
    public void onPlayerTick(ServerPlayer player) {
        effectLease.apply(player, 220);
    }

    @Override
    public void onToggle(ServerPlayer player, boolean enabled) {
        if (!enabled) {
            effectLease.remove(player);
        } else {
            effectLease.apply(player, durationTicks);
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
