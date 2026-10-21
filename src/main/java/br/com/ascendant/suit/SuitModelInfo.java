package br.com.ascendant.suit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Visual modeling and modular aesthetic specification for a suit,
 * decoupled from game mechanics (inspired by the Armoured Workshop concept).
 */
public final class SuitModelInfo {
    private final String texturePath;
    private final String emissivePath;
    private final int primaryColor;
    private final int secondaryColor;
    private final List<String> pieces;
    private final boolean renderCape;

    public SuitModelInfo(String texturePath, String emissivePath, int primaryColor,
                         int secondaryColor, List<String> pieces, boolean renderCape) {
        this.texturePath = texturePath;
        this.emissivePath = emissivePath;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.pieces = List.copyOf(pieces);
        this.renderCape = renderCape;
    }

    public static SuitModelInfo standard(String texturePath, int primaryColor, int secondaryColor) {
        return new SuitModelInfo(texturePath, "", primaryColor, secondaryColor,
                List.of("helmet", "chestplate", "leggings", "boots"), false);
    }

    public String getTexturePath() {
        return texturePath;
    }

    public String getEmissivePath() {
        return emissivePath;
    }

    public int getPrimaryColor() {
        return primaryColor;
    }

    public int getSecondaryColor() {
        return secondaryColor;
    }

    public List<String> getPieces() {
        return pieces;
    }

    public boolean hasCape() {
        return renderCape;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("texturePath", texturePath);
        json.addProperty("emissivePath", emissivePath);
        json.addProperty("primaryColor", primaryColor);
        json.addProperty("secondaryColor", secondaryColor);
        json.addProperty("renderCape", renderCape);

        JsonArray piecesArr = new JsonArray();
        for (String p : pieces) {
            piecesArr.add(p);
        }
        json.add("pieces", piecesArr);
        return json;
    }

    public static SuitModelInfo fromJson(JsonObject json) {
        String tex = json.has("texturePath") ? json.get("texturePath").getAsString() : "";
        String emissive = json.has("emissivePath") ? json.get("emissivePath").getAsString() : "";
        int prim = json.has("primaryColor") ? json.get("primaryColor").getAsInt() : 0xFFFFFF;
        int sec = json.has("secondaryColor") ? json.get("secondaryColor").getAsInt() : 0x000000;
        boolean cape = json.has("renderCape") && json.get("renderCape").getAsBoolean();

        List<String> pieces = new ArrayList<>();
        if (json.has("pieces") && json.get("pieces").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("pieces")) {
                pieces.add(el.getAsString());
            }
        } else {
            pieces = List.of("helmet", "chestplate", "leggings", "boots");
        }

        return new SuitModelInfo(tex, emissive, prim, sec, pieces, cape);
    }
}
