package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Provides underwater respiration, swim speed (Dolphin's Grace), and vision clarity in water.
 */
public final class WaterAdaptationComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:water_adaptation";
    private final boolean grantConduitPower;
    private final boolean grantDolphinGrace;
    private final ManagedEffectLease conduitEffect = new ManagedEffectLease(MobEffects.CONDUIT_POWER);
    private final ManagedEffectLease dolphinEffect = new ManagedEffectLease(MobEffects.DOLPHINS_GRACE);

    public WaterAdaptationComponent(boolean grantConduitPower, boolean grantDolphinGrace) {
        this.grantConduitPower = grantConduitPower;
        this.grantDolphinGrace = grantDolphinGrace;
    }

    public WaterAdaptationComponent() {
        this(true, true);
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
    public void onPlayerTick(ServerPlayer player) {
        if (player.isInWaterOrRain() || player.isEyeInFluidType(net.minecraftforge.common.ForgeMod.WATER_TYPE.get())) {
            player.setAirSupply(player.getMaxAirSupply());
            if (grantConduitPower) {
                conduitEffect.apply(player, 40);
            }
            if (grantDolphinGrace) {
                dolphinEffect.apply(player, 40);
            }
        }
    }

    @Override
    public void onToggle(ServerPlayer player, boolean enabled) {
        if (!enabled) {
            conduitEffect.remove(player);
            dolphinEffect.remove(player);
        }
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("grantConduitPower", grantConduitPower);
        obj.addProperty("grantDolphinGrace", grantDolphinGrace);
        return obj;
    }

    public static WaterAdaptationComponent fromJson(JsonObject json) {
        boolean conduit = !json.has("grantConduitPower") || json.get("grantConduitPower").getAsBoolean();
        boolean dolphin = !json.has("grantDolphinGrace") || json.get("grantDolphinGrace").getAsBoolean();
        return new WaterAdaptationComponent(conduit, dolphin);
    }
}
