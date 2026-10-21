package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Reduces or completely negates specific damage types (e.g. all, fall, fire, explosion, drowning).
 */
public final class DamageMitigationComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:damage_mitigation";
    private final float mitigationRatio; // 0.0 to 1.0 (1.0 = 100% immunity)
    private final String damageSourceType;

    public DamageMitigationComponent(float mitigationRatio, String damageSourceType) {
        this.mitigationRatio = Math.max(0.0F, Math.min(1.0F, mitigationRatio));
        this.damageSourceType = damageSourceType;
    }

    public DamageMitigationComponent(float mitigationRatio) {
        this(mitigationRatio, "all");
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
    public void onHurt(ServerPlayer player, LivingHurtEvent event) {
        if (appliesTo(event)) {
            if (mitigationRatio >= 1.0F) {
                event.setCanceled(true);
            } else {
                float newAmount = event.getAmount() * (1.0F - mitigationRatio);
                event.setAmount(Math.max(0.0F, newAmount));
            }
        }
    }

    @Override
    public void onFall(ServerPlayer player, LivingFallEvent event) {
        if ("fall".equalsIgnoreCase(damageSourceType) || "all".equalsIgnoreCase(damageSourceType)) {
            if (mitigationRatio >= 1.0F) {
                event.setCanceled(true);
            } else {
                event.setDamageMultiplier(Math.max(0.0F, 1.0F - mitigationRatio));
            }
        }
    }

    private boolean appliesTo(LivingHurtEvent event) {
        if ("all".equalsIgnoreCase(damageSourceType)) {
            return true;
        }
        if ("fall".equalsIgnoreCase(damageSourceType)) {
            return event.getSource().is(DamageTypeTags.IS_FALL);
        }
        if ("fire".equalsIgnoreCase(damageSourceType)) {
            return event.getSource().is(DamageTypeTags.IS_FIRE);
        }
        if ("explosion".equalsIgnoreCase(damageSourceType)) {
            return event.getSource().is(DamageTypeTags.IS_EXPLOSION);
        }
        if ("drown".equalsIgnoreCase(damageSourceType)) {
            return event.getSource().is(DamageTypeTags.IS_DROWNING);
        }
        return false;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("mitigationRatio", mitigationRatio);
        obj.addProperty("damageSourceType", damageSourceType);
        return obj;
    }

    public static DamageMitigationComponent fromJson(JsonObject json) {
        float ratio = json.has("mitigationRatio") ? json.get("mitigationRatio").getAsFloat() : 0.5F;
        String type = json.has("damageSourceType") ? json.get("damageSourceType").getAsString() : "all";
        return new DamageMitigationComponent(ratio, type);
    }
}
