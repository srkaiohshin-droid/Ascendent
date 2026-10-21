package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard data-holding template component for catalog entries whose specific
 * mechanical runtime is scheduled for subsequent expansion batches.
 * Transparently reports isFunctional() == false so it is never falsely counted as functional.
 */
public final class TemplateComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:template";
    private final String mechanicName;
    private final Map<String, String> parameters;

    public TemplateComponent(String mechanicName, Map<String, String> parameters) {
        this.mechanicName = mechanicName;
        this.parameters = new HashMap<>(parameters);
    }

    public TemplateComponent(String mechanicName) {
        this(mechanicName, Collections.emptyMap());
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isFunctional() {
        return false;
    }

    public String getMechanicName() {
        return mechanicName;
    }

    public Map<String, String> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("mechanic", mechanicName);
        JsonObject params = new JsonObject();
        parameters.forEach(params::addProperty);
        obj.add("parameters", params);
        return obj;
    }

    public static TemplateComponent fromJson(JsonObject json) {
        String mech = json.has("mechanic") ? json.get("mechanic").getAsString() : "unspecified";
        Map<String, String> params = new HashMap<>();
        if (json.has("parameters") && json.get("parameters").isJsonObject()) {
            JsonObject pObj = json.getAsJsonObject("parameters");
            for (String key : pObj.keySet()) {
                params.put(key, pObj.get(key).getAsString());
            }
        }
        return new TemplateComponent(mech, params);
    }
}
