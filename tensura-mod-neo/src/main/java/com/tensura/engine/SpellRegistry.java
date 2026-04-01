package com.tensura.engine;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SpellRegistry {

    private static final Map<ResourceLocation, SpellDefinition> SPELLS = new HashMap<>();

    public static void register(ResourceLocation id, SpellDefinition def) {
        SPELLS.put(id, def);
    }

    public static Optional<SpellDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(SPELLS.get(id));
    }

    public static Map<ResourceLocation, SpellDefinition> all() {
        return Collections.unmodifiableMap(SPELLS);
    }

    public static void clear() {
        SPELLS.clear();
    }
}
