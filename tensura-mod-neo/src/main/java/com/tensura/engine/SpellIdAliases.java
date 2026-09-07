package com.tensura.engine;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public final class SpellIdAliases {

    private static final Map<String, String> LEGACY_PATHS = Map.of(
        "iron_strike", "bullet_punch",
        "frost_nova", "icy_wind",
        "nature_burst", "giga_drain",
        "poison_strike", "venoshock",
        "seismic_slam", "seismic_toss",
        "thundershock", "thunder_shock"
    );

    private SpellIdAliases() {}

    public static ResourceLocation canonicalize(ResourceLocation spellId) {
        if (spellId == null || !"tensura".equals(spellId.getNamespace())) return spellId;
        String canonicalPath = LEGACY_PATHS.get(spellId.getPath());
        return canonicalPath == null
            ? spellId
            : ResourceLocation.fromNamespaceAndPath(spellId.getNamespace(), canonicalPath);
    }
}
