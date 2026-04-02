package com.tensura.network;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICivilianData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.tensura.TensuraMod;
import com.tensura.data.ConversionHelper;
import com.tensura.data.DynamicCitizenSpeciesData;
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

public record RecallCitizenPacket(int citizenId) implements CustomPacketPayload {

    public static final Type<RecallCitizenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "recall_citizen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecallCitizenPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, RecallCitizenPacket::citizenId,
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

            DynamicCitizenSpeciesData data = DynamicCitizenSpeciesData.get(level);
            int id = pkt.citizenId;
            if (!data.contains(id)) return;

            // Only the citizen's owner may recall via the Recall Station
            UUID ownerUUID = data.ownerMap.get(id);
            if (ownerUUID == null || !ownerUUID.equals(sender.getUUID())) return;

            Integer colonyId = data.colonyIdMap.get(id);
            if (colonyId == null) return;

            // Find the citizen entity in the world to pass to ConversionHelper
            IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, level);
            if (colony == null) return;

            ICivilianData civilianData = colony.getCitizenManager().getCivilian(id);
            if (civilianData == null) return;

            // Resolve the citizen entity (needed for ConversionHelper.resolveSpecies fallback)
            AbstractEntityCitizen citizenEntity = civilianData.getEntity()
                    .map(e -> (AbstractEntityCitizen) e)
                    .orElse(null);
            // citizenEntity may be null if chunk is unloaded; ConversionHelper handles it
            // (for enrolled citizens the entity isn't needed — only the NBT)
            var skills = civilianData.getCitizenSkillHandler();

            Pokemon restoredPokemon = ConversionHelper.buildRecalledPokemon(
                    id, citizenEntity, skills, data, level.registryAccess());
            if (restoredPokemon == null) return;

            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
                try {
                    PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(owner);
                    party.add(restoredPokemon);
                } catch (Exception e) {
                    TensuraMod.LOGGER.warn("[Tensura] Failed to restore Pokemon on recall: {}", e.getMessage());
                }
            }

            if (citizenEntity != null) citizenEntity.discard();
            colony.getCitizenManager().removeCivilian(civilianData);

            String speciesName = capitalize(restoredPokemon.getSpecies().getName());
            data.remove(id);
            ColonyStartupEvents.broadcastSpeciesMap(level);
            TensuraMod.LOGGER.info("[Tensura] Recalled citizen #{} via RecallStation (owner={})", id, ownerUUID);

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
