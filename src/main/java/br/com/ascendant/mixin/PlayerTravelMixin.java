package br.com.ascendant.mixin;

import br.com.ascendant.flight.FlightController;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla player travel while flight is active.
 *
 * <p>The reference implementation hooks the equivalent of
 * {@code moveEntityWithHeading} and returns before vanilla movement runs. The
 * injection lives in the common mixin set because the server and the owning
 * client must run the same motion kernel; the client prediction never gains
 * authority over the server path.</p>
 */
@Mixin(Player.class)
public abstract class PlayerTravelMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void ascendant$flightTravel(Vec3 travelVector, CallbackInfo callback) {
        Player player = (Player) (Object) this;
        if (FlightController.travel(player)) {
            callback.cancel();
        }
    }
}
