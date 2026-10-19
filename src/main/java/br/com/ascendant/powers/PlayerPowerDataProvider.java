package br.com.ascendant.powers;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public final class PlayerPowerDataProvider implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<IPlayerPowerData> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    private final PlayerPowerData data = new PlayerPowerData();
    private LazyOptional<IPlayerPowerData> optional = LazyOptional.of(() -> data);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        // A removed Player's caps are temporarily revived during PlayerEvent.Clone.
        // The old LazyOptional was invalidated by the attach listener, so create a
        // fresh view over the same data for that short copy window.
        if (capability == CAPABILITY && !optional.isPresent()) {
            optional = LazyOptional.of(() -> data);
        }
        return CAPABILITY.orEmpty(capability, optional);
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        data.deserializeNBT(tag);
    }

    public void invalidate() {
        optional.invalidate();
    }
}
