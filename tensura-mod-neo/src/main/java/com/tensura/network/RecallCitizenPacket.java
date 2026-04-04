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

            ICivilianData civilianData = colony.getCitizenManager().getCivilian(pkt.citizenId());
            if (civilianData == null) return;

            DynamicCitizenSpeciesData data = DynamicCitizenSpeciesData.get(level);

            // Enrolled citizens: only the registered owner may recall
            if (data.contains(pkt.citizenId())) {
                UUID ownerUUID = data.ownerMap.get(pkt.citizenId());
                if (ownerUUID == null || !ownerUUID.equals(sender.getUUID())) return;
            }

            AbstractEntityCitizen citizenEntity = civilianData.getEntity()
                    .filter(e -> e instanceof AbstractEntityCitizen)
                    .map(e -> (AbstractEntityCitizen) e)
                    .orElse(null);

            var skills = ((ICitizenData) civilianData).getCitizenSkillHandler();

            Pokemon restoredPokemon = ConversionHelper.buildRecalledPokemon(
                    pkt.citizenId(), citizenEntity, skills, data, level.registryAccess());
            if (restoredPokemon == null) return;

            // Owner is sender for non-enrolled; stored UUID for enrolled
            UUID ownerUUID = data.contains(pkt.citizenId())
                    ? data.ownerMap.get(pkt.citizenId())
                    : sender.getUUID();

            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
                try {
                    PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(owner);
                    if (!party.add(restoredPokemon)) {
                        owner.sendSystemMessage(Component.literal("§cParty jest pełne! Zwolnij miejsce przed recall."));
                        return;
                    }
                } catch (Exception e) {
                    TensuraMod.LOGGER.warn("[Tensura] Failed to restore Pokemon on recall: {}", e.getMessage());
                    sender.sendSystemMessage(Component.literal("§cBłąd podczas recall — spróbuj ponownie."));
                    return;
                }
            }

            if (citizenEntity != null) citizenEntity.discard();
            colony.getCitizenManager().removeCivilian(civilianData);

            String speciesName = capitalize(restoredPokemon.getSpecies().getName());
            data.remove(pkt.citizenId());
            ColonyStartupEvents.broadcastSpeciesMap(level);
            TensuraMod.LOGGER.info("[Tensura] Recalled citizen #{} via RecallStation (owner={})", pkt.citizenId(), ownerUUID);

            if (owner != null) {
                owner.sendSystemMessage(Component.literal("\u00a7b" + speciesName + " powrócił do drużyny."));
            }
        });
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
