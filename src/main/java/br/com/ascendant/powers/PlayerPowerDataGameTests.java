package br.com.ascendant.powers;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

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
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void unlockedPowersSurviveServerRespawnAndEndReturn(GameTestHelper helper) {
        ResourceLocation flight = new ResourceLocation("ascendant", "flight");
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer originalPlayer = new ServerPlayer(server, level,
                new GameProfile(UUID.randomUUID(), "asc_respawn")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel mockClientChannel = new EmbeddedChannel();
        Connection.configureSerialization(mockClientChannel.pipeline(), PacketFlow.SERVERBOUND);
        mockClientChannel.pipeline().addLast("packet_handler", connection);
        mockClientChannel.pipeline().fireChannelActive();
        NetworkHooks.registerClientLoginChannel(connection);
        PlayerList playerList = server.getPlayerList();
        playerList.placeNewPlayer(connection, originalPlayer);
        IPlayerPowerData originalData = originalPlayer.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Original server player should have power data"));
        helper.assertTrue(originalData.unlockPower(flight), "Test setup should unlock flight");

        originalPlayer.setHealth(0.0F);
        ServerPlayer deathRespawn = playerList.respawn(originalPlayer, false);
        // PlayerList's false flag produces the death clone branch.
        helper.assertTrue(deathRespawn != originalPlayer, "Death respawn should create a replacement player");
        helper.assertTrue(deathRespawn.getUUID().equals(originalPlayer.getUUID()),
                "Respawn should preserve the player's identity");
        helper.assertTrue(!originalPlayer.getCapability(PlayerPowerDataProvider.CAPABILITY).isPresent(),
                "Original player's capability should be invalidated after cloning");
        IPlayerPowerData deathRespawnData = deathRespawn.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Death-respawned player should have power data"));
        helper.assertTrue(deathRespawnData.hasPower(flight), "Death respawn should preserve unlocked powers");

        ServerPlayer endReturn = playerList.respawn(deathRespawn, true);
        // The true flag produces Forge's non-death clone branch used for End return.
        helper.assertTrue(!deathRespawn.getCapability(PlayerPowerDataProvider.CAPABILITY).isPresent(),
                "Replaced player's capability should be invalidated after cloning");
        IPlayerPowerData endReturnData = endReturn.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("End-return player should have power data"));
        helper.assertTrue(endReturnData.hasPower(flight), "End return should retain the existing unlocked power");
        helper.assertTrue(endReturnData.getUnlockedPowers().size() == 1,
                "End return must not duplicate unlocked powers");
        mockClientChannel.finishAndReleaseAll();
        helper.succeed();
    }
}
