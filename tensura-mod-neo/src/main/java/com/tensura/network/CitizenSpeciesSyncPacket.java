package com.tensura.network;

import com.tensura.TensuraMod;
import com.tensura.client.CitizenSpeciesClient;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * S→C: sends the full map of citizenId → pokemon species to the client.
 * Sent on player join. citizenId is MineColonies ICitizenData.getId() — stable across sessions.
 */
public record CitizenSpeciesSyncPacket(Map<Integer, String> idToSpecies) implements CustomPacketPayload {

    public static final Type<CitizenSpeciesSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "citizen_species_sync"));

    private static final StreamCodec<ByteBuf, Map<Integer, String>> MAP_CODEC =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.VAR_INT, ByteBufCodecs.STRING_UTF8);

    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenSpeciesSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    MAP_CODEC, CitizenSpeciesSyncPacket::idToSpecies,
                    CitizenSpeciesSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CitizenSpeciesSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> CitizenSpeciesClient.update(pkt.idToSpecies));
    }

    public static void sendToPlayer(ServerPlayer player, Map<Integer, String> idToSpecies) {
        PacketDistributor.sendToPlayer(player, new CitizenSpeciesSyncPacket(idToSpecies));
    }
}
