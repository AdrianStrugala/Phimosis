package com.tensura.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tensura.TensuraMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@EventBusSubscriber(modid = TensuraMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class SpellLoader extends SimplePreparableReloadListener<Map<ResourceLocation, SpellDefinition>> {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String FOLDER = "spells";

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SpellLoader());
    }

    @Override
    protected Map<ResourceLocation, SpellDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, SpellDefinition> loaded = new java.util.HashMap<>();
        manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((location, resource) -> {
            try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                SpellDefinition def = GSON.fromJson(reader, SpellDefinition.class);
                // Convert path "tensura/spells/thunderbolt.json" → id "tensura:thunderbolt"
                String path = location.getPath(); // e.g. "tensura/spells/thunderbolt.json"
                String name = path.substring(FOLDER.length() + 1, path.length() - 5); // strip folder/ and .json
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, name);
                loaded.put(id, def);
                TensuraMod.LOGGER.debug("Loaded spell: {}", id);
            } catch (IOException e) {
                TensuraMod.LOGGER.error("Failed to load spell {}: {}", location, e.getMessage());
            }
        });
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, SpellDefinition> prepared, ResourceManager manager, ProfilerFiller profiler) {
        SpellRegistry.clear();
        prepared.forEach(SpellRegistry::register);
        TensuraMod.LOGGER.info("Loaded {} tensura spells", prepared.size());
    }
}
