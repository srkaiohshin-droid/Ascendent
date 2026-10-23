package br.com.ascendant.flight.client;

import br.com.ascendant.flight.FlightPoseLibrary;
import br.com.ascendant.speedster.SpeedsterController;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Lightweight run, super-speed and idle presentation layered on top of the
 * vanilla humanoid animation. Flight always takes priority: the mixins only call
 * this controller when {@link FlightPoseRenderer} did not apply a flight pose.
 *
 * <p>All values are additive to the pose that vanilla produced, so ordinary
 * walking, swimming, riding and item use keep their normal silhouettes.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class LocomotionPoseController {
    private static final float BODY_PIVOT_Y = FlightPoseLibrary.BODY_PIVOT_Y;
    private static final float IDLE_BREATH_SPEED = 0.075F;

    private LocomotionPoseController() {
    }

    /** Adds a forward lean for sprints and super-speed. Returns true when applied. */
    public static boolean applyBody(PoseStack poseStack, Player player, float partialTick) {
        float lean = bodyLean(player, partialTick);
        if (lean < 0.05F) {
            return false;
        }
        poseStack.translate(0.0F, BODY_PIVOT_Y, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-lean));
        poseStack.translate(0.0F, -BODY_PIVOT_Y, 0.0F);
        return true;
    }

    /** Adds a stride accent while moving and a subtle breathing sway while idle. */
    public static void applyModel(HumanoidModel<?> model, Player player, float partialTick) {
        if (player.isPassenger() || player.isSleeping() || player.isSwimming()
                || player.isFallFlying() || player.isSpectator()) {
            return;
        }

        boolean speedster = SpeedsterController.isModeActive(player);
        float walkSpeed = player.walkAnimation.speed(partialTick);
        float movement = Mth.clamp(walkSpeed, 0.0F, 1.0F);
        if (speedster) {
            movement = Math.max(movement, moving(player) ? 0.85F : 0.15F);
        }

        if (movement < 0.03F) {
            applyIdle(model, player, partialTick);
            return;
        }

        float phase = player.walkAnimation.position(partialTick);
        float amplitude = (speedster ? 0.95F : 0.35F) * movement;
        float swing = Mth.sin(phase) * amplitude;

        // Additive to vanilla's own stride. The legs get a slightly larger
        // counter-swing so a super-speed run reads as a longer stride rather
        // than a faster shuffle.
        model.rightArm.xRot += swing;
        model.leftArm.xRot -= swing;
        model.rightLeg.xRot -= swing * 1.25F;
        model.leftLeg.xRot += swing * 1.25F;

        if (speedster) {
            model.rightArm.zRot += 0.08F;
            model.leftArm.zRot -= 0.08F;
        }
    }

    private static void applyIdle(HumanoidModel<?> model, Player player, float partialTick) {
        float breath = Mth.sin((player.tickCount + partialTick) * IDLE_BREATH_SPEED) * 0.025F;
        model.rightArm.zRot += breath;
        model.leftArm.zRot -= breath;
        model.head.xRot += breath * 0.6F;
        model.rightLeg.zRot += breath * 0.35F;
        model.leftLeg.zRot -= breath * 0.35F;
    }

    private static float bodyLean(Player player, float partialTick) {
        if (player.isPassenger() || player.isSleeping() || player.isSwimming()
                || player.isFallFlying() || player.isSpectator()) {
            return 0.0F;
        }
        float walkSpeed = player.walkAnimation.speed(partialTick);
        float movement = Mth.clamp(walkSpeed, 0.0F, 1.0F);
        if (SpeedsterController.isModeActive(player)) {
            return 14.0F * Math.max(movement, moving(player) ? 0.8F : 0.0F);
        }
        if (!player.isSprinting()) {
            return 0.0F;
        }
        return 6.0F * movement;
    }

    private static boolean moving(Player player) {
        double horizontalSqr = player.getDeltaMovement().x * player.getDeltaMovement().x
                + player.getDeltaMovement().z * player.getDeltaMovement().z;
        return horizontalSqr > 0.0025D || player.walkAnimation.speed() > 0.02F;
    }
}
