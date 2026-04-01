package com.tensura.network;

import com.tensura.TensuraMod;
import com.tensura.client.ClientPacketHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record OpenCodexPacket(List<ResourceLocation> absorbed) implements CustomPacketPayload {

    public static final Type<OpenCodexPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "open_codex"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCodexPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.collection(ArrayList::new)),
                    OpenCodexPacket::absorbed,
                    OpenCodexPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenCodexPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandlers.openCodex(pkt.absorbed()));
    }
}
