package com.tensura.network;

import com.tensura.TensuraMod;
import com.tensura.client.ClientPacketHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Opens the catalyst radial with one spell held on the cursor, ready to drop into a slot.
 *
 * Sent from {@code tensura devour_recover}, which is the reward the Devour tree's dispenser
 * node fires — so clicking a spell in the tree lands the player straight in the radial. The
 * keybind opens the same screen client-side without a packet.
 */
public record OpenRadialPacket(ResourceLocation spellId) implements CustomPacketPayload {

    public static final Type<OpenRadialPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "open_radial"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRadialPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, OpenRadialPacket::spellId,
                    OpenRadialPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenRadialPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandlers.openRadialForAssign(pkt.spellId()));
    }
}
