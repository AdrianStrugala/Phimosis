package com.tensura.data;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists absorbed spell IDs in player NBT under "tensura:absorbed".
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
        player.getPersistentData().put(KEY, list);
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

    private static ListTag getList(Player player) {
        return player.getPersistentData().getList(KEY, Tag.TAG_STRING);
    }
}
