package com.tensura.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class ClientCooldownTracker {

    // spellId -> {startMs, durationMs}
    private static final Map<ResourceLocation, long[]> cooldowns = new HashMap<>();

    public static void set(ResourceLocation spellId, int durationTicks) {
        cooldowns.put(spellId, new long[]{System.currentTimeMillis(), durationTicks * 50L});
    }

    public static boolean isOnCooldown(ResourceLocation spellId) {
        long[] cd = cooldowns.get(spellId);
        if (cd == null) return false;
        return System.currentTimeMillis() < cd[0] + cd[1];
    }

    /** Returns fraction remaining: 1.0 = just cast, 0.0 = ready */
    public static float getRemainingFraction(ResourceLocation spellId) {
        long[] cd = cooldowns.get(spellId);
        if (cd == null) return 0f;
        long elapsed = System.currentTimeMillis() - cd[0];
        if (elapsed >= cd[1]) return 0f;
        return 1f - (float) elapsed / cd[1];
    }
}
