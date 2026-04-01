package com.tensura.network;

import com.tensura.TensuraMod;
import com.tensura.client.ClientPacketHandlers;
import com.tensura.spell.SkillEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record OpenSkillSelectPacket(List<SkillEntry> skills, String mobName, ResourceLocation species)
        implements CustomPacketPayload {

    public static final Type<OpenSkillSelectPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "open_skill_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSkillSelectPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, OpenSkillSelectPacket::mobName,
                    ResourceLocation.STREAM_CODEC, OpenSkillSelectPacket::species,
                    SkillEntry.STREAM_CODEC.apply(ByteBufCodecs.collection(ArrayList::new)), OpenSkillSelectPacket::skills,
                    (mobName, species, skills) -> new OpenSkillSelectPacket(skills, mobName, species)
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenSkillSelectPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandlers.openSkillSelect(pkt.skills, pkt.mobName, pkt.species));
    }

    public static void sendToPlayer(ServerPlayer player, List<SkillEntry> skills, String mobName, ResourceLocation species) {
        PacketDistributor.sendToPlayer(player, new OpenSkillSelectPacket(skills, mobName, species));
    }
}
