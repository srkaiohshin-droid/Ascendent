package br.com.ascendant.flight;

import br.com.ascendant.Ascendant;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client visual presentation handler for the camera.
 *
 * <p>The player body pose itself is applied by the client renderer mixins and
 * {@link br.com.ascendant.flight.client.FlightAnimationController}. This class
 * only adds the subtle camera bank during high-speed curves, which is a camera
 * effect and must not be mixed with the entity orientation.</p>
 */
@Mod.EventBusSubscriber(modid = Ascendant.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FlightRenderEvents {

    private FlightRenderEvents() {
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        FlightState state = FlightController.getState(minecraft.player);
        if (state == null || !state.isActive()) {
            return;
        }

        Vec3 motion = minecraft.player.getDeltaMovement();
        Vec3 look = minecraft.player.getViewVector((float) event.getPartialTick());
        double horizontalVelocity = motion.x * motion.x + motion.z * motion.z;
        double horizontalLook = look.x * look.x + look.z * look.z;
        if (horizontalVelocity <= 0.01D || horizontalLook <= 0.01D) {
            return;
        }

        double cross = motion.x * look.z - motion.z * look.x;
        double dot = (motion.x * look.x + motion.z * look.z)
                / Math.sqrt(horizontalVelocity * horizontalLook);
        dot = Math.max(-1.0D, Math.min(1.0D, dot));
        float roll = (float) (Math.signum(cross) * Math.acos(dot) * (180.0D / Math.PI) * 0.15D);
        roll = Math.max(-12.0F, Math.min(12.0F, roll));
        event.setRoll(event.getRoll() + roll);
    }
}
