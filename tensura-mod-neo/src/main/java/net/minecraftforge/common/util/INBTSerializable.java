package net.minecraftforge.common.util;

import net.minecraft.nbt.Tag;

/**
 * Stub for Forge compatibility — MineColonies built against Forge uses this interface.
 */
public interface INBTSerializable<T extends Tag> {
    T serializeNBT();
    void deserializeNBT(T nbt);
}
