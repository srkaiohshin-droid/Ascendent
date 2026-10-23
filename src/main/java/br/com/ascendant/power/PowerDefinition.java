package br.com.ascendant.power;

import br.com.ascendant.core.PowerCategory;
import br.com.ascendant.core.PowerType;
import br.com.ascendant.power.component.ComponentRegistry;
import br.com.ascendant.power.component.AttributeModifierComponent;
import br.com.ascendant.power.component.IPowerComponent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable definition of a Superpower in the Ascendant Framework.
 */
public final class PowerDefinition {
    private final ResourceLocation id;
    private final String nameKey;
    private final String descriptionKey;
    private final PowerCategory category;
    private final PowerType type;
    private final int cooldownTicks;
    private final List<IPowerComponent> components;
    private final List<ResourceLocation> abilities;
    private final boolean functional;

    public PowerDefinition(ResourceLocation id, String nameKey, String descriptionKey,
                           PowerCategory category, PowerType type, int cooldownTicks,
                           List<IPowerComponent> components, List<ResourceLocation> abilities,
                           boolean functional) {
        this.id = Objects.requireNonNull(id, "id");
        this.nameKey = Objects.requireNonNull(nameKey, "nameKey");
        this.descriptionKey = Objects.requireNonNull(descriptionKey, "descriptionKey");
        this.category = Objects.requireNonNull(category, "category");
        this.type = Objects.requireNonNull(type, "type");
        this.cooldownTicks = Math.max(0, cooldownTicks);
        List<IPowerComponent> ownedComponents = new ArrayList<>(components.size());
        for (int index = 0; index < components.size(); index++) {
            IPowerComponent component = components.get(index);
            if (component instanceof AttributeModifierComponent modifier) {
                component = modifier.withIdentity(id + ":component:" + index);
            }
            ownedComponents.add(component);
        }
        this.components = List.copyOf(ownedComponents);
        this.abilities = List.copyOf(abilities);
        this.functional = functional;
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public PowerCategory getCategory() {
        return category;
    }

    public PowerType getType() {
        return type;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public List<IPowerComponent> getComponents() {
        return components;
    }

    public List<ResourceLocation> getAbilities() {
        return abilities;
    }

    public boolean isFunctional() {
        return functional;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("nameKey", nameKey);
        json.addProperty("descriptionKey", descriptionKey);
        json.addProperty("category", category.name());
        json.addProperty("type", type.name());
        json.addProperty("cooldownTicks", cooldownTicks);
        json.addProperty("functional", functional);

        JsonArray compArray = new JsonArray();
        for (IPowerComponent comp : components) {
            compArray.add(comp.toJson());
        }
        json.add("components", compArray);

        JsonArray abArray = new JsonArray();
        for (ResourceLocation ab : abilities) {
            abArray.add(ab.toString());
        }
        json.add("abilities", abArray);

        return json;
    }

    public static PowerDefinition fromJson(JsonObject json) {
        ResourceLocation id = ResourceLocation.tryParse(json.get("id").getAsString());
        if (id == null) {
            throw new IllegalArgumentException("Invalid power id in json: " + json);
        }
        String nameKey = json.has("nameKey") ? json.get("nameKey").getAsString() : "power." + id.getNamespace() + "." + id.getPath();
        String descKey = json.has("descriptionKey") ? json.get("descriptionKey").getAsString() : nameKey + ".desc";
        PowerCategory category = PowerCategory.valueOf(json.get("category").getAsString().toUpperCase());
        PowerType type = PowerType.valueOf(json.get("type").getAsString().toUpperCase());
        int cooldown = json.has("cooldownTicks") ? json.get("cooldownTicks").getAsInt() : 0;
        boolean functional = json.has("functional") && json.get("functional").getAsBoolean();

        List<IPowerComponent> components = new ArrayList<>();
        if (json.has("components") && json.get("components").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("components")) {
                if (el.isJsonObject()) {
                    components.add(ComponentRegistry.deserialize(el.getAsJsonObject()));
                }
            }
        }

        List<ResourceLocation> abilities = new ArrayList<>();
        if (json.has("abilities") && json.get("abilities").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("abilities")) {
                ResourceLocation abId = ResourceLocation.tryParse(el.getAsString());
                if (abId != null) {
                    abilities.add(abId);
                }
            }
        }

        return new PowerDefinition(id, nameKey, descKey, category, type, cooldown, components, abilities, functional);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private String nameKey;
        private String descriptionKey;
        private PowerCategory category = PowerCategory.HYBRID;
        private PowerType type = PowerType.ACTIVE;
        private int cooldownTicks = 0;
        private final List<IPowerComponent> components = new ArrayList<>();
        private final List<ResourceLocation> abilities = new ArrayList<>();
        private boolean functional = false;

        public Builder(ResourceLocation id) {
            this.id = id;
            this.nameKey = "power." + id.getNamespace() + "." + id.getPath();
            this.descriptionKey = nameKey + ".desc";
        }

        public Builder nameKey(String key) {
            this.nameKey = key;
            return this;
        }

        public Builder descriptionKey(String key) {
            this.descriptionKey = key;
            return this;
        }

        public Builder category(PowerCategory category) {
            this.category = category;
            return this;
        }

        public Builder type(PowerType type) {
            this.type = type;
            return this;
        }

        public Builder cooldown(int ticks) {
            this.cooldownTicks = ticks;
            return this;
        }

        public Builder addComponent(IPowerComponent component) {
            this.components.add(component);
            return this;
        }

        public Builder addAbility(ResourceLocation abilityId) {
            this.abilities.add(abilityId);
            return this;
        }

        public Builder functional(boolean functional) {
            this.functional = functional;
            return this;
        }

        public PowerDefinition build() {
            return new PowerDefinition(id, nameKey, descriptionKey, category, type, cooldownTicks, components, abilities, functional);
        }
    }
}
