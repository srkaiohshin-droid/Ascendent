package br.com.ascendant.flight.client;

import br.com.ascendant.flight.FlightPose;
import br.com.ascendant.flight.FlightPoseLibrary;
import br.com.ascendant.flight.FlightPoseSample;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Applies {@link FlightPose} values to the vanilla player renderer.
 *
 * <p>The whole-body rotation is applied at the tail of {@code setupRotations},
 * inside the renderer's own pose-stack scope. This keeps the rotation entirely
 * visual: the entity position, hitbox, camera yaw, pitch and collision shape are
 * untouched. The pivot is the torso centre ({@link
 * FlightPoseLibrary#FLIGHT_BODY_PIVOT_Y}).</p>
 *
 * <p>Vanilla rotated the model by the interpolated movement-body yaw, which can
 * point at the last horizontal motion instead of the crosshair. The renderer
 * adds the shortest yaw correction from that body yaw to the real interpolated
 * look yaw before pitching, so the chest, shoulders and head all face the
 * player's actual view. There is no body-yaw lead and no neck translation; a
 * 180-degree reversal is still entirely controlled by the mouse. Pitch is
 * applied before the bank/maneuvre roll on the longitudinal {@link Axis#ZP}
 * axis, which is the axis the reference animation rotates around. The
 * barrel-roll limb/pitch accents are added outside the ordinary pose smoothing
 * so the ten-tick reference beat stays crisp.</p>
 */
public final class FlightPoseRenderer {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);

    private FlightPoseRenderer() {
    }

    /**
     * Rotates the whole model towards the requested body orientation.
     *
     * @param rotationYaw the interpolated movement-body yaw vanilla already used
     *                    for this render frame
     */
    public static boolean applyBody(PoseStack poseStack, Player player, float rotationYaw,
                                    float partialTick) {
        if (!isPoseVisible(player)) {
            return false;
        }
        FlightPoseSample sample = FlightAnimationController.sample(player, partialTick);
        if (sample == null) {
            return false;
        }
        float weight = sample.weight();
        if (weight <= 1.0E-4F) {
            return false;
        }
        FlightPose pose = sample.pose();
        FlightPose overlay = sample.rollOverlay();
        float delta = Mth.clamp(partialTick, 0.0F, 1.0F);
        float lookYaw = Mth.rotLerp(delta, player.yRotO, player.getYRot());
        float yawCorrection = FlightPoseLibrary.lookYawCorrectionDegrees(
                rotationYaw, lookYaw) * weight;

        // bodyLift is expressed in this pre-flip frame: positive values lower the
        // horizontal body into the middle of the player bounding box.
        poseStack.translate(0.0F, (pose.bodyLift + overlay.bodyLift) * weight, 0.0F);
        poseStack.translate(0.0F, FlightPoseLibrary.FLIGHT_BODY_PIVOT_Y, 0.0F);
        // PoseStack post-multiplies, so these are listed from the outermost
        // orientation inwards: the look-yaw correction, body pitch, then the
        // longitudinal roll. The maneuvre angles are authoritative and are
        // added outside the blend; the barrel-roll overlay is added outside the
        // ordinary pose smoothing.
        poseStack.mulPose(Axis.YP.rotationDegrees(yawCorrection));
        float bodyPitch = pose.bodyPitchDegrees + sample.maneuverPitchDegrees()
                + overlay.bodyPitchDegrees;
        poseStack.mulPose(Axis.XP.rotationDegrees(bodyPitch * weight));
        float bank = pose.bodyRollDegrees + sample.maneuverRollDegrees()
                + overlay.bodyRollDegrees;
        poseStack.mulPose(Axis.ZP.rotationDegrees(bank * weight));
        poseStack.translate(0.0F, -FlightPoseLibrary.FLIGHT_BODY_PIVOT_Y, 0.0F);
        return true;
    }

    /** Blends the flight joints over the vanilla pose that setupAnim just produced. */
    public static boolean applyModel(HumanoidModel<?> model, Player player, float partialTick) {
        if (!isPoseVisible(player)) {
            return false;
        }
        FlightPoseSample sample = FlightAnimationController.sample(player, partialTick);
        if (sample == null) {
            return false;
        }
        float weight = sample.weight();
        if (weight <= 1.0E-4F) {
            return false;
        }
        FlightPose pose = sample.pose();
        FlightPose overlay = sample.rollOverlay();

        model.rightArm.xRot = lerp(model.rightArm.xRot, radians(pose.rightArmXRot), weight)
                + radians(overlay.rightArmXRot) * weight;
        model.rightArm.yRot = lerp(model.rightArm.yRot, radians(pose.rightArmYRot), weight)
                + radians(overlay.rightArmYRot) * weight;
        model.rightArm.zRot = lerp(model.rightArm.zRot, radians(pose.rightArmZRot), weight)
                + radians(overlay.rightArmZRot) * weight;
        model.leftArm.xRot = lerp(model.leftArm.xRot, radians(pose.leftArmXRot), weight)
                + radians(overlay.leftArmXRot) * weight;
        model.leftArm.yRot = lerp(model.leftArm.yRot, radians(pose.leftArmYRot), weight)
                + radians(overlay.leftArmYRot) * weight;
        model.leftArm.zRot = lerp(model.leftArm.zRot, radians(pose.leftArmZRot), weight)
                + radians(overlay.leftArmZRot) * weight;

        model.rightLeg.xRot = lerp(model.rightLeg.xRot, radians(pose.rightLegXRot), weight)
                + radians(overlay.rightLegXRot) * weight;
        model.rightLeg.yRot = lerp(model.rightLeg.yRot, radians(pose.rightLegYRot), weight)
                + radians(overlay.rightLegYRot) * weight;
        model.rightLeg.zRot = lerp(model.rightLeg.zRot, radians(pose.rightLegZRot), weight)
                + radians(overlay.rightLegZRot) * weight;
        model.leftLeg.xRot = lerp(model.leftLeg.xRot, radians(pose.leftLegXRot), weight)
                + radians(overlay.leftLegXRot) * weight;
        model.leftLeg.yRot = lerp(model.leftLeg.yRot, radians(pose.leftLegYRot), weight)
                + radians(overlay.leftLegYRot) * weight;
        model.leftLeg.zRot = lerp(model.leftLeg.zRot, radians(pose.leftLegZRot), weight)
                + radians(overlay.leftLegZRot) * weight;

        model.head.xRot = lerp(model.head.xRot, radians(pose.headXRotDegrees), weight)
                + radians(overlay.headXRotDegrees) * weight;
        model.head.yRot = lerp(model.head.yRot, radians(pose.headYRotDegrees), weight)
                + radians(overlay.headYRotDegrees) * weight;
        model.head.zRot = lerp(model.head.zRot, radians(pose.headZRotDegrees), weight)
                + radians(overlay.headZRotDegrees) * weight;
        model.hat.copyFrom(model.head);
        return true;
    }

    private static boolean isPoseVisible(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        return player != minecraft.player || !minecraft.options.getCameraType().isFirstPerson();
    }

    private static float radians(float degrees) {
        return degrees * DEG_TO_RAD;
    }

    private static float lerp(float from, float to, float delta) {
        return from + (to - from) * delta;
    }
}
