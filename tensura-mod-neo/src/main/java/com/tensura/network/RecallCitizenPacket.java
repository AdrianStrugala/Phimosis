package com.tensura.network;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICivilianData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.tensura.TensuraMod;
import com.tensura.data.DynamicCitizenSpeciesData;
import com.tensura.event.ColonyStartupEvents;
import net.minecraft.nbt.CompoundTag;
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

            UUID ownerUUID = data.ownerMap.get(id);
            CompoundTag pokemonNbt = data.pokemonNbt.get(id);
            Integer colonyId = data.colonyIdMap.get(id);
            if (pokemonNbt == null || ownerUUID == null || colonyId == null) return;

            Pokemon restoredPokemon = new Pokemon();
            restoredPokemon.loadFromNBT(level.registryAccess(), pokemonNbt);

            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
                try {
                    PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(owner);
                    party.add(restoredPokemon);
                } catch (Exception e) {
                    TensuraMod.LOGGER.warn("[Tensura] Failed to restore Pokemon on recall: {}", e.getMessage());
                }
            }

            IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, level);
            if (colony != null) {
                ICivilianData civilianData = colony.getCitizenManager().getCivilian(id);
                if (civilianData != null) {
                    civilianData.getEntity().ifPresent(e -> ((AbstractEntityCitizen) e).discard());
                    colony.getCitizenManager().removeCivilian(civilianData);
                }
            }

            String speciesName = capitalize(data.dynamicSpecies.getOrDefault(id, "Pokemon"));
            data.remove(id);
            ColonyStartupEvents.broadcastSpeciesMap(level);
            TensuraMod.LOGGER.info("[Tensura] Recalled citizen #{} via RecallStation (owner={})", id, ownerUUID);

            ServerPlayer owner2 = level.getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner2 != null) {
                owner2.sendSystemMessage(Component.literal("\u00a7b" + speciesName + " powrócił do drużyny."));
            }
        });
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
