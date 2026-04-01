package com.tensura.event;

import com.tensura.registry.TensuraAttributes;
import com.tensura.registry.TensuraMobEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class TensuraAttributeEffects {

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // DARK SENSE → permanent Night Vision
        // Refresh when < 200 ticks remain (10 seconds buffer) to avoid flicker
        AttributeInstance darkSense = player.getAttribute(TensuraAttributes.DARK_SENSE);
        if (darkSense != null && darkSense.getValue() > 0) {
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current == null || current.getDuration() < 200) {
                // hideParticles=true, showIcon=true — visible in HUD, no particle clutter
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, false, false, true));
            }
        }

        // COLONY AURA → Hero of the Village, refreshed every 10 seconds
        if (player.tickCount % 200 == 0) {
            AttributeInstance colonyAura = player.getAttribute(TensuraAttributes.COLONY_AURA);
            if (colonyAura != null && colonyAura.getValue() > 0) {
                int amplifier = (int) Math.min(colonyAura.getValue() - 1.0, 4.0);
                player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 300, amplifier, false, false, true));
            }
        }

        // SCHOLAR AURA visual indicator — refresh every 5 seconds
        if (player.tickCount % 100 == 0) {
            AttributeInstance xpMult = player.getAttribute(TensuraAttributes.XP_GAIN_MULTIPLIER);
            if (xpMult != null && xpMult.getValue() > 0) {
                MobEffectInstance current = player.getEffect(TensuraMobEffects.SCHOLAR_AURA);
                if (current == null || current.getDuration() < 50) {
                    int amplifier = (int) Math.min(Math.max(xpMult.getValue() - 1.0, 0.0), 4.0);
                    player.addEffect(new MobEffectInstance(TensuraMobEffects.SCHOLAR_AURA, 300, amplifier, false, false, true));
                }
            }
        }
    }

    @SubscribeEvent
    public void onXPDrop(LivingExperienceDropEvent event) {
        if (!(event.getAttackingPlayer() instanceof ServerPlayer player)) return;
        AttributeInstance xpMult = player.getAttribute(TensuraAttributes.XP_GAIN_MULTIPLIER);
        if (xpMult == null || xpMult.getValue() <= 0) return;
        int bonus = (int) (event.getDroppedExperience() * xpMult.getValue());
        event.setDroppedExperience(event.getDroppedExperience() + bonus);
    }
}
