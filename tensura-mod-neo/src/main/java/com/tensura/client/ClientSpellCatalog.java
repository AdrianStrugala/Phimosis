package com.tensura.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tensura.TensuraMod;
import com.tensura.engine.SpellDefinition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Spell definitions read straight out of the mod jar.
 *
 * SpellRegistry is filled by SpellLoader from the server's datapacks, so on a client
 * connected to a dedicated server it is empty — the creative tab would have nothing to
 * list and item tooltips nothing to describe. The same JSONs also ship inside the jar,
 * and NeoForge hands out a Path into them (a zip filesystem in production, a plain
 * directory in dev), so they are read once here and cached.
 *
 * This is only ever a fallback. Anything SpellRegistry knows wins, because a datapack
 * may override a spell that the jar still describes the old way.
 */
public final class ClientSpellCatalog {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String SPELL_DIR = "data/" + TensuraMod.MOD_ID + "/spells";
    private static final String JSON_SUFFIX = ".json";

    private ClientSpellCatalog() {}

    /** Loaded on first access; the holder makes that lazy and thread-safe. */
    private static final class Holder {
        static final Map<ResourceLocation, SpellDefinition> CATALOG = read();
    }

    public static Map<ResourceLocation, SpellDefinition> all() {
        return Holder.CATALOG;
    }

    public static Optional<SpellDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(Holder.CATALOG.get(id));
    }

    private static Map<ResourceLocation, SpellDefinition> read() {
        IModFileInfo info = ModList.get().getModFileById(TensuraMod.MOD_ID);
        if (info == null) {
            TensuraMod.LOGGER.error("Cannot read spells from the jar: mod file '{}' not found",
                    TensuraMod.MOD_ID);
            return Map.of();
        }

        Path dir = info.getFile().findResource(SPELL_DIR);
        if (!Files.isDirectory(dir)) {
            TensuraMod.LOGGER.error("Cannot read spells from the jar: '{}' is not a directory", dir);
            return Map.of();
        }

        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(JSON_SUFFIX)).forEach(files::add);
        } catch (Exception e) {
            TensuraMod.LOGGER.error("Failed to list spells in the jar: {}", e.toString());
            return Map.of();
        }
        Collections.sort(files);

        Map<ResourceLocation, SpellDefinition> loaded = new LinkedHashMap<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String name = fileName.substring(0, fileName.length() - JSON_SUFFIX.length());
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                SpellDefinition def = GSON.fromJson(reader, SpellDefinition.class);
                if (def == null) {
                    TensuraMod.LOGGER.error("Spell '{}' in the jar is empty", name);
                    continue;
                }
                loaded.put(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, name), def);
            } catch (Exception e) {
                TensuraMod.LOGGER.error("Failed to read spell '{}' from the jar: {}", name, e.toString());
            }
        }

        TensuraMod.LOGGER.info("Read {} spells from the tensura jar", loaded.size());
        return Collections.unmodifiableMap(loaded);
    }
}
