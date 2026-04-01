package com.tensura.network;

import com.tensura.TensuraMod;
import com.tensura.data.PredatorData;
import com.tensura.item.SpellItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SelectSkillPacket(ResourceLocation spellId, int xpCost) implements CustomPacketPayload {

    public static final Type<SelectSkillPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "select_skill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectSkillPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, SelectSkillPacket::spellId,
                    ByteBufCodecs.VAR_INT, SelectSkillPacket::xpCost,
                    SelectSkillPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectSkillPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();

            if (PredatorData.hasAbsorbed(player, pkt.spellId)) {
                player.sendSystemMessage(Component.literal("§e[Predator] Znasz już tę umiejętność!"));
                return;
            }
            if (player.experienceLevel < pkt.xpCost) {
                player.sendSystemMessage(Component.literal(
                        "§c[Predator] Potrzebujesz " + pkt.xpCost + " lvl XP! (masz " + player.experienceLevel + ")"));
                return;
            }

            player.giveExperienceLevels(-pkt.xpCost);
            PredatorData.markAbsorbed(player, pkt.spellId);

            String spellPath = pkt.spellId.getPath();
            if (spellPath.equals("tackle")) {
                // Root node reward is empty — give item directly
                player.addItem(SpellItem.create(pkt.spellId));
            }
            // For all other spells: puffish_skills unlock fires devour_recover reward → gives item + re-locks
            player.getServer().getCommands().performPrefixedCommand(
                player.getServer().createCommandSourceStack(),
                "puffish_skills skills unlock " + player.getGameProfile().getName() + " devour " + spellPath
            );

            String spellName = spellPath.replace("_", " ");
            player.sendSystemMessage(Component.literal("§a[Predator] Pochłonięto: " + spellName));
        });
    }
}
