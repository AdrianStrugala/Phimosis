package com.tensura.registry;

import com.tensura.TensuraMod;
import com.tensura.client.ClientSpellCatalog;
import com.tensura.engine.SpellDefinition;
import com.tensura.item.SpellItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The mod's creative tab.
 *
 * Spell stacks are built from {@link ClientSpellCatalog} rather than SpellRegistry: tab
 * contents are assembled on the client, where the server's datapack-loaded registry is
 * empty. The spell_icon_* items stay out of the tab — they only exist to carry models.
 */
public class TensuraCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TensuraMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SPELLS =
            TABS.register("spells", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tensura.spells"))
                    .icon(() -> new ItemStack(TensuraItemRegistry.SPELL_FOCUS.get()))
                    .displayItems((params, output) -> {
                        output.accept(TensuraItemRegistry.SPELL_FOCUS.get());
                        output.accept(TensuraItemRegistry.RECALL_STATION.get());
                        for (Map.Entry<ResourceLocation, SpellDefinition> entry : sortedSpells()) {
                            output.accept(SpellItem.create(entry.getKey(), entry.getValue()));
                        }
                    })
                    .build());

    /** School groups in SpellItem's order, alphabetical within a group, unknown schools last. */
    private static List<Map.Entry<ResourceLocation, SpellDefinition>> sortedSpells() {
        List<Map.Entry<ResourceLocation, SpellDefinition>> spells =
                new ArrayList<>(ClientSpellCatalog.all().entrySet());
        spells.sort(Comparator
                .comparingInt((Map.Entry<ResourceLocation, SpellDefinition> e) ->
                        SpellItem.schoolOrder(e.getValue().school))
                .thenComparing(e -> e.getKey().getPath()));
        return spells;
    }
}
