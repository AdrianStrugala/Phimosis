package com.tensura.event;

import com.tensura.engine.SpellDefinition;
import com.tensura.engine.SpellExecutor;
import com.tensura.registry.TensuraMobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
            new ActiveDash(definition, caster.position(), direction.normalize(), distance, durationTicks));
        return true;
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ActiveDash dash = ACTIVE_DASHES.get(player.getUUID());
        if (dash == null) return;
        if (!player.isAlive() || player.hasEffect(TensuraMobEffects.ASLEEP)) {
            stopDash(player);
            return;
        }

        double stepLength = Math.min(dash.remainingDistance, dash.stepLength);
        Vec3 step = dash.direction.scale(stepLength);
        AABB startBox = player.getBoundingBox().move(dash.position.subtract(player.position()));
        AABB destinationBox = startBox.move(step);
        if (!player.level().noCollision(player, destinationBox)) {
            stopDash(player);
            return;
        }

        AABB sweptBox = startBox.expandTowards(step).inflate(0.35);
        dash.position = dash.position.add(step);
        player.teleportTo(dash.position.x, dash.position.y, dash.position.z);
        player.setDeltaMovement(step);
        player.hurtMarked = true;

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(),
                    5, 0.2, 0.1, 0.2, 0.01);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweptBox,
                    entity -> entity != player && entity.isAlive())) {
                if (dash.hitEntities.add(target.getUUID())) {
                    SpellExecutor.applyImpacts(player, target, dash.definition);
                    int maxTargets = dash.definition.targeting.max_targets;
                    if (maxTargets > 0 && dash.hitEntities.size() >= maxTargets) {
                        stopDash(player);
                        return;
                    }
                }
            }
        }

        dash.remainingDistance -= stepLength;
        dash.remainingTicks--;
        if (dash.remainingDistance <= 0.01 || dash.remainingTicks <= 0) {
            stopDash(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_DASHES.remove(event.getEntity().getUUID());
    }

    private static void stopDash(ServerPlayer player) {
        ACTIVE_DASHES.remove(player.getUUID());
        Vec3 movement = player.getDeltaMovement();
        player.setDeltaMovement(0.0, movement.y, 0.0);
        player.hurtMarked = true;
    }

    private static class ActiveDash {
        private final SpellDefinition definition;
        private final Vec3 direction;
        private final double stepLength;
        private final Set<UUID> hitEntities = new HashSet<>();
        private Vec3 position;
        private double remainingDistance;
        private int remainingTicks;

        private ActiveDash(SpellDefinition definition, Vec3 position, Vec3 direction,
                           double distance, int durationTicks) {
            this.definition = definition;
            this.position = position;
            this.direction = direction;
            this.remainingDistance = distance;
            this.remainingTicks = durationTicks;
            this.stepLength = distance / durationTicks;
        }
    }
}