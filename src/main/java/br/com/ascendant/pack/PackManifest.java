package br.com.ascendant.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Metadata manifest for an Ascendant Content Pack (powers, abilities, suits, assets).
 */
public final class PackManifest {
    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_VERSION_LENGTH = 64;
    private static final int MAX_AUTHOR_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 4096;
    private static final int MAX_DEPENDENCIES = 128;
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "con", "prn", "aux", "nul", "conin$", "conout$", "clock$"
    );

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
        this.id = validatePackId(id);
        this.name = requireText(name, "name", MAX_NAME_LENGTH);
        this.version = requireText(version, "version", MAX_VERSION_LENGTH);
        this.author = requireText(author, "author", MAX_AUTHOR_LENGTH);
        this.description = requireLength(Objects.requireNonNull(description, "description"),
                "description", MAX_DESCRIPTION_LENGTH);
        this.minMcVersion = optionalText(minMcVersion, "minMcVersion", MAX_VERSION_LENGTH);
        this.minForgeVersion = optionalText(minForgeVersion, "minForgeVersion", MAX_VERSION_LENGTH);
        this.minAscendantVersion = optionalText(minAscendantVersion, "minAscendantVersion", MAX_VERSION_LENGTH);
        this.dependencies = validateDependencies(dependencies);
        if (powerCount < 0 || abilityCount < 0 || suitCount < 0) {
            throw new IllegalArgumentException("Pack content counts cannot be negative.");
        }
        this.powerCount = powerCount;
        this.abilityCount = abilityCount;
        this.suitCount = suitCount;
        this.sha256 = requireLength(Objects.requireNonNullElse(sha256, ""), "sha256", 64);
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

    public static PackManifest fromJson(JsonObject json) {
        if (json == null) {
            throw new IllegalArgumentException("Pack manifest must contain a JSON object.");
        }

        String id = readString(json, "id", null);
        String name = readString(json, "name", id);
        String version = readString(json, "version", "1.0.0");
        String author = readString(json, "author", "Unknown");
        String description = readString(json, "description", "");
        String minMc = readString(json, "minMcVersion", "1.20.1");
        String minForge = readString(json, "minForgeVersion", "47.3.0");
        String minAsc = readString(json, "minAscendantVersion", "0.1.0");
        int powers = readCount(json, "powerCount");
        int abilities = readCount(json, "abilityCount");
        int suits = readCount(json, "suitCount");
        String sha = readString(json, "sha256", "");

        List<String> deps = new ArrayList<>();
        if (json.has("dependencies")) {
            JsonElement dependencies = json.get("dependencies");
            if (!dependencies.isJsonArray()) {
                throw new IllegalArgumentException("Pack dependencies must be a JSON array.");
            }
            for (JsonElement dependency : dependencies.getAsJsonArray()) {
                if (!dependency.isJsonPrimitive() || !dependency.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Pack dependency IDs must be strings.");
                }
                deps.add(dependency.getAsString());
            }
        }

        return new PackManifest(id, name, version, author, description, minMc, minForge, minAsc, deps, powers, abilities, suits, sha);
    }

    static String validatePackId(String id) {
        String deviceName = id == null ? "" : id.split("\\.", 2)[0];
        if (id == null || id.length() > MAX_ID_LENGTH
                || !id.matches("[a-z0-9][a-z0-9._-]*")
                || id.endsWith(".") || id.contains("..")
                || WINDOWS_RESERVED_NAMES.contains(deviceName)
                || deviceName.matches("(com|lpt)[1-9]")) {
            throw new IllegalArgumentException("Pack ID must be a safe lowercase identifier of at most "
                    + MAX_ID_LENGTH + " characters: " + id);
        }
        return id;
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Pack manifest field '" + fieldName + "' cannot be blank.");
        }
        return requireLength(value, fieldName, maxLength);
    }

    private static String optionalText(String value, String fieldName, int maxLength) {
        return value == null ? null : requireLength(value, fieldName, maxLength);
    }

    private static String requireLength(String value, String fieldName, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("Pack manifest field '" + fieldName + "' exceeds "
                    + maxLength + " characters.");
        }
        return value;
    }

    private static String readString(JsonObject json, String fieldName, String fallback) {
        if (!json.has(fieldName) || json.get(fieldName).isJsonNull()) {
            if (fallback == null) {
                throw new IllegalArgumentException("Pack manifest is missing required field '" + fieldName + "'.");
            }
            return fallback;
        }

        JsonElement value = json.get(fieldName);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Pack manifest field '" + fieldName + "' must be a string.");
        }
        return value.getAsString();
    }

    private static int readCount(JsonObject json, String fieldName) {
        if (!json.has(fieldName)) {
            return 0;
        }

        JsonElement value = json.get(fieldName);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Pack manifest field '" + fieldName + "' must be an integer.");
        }
        try {
            int count = value.getAsBigDecimal().intValueExact();
            if (count < 0) {
                throw new IllegalArgumentException("Pack manifest field '" + fieldName + "' cannot be negative.");
            }
            return count;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Pack manifest field '" + fieldName + "' must be an integer.", exception);
        }
    }

    private static List<String> validateDependencies(List<String> dependencies) {
        Objects.requireNonNull(dependencies, "dependencies");
        if (dependencies.size() > MAX_DEPENDENCIES) {
            throw new IllegalArgumentException("Pack manifest cannot declare more than "
                    + MAX_DEPENDENCIES + " dependencies.");
        }
        List<String> validated = new ArrayList<>(dependencies.size());
        Set<String> unique = new HashSet<>();
        for (String dependency : dependencies) {
            String id = validatePackId(dependency);
            if (!unique.add(id)) {
                throw new IllegalArgumentException("Duplicate pack dependency: " + id);
            }
            validated.add(id);
        }
        return List.copyOf(validated);
    }
}
