package br.com.ascendant.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Metadata manifest for an Ascendant Content Pack (powers, abilities, suits, assets).
 */
public final class PackManifest {
    private final String id;
    private final String name;
    private final String version;
    private final String author;
    private final String description;
    private final String minMcVersion;
    private final String minForgeVersion;
    private final String minAscendantVersion;
    private final List<String> dependencies;
    private final int powerCount;
    private final int abilityCount;
    private final int suitCount;
    private final String sha256;

    public PackManifest(String id, String name, String version, String author, String description,
                        String minMcVersion, String minForgeVersion, String minAscendantVersion,
                        List<String> dependencies, int powerCount, int abilityCount, int suitCount, String sha256) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
        this.author = Objects.requireNonNull(author, "author");
        this.description = Objects.requireNonNull(description, "description");
        this.minMcVersion = minMcVersion;
        this.minForgeVersion = minForgeVersion;
        this.minAscendantVersion = minAscendantVersion;
        this.dependencies = List.copyOf(dependencies);
        this.powerCount = powerCount;
        this.abilityCount = abilityCount;
        this.suitCount = suitCount;
        this.sha256 = sha256;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getMinMcVersion() {
        return minMcVersion;
    }

    public String getMinForgeVersion() {
        return minForgeVersion;
    }

    public String getMinAscendantVersion() {
        return minAscendantVersion;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public int getPowerCount() {
        return powerCount;
    }

    public int getAbilityCount() {
        return abilityCount;
    }

    public int getSuitCount() {
        return suitCount;
    }

    public String getSha256() {
        return sha256;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("version", version);
        json.addProperty("author", author);
        json.addProperty("description", description);
        json.addProperty("minMcVersion", minMcVersion);
        json.addProperty("minForgeVersion", minForgeVersion);
        json.addProperty("minAscendantVersion", minAscendantVersion);
        json.addProperty("powerCount", powerCount);
        json.addProperty("abilityCount", abilityCount);
        json.addProperty("suitCount", suitCount);
        json.addProperty("sha256", sha256);

        JsonArray deps = new JsonArray();
        for (String dep : dependencies) {
            deps.add(dep);
        }
        json.add("dependencies", deps);
        return json;
    }

    public static PackManifest fromJson(JsonObject json) throws IllegalArgumentException {
        if (!json.has("id") || !json.get("id").isJsonPrimitive()) {
            throw new IllegalArgumentException("Pack manifest missing required field: id");
        }
        String id = json.get("id").getAsString();
        if (id.isBlank()) {
            throw new IllegalArgumentException("Pack manifest id must not be blank");
        }
        String name = json.has("name") ? json.get("name").getAsString() : id;
        String version = json.has("version") ? json.get("version").getAsString() : "1.0.0";
        String author = json.has("author") ? json.get("author").getAsString() : "Unknown";
        String description = json.has("description") ? json.get("description").getAsString() : "";
        String minMc = json.has("minMcVersion") ? json.get("minMcVersion").getAsString() : "1.20.1";
        String minForge = json.has("minForgeVersion") ? json.get("minForgeVersion").getAsString() : "47.3.0";
        String minAsc = json.has("minAscendantVersion") ? json.get("minAscendantVersion").getAsString() : "0.1.0";
        int powers = json.has("powerCount") ? json.get("powerCount").getAsInt() : 0;
        int abilities = json.has("abilityCount") ? json.get("abilityCount").getAsInt() : 0;
        int suits = json.has("suitCount") ? json.get("suitCount").getAsInt() : 0;
        String sha = json.has("sha256") ? json.get("sha256").getAsString() : "";

        List<String> deps = new ArrayList<>();
        if (json.has("dependencies") && json.get("dependencies").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("dependencies")) {
                deps.add(el.getAsString());
            }
        }

        return new PackManifest(id, name, version, author, description, minMc, minForge, minAsc, deps, powers, abilities, suits, sha);
    }
}
