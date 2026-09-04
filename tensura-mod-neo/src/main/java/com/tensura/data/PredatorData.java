package com.tensura.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists absorbed spell IDs in player NBT under "tensura:absorbed".
 *
 * The list lives inside the {@link Player#PERSISTED_NBT_TAG} sub-compound, because that is the
 * only part of the persistent data NeoForge copies onto the new player entity on respawn — a list
 * kept at the root of getPersistentData() is silently wiped by every death.
 */
public class PredatorData {

    private static final String KEY = "tensura:absorbed";

    public static boolean hasAbsorbed(Player player, ResourceLocation spellId) {
        ListTag list = getList(player);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(spellId.toString())) return true;
        }
        return false;
    }

    public static void markAbsorbed(Player player, ResourceLocation spellId) {
        ListTag list = getList(player);
        list.add(StringTag.valueOf(spellId.toString()));
        persisted(player).put(KEY, list);
    }

    public static List<ResourceLocation> getAbsorbed(Player player) {
        ListTag list = getList(player);
        List<ResourceLocation> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) result.add(id);
        }
        return result;
    }

    /** The death-proof sub-compound of the player's persistent data, created on demand. */
    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static ListTag getList(Player player) {
        CompoundTag persisted = persisted(player);
        if (persisted.contains(KEY, Tag.TAG_LIST)) {
            return persisted.getList(KEY, Tag.TAG_STRING);
        }
        // Legacy layout: the list used to sit at the root of the persistent data. Move it over so
        // players who absorbed spells before this fix keep them.
        CompoundTag root = player.getPersistentData();
        ListTag legacy = root.getList(KEY, Tag.TAG_STRING);
        if (!legacy.isEmpty()) {
            persisted.put(KEY, legacy.copy());
            root.remove(KEY);
            return persisted.getList(KEY, Tag.TAG_STRING);
        }
        return new ListTag();
    }
}
