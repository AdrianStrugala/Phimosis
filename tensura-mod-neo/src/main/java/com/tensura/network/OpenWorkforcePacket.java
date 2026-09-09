package com.tensura.network;

import com.tensura.TensuraMod;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.tensura.gui.WorkforceService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenWorkforcePacket(int colonyId) implements CustomPacketPayload {

    public static final Type<OpenWorkforcePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "open_workforce"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWorkforcePacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenWorkforcePacket::colonyId, OpenWorkforcePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenWorkforcePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                IColony colony = IColonyManager.getInstance()
                        .getColonyByWorld(packet.colonyId(), player.serverLevel());
                if (colony != null) WorkforceService.sendSnapshot(player, colony);
            }
        });
    }
}