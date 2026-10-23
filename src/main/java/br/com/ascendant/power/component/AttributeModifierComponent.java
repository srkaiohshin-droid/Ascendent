package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Modifies an entity attribute (e.g. movement speed, attack damage, armor, max health)
 * safely and transiently using a deterministic modifier UUID.
 */
public final class AttributeModifierComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:attribute_modifier";
    private final String attributeKey;
    private final double amount;
    private final AttributeModifier.Operation operation;
    private final String identity;
    private final UUID modifierId;

    public AttributeModifierComponent(String attributeKey, double amount, AttributeModifier.Operation operation) {
        this(attributeKey, amount, operation, attributeKey + ":" + amount + ":" + operation.name());
    }

    private AttributeModifierComponent(String attributeKey, double amount,
                                       AttributeModifier.Operation operation, String identity) {
        this.attributeKey = attributeKey;
        this.amount = amount;
        this.operation = operation;
        this.identity = identity;
        this.modifierId = UUID.nameUUIDFromBytes(("ascendant:attribute_modifier:" + identity
                + ":" + attributeKey.toLowerCase() + ":" + operation.name()).getBytes(StandardCharsets.UTF_8));
    }

    public AttributeModifierComponent(String attributeKey, double amount) {
        this(attributeKey, amount, AttributeModifier.Operation.ADDITION);
    }

    public AttributeModifierComponent withIdentity(String identity) {
        return new AttributeModifierComponent(attributeKey, amount, operation, identity);
    }

    public UUID getModifierId() {
        return modifierId;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isFunctional() {
        return true;
    }

    private Attribute resolveAttribute() {
        return switch (attributeKey.toLowerCase()) {
            case "movement_speed", "speed" -> Attributes.MOVEMENT_SPEED;
            case "attack_damage", "strength" -> Attributes.ATTACK_DAMAGE;
            case "armor" -> Attributes.ARMOR;
            case "armor_toughness" -> Attributes.ARMOR_TOUGHNESS;
            case "max_health", "health" -> Attributes.MAX_HEALTH;
            case "knockback_resistance" -> Attributes.KNOCKBACK_RESISTANCE;
            case "swim_speed" -> ForgeMod.SWIM_SPEED.get();
            case "step_height" -> ForgeMod.STEP_HEIGHT_ADDITION.get();
            default -> Attributes.MOVEMENT_SPEED;
        };
    }

    public void apply(ServerPlayer player) {
        Attribute attr = resolveAttribute();
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null && !inst.hasModifier(getModifier())) {
            inst.addTransientModifier(getModifier());
        }
    }

    public void remove(ServerPlayer player) {
        Attribute attr = resolveAttribute();
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null && inst.hasModifier(getModifier())) {
            inst.removeModifier(modifierId);
        }
    }

    @Override
    public void onPlayerTick(ServerPlayer player) {
        apply(player);
    }

    @Override
    public void onToggle(ServerPlayer player, boolean enabled) {
        if (enabled) {
            apply(player);
        } else {
            remove(player);
        }
    }

    private AttributeModifier getModifier() {
        return new AttributeModifier(modifierId, "Ascendant " + attributeKey, amount, operation);
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("attributeKey", attributeKey);
        obj.addProperty("amount", amount);
        obj.addProperty("operation", operation.toValue());
        return obj;
    }

    public static AttributeModifierComponent fromJson(JsonObject json) {
        String key = json.has("attributeKey") ? json.get("attributeKey").getAsString() : "movement_speed";
        double amt = json.has("amount") ? json.get("amount").getAsDouble() : 0.1D;
        int opVal = json.has("operation") ? json.get("operation").getAsInt() : 0;
        AttributeModifier.Operation op = AttributeModifier.Operation.fromValue(opVal);
        return new AttributeModifierComponent(key, amt, op);
    }
}
