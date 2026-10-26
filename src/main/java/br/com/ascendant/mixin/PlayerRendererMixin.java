package br.com.ascendant.mixin;

import br.com.ascendant.flight.client.FlightPoseRenderer;
import br.com.ascendant.flight.client.LocomotionPoseController;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rotates the whole player model into the flight attitude.
 *
 * <p>The injection runs at the tail of {@code setupRotations}, after the vanilla
 * body yaw and before the renderer flips the model. It receives that body yaw
 * so the flight renderer can replace it with the real look yaw instead of
 * following the last movement direction. All transforms stay inside the
 * renderer's pose-stack push/pop, so nothing leaks into other entities and no
 * entity orientation fields are modified.</p>
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(
            method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At("TAIL")
    )
    private void ascendant$applyFlightBody(AbstractClientPlayer player, PoseStack poseStack,
                                           float ageInTicks, float rotationYaw, float partialTick,
                                           CallbackInfo callback) {
        if (!FlightPoseRenderer.applyBody(poseStack, player, rotationYaw, partialTick)) {
            LocomotionPoseController.applyBody(poseStack, player, partialTick);
        }
    }
}
