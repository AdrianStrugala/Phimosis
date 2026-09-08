package com.tensura.network;

import com.tensura.TensuraMod;
import com.tensura.item.SpellCasting;
import com.tensura.item.SpellFocusItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Player picked a different slot in the radial. */
public record SetActiveSpellPacket(int slot) implements CustomPacketPayload {

    public static final Type<SetActiveSpellPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "set_active_spell"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetActiveSpellPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetActiveSpellPacket::slot,
                    SetActiveSpellPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetActiveSpellPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // The client never decides which stack this applies to — the server resolves the
            // catalyst itself and range-checks the slot against that stack's own capacity.
            ItemStack focus = SpellCasting.findFocus(player);
            if (focus == null) return;
            if (pkt.slot() < 0 || pkt.slot() >= SpellFocusItem.maxSlotsOf(focus)) return;

            SpellFocusItem.setActiveIndex(focus, pkt.slot());
        });
    }
}
