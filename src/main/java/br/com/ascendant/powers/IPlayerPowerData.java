package br.com.ascendant.powers;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

import java.util.Set;

/** Persistent power ownership for one player. Gameplay callers must mutate it on the server. */
@AutoRegisterCapability
public interface IPlayerPowerData {
    boolean hasPower(ResourceLocation powerId);

    /** @return true when this call added a previously unowned power. */
    boolean unlockPower(ResourceLocation powerId);

    /** @return true when this call removed an owned power. */
    boolean revokePower(ResourceLocation powerId);

    Set<ResourceLocation> getUnlockedPowers();

    void copyFrom(IPlayerPowerData other);
}
