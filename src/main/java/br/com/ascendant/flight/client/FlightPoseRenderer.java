package br.com.ascendant.flight.client;

import br.com.ascendant.flight.FlightPose;
import br.com.ascendant.flight.FlightPoseLibrary;
import br.com.ascendant.flight.FlightPoseSample;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;

/**
 * Applies {@link FlightPose} values to the vanilla player renderer.
 *
 * <p>The whole-body rotation is applied at the tail of {@code setupRotations},
 * inside the renderer's own pose-stack scope. This keeps the rotation entirely
 * visual: the entity position, hitbox, camera yaw, pitch and collision shape are
 * untouched. The pivot is the torso centre ({@link
 * FlightPoseLibrary#FLIGHT_BODY_PIVOT_Y}) rather than the old knee-height pivot,
 * which is what kept the chest and legs from moving as one rigid plank.</p>
 *
 * <p>Head values are local neck angles. They are intentionally much smaller than
 * the torso pitch, following the vanilla horizontal-flight reference, and are
 * applied on all three axes so the head can look into a bank without detaching
 * from the shoulders.</p>
 */
public final class FlightPoseRenderer {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);
    /** Maximum forward neck shift in model pixels; purely cosmetic and bounded. */
    private static final float MAX_NECK_SHIFT = 0.35F;

    private FlightPoseRenderer() {
    }

    /** Rotates the whole model towards the requested body orientation. */
    public static boolean applyBody(PoseStack poseStack, Player player, float partialTick) {
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

        // bodyLift is expressed in this pre-flip frame: positive values lower the
        // horizontal body into the middle of the player bounding box.
        poseStack.translate(0.0F, pose.bodyLift * weight, 0.0F);
        poseStack.translate(0.0F, FlightPoseLibrary.FLIGHT_BODY_PIVOT_Y, 0.0F);
        // PoseStack post-multiplies, so these are listed from the outermost
        // orientation inwards: first the body's trajectory yaw relative to the
        // camera, then the horizontal pitch, then the longitudinal bank. The
        // yaw must stay outside the pitch or a 180-degree turn would twist the
        // horizontal body around its own chest instead of turning it around
        // the world's vertical axis.
        poseStack.mulPose(Axis.YP.rotationDegrees(
                sample.bodyYawOffsetDegrees() * weight));
        float bodyPitch = pose.bodyPitchDegrees + sample.maneuverPitchDegrees();
        poseStack.mulPose(Axis.XP.rotationDegrees(bodyPitch * weight));
        float bank = pose.bodyRollDegrees + sample.maneuverRollDegrees();
        poseStack.mulPose(Axis.YP.rotationDegrees(bank * weight));
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
            resetNeckOffset(model);
            return false;
        }
        float weight = sample.weight();
        if (weight <= 1.0E-4F) {
            resetNeckOffset(model);
            return false;
        }
        FlightPose pose = sample.pose();

        model.rightArm.xRot = lerp(model.rightArm.xRot, radians(pose.rightArmXRot), weight);
        model.rightArm.yRot = lerp(model.rightArm.yRot, radians(pose.rightArmYRot), weight);
        model.rightArm.zRot = lerp(model.rightArm.zRot, radians(pose.rightArmZRot), weight);
        model.leftArm.xRot = lerp(model.leftArm.xRot, radians(pose.leftArmXRot), weight);
        model.leftArm.yRot = lerp(model.leftArm.yRot, radians(pose.leftArmYRot), weight);
        model.leftArm.zRot = lerp(model.leftArm.zRot, radians(pose.leftArmZRot), weight);

        model.rightLeg.xRot = lerp(model.rightLeg.xRot, radians(pose.rightLegXRot), weight);
        model.rightLeg.yRot = lerp(model.rightLeg.yRot, radians(pose.rightLegYRot), weight);
        model.rightLeg.zRot = lerp(model.rightLeg.zRot, radians(pose.rightLegZRot), weight);
        model.leftLeg.xRot = lerp(model.leftLeg.xRot, radians(pose.leftLegXRot), weight);
        model.leftLeg.yRot = lerp(model.leftLeg.yRot, radians(pose.leftLegYRot), weight);
        model.leftLeg.zRot = lerp(model.leftLeg.zRot, radians(pose.leftLegZRot), weight);

        // Local neck angles, not a copy of the torso pitch. The small forward
        // shift is capped below half a model pixel so the head cannot slide off
        // the chest or leave a gap.
        model.head.xRot = lerp(model.head.xRot, radians(pose.headXRotDegrees), weight);
        model.head.yRot = lerp(model.head.yRot, radians(pose.headYRotDegrees), weight);
        model.head.zRot = lerp(model.head.zRot, radians(pose.headZRotDegrees), weight);
        float neckExtension = Math.min(1.0F, Math.max(0.0F, -pose.headXRotDegrees / 75.0F));
        // Set the offset directly from the interpolated tick sample. Lerping the
        // previous value would make this cosmetic offset depend on the render
        // frame rate and would leave it stuck after flight ends.
        model.head.z = -MAX_NECK_SHIFT * neckExtension * weight;
        model.hat.copyFrom(model.head);
        return true;
    }

    /**
     * Vanilla never resets {@code head.z}, so the flight offset must be cleared
     * when the pose stops. The hat is a sibling part in 1.20.1 and needs the
     * same reset or it would stay detached from the head.
     */
    private static void resetNeckOffset(HumanoidModel<?> model) {
        model.head.z = 0.0F;
        model.hat.z = 0.0F;
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
