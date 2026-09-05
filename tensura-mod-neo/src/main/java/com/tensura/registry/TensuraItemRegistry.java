package com.tensura.registry;

import com.tensura.TensuraMod;
import com.tensura.item.PredatorCodexItem;
import com.tensura.item.SpellItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

public class TensuraItemRegistry {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, TensuraMod.MOD_ID);

    public static final DeferredHolder<Item, SpellItem> SPELL_ITEM =
            ITEMS.register("spell_item", () -> new SpellItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, PredatorCodexItem> PREDATOR_CODEX =
            ITEMS.register("predator_codex",
                    () -> new PredatorCodexItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, BlockItem> RECALL_STATION =
            ITEMS.register("recall_station", () ->
                    new BlockItem(TensuraBlockRegistry.RECALL_STATION.get(), new Item.Properties()));

    public static final Map<String, DeferredHolder<Item, Item>> SPELL_ICONS = registerSpellIcons();

    private static Map<String, DeferredHolder<Item, Item>> registerSpellIcons() {
        Map<String, DeferredHolder<Item, Item>> icons = new LinkedHashMap<>();
        for (String spellId : SpellItem.CUSTOM_ICON_ORDER) {
            icons.put(spellId, ITEMS.register("spell_icon_" + spellId,
                    () -> new Item(new Item.Properties())));
        }
        return Map.copyOf(icons);
    }
}
