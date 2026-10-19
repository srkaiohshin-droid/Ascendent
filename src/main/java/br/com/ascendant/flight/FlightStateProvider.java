package br.com.ascendant.flight;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

public final class FlightStateProvider implements ICapabilityProvider {
    public static final Capability<FlightState> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    private final FlightState state = new PlayerFlightState();
    private final LazyOptional<FlightState> optional = LazyOptional.of(() -> state);

    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return CAPABILITY.orEmpty(capability, optional);
    }

    public void invalidate() {
        optional.invalidate();
        state.clear();
    }
}
