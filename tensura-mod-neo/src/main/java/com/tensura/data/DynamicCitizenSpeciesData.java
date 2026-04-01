package com.tensura.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.level.ServerLevel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent server-side storage for dynamically enrolled Pokemon-citizens.
 * Stored in the overworld's SavedData under key "tensura_citizens".
 */
public class DynamicCitizenSpeciesData extends SavedData {

    private static final String KEY = "tensura_citizens";

    // citizenId → species name (e.g. "lucario")
    public final Map<Integer, String>      dynamicSpecies = new HashMap<>();
    // citizenId → full Pokemon NBT saved at enrollment time
    public final Map<Integer, CompoundTag> pokemonNbt     = new HashMap<>();
    // citizenId → ownerUUID
    public final Map<Integer, UUID>        ownerMap       = new HashMap<>();
    // citizenId → colonyId (so recall can look up the right colony)
    public final Map<Integer, Integer>     colonyIdMap    = new HashMap<>();

    // ── Lifecycle ────────────────────────────────────────────────────────────

    private static final SavedData.Factory<DynamicCitizenSpeciesData> FACTORY =
            new SavedData.Factory<>(DynamicCitizenSpeciesData::new, DynamicCitizenSpeciesData::load);

    public static DynamicCitizenSpeciesData get(ServerLevel level) {
        return level.getServer().overworld()
                .getDataStorage()
                .computeIfAbsent(FACTORY, KEY);
    }

    // ── Mutation helpers ─────────────────────────────────────────────────────

    public void add(int citizenId, String species, CompoundTag nbt, UUID ownerUUID, int colonyId) {
        dynamicSpecies.put(citizenId, species);
        pokemonNbt.put(citizenId, nbt);
        ownerMap.put(citizenId, ownerUUID);
        colonyIdMap.put(citizenId, colonyId);
        setDirty();
    }

    public void remove(int citizenId) {
        dynamicSpecies.remove(citizenId);
        pokemonNbt.remove(citizenId);
        ownerMap.remove(citizenId);
        colonyIdMap.remove(citizenId);
        setDirty();
    }

    public boolean contains(int citizenId) {
        return dynamicSpecies.containsKey(citizenId);
    }

    /** Returns an unmodifiable merged view (hardcoded map takes priority over dynamic). */
    public Map<Integer, String> mergedWith(Map<Integer, String> hardcoded) {
        Map<Integer, String> merged = new HashMap<>(hardcoded);
        merged.putAll(dynamicSpecies);
        return Collections.unmodifiableMap(merged);
    }

    // ── NBT serialization ────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Integer, String> entry : dynamicSpecies.entrySet()) {
            int cid = entry.getKey();
            CompoundTag record = new CompoundTag();
            record.putInt("citizenId", cid);
            record.putString("species", entry.getValue());
            UUID owner = ownerMap.get(cid);
            if (owner != null) record.putUUID("owner", owner);
            CompoundTag pnbt = pokemonNbt.get(cid);
            if (pnbt != null) record.put("pokemonNbt", pnbt);
            Integer colonyId = colonyIdMap.get(cid);
            if (colonyId != null) record.putInt("colonyId", colonyId);
            list.add(record);
        }
        tag.put("citizens", list);
        return tag;
    }

    public static DynamicCitizenSpeciesData load(CompoundTag tag, HolderLookup.Provider registries) {
        DynamicCitizenSpeciesData data = new DynamicCitizenSpeciesData();
        ListTag list = tag.getList("citizens", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag record = list.getCompound(i);
            int cid = record.getInt("citizenId");
            data.dynamicSpecies.put(cid, record.getString("species"));
            if (record.contains("owner")) data.ownerMap.put(cid, record.getUUID("owner"));
            if (record.contains("pokemonNbt")) data.pokemonNbt.put(cid, record.getCompound("pokemonNbt"));
            if (record.contains("colonyId")) data.colonyIdMap.put(cid, record.getInt("colonyId"));
        }
        return data;
    }
}
