package com.tensura.network;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICivilianData;
import com.tensura.TensuraMod;
import com.tensura.data.ConversionHelper;
import com.tensura.data.DynamicCitizenSpeciesData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.tensura.event.ColonyStartupEvents;
import com.tensura.gui.WorkforceService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record RecallCitizenPacket(int citizenId, int colonyId) implements CustomPacketPayload {

    public static final Type<RecallCitizenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "recall_citizen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecallCitizenPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, RecallCitizenPacket::citizenId,
                    ByteBufCodecs.INT, RecallCitizenPacket::colonyId,
                    RecallCitizenPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RecallCitizenPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.player();
            ServerLevel level = sender.serverLevel();

            IColony colony = IColonyManager.getInstance().getColonyByWorld(pkt.colonyId(), level);
            if (colony == null) return;
            if (!WorkforceService.canAccess(sender, colony)) return;

            ICivilianData civilianData = colony.getCitizenManager().getCivilian(pkt.citizenId());
            if (civilianData == null) return;

            DynamicCitizenSpeciesData data = DynamicCitizenSpeciesData.get(level);
            if (!data.contains(pkt.citizenId())) {
                sender.sendSystemMessage(Component.literal("§cTen citizen nie jest zapisanym Pokémonem gracza."));
                return;
            }
            if (!ConversionHelper.canRecallPokemon(colony, sender, data, pkt.citizenId())) {
                sender.sendSystemMessage(Component.literal("§cTylko pierwotny właściciel może przywrócić tego Pokémona."));
                return;
            }
            UUID recipientId = sender.getUUID();

            AbstractEntityCitizen citizenEntity = civilianData.getEntity()
                    .filter(e -> e instanceof AbstractEntityCitizen)
                    .map(e -> (AbstractEntityCitizen) e)
                    .orElse(null);

            var skills = ((ICitizenData) civilianData).getCitizenSkillHandler();

            Pokemon restoredPokemon = ConversionHelper.buildRecalledPokemon(
                    pkt.citizenId(), citizenEntity, skills, data, level.registryAccess());
            if (restoredPokemon == null) {
                sender.sendSystemMessage(Component.literal("§cBrak poprawnych danych Pokémona dla tego citizena."));
                return;
            }

            try {
                PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(recipientId, level.registryAccess());
                if (!party.add(restoredPokemon)) {
                    sender.sendSystemMessage(Component.literal("§cNie ma miejsca na przywróconego Pokémona."));
                    return;
                }
            } catch (Exception e) {
                TensuraMod.LOGGER.warn("[Tensura] Failed to restore Pokemon on recall: {}", e.getMessage());
                sender.sendSystemMessage(Component.literal("§cBłąd podczas recall — spróbuj ponownie."));
                return;
            }

            if (citizenEntity != null) citizenEntity.discard();
            colony.getCitizenManager().removeCivilian(civilianData);

            String speciesName = capitalize(restoredPokemon.getSpecies().getName());
            data.remove(pkt.citizenId());
            ColonyStartupEvents.broadcastSpeciesMap(level);
                TensuraMod.LOGGER.info("[Tensura] Recalled citizen #{} via Town Hall by colony manager {} for Pokemon owner {}",
                    pkt.citizenId(), sender.getUUID(), recipientId);
            ServerPlayer recipient = level.getServer().getPlayerList().getPlayer(recipientId);
            if (recipient != null) {
                recipient.sendSystemMessage(Component.literal("\u00a7b" + speciesName + " powrócił do drużyny."));
            }
            if (!recipientId.equals(sender.getUUID())) {
                sender.sendSystemMessage(Component.literal("§aPokémon wrócił do drużyny pierwotnego właściciela."));
            }
            WorkforceService.sendSnapshot(sender, colony);
        });
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
