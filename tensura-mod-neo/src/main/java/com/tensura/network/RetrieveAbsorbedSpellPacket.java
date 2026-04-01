package com.tensura.network;

import com.tensura.TensuraMod;
import com.tensura.data.PredatorData;
import com.tensura.item.SpellItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RetrieveAbsorbedSpellPacket(ResourceLocation spellId) implements CustomPacketPayload {

    public static final Type<RetrieveAbsorbedSpellPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "retrieve_absorbed_spell"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RetrieveAbsorbedSpellPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, RetrieveAbsorbedSpellPacket::spellId,
                    RetrieveAbsorbedSpellPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RetrieveAbsorbedSpellPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (!PredatorData.hasAbsorbed(player, pkt.spellId())) {
                player.sendSystemMessage(Component.literal("§c[Predator] Nie pochłonąłeś tego czaru!"));
                return;
            }
            ItemStack spell = SpellItem.create(pkt.spellId());
            player.addItem(spell);
            String name = pkt.spellId().getPath().replace("_", " ");
            player.sendSystemMessage(Component.literal("§a[Predator] Odzyskano: §e" + name));
        });
    }
}
