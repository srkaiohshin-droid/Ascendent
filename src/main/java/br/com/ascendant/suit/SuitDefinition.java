package br.com.ascendant.suit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Data-driven definition of a superhero suit.
 * Supports cosmetic suits, suits that grant powers, or suits with attribute enhancements.
 */
public final class SuitDefinition {
    private final ResourceLocation id;
    private final String nameKey;
    private final String descriptionKey;
    private final String universe;
    private final String author;
    private final SuitRarity rarity;
    private final List<ResourceLocation> grantedPowers;
    private final List<ResourceLocation> grantedAbilities;
    private final Map<String, Double> attributeModifiers;
    private final SuitModelInfo modelInfo;

    public SuitDefinition(ResourceLocation id, String nameKey, String descriptionKey,
                          String universe, String author, SuitRarity rarity,
                          List<ResourceLocation> grantedPowers,
                          List<ResourceLocation> grantedAbilities,
                          Map<String, Double> attributeModifiers,
                          SuitModelInfo modelInfo) {
        this.id = Objects.requireNonNull(id, "id");
        this.nameKey = Objects.requireNonNull(nameKey, "nameKey");
        this.descriptionKey = Objects.requireNonNull(descriptionKey, "descriptionKey");
        this.universe = Objects.requireNonNull(universe, "universe");
        this.author = Objects.requireNonNull(author, "author");
        this.rarity = Objects.requireNonNull(rarity, "rarity");
        this.grantedPowers = List.copyOf(grantedPowers);
        this.grantedAbilities = List.copyOf(grantedAbilities);
        this.attributeModifiers = Map.copyOf(attributeModifiers);
        this.modelInfo = Objects.requireNonNull(modelInfo, "modelInfo");
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

    public String getUniverse() {
        return universe;
    }

    public String getAuthor() {
        return author;
    }

    public SuitRarity getRarity() {
        return rarity;
    }

    public List<ResourceLocation> getGrantedPowers() {
        return grantedPowers;
    }

    public List<ResourceLocation> getGrantedAbilities() {
        return grantedAbilities;
    }

    public Map<String, Double> getAttributeModifiers() {
        return attributeModifiers;
    }

    public SuitModelInfo getModelInfo() {
        return modelInfo;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("nameKey", nameKey);
        json.addProperty("descriptionKey", descriptionKey);
        json.addProperty("universe", universe);
        json.addProperty("author", author);
        json.addProperty("rarity", rarity.name());

        JsonArray powersArr = new JsonArray();
        for (ResourceLocation p : grantedPowers) {
            powersArr.add(p.toString());
        }
        json.add("grantedPowers", powersArr);

        JsonArray abilitiesArr = new JsonArray();
        for (ResourceLocation a : grantedAbilities) {
            abilitiesArr.add(a.toString());
        }
        json.add("grantedAbilities", abilitiesArr);

        JsonObject attrs = new JsonObject();
        attributeModifiers.forEach(attrs::addProperty);
        json.add("attributeModifiers", attrs);

        json.add("modelInfo", modelInfo.toJson());
        return json;
    }

    public static SuitDefinition fromJson(JsonObject json) {
        ResourceLocation id = ResourceLocation.tryParse(json.get("id").getAsString());
        if (id == null) {
            throw new IllegalArgumentException("Invalid suit id in json: " + json);
        }
        String nameKey = json.has("nameKey") ? json.get("nameKey").getAsString() : "suit." + id.getNamespace() + "." + id.getPath();
        String descKey = json.has("descriptionKey") ? json.get("descriptionKey").getAsString() : nameKey + ".desc";
        String universe = json.has("universe") ? json.get("universe").getAsString() : "Ascendant";
        String author = json.has("author") ? json.get("author").getAsString() : "Ascendant Team";
        SuitRarity rarity = SuitRarity.valueOf(json.has("rarity") ? json.get("rarity").getAsString().toUpperCase() : "COMMON");

        List<ResourceLocation> powers = new ArrayList<>();
        if (json.has("grantedPowers") && json.get("grantedPowers").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("grantedPowers")) {
                ResourceLocation p = ResourceLocation.tryParse(el.getAsString());
                if (p != null) powers.add(p);
            }
        }

        List<ResourceLocation> abilities = new ArrayList<>();
        if (json.has("grantedAbilities") && json.get("grantedAbilities").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("grantedAbilities")) {
                ResourceLocation a = ResourceLocation.tryParse(el.getAsString());
                if (a != null) abilities.add(a);
            }
        }

        Map<String, Double> attrs = new HashMap<>();
        if (json.has("attributeModifiers") && json.get("attributeModifiers").isJsonObject()) {
            JsonObject aObj = json.getAsJsonObject("attributeModifiers");
            for (String key : aObj.keySet()) {
                attrs.put(key, aObj.get(key).getAsDouble());
            }
        }

        SuitModelInfo model = json.has("modelInfo") && json.get("modelInfo").isJsonObject() ?
                SuitModelInfo.fromJson(json.getAsJsonObject("modelInfo")) :
                SuitModelInfo.standard("ascendant:textures/models/suit/default.png", 0x3366CC, 0xFFCC00);

        return new SuitDefinition(id, nameKey, descKey, universe, author, rarity, powers, abilities, attrs, model);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private String nameKey;
        private String descriptionKey;
        private String universe = "Ascendant Prime";
        private String author = "Ascendant Team";
        private SuitRarity rarity = SuitRarity.RARE;
        private final List<ResourceLocation> grantedPowers = new ArrayList<>();
        private final List<ResourceLocation> grantedAbilities = new ArrayList<>();
        private final Map<String, Double> attributeModifiers = new HashMap<>();
        private SuitModelInfo modelInfo;

        public Builder(ResourceLocation id) {
            this.id = id;
            this.nameKey = "suit." + id.getNamespace() + "." + id.getPath();
            this.descriptionKey = nameKey + ".desc";
            this.modelInfo = SuitModelInfo.standard("ascendant:textures/models/suit/" + id.getPath() + ".png", 0x2244AA, 0xFFDD00);
        }

        public Builder nameKey(String key) {
            this.nameKey = key;
            return this;
        }

        public Builder descriptionKey(String key) {
            this.descriptionKey = key;
            return this;
        }

        public Builder universe(String universe) {
            this.universe = universe;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder rarity(SuitRarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public Builder addPower(ResourceLocation powerId) {
            this.grantedPowers.add(powerId);
            return this;
        }

        public Builder addAbility(ResourceLocation abilityId) {
            this.grantedAbilities.add(abilityId);
            return this;
        }

        public Builder addAttributeModifier(String attribute, double value) {
            this.attributeModifiers.put(attribute, value);
            return this;
        }

        public Builder modelInfo(SuitModelInfo modelInfo) {
            this.modelInfo = modelInfo;
            return this;
        }

        public SuitDefinition build() {
            return new SuitDefinition(id, nameKey, descriptionKey, universe, author, rarity,
                    grantedPowers, grantedAbilities, attributeModifiers, modelInfo);
        }
    }
}
