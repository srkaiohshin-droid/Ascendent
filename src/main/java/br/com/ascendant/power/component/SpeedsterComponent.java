package br.com.ascendant.power.component;

import br.com.ascendant.speedster.SpeedsterController;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the {@code ascendant:kinetic_sprint} toggle power to the server-authoritative
 * speedster movement controller.
 *
 * <p>The power runtime owns the toggle state; this component only mirrors it into the
 * transient speedster state and validates that the mode can actually start (alive, on the
 * ground, not in a fluid, not already flying). That keeps the catalog, the radial menu and
 * the dedicated speedster keybind on a single source of truth.</p>
 */
public final class SpeedsterComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:speedster";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isFunctional() {
        return true;
    }

    @Override
    public boolean canToggle(ServerPlayer player, boolean enable) {
        return !enable || SpeedsterController.canActivate(player);
    }

    @Override
    public void onToggle(ServerPlayer player, boolean enabled) {
        SpeedsterController.setModeEnabled(player, enabled);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        return json;
    }

    public static SpeedsterComponent fromJson(JsonObject json) {
        return new SpeedsterComponent();
    }
}
