package com.tensura.event;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.engine.SpellDefinition;
import com.tensura.engine.SpellExecutor;
import com.tensura.engine.SpellTargetingRules;
import com.tensura.network.SpellVfxDispatcher;
import com.tensura.registry.TensuraMobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpellMovementController {

    private static final Map<UUID, ActiveDash> ACTIVE_DASHES = new HashMap<>();

    public static boolean startDash(ServerPlayer caster, SpellDefinition definition) {
        if (caster.isPassenger() || caster.hasEffect(TensuraMobEffects.ASLEEP)) return false;

        Vec3 look = caster.getLookAngle();
        Vec3 direction = new Vec3(look.x, 0.0, look.z);
        if (direction.lengthSqr() < 1.0E-6) return false;

        double distance = definition.delivery.distance > 0.0
                ? definition.delivery.distance
                : definition.targeting.range;
        int durationTicks = definition.delivery.duration_ticks > 0
                ? definition.delivery.duration_ticks
                : Math.max(1, (int) Math.ceil(distance / Math.max(0.1, definition.delivery.speed)));

        ACTIVE_DASHES.put(caster.getUUID(),
                new ActiveDash(caster.getUUID(), definition, caster.position(),
                        direction.normalize(), distance, durationTicks));
        if (caster.level() instanceof ServerLevel level) {
            SpellVfxDispatcher.send(level, "attachment", definition.visual.trail,
                definition.school, caster.position(),
                caster.position().add(direction.normalize().scale(distance)),
                1.0, durationTicks, caster, false);
        }
        return true;
    }

    public static boolean startDash(ServerPlayer owner, PokemonEntity companion,
                                    LivingEntity target, SpellDefinition definition) {
        if (companion.isPassenger() || companion.hasEffect(TensuraMobEffects.ASLEEP)
                || companion.hasEffect(TensuraMobEffects.FROZEN)
                || !SpellTargetingRules.canHarm(owner, companion, target)) return false;

        Vec3 direction = target.position().subtract(companion.position());
        direction = new Vec3(direction.x, 0.0, direction.z);
        if (direction.lengthSqr() < 1.0E-6) return false;

        double distance = definition.delivery.distance > 0.0
                ? definition.delivery.distance
                : definition.targeting.range;
        int durationTicks = definition.delivery.duration_ticks > 0
                ? definition.delivery.duration_ticks
                : Math.max(1, (int) Math.ceil(distance / Math.max(0.1, definition.delivery.speed)));

        ACTIVE_DASHES.put(companion.getUUID(),
                new ActiveDash(owner.getUUID(), definition, companion.position(),
                        direction.normalize(), distance, durationTicks));
        if (companion.level() instanceof ServerLevel level) {
            SpellVfxDispatcher.send(level, "attachment", definition.visual.trail,
                definition.school, companion.position(),
                companion.position().add(direction.normalize().scale(distance)),
                1.0, durationTicks, companion, false);
        }
        return true;
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        tickDash(player);
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof PokemonEntity companion) {
            tickDash(companion);
        }
    }

    private static void tickDash(LivingEntity caster) {
        ActiveDash dash = ACTIVE_DASHES.get(caster.getUUID());
        if (dash == null) return;
        if (!(caster.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = caster instanceof ServerPlayer player
                ? player
            : level.getServer().getPlayerList().getPlayer(dash.ownerId);
        if (owner == null || !caster.isAlive() || caster.hasEffect(TensuraMobEffects.ASLEEP)
                || caster.hasEffect(TensuraMobEffects.FROZEN)) {
            stopDash(caster);
            return;
        }

        double stepLength = Math.min(dash.remainingDistance, dash.stepLength);
        if (dash.definition.delivery.steerable && caster instanceof ServerPlayer) {
            Vec3 look = caster.getLookAngle();
            Vec3 steeredDirection = new Vec3(look.x, 0.0, look.z);
            if (steeredDirection.lengthSqr() > 1.0E-6) {
                dash.direction = steeredDirection.normalize();
            }
        }
        Vec3 step = dash.direction.scale(stepLength);
        AABB startBox = caster.getBoundingBox().move(dash.position.subtract(caster.position()));
        AABB destinationBox = startBox.move(step);
        if (!caster.level().noCollision(caster, destinationBox)) {
            stopDash(caster);
            return;
        }

        AABB sweptBox = startBox.expandTowards(step).inflate(0.35);
        dash.position = dash.position.add(step);
        caster.teleportTo(dash.position.x, dash.position.y, dash.position.z);
        caster.setDeltaMovement(step);
        caster.hurtMarked = true;

        level.sendParticles("water".equals(dash.definition.school)
                ? ParticleTypes.SPLASH : ParticleTypes.CLOUD,
                caster.getX(), caster.getY() + 0.2, caster.getZ(),
                5, 0.2, 0.1, 0.2, 0.01);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweptBox,
            entity -> SpellTargetingRules.canHarm(owner, caster, entity))) {
            if (dash.hitEntities.add(target.getUUID())) {
                SpellExecutor.applyImpacts(owner, caster, target, dash.definition);
                int maxTargets = dash.definition.targeting.max_targets;
                if (maxTargets > 0 && dash.hitEntities.size() >= maxTargets) {
                    stopDash(caster);
                    return;
                }
            }
        }

        dash.remainingDistance -= stepLength;
        dash.remainingTicks--;
        if (dash.remainingDistance <= 0.01 || dash.remainingTicks <= 0) {
            stopDash(caster);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        ACTIVE_DASHES.entrySet().removeIf(entry -> entry.getKey().equals(playerId)
                || entry.getValue().ownerId.equals(playerId));
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_DASHES.clear();
    }

    private static void stopDash(LivingEntity caster) {
        ACTIVE_DASHES.remove(caster.getUUID());
        Vec3 movement = caster.getDeltaMovement();
        caster.setDeltaMovement(0.0, movement.y, 0.0);
        caster.hurtMarked = true;
    }

    private static class ActiveDash {
        private final UUID ownerId;
        private final SpellDefinition definition;
        private Vec3 direction;
        private final double stepLength;
        private final Set<UUID> hitEntities = new HashSet<>();
        private Vec3 position;
        private double remainingDistance;
        private int remainingTicks;

        private ActiveDash(UUID ownerId, SpellDefinition definition, Vec3 position, Vec3 direction,
                           double distance, int durationTicks) {
            this.ownerId = ownerId;
            this.definition = definition;
            this.position = position;
            this.direction = direction;
            this.remainingDistance = distance;
            this.remainingTicks = durationTicks;
            this.stepLength = distance / durationTicks;
        }
    }
}