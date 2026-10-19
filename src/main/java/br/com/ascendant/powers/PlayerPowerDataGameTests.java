package br.com.ascendant.powers;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class PlayerPowerDataGameTests {
    private PlayerPowerDataGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void unlockedPowersRoundTripThroughNbt(GameTestHelper helper) {
        PlayerPowerDataProvider original = new PlayerPowerDataProvider();
        ResourceLocation flight = new ResourceLocation("ascendant", "flight");
        IPlayerPowerData originalData = original.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Power data capability is unavailable"));

        helper.assertTrue(originalData.unlockPower(flight), "First unlock should change player state");
        helper.assertTrue(!originalData.unlockPower(flight), "Duplicate unlock should be idempotent");

        PlayerPowerDataProvider restored = new PlayerPowerDataProvider();
        restored.deserializeNBT(original.serializeNBT());
        IPlayerPowerData restoredData = restored.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Restored power data capability is unavailable"));
        helper.assertTrue(restoredData.hasPower(flight), "Unlocked power should survive NBT round trip");

        Player player = helper.makeMockPlayer();
        IPlayerPowerData attachedData = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Player should receive the power data capability"));
        helper.assertTrue(attachedData.getUnlockedPowers().isEmpty(), "New players should start with no powers");
        attachedData.copyFrom(restoredData);
        helper.assertTrue(attachedData.hasPower(flight), "Player state copy should preserve unlocked powers");
        helper.succeed();
    }
}
