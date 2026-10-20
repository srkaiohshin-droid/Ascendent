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
 * untouched. The injection point is before the renderer flips the model, so the
 * pitch sign is expressed in the model's own frame and the body is pivoted around
 * the torso instead of the feet.</p>
 */
public final class FlightPoseRenderer {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);

    private FlightPoseRenderer() {
    }

    /** Rotates the whole model towards the requested body orientation. */
    public static void applyBody(PoseStack poseStack, Player player, float partialTick) {
        if (!isPoseVisible(player)) {
            return;
        }
        FlightPoseSample sample = FlightAnimationController.sample(player, partialTick);
        if (sample == null) {
            return;
        }
        float weight = sample.weight();
        if (weight <= 1.0E-4F) {
            return;
        }
        FlightPose pose = sample.pose();

        // bodyLift is expressed in this pre-flip frame: positive values lower the
        // horizontal body into the middle of the player bounding box.
        poseStack.translate(0.0F, pose.bodyLift * weight, 0.0F);
        poseStack.translate(0.0F, FlightPoseLibrary.BODY_PIVOT_Y, 0.0F);
        // PoseStack post-multiplies: the Y rotation is applied to vertices first,
        // so it banks around the body's long axis before the body is pitched.
        poseStack.mulPose(Axis.XP.rotationDegrees(pose.bodyPitchDegrees * weight));
        poseStack.mulPose(Axis.YP.rotationDegrees(pose.bodyRollDegrees * weight));
        poseStack.translate(0.0F, -FlightPoseLibrary.BODY_PIVOT_Y, 0.0F);
    }

    /** Blends the flight joints over the vanilla pose that setupAnim just produced. */
    public static void applyModel(HumanoidModel<?> model, Player player, float partialTick) {
        if (!isPoseVisible(player)) {
            return;
        }
        FlightPoseSample sample = FlightAnimationController.sample(player, partialTick);
        if (sample == null) {
            return;
        }
        float weight = sample.weight();
        if (weight <= 1.0E-4F) {
            return;
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

        // A horizontal body would otherwise point the face at the ground. The
        // head counter-rotates with the body; the small forward/up shift is the
        // neck extension that keeps the rotated head clear of the chest.
        model.head.xRot = lerp(model.head.xRot, radians(pose.headXRotDegrees), weight);
        float neckExtension = Math.min(1.0F, Math.max(0.0F, -pose.headXRotDegrees / 90.0F));
        model.head.z = lerp(model.head.z, -2.5F * neckExtension, weight);
        model.head.y = lerp(model.head.y, -1.0F * neckExtension, weight);
        model.hat.copyFrom(model.head);
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
