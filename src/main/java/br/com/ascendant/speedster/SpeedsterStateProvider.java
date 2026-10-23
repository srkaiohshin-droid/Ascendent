package br.com.ascendant.speedster;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

public final class SpeedsterStateProvider implements ICapabilityProvider {
    public static final Capability<SpeedsterState> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    private final SpeedsterState state = new PlayerSpeedsterState();
    private final LazyOptional<SpeedsterState> optional = LazyOptional.of(() -> state);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return CAPABILITY.orEmpty(capability, optional);
    }

    public void invalidate() {
        optional.invalidate();
        state.clear();
    }
}
