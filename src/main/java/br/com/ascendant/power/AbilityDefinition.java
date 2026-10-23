package br.com.ascendant.power;

import br.com.ascendant.core.AbilityCategory;
import br.com.ascendant.core.AbilityTrigger;
import br.com.ascendant.power.component.AttributeModifierComponent;
import br.com.ascendant.power.component.ComponentRegistry;
import br.com.ascendant.power.component.IPowerComponent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable definition of an Ability in the Ascendant Framework.
 * Abilities represent specific techniques, actions, or variations.
 */
public final class AbilityDefinition {
    private final ResourceLocation id;
    @Nullable
    private final ResourceLocation powerId;
    private final String nameKey;
    private final String descriptionKey;
    private final AbilityCategory category;
    private final AbilityTrigger trigger;
    private final int cooldownTicks;
    private final float healthThreshold;
    private final List<IPowerComponent> actions;
    private final boolean functional;

    public AbilityDefinition(ResourceLocation id, @Nullable ResourceLocation powerId,
                             String nameKey, String descriptionKey,
                             AbilityCategory category, AbilityTrigger trigger,
                             int cooldownTicks, float healthThreshold, List<IPowerComponent> actions,
                             boolean functional) {
        this.id = Objects.requireNonNull(id, "id");
        this.powerId = powerId;
        this.nameKey = Objects.requireNonNull(nameKey, "nameKey");
        this.descriptionKey = Objects.requireNonNull(descriptionKey, "descriptionKey");
        this.category = Objects.requireNonNull(category, "category");
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.cooldownTicks = Math.max(0, cooldownTicks);
        this.healthThreshold = Float.isFinite(healthThreshold)
                ? Math.max(0.0F, Math.min(1.0F, healthThreshold)) : 1.0F;
        List<IPowerComponent> ownedActions = new ArrayList<>(actions.size());
        for (int index = 0; index < actions.size(); index++) {
            IPowerComponent action = actions.get(index);
            if (action instanceof AttributeModifierComponent modifier) {
                action = modifier.withIdentity(id + ":action:" + index);
            }
            ownedActions.add(action);
        }
        this.actions = List.copyOf(ownedActions);
        this.functional = functional;
    }

    public ResourceLocation getId() {
        return id;
    }

    @Nullable
    public ResourceLocation getPowerId() {
        return powerId;
    }

    public boolean isUniversal() {
        return powerId == null;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public AbilityCategory getCategory() {
        return category;
    }

    public AbilityTrigger getTrigger() {
        return trigger;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public float getHealthThreshold() {
        return healthThreshold;
    }

    public List<IPowerComponent> getActions() {
        return actions;
    }

    public boolean isFunctional() {
        return functional;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        if (powerId != null) {
            json.addProperty("powerId", powerId.toString());
        }
        json.addProperty("nameKey", nameKey);
        json.addProperty("descriptionKey", descriptionKey);
        json.addProperty("category", category.name());
        json.addProperty("trigger", trigger.name());
        json.addProperty("cooldownTicks", cooldownTicks);
        json.addProperty("healthThreshold", healthThreshold);
        json.addProperty("functional", functional);

        JsonArray actArray = new JsonArray();
        for (IPowerComponent act : actions) {
            actArray.add(act.toJson());
        }
        json.add("actions", actArray);

        return json;
    }

    public static AbilityDefinition fromJson(JsonObject json) {
        ResourceLocation id = ResourceLocation.tryParse(json.get("id").getAsString());
        if (id == null) {
            throw new IllegalArgumentException("Invalid ability id in json: " + json);
        }
        ResourceLocation powerId = json.has("powerId") ? ResourceLocation.tryParse(json.get("powerId").getAsString()) : null;
        String nameKey = json.has("nameKey") ? json.get("nameKey").getAsString() : "ability." + id.getNamespace() + "." + id.getPath();
        String descKey = json.has("descriptionKey") ? json.get("descriptionKey").getAsString() : nameKey + ".desc";
        AbilityCategory category = AbilityCategory.valueOf(json.get("category").getAsString().toUpperCase());
        AbilityTrigger trigger = AbilityTrigger.valueOf(json.get("trigger").getAsString().toUpperCase());
        int cooldown = json.has("cooldownTicks") ? json.get("cooldownTicks").getAsInt() : 0;
        float healthThreshold = json.has("healthThreshold") ? json.get("healthThreshold").getAsFloat() : 1.0F;
        boolean functional = json.has("functional") && json.get("functional").getAsBoolean();

        List<IPowerComponent> actions = new ArrayList<>();
        if (json.has("actions") && json.get("actions").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("actions")) {
                if (el.isJsonObject()) {
                    actions.add(ComponentRegistry.deserialize(el.getAsJsonObject()));
                }
            }
        }

        return new AbilityDefinition(id, powerId, nameKey, descKey, category, trigger,
                cooldown, healthThreshold, actions, functional);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private ResourceLocation powerId;
        private String nameKey;
        private String descriptionKey;
        private AbilityCategory category = AbilityCategory.OFFENSIVE;
        private AbilityTrigger trigger = AbilityTrigger.KEY_PRESS;
        private int cooldownTicks = 0;
        private float healthThreshold = 1.0F;
        private final List<IPowerComponent> actions = new ArrayList<>();
        private boolean functional = false;

        public Builder(ResourceLocation id) {
            this.id = id;
            this.nameKey = "ability." + id.getNamespace() + "." + id.getPath();
            this.descriptionKey = nameKey + ".desc";
        }

        public Builder powerId(ResourceLocation powerId) {
            this.powerId = powerId;
            return this;
        }

        public Builder nameKey(String key) {
            this.nameKey = key;
            return this;
        }

        public Builder descriptionKey(String key) {
            this.descriptionKey = key;
            return this;
        }

        public Builder category(AbilityCategory category) {
            this.category = category;
            return this;
        }

        public Builder trigger(AbilityTrigger trigger) {
            this.trigger = trigger;
            return this;
        }

        public Builder cooldown(int ticks) {
            this.cooldownTicks = ticks;
            return this;
        }

        public Builder healthThreshold(float fraction) {
            this.healthThreshold = fraction;
            return this;
        }

        public Builder addAction(IPowerComponent action) {
            this.actions.add(action);
            return this;
        }

        public Builder functional(boolean functional) {
            this.functional = functional;
            return this;
        }

        public AbilityDefinition build() {
            return new AbilityDefinition(id, powerId, nameKey, descriptionKey, category, trigger,
                    cooldownTicks, healthThreshold, actions, functional);
        }
    }
}
