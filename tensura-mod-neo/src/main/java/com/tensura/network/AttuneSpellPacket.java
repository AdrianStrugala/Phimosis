package com.tensura.network;

import com.tensura.TensuraMod;
import com.tensura.data.PredatorData;
import com.tensura.item.SpellCasting;
import com.tensura.item.SpellFocusItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/**
 * Binds a spell to one slot of the catalyst, or clears the slot when {@code spellId} is empty.
 *
 * The absorption check is the security boundary: the client says which spell it wants bound,
 * and the server refuses anything absent from {@link PredatorData}. Without it a modified
 * client would simply attune the entire roster.
 */
public record AttuneSpellPacket(int slot, Optional<ResourceLocation> spellId)
        implements CustomPacketPayload {

    public static final Type<AttuneSpellPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "attune_spell"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AttuneSpellPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AttuneSpellPacket::slot,
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), AttuneSpellPacket::spellId,
                    AttuneSpellPacket::new
            );

    public static AttuneSpellPacket bind(int slot, ResourceLocation spellId) {
        return new AttuneSpellPacket(slot, Optional.of(spellId));
    }

    public static AttuneSpellPacket clear(int slot) {
        return new AttuneSpellPacket(slot, Optional.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AttuneSpellPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            ItemStack focus = SpellCasting.findFocus(player);
            if (focus == null) return;
            if (pkt.slot() < 0 || pkt.slot() >= SpellFocusItem.maxSlotsOf(focus)) return;

            if (pkt.spellId().isEmpty()) {
                SpellFocusItem.setSpell(focus, pkt.slot(), null);
                // Leaving the selection on the slot we just emptied would make right-click do
                // nothing at all, silently — move it to whatever is still attuned.
                if (SpellFocusItem.getActiveIndex(focus) == pkt.slot()) {
                    int fallback = SpellFocusItem.firstAttunedSlot(focus);
                    if (fallback >= 0) SpellFocusItem.setActiveIndex(focus, fallback);
                }
                return;
            }

            ResourceLocation spellId = pkt.spellId().get();
            if (!PredatorData.hasAbsorbed(player, spellId)) {
                player.sendSystemMessage(Component.literal(
                        "§c[Predator] Nie pochłonąłeś §e"
                                + SpellCasting.formatName(spellId.getPath())));
                return;
            }

            SpellFocusItem.setSpell(focus, pkt.slot(), spellId);
            SpellFocusItem.setActiveIndex(focus, pkt.slot());
            player.sendSystemMessage(Component.literal(
                    "§a[Katalizator] Przypisano: §e" + SpellCasting.formatName(spellId.getPath())));
        });
    }
}
