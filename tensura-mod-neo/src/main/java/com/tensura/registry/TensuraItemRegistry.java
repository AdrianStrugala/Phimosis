package com.tensura.registry;

import com.tensura.TensuraMod;
import com.tensura.item.PredatorCodexItem;
import com.tensura.item.SpellItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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

    public static final DeferredHolder<Item, Item> SPELL_ICON_CLOSE_COMBAT =
            ITEMS.register("spell_icon_close_combat", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPELL_ICON_FLAMETHROWER =
            ITEMS.register("spell_icon_flamethrower", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPELL_ICON_VOLT_TACKLE =
            ITEMS.register("spell_icon_volt_tackle", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPELL_ICON_SURF =
            ITEMS.register("spell_icon_surf", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPELL_ICON_PSYBEAM =
            ITEMS.register("spell_icon_psybeam", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPELL_ICON_DARK_PULSE =
            ITEMS.register("spell_icon_dark_pulse", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPELL_ICON_ROCK_SLIDE =
            ITEMS.register("spell_icon_rock_slide", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPELL_ICON_TOXIC_SPIKES =
            ITEMS.register("spell_icon_toxic_spikes", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPELL_ICON_SHADOW_SNEAK =
            ITEMS.register("spell_icon_shadow_sneak", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPELL_ICON_FIRE_SPIN =
            ITEMS.register("spell_icon_fire_spin", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPELL_ICON_RECOVER =
            ITEMS.register("spell_icon_recover", () -> new Item(new Item.Properties()));
}
