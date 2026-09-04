package com.tensura.registry;

import com.tensura.TensuraMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class TensuraMobEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, TensuraMod.MOD_ID);

    /**
     * Visual HUD indicator for xp_gain_multiplier attribute.
     * Golden color. Amplifier = floor(multiplier - 1), capped at 4.
     * Texture: assets/tensura/textures/mob_effect/scholar_aura.png
     */
    public static final DeferredHolder<MobEffect, MobEffect> SCHOLAR_AURA =
            MOB_EFFECTS.register("scholar_aura",
                    () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFFD700) {});

    public static final DeferredHolder<MobEffect, MobEffect> ASLEEP =
            MOB_EFFECTS.register("asleep",
                    () -> new MobEffect(MobEffectCategory.HARMFUL, 0x6D83C5) {});

    public static final DeferredHolder<MobEffect, MobEffect> WET =
            MOB_EFFECTS.register("wet",
                    () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x3D9BE9) {});

    public static final DeferredHolder<MobEffect, MobEffect> FROZEN =
            MOB_EFFECTS.register("frozen",
                    () -> new MobEffect(MobEffectCategory.HARMFUL, 0xA8E9FF) {});

    public static final DeferredHolder<MobEffect, MobEffect> PARALYZED =
            MOB_EFFECTS.register("paralyzed",
                    () -> new MobEffect(MobEffectCategory.HARMFUL, 0xF6D743) {});

    public static final DeferredHolder<MobEffect, MobEffect> EXHAUSTED =
            MOB_EFFECTS.register("exhausted",
                    () -> new MobEffect(MobEffectCategory.HARMFUL, 0x744A88) {});
}
