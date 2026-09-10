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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
    private static final Map<UUID, ActivePhaseMovement> ACTIVE_PHASE_MOVEMENTS = new HashMap<>();

    public static boolean startDash(ServerPlayer caster, SpellDefinition definition) {
        return startDash(caster, definition, 0.0);
    }

    public static boolean startDash(ServerPlayer caster, SpellDefinition definition,
                                    double collisionBonus) {
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
                    direction.normalize(), distance, durationTicks, collisionBonus));
        if (caster.level() instanceof ServerLevel level) {
            SpellVfxDispatcher.send(level, "attachment", definition.visual.trail,
                definition.school, caster.position(),
                caster.position().add(direction.normalize().scale(distance)),
                1.0, durationTicks, caster, false);
        }
        return true;
    }

    public static boolean startPhaseMovement(ServerPlayer caster, SpellDefinition definition) {
        return startPhaseMovementInternal(caster, caster, null, definition);
    }

    public static boolean startPhaseMovement(ServerPlayer owner, PokemonEntity companion,
                                             LivingEntity target,
                                             SpellDefinition definition) {
        return startPhaseMovementInternal(owner, companion, target, definition);
    }

    private static boolean startPhaseMovementInternal(ServerPlayer owner,
                                                      LivingEntity effectCaster,
                                                      LivingEntity target,
                                                      SpellDefinition definition) {
        if (effectCaster.isPassenger() || effectCaster.hasEffect(TensuraMobEffects.ASLEEP)
                || effectCaster.hasEffect(TensuraMobEffects.FROZEN)
                || ACTIVE_DASHES.containsKey(effectCaster.getUUID())) return false;
        Vec3 direction = target == null
                ? effectCaster.getLookAngle() : target.position().subtract(effectCaster.position());
        direction = new Vec3(direction.x, 0.0, direction.z);
        if (direction.lengthSqr() < 1.0E-6) return false;
        int duration = Math.max(1, definition.delivery.duration_ticks);
        boolean removeInvisibility = !effectCaster.hasEffect(MobEffects.INVISIBILITY);
        if (removeInvisibility) {
            effectCaster.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY, duration + 2, 0, false, false, false));
        }
        ACTIVE_PHASE_MOVEMENTS.put(effectCaster.getUUID(), new ActivePhaseMovement(
                owner.getUUID(), definition, direction.normalize(),
                Math.max(0.1, definition.delivery.movement_speed), duration,
                removeInvisibility));
        return true;
    }

    public static boolean releasePhaseMovement(LivingEntity caster) {
        ActivePhaseMovement movement = ACTIVE_PHASE_MOVEMENTS.remove(caster.getUUID());
        if (movement == null) return false;
        finishPhaseMovement(caster, movement, null);
        return true;
    }

    public static boolean startDash(ServerPlayer owner, PokemonEntity companion,
                                    LivingEntity target, SpellDefinition definition) {
        return startDash(owner, companion, target, definition, 0.0);
    }

    public static boolean startDash(ServerPlayer owner, PokemonEntity companion,
                                    LivingEntity target, SpellDefinition definition,
                                    double collisionBonus) {
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
                    direction.normalize(), distance, durationTicks, collisionBonus));
        if (companion.level() instanceof ServerLevel level) {
            SpellVfxDispatcher.send(level, "attachment", definition.visual.trail,
                definition.school, companion.position(),
                companion.position().add(direction.normalize().scale(distance)),
                1.0, durationTicks, companion, false);
        }
        return true;
    }

    public static void clearCompanionState(PokemonEntity companion) {
        if (ACTIVE_DASHES.containsKey(companion.getUUID())) stopDash(companion);
        ActivePhaseMovement movement = ACTIVE_PHASE_MOVEMENTS.remove(companion.getUUID());
        if (movement != null) clearPhaseInvisibility(companion, movement);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        tickDash(player);
        tickPhaseMovement(player);
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof PokemonEntity companion) {
            tickDash(companion);
            tickPhaseMovement(companion);
        }
    }

    private static void tickPhaseMovement(LivingEntity caster) {
        ActivePhaseMovement movement = ACTIVE_PHASE_MOVEMENTS.get(caster.getUUID());
        if (movement == null || !(caster.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = caster instanceof ServerPlayer player ? player
                : level.getServer().getPlayerList().getPlayer(movement.ownerId);
        if (owner == null || !caster.isAlive()
                || caster instanceof PokemonEntity pokemon
                    && (pokemon.isBattling() || pokemon.isVehicle())) {
            ACTIVE_PHASE_MOVEMENTS.remove(caster.getUUID());
            clearPhaseInvisibility(caster, movement);
            return;
        }

        if (caster instanceof ServerPlayer) {
            Vec3 look = caster.getLookAngle();
            Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
            if (horizontal.lengthSqr() > 1.0E-6) movement.direction = horizontal.normalize();
        }
        Vec3 step = movement.direction.scale(movement.speed);
        AABB destination = caster.getBoundingBox().move(step);
        if (!level.noCollision(caster, destination)) {
            ACTIVE_PHASE_MOVEMENTS.remove(caster.getUUID());
            finishPhaseMovement(caster, movement, null);
            return;
        }

        AABB swept = caster.getBoundingBox().expandTowards(step).inflate(0.45);
        caster.teleportTo(caster.getX() + step.x, caster.getY(), caster.getZ() + step.z);
        caster.setDeltaMovement(step);
        caster.hurtMarked = true;
        level.sendParticles(ParticleTypes.POOF,
                caster.getX(), caster.getY() + 0.1, caster.getZ(),
                8, 0.35, 0.08, 0.35, 0.04);
        LivingEntity contact = level.getEntitiesOfClass(LivingEntity.class, swept,
                target -> SpellTargetingRules.canHarm(owner, caster, target))
                .stream().findFirst().orElse(null);
        if (contact != null || --movement.remainingTicks <= 0) {
            ACTIVE_PHASE_MOVEMENTS.remove(caster.getUUID());
            finishPhaseMovement(caster, movement, contact);
        }
    }

    private static void finishPhaseMovement(LivingEntity caster,
                                            ActivePhaseMovement movement,
                                            LivingEntity contact) {
        clearPhaseInvisibility(caster, movement);
        if (!(caster.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = caster instanceof ServerPlayer player ? player
                : level.getServer().getPlayerList().getPlayer(movement.ownerId);
        if (owner == null) return;
        LivingEntity target = contact;
        if (target == null) {
            target = level.getEntitiesOfClass(LivingEntity.class,
                    caster.getBoundingBox().inflate(2.5),
                entity -> SpellTargetingRules.canHarm(owner, caster, entity)
                    && isAhead(caster, movement.direction, entity))
                    .stream().min(java.util.Comparator.comparingDouble(caster::distanceToSqr))
                    .orElse(null);
        }
        if (target != null) {
            SpellExecutor.applyTargetImpacts(owner, caster, target, movement.definition);
        }
        level.sendParticles(ParticleTypes.POOF,
                caster.getX(), caster.getY() + 0.2, caster.getZ(),
                28, 0.8, 0.35, 0.8, 0.1);
    }

    private static boolean isAhead(LivingEntity caster, Vec3 direction, LivingEntity target) {
        Vec3 offset = target.getBoundingBox().getCenter().subtract(caster.getEyePosition());
        return offset.lengthSqr() > 1.0E-6 && direction.dot(offset.normalize()) >= 0.5;
    }

    private static void clearPhaseInvisibility(LivingEntity caster,
                                               ActivePhaseMovement movement) {
        if (movement.removeInvisibilityOnFinish) caster.removeEffect(MobEffects.INVISIBILITY);
    }

    private static void tickDash(LivingEntity caster) {
        ActiveDash dash = ACTIVE_DASHES.get(caster.getUUID());
        if (dash == null) return;
        if (!(caster.level() instanceof ServerLevel level)) return;
        if (caster instanceof PokemonEntity pokemon
                && (pokemon.isBattling() || pokemon.isVehicle())) {
            stopDash(caster);
            return;
        }
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

        AABB sweptBox = startBox.expandTowards(step).inflate(0.35 + dash.collisionBonus);
        dash.position = dash.position.add(step);
        caster.teleportTo(dash.position.x, dash.position.y, dash.position.z);
        caster.setDeltaMovement(step);
        caster.hurtMarked = true;

        level.sendParticles(switch (dash.definition.school) {
            case "water" -> ParticleTypes.SPLASH;
            case "lightning" -> ParticleTypes.ELECTRIC_SPARK;
            case "fire" -> ParticleTypes.FLAME;
            default -> ParticleTypes.CLOUD;
            },
                caster.getX(), caster.getY() + 0.2, caster.getZ(),
                5, 0.2, 0.1, 0.2, 0.01);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweptBox,
            entity -> SpellTargetingRules.canHarm(owner, caster, entity))) {
            if (dash.hitEntities.contains(target.getUUID())) continue;
            int maxTargets = dash.definition.targeting.max_targets;
            if (maxTargets > 0 && dash.hitEntities.size() >= maxTargets) continue;

            dash.hitEntities.add(target.getUUID());
            SpellExecutor.applyImpacts(owner, caster, target, dash.definition);
            if (maxTargets > 0 && dash.hitEntities.size() >= maxTargets) {
                if (dash.definition.delivery.return_to_origin && !dash.returning
                        && beginReturn(dash)) {
                    return;
                }
                if (!dash.returning) {
                    stopDash(caster);
                    return;
                }
            }
        }

        dash.remainingDistance -= stepLength;
        dash.remainingTicks--;
        if (dash.remainingDistance <= 0.01 || dash.remainingTicks <= 0) {
            if (dash.definition.delivery.return_to_origin && !dash.returning
                    && beginReturn(dash)) return;
            stopDash(caster);
        }
    }

    private static boolean beginReturn(ActiveDash dash) {
        Vec3 returnDirection = dash.origin.subtract(dash.position);
        returnDirection = new Vec3(returnDirection.x, 0.0, returnDirection.z);
        if (returnDirection.lengthSqr() <= 1.0E-6) return false;

        dash.direction = returnDirection.normalize();
        dash.remainingDistance = returnDirection.length();
        dash.remainingTicks = durationForDistance(dash.remainingDistance, dash.stepLength);
        dash.returning = true;
        return true;
    }

    private static int durationForDistance(double distance, double stepLength) {
        return Math.max(1, (int) Math.ceil(distance / Math.max(0.1, stepLength)));
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        ACTIVE_DASHES.entrySet().removeIf(entry -> entry.getKey().equals(playerId)
                || entry.getValue().ownerId.equals(playerId));
        ActivePhaseMovement ownMovement = ACTIVE_PHASE_MOVEMENTS.remove(playerId);
        if (ownMovement != null) clearPhaseInvisibility(event.getEntity(), ownMovement);
        var iterator = ACTIVE_PHASE_MOVEMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActivePhaseMovement> entry = iterator.next();
            if (!entry.getValue().ownerId.equals(playerId)) continue;
            if (event.getEntity().level() instanceof ServerLevel level) {
                var companion = level.getEntity(entry.getKey());
                if (companion instanceof LivingEntity living) {
                    clearPhaseInvisibility(living, entry.getValue());
                }
            }
            iterator.remove();
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_DASHES.clear();
        for (Map.Entry<UUID, ActivePhaseMovement> entry : ACTIVE_PHASE_MOVEMENTS.entrySet()) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                var entity = level.getEntity(entry.getKey());
                if (entity instanceof LivingEntity living) {
                    clearPhaseInvisibility(living, entry.getValue());
                    break;
                }
            }
        }
        ACTIVE_PHASE_MOVEMENTS.clear();
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
        private final Vec3 origin;
        private Vec3 direction;
        private final double stepLength;
        private final double collisionBonus;
        private final Set<UUID> hitEntities = new HashSet<>();
        private Vec3 position;
        private double remainingDistance;
        private int remainingTicks;
        private boolean returning;

        private ActiveDash(UUID ownerId, SpellDefinition definition, Vec3 position, Vec3 direction,
                           double distance, int durationTicks, double collisionBonus) {
            this.ownerId = ownerId;
            this.definition = definition;
            this.origin = position;
            this.position = position;
            this.direction = direction;
            this.remainingDistance = distance;
            this.remainingTicks = durationTicks;
            this.stepLength = distance / durationTicks;
            this.collisionBonus = collisionBonus;
        }
    }

    private static class ActivePhaseMovement {
        private final UUID ownerId;
        private final SpellDefinition definition;
        private Vec3 direction;
        private final double speed;
        private final boolean removeInvisibilityOnFinish;
        private int remainingTicks;

        private ActivePhaseMovement(UUID ownerId, SpellDefinition definition,
                                    Vec3 direction, double speed, int remainingTicks,
                                    boolean removeInvisibilityOnFinish) {
            this.ownerId = ownerId;
            this.definition = definition;
            this.direction = direction;
            this.speed = speed;
            this.remainingTicks = remainingTicks;
            this.removeInvisibilityOnFinish = removeInvisibilityOnFinish;
        }
    }
}