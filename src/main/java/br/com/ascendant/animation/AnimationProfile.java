package br.com.ascendant.animation;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data profile describing the visual presentation of a power or ability.
 * <p>
 * Profiles are purely visual and contain no gameplay logic. They are associated
 * with power/ability IDs so the client can play appropriate effects when a power
 * is activated, ticked, or deactivated.
 */
public final class AnimationProfile {
    public static final String TYPE_POSE = "pose";
    public static final String TYPE_PARTICLES = "particles";
    public static final String TYPE_SOUND = "sound";
    public static final String TYPE_AURA = "aura";
    public static final String TYPE_IMPACT = "impact";

    private final ResourceLocation id;
    private final String name;
    private final List<EffectLayer> layers;

    public AnimationProfile(ResourceLocation id, String name, List<EffectLayer> layers) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<EffectLayer> getLayers() {
        return layers;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("name", name);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (EffectLayer layer : layers) {
            arr.add(layer.toJson());
        }
        json.add("layers", arr);
        return json;
    }

    public static AnimationProfile fromJson(JsonObject json) {
        ResourceLocation id = ResourceLocation.tryParse(json.get("id").getAsString());
        String name = json.has("name") ? json.get("name").getAsString() : id.getPath();
        List<EffectLayer> layers = new ArrayList<>();
        if (json.has("layers") && json.get("layers").isJsonArray()) {
            for (com.google.gson.JsonElement el : json.getAsJsonArray("layers")) {
                if (el.isJsonObject()) {
                    layers.add(EffectLayer.fromJson(el.getAsJsonObject()));
                }
            }
        }
        return new AnimationProfile(id, name, layers);
    }

    /** Single visual layer within an animation profile. */
    public static final class EffectLayer {
        private final String type;
        private final Map<String, String> parameters;

        public EffectLayer(String type, Map<String, String> parameters) {
            this.type = Objects.requireNonNull(type, "type");
            this.parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
        }

        public String getType() {
            return type;
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("type", type);
            com.google.gson.JsonObject params = new com.google.gson.JsonObject();
            parameters.forEach(params::addProperty);
            json.add("parameters", params);
            return json;
        }

        public static EffectLayer fromJson(JsonObject json) {
            String type = json.get("type").getAsString();
            com.google.gson.JsonObject params = json.getAsJsonObject("parameters");
            java.util.Map<String, String> map = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : params.entrySet()) {
                map.put(e.getKey(), e.getValue().getAsString());
            }
            return new EffectLayer(type, map);
        }
    }
}
