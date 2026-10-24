package br.com.ascendant.flight.client;

import br.com.ascendant.Ascendant;
import br.com.ascendant.flight.FlightClientTuning;
import br.com.ascendant.flight.FlightController;
import br.com.ascendant.flight.FlightPoseLibrary;
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
 * Optional client-side flight camera. Every adjustment is applied to the
 * per-frame camera event, so vanilla owns the base angles and disabling the
 * options restores the normal third-person camera immediately.
 *
 * <p>This is the single owner of the flight camera. The old second handler that
 * added another roll during curves was removed, and the bank now comes from the
 * same per-tick smoothed value that poses the body. FOV smoothing runs on the
 * client tick and is interpolated by the partial tick, so it does not depend on
 * the frame rate.</p>
 */
@Mod.EventBusSubscriber(modid = Ascendant.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FlightCameraController {
    private static final float MAX_BANK_DEGREES = 18.0F;
    private static final float MAX_PITCH_DEGREES = 7.0F;
    private static final float MAX_MANEUVER_CAMERA_ROLL = 12.0F;
    private static final float FOV_SMOOTHING = 0.20F;

    private static float previousFovOffset;
    private static float currentFovOffset;

    private FlightCameraController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean enabled = FlightClientTuning.ENABLED.get()
                && FlightClientTuning.DYNAMIC_FOV.get();
        FlightState state = player == null ? null : FlightController.getState(player);
        boolean active = enabled && player != null && minecraft.level != null
                && minecraft.screen == null && state != null && state.isActive();

        previousFovOffset = currentFovOffset;
        if (!enabled) {
            previousFovOffset = 0.0F;
            currentFovOffset = 0.0F;
            return;
        }
        if (!active) {
            currentFovOffset += (0.0F - currentFovOffset) * FOV_SMOOTHING;
            if (Math.abs(currentFovOffset) < 0.01F) {
                previousFovOffset = 0.0F;
                currentFovOffset = 0.0F;
            }
            return;
        }

        double speed = player.getDeltaMovement().length();
        float speedFactor = Mth.clamp((float) (speed / 0.65D), 0.0F, 1.6F);
        float target = speedFactor * (float) (double) FlightClientTuning.FOV_STRENGTH.get();
        currentFovOffset += (target - currentFovOffset) * FOV_SMOOTHING;
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!FlightClientTuning.ENABLED.get() || !FlightClientTuning.DYNAMIC_CAMERA.get()) {
            return;
        }
        LocalPlayer player = localFlyingPlayer();
        if (player == null || Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }

        float strength = (float) (double) FlightClientTuning.CAMERA_STRENGTH.get();
        float bank = FlightAnimationController.cameraBank(player, (float) event.getPartialTick());
        float pitch = Mth.clamp((float) player.getDeltaMovement().y * -24.0F,
                -MAX_PITCH_DEGREES, MAX_PITCH_DEGREES) * strength;

        FlightState state = FlightController.getState(player);
        if (state != null && state.getManeuverTicksRemaining() > 0
                && state.getManeuverKind() == FlightState.MANEUVER_ROLL_360
                && state.getManeuverDirection() != 0) {
            // A normalized bump that starts and ends at zero. Scaling the raw
            // 0..360 roll would leave the camera tilted at the final tick and
            // pop back to neutral on the next frame.
            float progress = Mth.clamp((FlightPoseLibrary.MANEUVER_TICKS
                    - state.getManeuverTicksRemaining())
                    / (float) (FlightPoseLibrary.MANEUVER_TICKS - 1), 0.0F, 1.0F);
            float bump = Mth.sin(progress * (float) Math.PI);
            bank += state.getManeuverDirection() * MAX_MANEUVER_CAMERA_ROLL * bump * bump;
        }

        event.setRoll(event.getRoll() + Mth.clamp(bank * strength * 0.55F,
                -MAX_BANK_DEGREES, MAX_BANK_DEGREES));
        event.setPitch(event.getPitch() + pitch);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!FlightClientTuning.ENABLED.get() || !FlightClientTuning.DYNAMIC_FOV.get()) {
            return;
        }
        float offset = Mth.lerp((float) event.getPartialTick(), previousFovOffset, currentFovOffset);
        if (Math.abs(offset) > 0.01F) {
            event.setFOV(event.getFOV() + offset);
            return;
        }
        // A maneuver is a visual rotation; add a tiny speed-independent pulse at
        // its edges without letting the FOV drift while the roll is running.
        LocalPlayer player = localFlyingPlayer();
        if (player == null) {
            return;
        }
        FlightState state = FlightController.getState(player);
        if (state == null || state.getManeuverTicksRemaining() <= 0) {
            return;
        }
        float progress = (FlightPoseLibrary.MANEUVER_TICKS - state.getManeuverTicksRemaining())
                / (float) (FlightPoseLibrary.MANEUVER_TICKS - 1);
        float pulse = (float) Math.sin(Mth.clamp(progress, 0.0F, 1.0F) * Math.PI) * 1.5F;
        event.setFOV(event.getFOV() + pulse);
    }

    private static LocalPlayer localFlyingPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null) {
            return null;
        }
        FlightState state = FlightController.getState(player);
        return state != null && state.isActive() ? player : null;
    }
}
