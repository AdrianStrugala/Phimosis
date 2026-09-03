package com.tensura.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side tunables. Lands in world/serverconfig/tensura-server.toml. */
public final class TensuraConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue DEVOUR_DROP_CHANCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Predator / Devour").push("devour");
        DEVOUR_DROP_CHANCE = builder
                .comment(
                    "Chance that a single mapped move of a killed Pokemon drops its spell item.",
                    "Rolled once per distinct move the player has not absorbed yet.",
                    "0.0 disables devour drops entirely, 1.0 makes every new move drop.")
                .defineInRange("dropChance", 0.15, 0.0, 1.0);
        builder.pop();

        SPEC = builder.build();
    }

    private TensuraConfig() {}
}
