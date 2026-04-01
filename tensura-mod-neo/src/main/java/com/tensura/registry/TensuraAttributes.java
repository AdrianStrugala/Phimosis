package com.tensura.registry;

import com.tensura.TensuraMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class TensuraAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, TensuraMod.MOD_ID);

    /** value > 0 → permanent Night Vision (applied via tick event). */
    public static final DeferredHolder<Attribute, Attribute> DARK_SENSE =
            ATTRIBUTES.register("dark_sense",
                    () -> new RangedAttribute("tensura.attribute.dark_sense", 0.0, 0.0, 1.0)
                            .setSyncable(true));

    /** Hero of the Village level = floor(value). value > 0 activates. Level = min(value-1, 4). */
    public static final DeferredHolder<Attribute, Attribute> COLONY_AURA =
            ATTRIBUTES.register("colony_aura",
                    () -> new RangedAttribute("tensura.attribute.colony_aura", 0.0, 0.0, 5.0)
                            .setSyncable(true));

    /** Bonus XP multiplier. 0.5 = +50% XP from mobs, 2.0 = +200% XP. Stacks additively. */
    public static final DeferredHolder<Attribute, Attribute> XP_GAIN_MULTIPLIER =
            ATTRIBUTES.register("xp_gain_multiplier",
                    () -> new RangedAttribute("tensura.attribute.xp_gain_multiplier", 0.0, 0.0, 5.0)
                            .setSyncable(true));
}
