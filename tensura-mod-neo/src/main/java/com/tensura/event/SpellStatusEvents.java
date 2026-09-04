package com.tensura.event;

import com.tensura.registry.TensuraMobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class SpellStatusEvents {

    public static final int REST_SLEEP_TICKS = 100;
    private static final int REST_MINIMUM_SLEEP_TICKS = 40;

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean immobilized = player.hasEffect(TensuraMobEffects.ASLEEP)
                || player.hasEffect(TensuraMobEffects.FROZEN);
        if (!immobilized && !player.hasEffect(TensuraMobEffects.PARALYZED)) return;

        Vec3 movement = player.getDeltaMovement();
        if (immobilized) {
            player.setDeltaMovement(0.0, Math.min(0.0, movement.y), 0.0);
            player.setSprinting(false);
        } else {
            player.setDeltaMovement(movement.x * 0.7, movement.y, movement.z * 0.7);
        }
        player.hurtMarked = true;
        if (player.hasEffect(TensuraMobEffects.ASLEEP)) showSleepParticles(player);
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living) || living instanceof ServerPlayer) return;
        if (living.level().isClientSide) return;
        boolean immobilized = living.hasEffect(TensuraMobEffects.ASLEEP)
                || living.hasEffect(TensuraMobEffects.FROZEN);
        if (!immobilized && !living.hasEffect(TensuraMobEffects.PARALYZED)) return;

        Vec3 movement = living.getDeltaMovement();
        if (immobilized) {
            living.setDeltaMovement(0.0, Math.min(0.0, movement.y), 0.0);
        } else {
            living.setDeltaMovement(movement.x * 0.7, movement.y, movement.z * 0.7);
        }
        living.hurtMarked = true;
        if (immobilized && living instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        if (living.hasEffect(TensuraMobEffects.ASLEEP)) showSleepParticles(living);
    }

    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        if (event.getEntity().hasEffect(TensuraMobEffects.ASLEEP)
                || event.getEntity().hasEffect(TensuraMobEffects.FROZEN)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && (attacker.hasEffect(TensuraMobEffects.ASLEEP)
                || attacker.hasEffect(TensuraMobEffects.FROZEN))) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity().hasEffect(TensuraMobEffects.FROZEN)) {
            event.getEntity().removeEffect(TensuraMobEffects.FROZEN);
        }

        MobEffectInstance asleep = event.getEntity().getEffect(TensuraMobEffects.ASLEEP);
        if (asleep == null) return;

        int elapsedTicks = REST_SLEEP_TICKS - asleep.getDuration();
        if (elapsedTicks >= REST_MINIMUM_SLEEP_TICKS) {
            event.getEntity().removeEffect(TensuraMobEffects.ASLEEP);
        }
    }

    private static void showSleepParticles(LivingEntity living) {
        if (living.tickCount % 10 != 0 || !(living.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.ENCHANT,
                living.getX(), living.getY() + living.getBbHeight() + 0.25, living.getZ(),
                3, 0.25, 0.15, 0.25, 0.02);
    }
}