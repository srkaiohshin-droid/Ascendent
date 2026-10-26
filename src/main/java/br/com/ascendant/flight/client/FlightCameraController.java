package br.com.ascendant.flight.client;

import br.com.ascendant.Ascendant;
import br.com.ascendant.flight.FlightCameraMath;
import br.com.ascendant.flight.FlightClientTuning;
import br.com.ascendant.flight.FlightController;
import br.com.ascendant.flight.FlightState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Optional client-side boost camera, ported from the reference flight mouse
 * manager.
 *
 * <p>The reference does not add a pitch offset, a field-of-view change or a
 * maneuvre camera. It only tilts the view around its longitudinal axis when the
 * horizontal velocity diverges from the crosshair while boosting:</p>
 *
 * <pre>
 * sign(motion.x * look.z - motion.z * look.x)
 *     * acos(dot(motion, look) / (|motion| * |look|))
 *     * min(|motion|^2, 1) / 2.5
 * </pre>
 *
 * <p>The target is clamped to +/-90 degrees and smoothed with
 * {@code (target + 2 * roll) / 3}. Everything is a per-frame view transform, so
 * the server, movement path and collision shape are untouched.</p>
 */
@Mod.EventBusSubscriber(modid = Ascendant.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FlightCameraController {
    private static float previousRoll;
    private static float currentRoll;

    private FlightCameraController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        boolean enabled = FlightClientTuning.ENABLED.get()
                && FlightClientTuning.DYNAMIC_CAMERA.get();
        if (!enabled) {
            previousRoll = 0.0F;
            currentRoll = 0.0F;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null) {
            decayRoll();
            return;
        }
        FlightState state = FlightController.getState(player);
        if (state == null || !state.isActive() || !player.isSprinting()) {
            decayRoll();
            return;
        }

        float target = FlightCameraMath.rollTarget(
                positionStep(player), player.getViewVector(1.0F));
        previousRoll = currentRoll;
        currentRoll = FlightCameraMath.smooth(currentRoll, target);
        if (Math.abs(currentRoll) < 0.01F && Math.abs(target) < 0.01F) {
            previousRoll = 0.0F;
            currentRoll = 0.0F;
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!FlightClientTuning.ENABLED.get() || !FlightClientTuning.DYNAMIC_CAMERA.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        FlightState state = FlightController.getState(player);
        if (state == null || !state.isActive()) {
            return;
        }
        float strength = (float) (double) FlightClientTuning.CAMERA_STRENGTH.get();
        float roll = Mth.lerp((float) event.getPartialTick(), previousRoll, currentRoll);
        if (Math.abs(roll) > 0.01F) {
            event.setRoll(event.getRoll() + roll * strength);
        }
    }

    /**
     * Real per-tick displacement, matching the reference's motion source. The
     * stored delta movement contains the extra hover drag that the position
     * step does not expose, so it would understate the turn.
     */
    private static Vec3 positionStep(LocalPlayer player) {
        return new Vec3(player.getX() - player.xOld, player.getY() - player.yOld,
                player.getZ() - player.zOld);
    }

    private static void decayRoll() {
        previousRoll = currentRoll;
        currentRoll = FlightCameraMath.smooth(currentRoll, 0.0F);
        if (Math.abs(currentRoll) < 0.01F) {
            previousRoll = 0.0F;
            currentRoll = 0.0F;
        }
    }
}
