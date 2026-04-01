package com.tensura.network;

import com.tensura.TensuraMod;
import com.tensura.client.ClientCooldownTracker;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CooldownSyncPacket(ResourceLocation spellId, int cooldownTicks) implements CustomPacketPayload {

    public static final Type<CooldownSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "cooldown_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CooldownSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, CooldownSyncPacket::spellId,
                    ByteBufCodecs.VAR_INT, CooldownSyncPacket::cooldownTicks,
                    CooldownSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CooldownSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientCooldownTracker.set(pkt.spellId, pkt.cooldownTicks));
    }

    public static void sendToPlayer(ServerPlayer player, ResourceLocation spellId, int cooldownTicks) {
        PacketDistributor.sendToPlayer(player, new CooldownSyncPacket(spellId, cooldownTicks));
    }
}
