package com.tensura.spell;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents one learnable skill: the spell ID and its XP level cost.
 */
public record SkillEntry(ResourceLocation spellId, int xpCost) {

    public static final StreamCodec<io.netty.buffer.ByteBuf, SkillEntry> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, SkillEntry::spellId,
                    ByteBufCodecs.VAR_INT, SkillEntry::xpCost,
                    SkillEntry::new
            );

    public static SkillEntry of(String spellId, int xpCost) {
        return new SkillEntry(ResourceLocation.parse(spellId), xpCost);
    }
}
