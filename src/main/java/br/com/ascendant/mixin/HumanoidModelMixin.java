package br.com.ascendant.mixin;

import br.com.ascendant.flight.client.FlightPoseRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the flight joint pose after vanilla has posed every humanoid.
 *
 * <p>Player armor layers copy the parent model's part rotations, so posing the
 * player model here also moves worn armor and held items. Non-player humanoids
 * are ignored.</p>
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void ascendant$applyFlightPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                           float ageInTicks, float netHeadYaw, float headPitch,
                                           CallbackInfo callback) {
        if (entity instanceof Player player) {
            float partialTick = ageInTicks - player.tickCount;
            FlightPoseRenderer.applyModel((HumanoidModel<?>) (Object) this, player, partialTick);
        }
    }
}
