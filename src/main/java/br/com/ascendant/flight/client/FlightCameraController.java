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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Optional client-side flight camera. Every adjustment is applied to the
 * per-frame camera event, so vanilla owns the base angles and disabling the
 * options restores the normal third-person camera immediately.
 */
@Mod.EventBusSubscriber(modid = Ascendant.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FlightCameraController {
    private static final float MAX_BANK_DEGREES = 18.0F;
    private static final float MAX_PITCH_DEGREES = 7.0F;
    private static final float MAX_CAMERA_ROLL_FROM_MANEUVER = 32.0F;

    private static float smoothedFovOffset;

    private FlightCameraController() {
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
        FlightState state = FlightController.getState(player);
        if (state == null || !state.isActive()) {
            return;
        }

        float strength = (float) (double) FlightClientTuning.CAMERA_STRENGTH.get();
        Vec3 velocity = player.getDeltaMovement();
        float bank = bankDegrees(player, velocity) * strength;
        float pitch = Mth.clamp((float) velocity.y * -24.0F, -MAX_PITCH_DEGREES, MAX_PITCH_DEGREES)
                * strength;

        if (state.getManeuverTicksRemaining() > 0 && state.getManeuverDirection() != 0) {
            float maneuver = FlightPoseLibrary.maneuverRoll(
                    state.getManeuverDirection(), state.getManeuverTicksRemaining());
            bank += maneuver * 0.12F;
        }

        event.setRoll(event.getRoll() + Mth.clamp(bank, -MAX_CAMERA_ROLL_FROM_MANEUVER,
                MAX_CAMERA_ROLL_FROM_MANEUVER));
        event.setPitch(event.getPitch() + pitch);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!FlightClientTuning.ENABLED.get() || !FlightClientTuning.DYNAMIC_FOV.get()) {
            smoothedFovOffset = 0.0F;
            return;
        }
        LocalPlayer player = localFlyingPlayer();
        FlightState state = player == null ? null : FlightController.getState(player);
        if (player == null || state == null || !state.isActive()) {
            smoothedFovOffset = 0.0F;
            return;
        }
        double speed = player.getDeltaMovement().length();
        float speedFactor = Mth.clamp((float) (speed / 0.65D), 0.0F, 1.6F);
        float target = speedFactor * (float) (double) FlightClientTuning.FOV_STRENGTH.get();
        smoothedFovOffset += (target - smoothedFovOffset) * 0.18F;
        event.setFOV(event.getFOV() + smoothedFovOffset);
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

    private static float bankDegrees(LocalPlayer player, Vec3 velocity) {
        double horizontalSqr = velocity.x * velocity.x + velocity.z * velocity.z;
        if (horizontalSqr < 0.0016D) {
            return 0.0F;
        }
        Vec3 look = player.getViewVector(1.0F);
        double lookHorizontalSqr = look.x * look.x + look.z * look.z;
        if (lookHorizontalSqr < 1.0E-4D) {
            return 0.0F;
        }
        double cross = velocity.x * look.z - velocity.z * look.x;
        double dot = velocity.x * look.x + velocity.z * look.z;
        float turn = (float) Math.toDegrees(Math.atan2(cross, dot));
        return Mth.clamp(turn * 0.35F, -MAX_BANK_DEGREES, MAX_BANK_DEGREES);
    }
}
