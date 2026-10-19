package br.com.ascendant.powers;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class PlayerPowerDataGameTests {
    private PlayerPowerDataGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void unlockedPowersRoundTripThroughNbt(GameTestHelper helper) {
        ResourceLocation flight = new ResourceLocation("ascendant", "flight");
        Player originalPlayer = helper.makeMockPlayer();
        IPlayerPowerData originalData = originalPlayer.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Player power data capability is unavailable"));
        helper.assertTrue(originalData.unlockPower(flight), "First unlock should change player state");
        helper.assertTrue(!originalData.unlockPower(flight), "Duplicate unlock should be idempotent");

        PlayerPowerDataProvider saved = new PlayerPowerDataProvider();
        saved.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Power data capability is unavailable"))
                .copyFrom(originalData);
        PlayerPowerDataProvider restored = new PlayerPowerDataProvider();
        restored.deserializeNBT(saved.serializeNBT());
        IPlayerPowerData restoredData = restored.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Restored power data capability is unavailable"));
        helper.assertTrue(restoredData.hasPower(flight), "Unlocked power should survive NBT round trip");

        Player respawnedPlayer = helper.makeMockPlayer();
        IPlayerPowerData respawnedData = respawnedPlayer.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Respawned player should receive the power capability"));
        helper.assertTrue(respawnedData.getUnlockedPowers().isEmpty(), "New players should start with no powers");
        PlayerPowerDataEvents.copyPlayerDataOnDeath(new PlayerEvent.Clone(respawnedPlayer, originalPlayer, true));
        helper.assertTrue(respawnedData.hasPower(flight), "Death clone should preserve unlocked powers");

        Player endReturnPlayer = helper.makeMockPlayer();
        IPlayerPowerData endReturnData = endReturnPlayer.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("End-return player should receive the power capability"));
        PlayerPowerDataEvents.copyPlayerDataOnDeath(new PlayerEvent.Clone(endReturnPlayer, respawnedPlayer, false));
        helper.assertTrue(endReturnData.getUnlockedPowers().isEmpty(), "Returning from the End should not duplicate data");
        helper.succeed();
    }
}
