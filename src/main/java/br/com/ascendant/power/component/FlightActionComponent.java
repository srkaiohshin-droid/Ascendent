package br.com.ascendant.power.component;

import br.com.ascendant.flight.FlightController;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

/**
 * Component bridging the Power Framework to the existing FlightController system.
 */
public final class FlightActionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:flight_action";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isFunctional() {
        return true;
    }

    @Override
    public boolean execute(ComponentContext context) {
        ServerPlayer player = context.getPlayer();
        boolean canFly = FlightController.canActivate(player);
        if (canFly) {
            return FlightController.requestActive(player, true);
        }
        return false;
    }

    @Override
    public void onToggle(ServerPlayer player, boolean enabled) {
        FlightController.requestActive(player, enabled);
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        return obj;
    }

    public static FlightActionComponent fromJson(JsonObject json) {
        return new FlightActionComponent();
    }
}
