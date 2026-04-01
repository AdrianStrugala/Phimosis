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
}
