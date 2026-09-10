package com.tensura.event;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.engine.SpellDefinition;
import com.tensura.engine.SpellExecutor;
import com.tensura.engine.SpellTargetingRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SpellCastController {

    private static final int COMPANION_CHANNEL_TICKS = 40;
    private static final List<ActiveChannelBeam> CHANNEL_BEAMS = new ArrayList<>();
    private static final List<ActiveChannelCone> CHANNEL_CONES = new ArrayList<>();
    private static final Map<UUID, PendingCast> PENDING_CASTS = new HashMap<>();
    private static final Map<UUID, PendingCompanionCast> PENDING_COMPANION_CASTS = new HashMap<>();

    public static boolean isCasting(ServerPlayer caster) {
        return PENDING_CASTS.containsKey(caster.getUUID())
            || SpellExecutor.isCharging(caster.getUUID());
    }

    public static void interruptPendingCast(LivingEntity target) {
        PENDING_CASTS.remove(target.getUUID());
        if (SpellExecutor.interruptCharge(target.getUUID())) {
            target.stopUsingItem();
        }
    }

    public static boolean startCast(ServerPlayer caster, ResourceLocation spellId,
                                    SpellDefinition definition) {
        if (PENDING_CASTS.containsKey(caster.getUUID())) return false;
        int castTime = effectiveCastTime(caster, definition);
        PENDING_CASTS.put(caster.getUUID(), new PendingCast(spellId, definition,
                caster.level().getGameTime() + Math.max(1, castTime)));
        return true;
    }

    private static int effectiveCastTime(LivingEntity caster, SpellDefinition definition) {
        if (definition.delivery.charge_release) {
            return Math.max(1, definition.delivery.maximum_charge_ticks);
        }
        if ("solar_beam_charge".equals(definition.visual.cast_animation)
                && caster.level().isDay()
                && caster.level().canSeeSky(caster.blockPosition())) {
            return 20;
        }
        return definition.cast_time_ticks;
    }

    public static boolean startChannelBeam(ServerPlayer caster, SpellDefinition definition) {
        return startChannelBeam(caster, caster, null, definition,
                channelDuration(definition));
    }

    public static boolean startCompanionChannelBeam(ServerPlayer owner,
                                                    LivingEntity effectCaster,
                                                    LivingEntity target,
                                                    SpellDefinition definition) {
        return startChannelBeam(owner, effectCaster, target, definition,
                Math.min(COMPANION_CHANNEL_TICKS, channelDuration(definition)));
    }

    private static boolean startChannelBeam(ServerPlayer owner, LivingEntity effectCaster,
                                            LivingEntity target, SpellDefinition definition,
                                            int duration) {
        CHANNEL_BEAMS.add(new ActiveChannelBeam(effectCaster.level().dimension(), owner.getUUID(),
                effectCaster.getUUID(), target == null ? null : target.getUUID(),
                definition, duration));
        SpellExecutor.playLoopSound(effectCaster, definition);
        if (!"hyper_beam_core".equals(definition.visual.trail)) {
            SpellExecutor.sendRuntimeBeamVfx(owner, effectCaster, target, definition);
        }
        return true;
    }

    public static boolean startChannelCone(ServerPlayer owner, LivingEntity effectCaster,
                                           SpellDefinition definition) {
        return startChannelCone(owner, effectCaster, null, definition,
            channelDuration(definition));
    }

    public static boolean startCompanionChannelCone(ServerPlayer owner,
                                                    LivingEntity effectCaster,
                                LivingEntity target,
                                                    SpellDefinition definition) {
        return startChannelCone(owner, effectCaster, target, definition,
                Math.min(COMPANION_CHANNEL_TICKS, channelDuration(definition)));
    }

        private static boolean startChannelCone(ServerPlayer owner, LivingEntity effectCaster,
                            LivingEntity target, SpellDefinition definition,
                            int duration) {
        CHANNEL_CONES.add(new ActiveChannelCone(effectCaster.level().dimension(),
            owner.getUUID(), effectCaster.getUUID(),
            target == null ? null : target.getUUID(), definition, duration));
        SpellExecutor.playLoopSound(effectCaster, definition);
        return true;
    }

    public static void stopPlayerChannels(UUID playerId) {
        CHANNEL_BEAMS.removeIf(beam -> beam.ownerId.equals(playerId)
                && beam.effectCasterId.equals(playerId));
        CHANNEL_CONES.removeIf(cone -> cone.ownerId.equals(playerId)
                && cone.effectCasterId.equals(playerId));
    }

    public static boolean startCompanionCast(ServerPlayer owner, PokemonEntity companion,
                                             LivingEntity target, ResourceLocation spellId,
                                             SpellDefinition definition) {
        if (PENDING_COMPANION_CASTS.containsKey(companion.getUUID())) return false;
        UUID targetId = "self".equals(definition.targeting.type)
                ? companion.getUUID() : target.getUUID();
        PENDING_COMPANION_CASTS.put(companion.getUUID(), new PendingCompanionCast(
                companion.level().dimension(), owner.getUUID(), targetId, spellId, definition,
            companion.level().getGameTime()
                + Math.max(1, effectiveCastTime(companion, definition))));
        return true;
    }

    public static boolean isCompanionBusy(UUID companionId) {
        return PENDING_COMPANION_CASTS.containsKey(companionId)
                || CHANNEL_BEAMS.stream().anyMatch(beam -> beam.effectCasterId.equals(companionId))
                || CHANNEL_CONES.stream().anyMatch(cone -> cone.effectCasterId.equals(companionId));
    }

    public static void clearCompanionState(UUID companionId) {
        PENDING_COMPANION_CASTS.remove(companionId);
        CHANNEL_BEAMS.removeIf(beam -> beam.effectCasterId.equals(companionId));
        CHANNEL_CONES.removeIf(cone -> cone.effectCasterId.equals(companionId));
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickChannelBeams(event.getServer());
        tickChannelCones(event.getServer());
        tickPendingCasts(event.getServer());
        tickPendingCompanionCasts(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        PENDING_CASTS.remove(playerId);
        PENDING_COMPANION_CASTS.entrySet().removeIf(entry ->
                entry.getValue().ownerId.equals(playerId));
        CHANNEL_BEAMS.removeIf(beam -> beam.ownerId.equals(playerId)
                || beam.effectCasterId.equals(playerId));
        CHANNEL_CONES.removeIf(cone -> cone.ownerId.equals(playerId)
                || cone.effectCasterId.equals(playerId));
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        CHANNEL_BEAMS.clear();
        CHANNEL_CONES.clear();
        PENDING_CASTS.clear();
        PENDING_COMPANION_CASTS.clear();
    }

    private static int channelDuration(SpellDefinition definition) {
        return definition.delivery.hold_to_channel && definition.delivery.duration_ticks <= 0
                ? Integer.MAX_VALUE : Math.max(1, definition.delivery.duration_ticks);
    }

    private static void tickChannelBeams(MinecraftServer server) {
        Iterator<ActiveChannelBeam> iterator = CHANNEL_BEAMS.iterator();
        while (iterator.hasNext()) {
            ActiveChannelBeam beam = iterator.next();
            ServerLevel level = server.getLevel(beam.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(beam.ownerId);
            Entity source = level == null ? null : level.getEntity(beam.effectCasterId);
            Entity targetEntity = level == null || beam.targetId == null
                    ? null : level.getEntity(beam.targetId);
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                    || companionUnavailable(effectCaster)
                    || beam.ownerId.equals(beam.effectCasterId)
                        && beam.definition.delivery.hold_to_channel && !owner.isUsingItem()
                    || --beam.remainingTicks < 0) {
                iterator.remove();
                continue;
            }

            LivingEntity target = targetEntity instanceof LivingEntity living && living.isAlive()
                    ? living : null;
            if (effectCaster instanceof PokemonEntity companion
                    && (target == null
                        || !SpellTargetingRules.canCompanionTarget(owner, companion, target))) {
                iterator.remove();
                continue;
            }

            int interval = Math.max(1, beam.definition.delivery.tick_interval_ticks);
            if (beam.remainingTicks > 0 && beam.remainingTicks % 20 == 0) {
                SpellExecutor.playLoopSound(effectCaster, beam.definition);
            }
            if (beam.remainingTicks % interval == 0) {
                int pulseCount = Math.max(1,
                    (int) Math.ceil((double) beam.durationTicks / interval));
                SpellExecutor.castRuntimeBeam(owner, effectCaster, target, beam.definition,
                    beam.pulseIndex++, pulseCount);
            }
            if (beam.remainingTicks == 0) iterator.remove();
        }
    }

    private static void tickChannelCones(MinecraftServer server) {
        Iterator<ActiveChannelCone> iterator = CHANNEL_CONES.iterator();
        while (iterator.hasNext()) {
            ActiveChannelCone cone = iterator.next();
            ServerLevel level = server.getLevel(cone.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(cone.ownerId);
            Entity source = level == null ? null : level.getEntity(cone.effectCasterId);
                Entity targetEntity = level == null || cone.targetId == null
                    ? null : level.getEntity(cone.targetId);
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                    || companionUnavailable(effectCaster)
                    || cone.ownerId.equals(cone.effectCasterId)
                        && cone.definition.delivery.hold_to_channel && !owner.isUsingItem()
                    || --cone.remainingTicks < 0) {
                iterator.remove();
                continue;
            }

            LivingEntity target = targetEntity instanceof LivingEntity living && living.isAlive()
                    ? living : null;
            if (effectCaster instanceof PokemonEntity companion
                    && (target == null
                        || !SpellTargetingRules.canCompanionTarget(owner, companion, target))) {
                iterator.remove();
                continue;
            }

            int interval = Math.max(1, cone.definition.delivery.tick_interval_ticks);
            if (cone.remainingTicks % interval == 0) {
                int pulseCount = Math.max(1,
                    (int) Math.ceil((double) cone.durationTicks / interval));
                SpellExecutor.castRuntimeCone(owner, effectCaster, target, cone.definition,
                    cone.pulseIndex++, pulseCount);
            }
            if (cone.remainingTicks > 0 && cone.remainingTicks % 20 == 0) {
                SpellExecutor.playLoopSound(effectCaster, cone.definition);
            }
            if (cone.remainingTicks == 0) iterator.remove();
        }
    }

    private static boolean companionUnavailable(LivingEntity effectCaster) {
        return effectCaster instanceof PokemonEntity pokemon
                && (pokemon.isBattling() || pokemon.isVehicle());
    }

    private static void tickPendingCasts(MinecraftServer server) {
        Iterator<Map.Entry<UUID, PendingCast>> iterator = PENDING_CASTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingCast> entry = iterator.next();
            ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
            PendingCast pending = entry.getValue();
            if (caster == null || !caster.isAlive()) {
                iterator.remove();
                continue;
            }

            long remaining = pending.completesAt - caster.level().getGameTime();
            Vec3 movement = caster.getDeltaMovement();
            caster.setDeltaMovement(0.0, Math.min(0.0, movement.y), 0.0);
            caster.setSprinting(false);
            caster.hurtMarked = true;
            if (remaining % 5 == 0 && caster.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                        caster.getX(), caster.getY() + 1.0, caster.getZ(),
                        8, 0.65, 0.8, 0.65, 0.03);
            }
            if (remaining <= 0) {
                iterator.remove();
                SpellExecutor.executeDelivery(caster, pending.spellId, pending.definition);
            }
        }
    }

    private static void tickPendingCompanionCasts(MinecraftServer server) {
        Iterator<Map.Entry<UUID, PendingCompanionCast>> iterator =
                PENDING_COMPANION_CASTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingCompanionCast> entry = iterator.next();
            PendingCompanionCast pending = entry.getValue();
            ServerLevel level = server.getLevel(pending.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(pending.ownerId);
            Entity source = level == null ? null : level.getEntity(entry.getKey());
            Entity targetEntity = level == null ? null : level.getEntity(pending.targetId);
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof PokemonEntity companion) || !companion.isAlive()
                    || companionUnavailable(companion)
                    || !(targetEntity instanceof LivingEntity target) || !target.isAlive()
                    || target != companion
                        && !SpellTargetingRules.canCompanionTarget(owner, companion, target)) {
                iterator.remove();
                continue;
            }

            long remaining = pending.completesAt - companion.level().getGameTime();
            Vec3 movement = companion.getDeltaMovement();
            companion.setDeltaMovement(0.0, Math.min(0.0, movement.y), 0.0);
            companion.hurtMarked = true;
            if (remaining % 5 == 0) {
                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                        companion.getX(), companion.getY() + 1.0, companion.getZ(),
                        8, 0.65, 0.8, 0.65, 0.03);
            }
            if (remaining <= 0) {
                iterator.remove();
                SpellExecutor.executeCompanionDelivery(owner, companion, target,
                        pending.spellId, pending.definition);
            }
        }
    }

    private static class ActiveChannelBeam {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final UUID targetId;
        private final SpellDefinition definition;
        private final int durationTicks;
        private int pulseIndex;
        private int remainingTicks;

        private ActiveChannelBeam(ResourceKey<Level> dimension, UUID ownerId,
                                  UUID effectCasterId, UUID targetId,
                                  SpellDefinition definition, int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.targetId = targetId;
            this.definition = definition;
            this.durationTicks = remainingTicks;
            this.remainingTicks = remainingTicks;
        }
    }

    private static class ActiveChannelCone {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final UUID targetId;
        private final SpellDefinition definition;
        private final int durationTicks;
        private int pulseIndex;
        private int remainingTicks;

        private ActiveChannelCone(ResourceKey<Level> dimension, UUID ownerId,
                                  UUID effectCasterId, UUID targetId,
                                  SpellDefinition definition,
                                  int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.targetId = targetId;
            this.definition = definition;
            this.durationTicks = remainingTicks;
            this.remainingTicks = remainingTicks;
        }
    }

    private record PendingCast(ResourceLocation spellId, SpellDefinition definition,
                               long completesAt) {
    }

    private record PendingCompanionCast(ResourceKey<Level> dimension, UUID ownerId,
                                        UUID targetId, ResourceLocation spellId,
                                        SpellDefinition definition, long completesAt) {
    }
}