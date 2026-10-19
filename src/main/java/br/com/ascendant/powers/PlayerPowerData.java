package br.com.ascendant.powers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class PlayerPowerData implements IPlayerPowerData {
    private static final String UNLOCKED_POWERS_TAG = "UnlockedPowers";
    private final Set<ResourceLocation> unlockedPowers = new HashSet<>();

    @Override
    public boolean hasPower(ResourceLocation powerId) {
        return unlockedPowers.contains(Objects.requireNonNull(powerId, "powerId"));
    }

    @Override
    public boolean unlockPower(ResourceLocation powerId) {
        return unlockedPowers.add(Objects.requireNonNull(powerId, "powerId"));
    }

    @Override
    public boolean revokePower(ResourceLocation powerId) {
        return unlockedPowers.remove(Objects.requireNonNull(powerId, "powerId"));
    }

    @Override
    public Set<ResourceLocation> getUnlockedPowers() {
        return Collections.unmodifiableSet(unlockedPowers);
    }

    @Override
    public void copyFrom(IPlayerPowerData other) {
        Objects.requireNonNull(other, "other");
        if (other == this) {
            return;
        }
        Set<ResourceLocation> copy = new HashSet<>(other.getUnlockedPowers());
        unlockedPowers.clear();
        unlockedPowers.addAll(copy);
    }

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag powers = new ListTag();
        unlockedPowers.stream().sorted().map(ResourceLocation::toString)
                .map(StringTag::valueOf).forEach(powers::add);
        tag.put(UNLOCKED_POWERS_TAG, powers);
        return tag;
    }

    void deserializeNBT(CompoundTag tag) {
        unlockedPowers.clear();
        ListTag powers = tag.getList(UNLOCKED_POWERS_TAG, CompoundTag.TAG_STRING);
        for (int index = 0; index < powers.size(); index++) {
            ResourceLocation powerId = ResourceLocation.tryParse(powers.getString(index));
            if (powerId != null) {
                unlockedPowers.add(powerId);
            }
        }
    }
}
