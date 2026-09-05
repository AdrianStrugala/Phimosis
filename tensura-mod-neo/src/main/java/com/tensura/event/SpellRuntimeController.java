package com.tensura.event;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.engine.SpellDefinition;
import com.tensura.engine.SpellExecutor;
import com.tensura.engine.SpellTargetingRules;
import com.tensura.network.SpellVfxDispatcher;
import com.tensura.registry.TensuraMobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpellRuntimeController {

    private static final List<ActiveVortex> VORTEXES = new ArrayList<>();
    private static final List<DelayedHit> DELAYED_HITS = new ArrayList<>();
    private static final List<ActiveChannelBeam> CHANNEL_BEAMS = new ArrayList<>();
    private static final List<ActiveChannelCone> CHANNEL_CONES = new ArrayList<>();
    private static final List<DelayedArea> DELAYED_AREAS = new ArrayList<>();
    private static final List<MovingZone> MOVING_ZONES = new ArrayList<>();
    private static final List<ActiveWave> WAVES = new ArrayList<>();
    private static final List<ActiveTrap> TRAPS = new ArrayList<>();
    private static final List<ActiveMeleeCombo> MELEE_COMBOS = new ArrayList<>();
    private static final List<ProtectiveAura> PROTECTIVE_AURAS = new ArrayList<>();
    private static final Map<UUID, ActiveCounter> COUNTERS = new HashMap<>();
    private static final Map<UUID, GuardState> GUARDS = new HashMap<>();
    private static final Map<UUID, MeteorGroup> METEOR_GROUPS = new HashMap<>();
    private static final Map<UUID, ProjectileGroup> PROJECTILE_GROUPS = new HashMap<>();
    private static final Map<UUID, PendingCast> PENDING_CASTS = new HashMap<>();
    private static final Map<UUID, PendingCompanionCast> PENDING_COMPANION_CASTS = new HashMap<>();

    public static boolean isCasting(ServerPlayer caster) {
        return PENDING_CASTS.containsKey(caster.getUUID());
    }

    public static void interruptPendingCast(LivingEntity target) {
        PENDING_CASTS.remove(target.getUUID());
    }

    public static boolean startCast(ServerPlayer caster, ResourceLocation spellId,
                                    SpellDefinition definition) {
        if (PENDING_CASTS.containsKey(caster.getUUID())) return false;
        PENDING_CASTS.put(caster.getUUID(), new PendingCast(spellId, definition,
                caster.level().getGameTime() + Math.max(1, definition.cast_time_ticks)));
        return true;
    }

    public static boolean startVortex(ServerPlayer caster, SpellDefinition definition, Vec3 center) {
        return startVortex(caster, caster, definition, center);
    }

    public static boolean startVortex(ServerPlayer owner, LivingEntity effectCaster,
                                      SpellDefinition definition, Vec3 center) {
        int duration = Math.max(1, definition.delivery.duration_ticks);
        VORTEXES.add(new ActiveVortex(effectCaster.level().dimension(), owner.getUUID(),
                effectCaster.getUUID(), center, definition, duration));
        SpellExecutor.playLoopSound(effectCaster, definition);
        if (effectCaster.level() instanceof ServerLevel level) {
            SpellVfxDispatcher.send(level, "telegraph", definition.visual.telegraph,
                definition.school, center, center, definition.targeting.radius,
                Math.min(20, duration), effectCaster, false);
            SpellVfxDispatcher.send(level, "zone", definition.visual.aftermath,
                definition.school, center, center, definition.targeting.radius,
                duration, effectCaster, false);
        }
        return true;
    }

    public static boolean startDelayed(ServerPlayer caster, LivingEntity target, SpellDefinition definition) {
        return startDelayed(caster, caster, target, definition);
    }

    public static boolean startDelayed(ServerPlayer owner, LivingEntity effectCaster,
                                       LivingEntity target, SpellDefinition definition) {
        int delay = Math.max(1, definition.delivery.delay_ticks);
        DELAYED_HITS.add(new DelayedHit(effectCaster.level().dimension(), owner.getUUID(),
                effectCaster.getUUID(), target.getUUID(), definition, delay));
        if (effectCaster.level() instanceof ServerLevel level) {
            Vec3 targetPosition = target.getBoundingBox().getCenter();
            SpellVfxDispatcher.send(level, "telegraph", definition.visual.telegraph,
                definition.school, targetPosition, targetPosition,
                definition.targeting.radius, delay, target, false);
        }
        return true;
    }

    public static boolean startCounter(ServerPlayer caster, SpellDefinition definition) {
        return startCounter(caster, caster, definition);
    }

    public static boolean startCounter(ServerPlayer owner, LivingEntity defender,
                                       SpellDefinition definition) {
        int duration = Math.max(1, definition.delivery.duration_ticks);
        COUNTERS.put(defender.getUUID(), new ActiveCounter(owner.getUUID(), definition,
                defender.level().getGameTime() + duration));
        return true;
    }

    public static boolean startChannelBeam(ServerPlayer caster, SpellDefinition definition) {
        return startChannelBeam(caster, caster, null, definition);
    }

    public static boolean startChannelBeam(ServerPlayer owner, LivingEntity effectCaster,
                                           LivingEntity target, SpellDefinition definition) {
        int duration = Math.max(1, definition.delivery.duration_ticks);
        CHANNEL_BEAMS.add(new ActiveChannelBeam(effectCaster.level().dimension(), owner.getUUID(),
                effectCaster.getUUID(), target == null ? null : target.getUUID(),
                definition, duration));
        SpellExecutor.playLoopSound(effectCaster, definition);
        SpellExecutor.sendRuntimeBeamVfx(owner, effectCaster, target, definition);
        return true;
    }

    public static void stopPlayerChannelBeam(UUID playerId) {
        CHANNEL_BEAMS.removeIf(beam -> beam.ownerId.equals(playerId)
                && beam.effectCasterId.equals(playerId));
    }

    public static boolean startChannelCone(ServerPlayer owner, LivingEntity effectCaster,
                                           SpellDefinition definition) {
        int duration = Math.max(1, definition.delivery.duration_ticks);
        CHANNEL_CONES.add(new ActiveChannelCone(effectCaster.level().dimension(),
                owner.getUUID(), effectCaster.getUUID(), definition, duration));
        SpellExecutor.playLoopSound(effectCaster, definition);
        return true;
    }

    public static boolean startWave(ServerPlayer owner, LivingEntity effectCaster,
                                    SpellDefinition definition, Vec3 center, Vec3 direction) {
        int duration = Math.max(1, definition.delivery.duration_ticks);
        WAVES.add(new ActiveWave(effectCaster.level().dimension(), owner.getUUID(),
                effectCaster.getUUID(), center, direction, definition, duration));
        SpellExecutor.playLoopSound(effectCaster, definition);
        return true;
    }

    public static boolean startTrap(ServerPlayer owner, LivingEntity effectCaster,
                                    SpellDefinition definition, Vec3 center, Vec3 direction) {
        TRAPS.removeIf(trap -> trap.ownerId.equals(owner.getUUID()));
        Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
        if (horizontal.lengthSqr() < 1.0E-6) horizontal = new Vec3(0.0, 0.0, 1.0);
        Vec3 right = new Vec3(-horizontal.z, 0.0, horizontal.x).normalize();
        int trapCount = Math.max(1, Math.min(3, definition.delivery.projectile_count));
        int duration = Math.max(20, definition.delivery.duration_ticks);
        Map<UUID, Integer> triggerCounts = new HashMap<>();
        for (int index = 0; index < trapCount; index++) {
            double offset = (index - (trapCount - 1) * 0.5) * 2.0;
            TRAPS.add(new ActiveTrap(effectCaster.level().dimension(), owner.getUUID(),
                    effectCaster.getUUID(), center.add(right.scale(offset)),
                definition, duration, triggerCounts));
        }
        return true;
    }

    public static boolean startMeleeCombo(ServerPlayer owner, LivingEntity effectCaster,
                                          LivingEntity target, SpellDefinition definition) {
        if (!SpellTargetingRules.canHarm(owner, effectCaster, target)) return false;
        MELEE_COMBOS.removeIf(combo -> combo.effectCasterId.equals(effectCaster.getUUID()));
        MELEE_COMBOS.add(new ActiveMeleeCombo(effectCaster.level().dimension(),
                owner.getUUID(), effectCaster.getUUID(), target.getUUID(), definition,
                Math.max(1, definition.delivery.combo_hits)));
        return true;
    }

    public static boolean startDelayedArea(ServerPlayer owner, LivingEntity effectCaster,
                                           SpellDefinition definition, Vec3 center) {
        int delay = Math.max(1, definition.delivery.delay_ticks);
        DELAYED_AREAS.add(new DelayedArea(effectCaster.level().dimension(), owner.getUUID(),
                effectCaster.getUUID(), center, definition, delay));
        if (effectCaster.level() instanceof ServerLevel level) {
            SpellVfxDispatcher.send(level, "telegraph", definition.visual.telegraph,
                definition.school, center, center, definition.targeting.radius,
                delay, effectCaster, false);
        }
        return true;
    }

    public static boolean startMovingZone(ServerPlayer owner, LivingEntity effectCaster,
                                          SpellDefinition definition, Vec3 center,
                                          Vec3 direction) {
        int duration = Math.max(1, definition.delivery.duration_ticks);
        MOVING_ZONES.add(new MovingZone(effectCaster.level().dimension(), owner.getUUID(),
                effectCaster.getUUID(), center, direction, definition, duration));
        SpellExecutor.playLoopSound(effectCaster, definition);
        if (effectCaster.level() instanceof ServerLevel level) {
            Vec3 end = center.add(direction.normalize().scale(
                Math.max(0.0, definition.delivery.movement_speed) * duration));
            SpellVfxDispatcher.send(level, "zone", definition.visual.telegraph,
                definition.school, center, end, definition.targeting.radius,
                Math.min(20, duration), effectCaster, false);
        }
        return true;
    }

    public static boolean startProtectiveAura(ServerPlayer owner, LivingEntity effectCaster,
                                              SpellDefinition definition) {
        int duration = Math.max(1, definition.delivery.duration_ticks);
        PROTECTIVE_AURAS.add(new ProtectiveAura(effectCaster.level().dimension(), owner.getUUID(),
                effectCaster.getUUID(), definition, duration));
        SpellExecutor.playLoopSound(effectCaster, definition);
        if (effectCaster.level() instanceof ServerLevel level) {
            SpellVfxDispatcher.send(level, "aura", definition.visual.telegraph,
                definition.school, effectCaster.position(), effectCaster.position(),
                definition.targeting.radius, Math.min(20, duration), effectCaster, true);
            SpellVfxDispatcher.send(level, "aura", definition.visual.aftermath,
                definition.school, effectCaster.position(), effectCaster.position(),
                definition.targeting.radius, duration, effectCaster, true);
        }
        return true;
    }

    public static boolean startCompanionCast(ServerPlayer owner, PokemonEntity companion,
                                             LivingEntity target, ResourceLocation spellId,
                                             SpellDefinition definition) {
        if (PENDING_COMPANION_CASTS.containsKey(companion.getUUID())) return false;
        UUID targetId = "self".equals(definition.targeting.type)
                ? companion.getUUID() : target.getUUID();
        PENDING_COMPANION_CASTS.put(companion.getUUID(), new PendingCompanionCast(
                companion.level().dimension(), owner.getUUID(), targetId, spellId, definition,
                companion.level().getGameTime() + Math.max(1, definition.cast_time_ticks)));
        return true;
    }

    public static void addGuard(LivingEntity target, double amount, int durationTicks) {
        if (amount <= 0.0 || durationTicks <= 0) return;
        GUARDS.put(target.getUUID(), new GuardState(target.level().dimension(), (float) amount,
                target.level().getGameTime() + durationTicks));
    }

    public static UUID createMeteorGroup(ServerPlayer caster, int lifetimeTicks) {
        return createMeteorGroup(caster, caster, lifetimeTicks);
    }

    public static UUID createMeteorGroup(ServerPlayer owner, LivingEntity effectCaster,
                                         int lifetimeTicks) {
        UUID groupId = UUID.randomUUID();
        METEOR_GROUPS.put(groupId, new MeteorGroup(
                effectCaster.level().dimension(), effectCaster.getUUID(),
                effectCaster.level().getGameTime() + Math.max(20, lifetimeTicks)));
        return groupId;
    }

    public static UUID createProjectileGroup(ServerLevel level, int lifetimeTicks) {
        UUID groupId = UUID.randomUUID();
        PROJECTILE_GROUPS.put(groupId, new ProjectileGroup(
                level.getGameTime() + Math.max(20, lifetimeTicks)));
        return groupId;
    }

    public static ProjectileImpact registerProjectileImpact(UUID groupId, UUID targetId,
                                                            int maxTargets) {
        ProjectileGroup group = PROJECTILE_GROUPS.get(groupId);
        if (group == null) return new ProjectileImpact(true, true);
        boolean knownTarget = group.targets.contains(targetId);
        if (!knownTarget && maxTargets > 0 && group.targets.size() >= maxTargets) {
            return new ProjectileImpact(false, false);
        }
        group.targets.add(targetId);
        return new ProjectileImpact(true, group.effectsApplied.add(targetId));
    }

    public static void applyMeteorImpact(ServerPlayer caster, SpellDefinition definition,
                                         Vec3 position, UUID groupId) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        MeteorGroup group = METEOR_GROUPS.get(groupId);
        if (group == null || !group.dimension.equals(level.dimension())) return;
        Entity source = level.getEntity(group.effectCasterId);
        LivingEntity effectCaster = source instanceof LivingEntity living ? living : caster;

        double radius = definition.targeting.radius > 0.0 ? definition.targeting.radius : 3.0;
        AABB area = new AABB(position, position).inflate(radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> SpellTargetingRules.canHarm(caster, effectCaster, entity)
                        && entity.position().distanceToSqr(position) <= radius * radius);
        targets.sort((left, right) -> Double.compare(
            left.position().distanceToSqr(position), right.position().distanceToSqr(position)));
        for (LivingEntity target : targets) {
            if (definition.targeting.max_targets > 0
                && group.hitEntities.size() >= definition.targeting.max_targets) break;
            if (group.hitEntities.add(target.getUUID())) {
                SpellExecutor.applyImpacts(caster, effectCaster, target, definition);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, position.x, position.y, position.z,
                4, radius * 0.35, 0.3, radius * 0.35, 0.05);
        level.sendParticles("earth".equals(definition.school)
                ? ParticleTypes.POOF : ParticleTypes.DRAGON_BREATH,
            position.x, position.y, position.z,
                30, radius * 0.5, 0.5, radius * 0.5, 0.08);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickVortexes(event.getServer());
        tickDelayedHits(event.getServer());
        tickChannelBeams(event.getServer());
        tickChannelCones(event.getServer());
        tickDelayedAreas(event.getServer());
        tickMovingZones(event.getServer());
        tickWaves(event.getServer());
        tickTraps(event.getServer());
        tickMeleeCombos(event.getServer());
        tickProtectiveAuras(event.getServer());
        tickGuards(event.getServer());
        tickPendingCasts(event.getServer());
        tickPendingCompanionCasts(event.getServer());

        long now = event.getServer().overworld().getGameTime();
        COUNTERS.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        METEOR_GROUPS.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        PROJECTILE_GROUPS.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }

    @SubscribeEvent
    public void onLivingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel serverLevel)) return;
        Entity sourceEntity = event.getSource().getEntity();

        ActiveCounter counter = COUNTERS.get(victim.getUUID());
        ServerPlayer owner = counter == null ? null
                : serverLevel.getServer().getPlayerList().getPlayer(counter.ownerId);
        if (counter != null && owner != null
                && sourceEntity instanceof LivingEntity attacker && attacker != victim
                && SpellTargetingRules.canHarm(owner, victim, attacker)
                && victim.distanceTo(attacker) <= counter.definition.targeting.range) {
            COUNTERS.remove(victim.getUUID());
            event.setCanceled(true);
            SpellExecutor.applyImpacts(owner, victim, attacker, counter.definition);
            if (victim.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SMOKE, attacker.getX(), attacker.getY() + 1.0,
                        attacker.getZ(), 15, 0.3, 0.5, 0.3, 0.04);
            }
            return;
        }

        double auraReduction = getProtectiveAuraReduction(serverLevel.getServer(), victim);
        if (auraReduction > 0.0) {
            event.setAmount((float) (event.getAmount() * (1.0 - auraReduction)));
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    8, 0.45, 0.65, 0.45, 0.02);
        }

        GuardState guard = GUARDS.get(victim.getUUID());
        if (guard == null || guard.expiresAt <= victim.level().getGameTime()) return;

        float absorbed = Math.min(guard.remaining, event.getAmount());
        guard.remaining -= absorbed;
        event.setAmount(event.getAmount() - absorbed);
        if (guard.remaining <= 0.0f) GUARDS.remove(victim.getUUID());

        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    12, 0.45, 0.65, 0.45, 0.08);
        }
    }

    @SubscribeEvent
    public void onLivingKnockBack(LivingKnockBackEvent event) {
        GuardState guard = GUARDS.get(event.getEntity().getUUID());
        if (guard != null && guard.expiresAt > event.getEntity().level().getGameTime()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        SpellExecutor.clearPlayerState(playerId);
        VORTEXES.removeIf(vortex -> vortex.ownerId.equals(playerId)
                || vortex.effectCasterId.equals(playerId));
        DELAYED_HITS.removeIf(delayed -> delayed.ownerId.equals(playerId)
                || delayed.effectCasterId.equals(playerId));
        CHANNEL_BEAMS.removeIf(beam -> beam.ownerId.equals(playerId)
                || beam.effectCasterId.equals(playerId));
        CHANNEL_CONES.removeIf(cone -> cone.ownerId.equals(playerId)
            || cone.effectCasterId.equals(playerId));
        DELAYED_AREAS.removeIf(area -> area.ownerId.equals(playerId)
                || area.effectCasterId.equals(playerId));
        MOVING_ZONES.removeIf(zone -> zone.ownerId.equals(playerId)
                || zone.effectCasterId.equals(playerId));
        WAVES.removeIf(wave -> wave.ownerId.equals(playerId)
            || wave.effectCasterId.equals(playerId));
        TRAPS.removeIf(trap -> trap.ownerId.equals(playerId)
            || trap.effectCasterId.equals(playerId));
        MELEE_COMBOS.removeIf(combo -> combo.ownerId.equals(playerId)
            || combo.effectCasterId.equals(playerId));
        PROTECTIVE_AURAS.removeIf(aura -> aura.ownerId.equals(playerId)
                || aura.effectCasterId.equals(playerId));
        COUNTERS.entrySet().removeIf(entry -> entry.getKey().equals(playerId)
                || entry.getValue().ownerId.equals(playerId));
        GUARDS.remove(playerId);
        PENDING_CASTS.remove(playerId);
        PENDING_COMPANION_CASTS.entrySet().removeIf(entry ->
                entry.getValue().ownerId.equals(playerId));
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        VORTEXES.clear();
        DELAYED_HITS.clear();
        CHANNEL_BEAMS.clear();
        CHANNEL_CONES.clear();
        DELAYED_AREAS.clear();
        MOVING_ZONES.clear();
        WAVES.clear();
        TRAPS.clear();
        MELEE_COMBOS.clear();
        PROTECTIVE_AURAS.clear();
        COUNTERS.clear();
        GUARDS.clear();
        METEOR_GROUPS.clear();
        PROJECTILE_GROUPS.clear();
        PENDING_CASTS.clear();
        PENDING_COMPANION_CASTS.clear();
        SpellExecutor.clearAllState();
    }

    private static void tickVortexes(MinecraftServer server) {
        Iterator<ActiveVortex> iterator = VORTEXES.iterator();
        while (iterator.hasNext()) {
            ActiveVortex vortex = iterator.next();
            ServerLevel level = server.getLevel(vortex.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(vortex.ownerId);
            Entity source = level == null ? null : level.getEntity(vortex.effectCasterId);
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                    || --vortex.remainingTicks < 0) {
                iterator.remove();
                continue;
            }

            double radius = vortex.definition.targeting.radius > 0.0
                    ? vortex.definition.targeting.radius : 4.0;
            double angle = vortex.remainingTicks * 0.35;
            for (int index = 0; index < 8; index++) {
                double particleAngle = angle + index * Math.PI / 4.0;
                double particleRadius = radius * (0.35 + (index % 3) * 0.25);
                level.sendParticles("fire".equals(vortex.definition.school)
                        ? ParticleTypes.FLAME : ParticleTypes.SPLASH,
                        vortex.center.x + Math.cos(particleAngle) * particleRadius,
                        vortex.center.y + 0.15 + (index % 2) * 0.35,
                        vortex.center.z + Math.sin(particleAngle) * particleRadius,
                        1, 0.05, 0.05, 0.05, 0.02);
            }
            if (vortex.remainingTicks > 0 && vortex.remainingTicks % 20 == 0) {
                SpellExecutor.playLoopSound(effectCaster, vortex.definition);
            }

            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(vortex.center, vortex.center).inflate(radius),
                    entity -> SpellTargetingRules.canHarm(owner, effectCaster, entity)
                            && entity.position().distanceToSqr(vortex.center) <= radius * radius);
            targets.sort((left, right) -> Double.compare(
                    left.position().distanceToSqr(vortex.center),
                    right.position().distanceToSqr(vortex.center)));
            if (vortex.definition.targeting.max_targets > 0
                    && targets.size() > vortex.definition.targeting.max_targets) {
                targets = targets.subList(0, vortex.definition.targeting.max_targets);
            }
            for (LivingEntity target : targets) {
                Vec3 pullDirection = vortex.center.subtract(target.position());
                if (pullDirection.lengthSqr() > 0.04) {
                    double strength = vortex.definition.delivery.pull_strength > 0.0
                            ? vortex.definition.delivery.pull_strength : 0.08;
                    Vec3 pull = pullDirection.normalize().scale(strength);
                    target.setDeltaMovement(target.getDeltaMovement().add(pull.x, 0.02, pull.z));
                    target.hurtMarked = true;
                }
                if (vortex.remainingTicks % 20 == 0) {
                    SpellExecutor.applyImpacts(owner, effectCaster, target, vortex.definition);
                }
            }

            if (vortex.remainingTicks == 0) iterator.remove();
        }
    }

    private static void tickDelayedHits(MinecraftServer server) {
        Iterator<DelayedHit> iterator = DELAYED_HITS.iterator();
        while (iterator.hasNext()) {
            DelayedHit delayed = iterator.next();
            ServerLevel level = server.getLevel(delayed.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(delayed.ownerId);
            Entity source = level == null ? null : level.getEntity(delayed.effectCasterId);
            Entity targetEntity = level == null ? null : level.getEntity(delayed.targetId);
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                    || !(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
                iterator.remove();
                continue;
            }

            delayed.remainingTicks--;
            if (delayed.remainingTicks % 10 == 0) {
                level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + target.getBbHeight() + 0.4,
                        target.getZ(), 8, 0.35, 0.15, 0.35, 0.02);
            }
            if (delayed.remainingTicks <= 0) {
                SpellExecutor.applyImpacts(owner, effectCaster, target, delayed.definition);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1.0,
                        target.getZ(), 35, 0.5, 0.8, 0.5, 0.08);
                iterator.remove();
            }
        }
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
                    || --beam.remainingTicks < 0) {
                iterator.remove();
                continue;
            }

            int interval = Math.max(1, beam.definition.delivery.tick_interval_ticks);
            if (beam.remainingTicks > 0 && beam.remainingTicks % 20 == 0) {
                SpellExecutor.playLoopSound(effectCaster, beam.definition);
            }
            if (beam.remainingTicks % interval == 0) {
                LivingEntity target = targetEntity instanceof LivingEntity living && living.isAlive()
                        ? living : null;
                SpellExecutor.castRuntimeBeam(owner, effectCaster, target, beam.definition);
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
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                    || --cone.remainingTicks < 0) {
                iterator.remove();
                continue;
            }

            int interval = Math.max(1, cone.definition.delivery.tick_interval_ticks);
            if (cone.remainingTicks % interval == 0) {
                SpellExecutor.castRuntimeCone(owner, effectCaster, cone.definition);
            }
            if (cone.remainingTicks > 0 && cone.remainingTicks % 20 == 0) {
                SpellExecutor.playLoopSound(effectCaster, cone.definition);
            }
            if (cone.remainingTicks == 0) iterator.remove();
        }
    }

    private static void tickDelayedAreas(MinecraftServer server) {
        Iterator<DelayedArea> iterator = DELAYED_AREAS.iterator();
        while (iterator.hasNext()) {
            DelayedArea area = iterator.next();
            ServerLevel level = server.getLevel(area.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(area.ownerId);
            Entity source = level == null ? null : level.getEntity(area.effectCasterId);
                if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()) {
                iterator.remove();
                continue;
            }

            area.remainingTicks--;
            double radius = area.definition.targeting.radius > 0.0
                    ? area.definition.targeting.radius : 3.0;
            double phase = area.remainingTicks * 0.3;
            for (int index = 0; index < 12; index++) {
                double angle = phase + index * Math.PI * 2.0 / 12.0;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        area.center.x + Math.cos(angle) * radius,
                        area.center.y + 0.08,
                        area.center.z + Math.sin(angle) * radius,
                        1, 0.02, 0.02, 0.02, 0.0);
            }
            if (area.remainingTicks > 0) continue;

            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(area.center, area.center).inflate(radius),
                    target -> SpellTargetingRules.canHarm(owner, effectCaster, target)
                            && target.position().distanceToSqr(area.center) <= radius * radius);
            targets.sort((left, right) -> Double.compare(
                    left.position().distanceToSqr(area.center),
                    right.position().distanceToSqr(area.center)));
            if (area.definition.targeting.max_targets > 0
                    && targets.size() > area.definition.targeting.max_targets) {
                targets = targets.subList(0, area.definition.targeting.max_targets);
            }
            for (LivingEntity target : targets) {
                SpellExecutor.applyImpacts(owner, effectCaster, target, area.definition);
            }
            LightningBolt lightning = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
            lightning.moveTo(area.center.x, area.center.y, area.center.z);
            lightning.setVisualOnly(true);
            level.addFreshEntity(lightning);
            level.sendParticles(ParticleTypes.FLASH, area.center.x, area.center.y + 1.0,
                    area.center.z, 2, radius * 0.2, 0.5, radius * 0.2, 0.0);
            iterator.remove();
        }
    }

    private static void tickMovingZones(MinecraftServer server) {
        Iterator<MovingZone> iterator = MOVING_ZONES.iterator();
        while (iterator.hasNext()) {
            MovingZone zone = iterator.next();
            ServerLevel level = server.getLevel(zone.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(zone.ownerId);
            Entity source = level == null ? null : level.getEntity(zone.effectCasterId);
                if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                    || --zone.remainingTicks < 0) {
                iterator.remove();
                continue;
            }

            zone.center = zone.center.add(zone.direction.scale(
                    Math.max(0.0, zone.definition.delivery.movement_speed)));
            double radius = zone.definition.targeting.radius > 0.0
                    ? zone.definition.targeting.radius : 4.0;
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    zone.center.x, zone.center.y + 1.0, zone.center.z,
                    10, radius * 0.55, 1.0, radius * 0.55, 0.03);
            level.sendParticles(ParticleTypes.CLOUD,
                    zone.center.x, zone.center.y + 0.3, zone.center.z,
                    4, radius * 0.45, 0.2, radius * 0.45, 0.02);
                if (zone.remainingTicks % 4 == 0) {
                SpellVfxDispatcher.send(level, "zone", zone.definition.visual.aftermath,
                    zone.definition.school, zone.center, zone.center, radius,
                    6, effectCaster, false);
                }
            if (zone.remainingTicks > 0 && zone.remainingTicks % 20 == 0) {
                SpellExecutor.playLoopSound(effectCaster, zone.definition);
            }

            int interval = Math.max(1, zone.definition.delivery.tick_interval_ticks);
            if (zone.remainingTicks % interval == 0) {
                List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(zone.center, zone.center).inflate(radius),
                        target -> SpellTargetingRules.canHarm(owner, effectCaster, target)
                                && target.position().distanceToSqr(zone.center) <= radius * radius);
                targets.sort((left, right) -> Double.compare(
                        left.position().distanceToSqr(zone.center),
                        right.position().distanceToSqr(zone.center)));
                if (zone.definition.targeting.max_targets > 0
                        && targets.size() > zone.definition.targeting.max_targets) {
                    targets = targets.subList(0, zone.definition.targeting.max_targets);
                }
                for (LivingEntity target : targets) {
                    SpellExecutor.applyImpacts(owner, effectCaster, target, zone.definition);
                }
            }
            if (zone.remainingTicks == 0) iterator.remove();
        }
    }

        private static void tickWaves(MinecraftServer server) {
        Iterator<ActiveWave> iterator = WAVES.iterator();
        while (iterator.hasNext()) {
            ActiveWave wave = iterator.next();
            ServerLevel level = server.getLevel(wave.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(wave.ownerId);
            Entity source = level == null ? null : level.getEntity(wave.effectCasterId);
            if (level == null || owner == null || owner.level() != level
                || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                || --wave.remainingTicks < 0) {
            iterator.remove();
            continue;
            }

            double speed = Math.max(0.1, wave.definition.delivery.movement_speed);
            Vec3 nextCenter = wave.center.add(wave.direction.scale(speed));
            HitResult obstruction = level.clip(new ClipContext(
                wave.center.add(0.0, 0.6, 0.0), nextCenter.add(0.0, 0.6, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, effectCaster));
            if (obstruction.getType() == HitResult.Type.BLOCK) {
            iterator.remove();
            continue;
            }
            wave.center = nextCenter;

            double width = wave.definition.targeting.width > 0.0
                ? wave.definition.targeting.width : 3.0;
            double radius = wave.definition.targeting.radius > 0.0
                ? wave.definition.targeting.radius : 1.5;
            level.sendParticles(ParticleTypes.SPLASH,
                wave.center.x, wave.center.y + 0.8, wave.center.z,
                18, width * 0.45, 0.8, width * 0.45, 0.08);
            level.sendParticles(ParticleTypes.BUBBLE,
                wave.center.x, wave.center.y + 0.45, wave.center.z,
                8, width * 0.4, 0.45, width * 0.4, 0.04);
            if (wave.remainingTicks % 4 == 0) {
            SpellVfxDispatcher.send(level, "wave", wave.definition.visual.aftermath,
                wave.definition.school, wave.center,
                wave.center.add(wave.direction.scale(2.0)), width,
                6, effectCaster, false);
            }

            AABB area = new AABB(wave.center, wave.center).inflate(width, radius, width);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
                target -> SpellTargetingRules.canHarm(owner, effectCaster, target)
                    && target.getY() <= wave.center.y + radius + 1.0);
            targets.sort((left, right) -> Double.compare(
                left.distanceToSqr(wave.center), right.distanceToSqr(wave.center)));
            boolean reachedTargetLimit = false;
            for (LivingEntity target : targets) {
            if (!wave.hitEntities.add(target.getUUID())) continue;
            SpellExecutor.applyImpacts(owner, effectCaster, target, wave.definition);
            Vec3 movement = target.getDeltaMovement();
            target.setDeltaMovement(movement.add(
                wave.direction.x * 0.55, 0.18, wave.direction.z * 0.55));
            target.hurtMarked = true;
            if (wave.definition.targeting.max_targets > 0
                && wave.hitEntities.size() >= wave.definition.targeting.max_targets) {
                reachedTargetLimit = true;
                break;
            }
            }
            if (reachedTargetLimit || wave.remainingTicks == 0) iterator.remove();
        }
        }

        private static void tickTraps(MinecraftServer server) {
        Iterator<ActiveTrap> iterator = TRAPS.iterator();
        while (iterator.hasNext()) {
            ActiveTrap trap = iterator.next();
            ServerLevel level = server.getLevel(trap.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(trap.ownerId);
            if (level == null || owner == null || owner.level() != level
                || --trap.remainingTicks < 0) {
            iterator.remove();
            continue;
            }
            Entity source = level.getEntity(trap.effectCasterId);
            LivingEntity effectCaster = source instanceof LivingEntity living && living.isAlive()
                ? living : owner;
            double radius = trap.definition.targeting.radius > 0.0
                ? trap.definition.targeting.radius : 1.25;
            if (trap.remainingTicks % 8 == 0) {
            level.sendParticles(ParticleTypes.WITCH,
                trap.center.x, trap.center.y + 0.12, trap.center.z,
                4, radius * 0.45, 0.08, radius * 0.45, 0.01);
            }

            Set<UUID> currentOccupants = new HashSet<>();
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(trap.center, trap.center).inflate(radius, 0.8, radius),
                target -> SpellTargetingRules.canHarm(owner, effectCaster, target));
            for (LivingEntity target : targets) {
            currentOccupants.add(target.getUUID());
            if (trap.occupants.contains(target.getUUID())) continue;
            int triggerCount = trap.triggerCounts.merge(target.getUUID(), 1, Integer::sum);
            if (triggerCount == 1) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON,
                    120, 0, false, true, true));
            } else {
                target.addEffect(new MobEffectInstance(TensuraMobEffects.TOXIC,
                    200, Math.min(2, triggerCount - 2), false, true, true));
            }
            SpellExecutor.applyImpacts(owner, effectCaster, target, trap.definition);
            }
            trap.occupants.clear();
            trap.occupants.addAll(currentOccupants);
            if (trap.remainingTicks == 0) iterator.remove();
        }
        }

        private static void tickMeleeCombos(MinecraftServer server) {
        Iterator<ActiveMeleeCombo> iterator = MELEE_COMBOS.iterator();
        while (iterator.hasNext()) {
            ActiveMeleeCombo combo = iterator.next();
            ServerLevel level = server.getLevel(combo.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(combo.ownerId);
            Entity source = level == null ? null : level.getEntity(combo.effectCasterId);
            Entity targetEntity = level == null ? null : level.getEntity(combo.targetId);
            if (level == null || owner == null || owner.level() != level
                || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                || !(targetEntity instanceof LivingEntity target) || !target.isAlive()
                || !SpellTargetingRules.canHarm(owner, effectCaster, target)
                || target.distanceTo(effectCaster) > combo.definition.targeting.range + 2.0) {
            iterator.remove();
            continue;
            }

            if (combo.ticksUntilHit-- > 0) continue;
            boolean finalHit = combo.remainingHits == 1;
            effectCaster.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                target.getBoundingBox().getCenter());
            if (combo.remainingHits < Math.max(1, combo.definition.delivery.combo_hits)) {
                target.invulnerableTime = 0;
            }
            SpellExecutor.applyImpacts(owner, effectCaster, target,
                combo.definition, finalHit);
            level.sendParticles(finalHit ? ParticleTypes.EXPLOSION : ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                finalHit ? 4 : 12, 0.35, 0.4, 0.35, 0.08);
            combo.remainingHits--;
            if (combo.remainingHits <= 0) {
            iterator.remove();
            } else {
            combo.ticksUntilHit = Math.max(1,
                combo.definition.delivery.combo_interval_ticks);
            }
        }
        }

    private static void tickProtectiveAuras(MinecraftServer server) {
        Iterator<ProtectiveAura> iterator = PROTECTIVE_AURAS.iterator();
        while (iterator.hasNext()) {
            ProtectiveAura aura = iterator.next();
            ServerLevel level = server.getLevel(aura.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(aura.ownerId);
            Entity source = level == null ? null : level.getEntity(aura.effectCasterId);
                if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                    || --aura.remainingTicks < 0) {
                iterator.remove();
                continue;
            }

            if (aura.remainingTicks % 5 == 0) {
                double radius = aura.definition.targeting.radius > 0.0
                        ? aura.definition.targeting.radius : 5.0;
                double phase = aura.remainingTicks * 0.08;
                for (int index = 0; index < 16; index++) {
                    double angle = phase + index * Math.PI * 2.0 / 16.0;
                    level.sendParticles(index % 2 == 0
                                    ? ParticleTypes.END_ROD : ParticleTypes.SNOWFLAKE,
                            effectCaster.getX() + Math.cos(angle) * radius,
                            effectCaster.getY() + 0.3 + (index % 4) * 0.55,
                            effectCaster.getZ() + Math.sin(angle) * radius,
                            1, 0.02, 0.04, 0.02, 0.0);
                }
            }
            if (aura.remainingTicks > 0 && aura.remainingTicks % 20 == 0) {
                SpellExecutor.playLoopSound(effectCaster, aura.definition);
            }
            int impactInterval = Math.max(1, aura.definition.delivery.tick_interval_ticks);
            if (aura.remainingTicks > 0 && aura.remainingTicks % impactInterval == 0) {
                double radius = aura.definition.targeting.radius > 0.0
                        ? aura.definition.targeting.radius : 5.0;
                for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class,
                        effectCaster.getBoundingBox().inflate(radius),
                        entity -> SpellTargetingRules.isProtectedAlly(owner, effectCaster, entity))) {
                    SpellExecutor.applyImpacts(owner, effectCaster, ally, aura.definition);
                }
            }
            if (aura.remainingTicks == 0) iterator.remove();
        }
    }

    private static double getProtectiveAuraReduction(MinecraftServer server, LivingEntity victim) {
        double reduction = 0.0;
        for (ProtectiveAura aura : PROTECTIVE_AURAS) {
            ServerLevel level = server.getLevel(aura.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(aura.ownerId);
            Entity source = level == null ? null : level.getEntity(aura.effectCasterId);
                if (level == null || victim.level() != level || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster)
                    || !SpellTargetingRules.isProtectedAlly(owner, effectCaster, victim)) continue;
            double radius = aura.definition.targeting.radius > 0.0
                    ? aura.definition.targeting.radius : 5.0;
            if (victim.distanceToSqr(effectCaster) <= radius * radius) {
                reduction = Math.max(reduction,
                        Math.max(0.0, Math.min(0.9, aura.definition.impact.stream()
                                .filter(impact -> "damage_reduction".equals(impact.type))
                                .mapToDouble(impact -> impact.reduction)
                                .max().orElse(0.0))));
            }
        }
        return reduction;
    }

    private static void tickGuards(MinecraftServer server) {
        Iterator<Map.Entry<UUID, GuardState>> iterator = GUARDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, GuardState> entry = iterator.next();
            GuardState guard = entry.getValue();
            ServerLevel level = server.getLevel(guard.dimension);
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isAlive()
                    || guard.expiresAt <= living.level().getGameTime()) {
                iterator.remove();
                continue;
            }
            if (living.tickCount % 10 == 0) {
                level.sendParticles(ParticleTypes.CRIT, living.getX(), living.getY() + 1.0, living.getZ(),
                        5, 0.6, 0.8, 0.6, 0.02);
            }
        }
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
                    || !(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
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

    private static class ActiveVortex {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final Vec3 center;
        private final SpellDefinition definition;
        private int remainingTicks;

        private ActiveVortex(ResourceKey<Level> dimension, UUID ownerId, UUID effectCasterId, Vec3 center,
                             SpellDefinition definition, int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.center = center;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private static class DelayedHit {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final UUID targetId;
        private final SpellDefinition definition;
        private int remainingTicks;

        private DelayedHit(ResourceKey<Level> dimension, UUID ownerId, UUID effectCasterId, UUID targetId,
                           SpellDefinition definition, int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.targetId = targetId;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private static class ActiveChannelBeam {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final UUID targetId;
        private final SpellDefinition definition;
        private int remainingTicks;

        private ActiveChannelBeam(ResourceKey<Level> dimension, UUID ownerId,
                                  UUID effectCasterId, UUID targetId,
                                  SpellDefinition definition, int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.targetId = targetId;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private static class ActiveChannelCone {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final SpellDefinition definition;
        private int remainingTicks;

        private ActiveChannelCone(ResourceKey<Level> dimension, UUID ownerId,
                                  UUID effectCasterId, SpellDefinition definition,
                                  int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private static class DelayedArea {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final Vec3 center;
        private final SpellDefinition definition;
        private int remainingTicks;

        private DelayedArea(ResourceKey<Level> dimension, UUID ownerId,
                            UUID effectCasterId, Vec3 center,
                            SpellDefinition definition, int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.center = center;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private static class MovingZone {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final Vec3 direction;
        private final SpellDefinition definition;
        private Vec3 center;
        private int remainingTicks;

        private MovingZone(ResourceKey<Level> dimension, UUID ownerId,
                           UUID effectCasterId, Vec3 center, Vec3 direction,
                           SpellDefinition definition, int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.center = center;
            Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
            this.direction = horizontal.lengthSqr() > 1.0E-6
                    ? horizontal.normalize() : Vec3.ZERO;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private static class ActiveWave {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final Vec3 direction;
        private final SpellDefinition definition;
        private final Set<UUID> hitEntities = new HashSet<>();
        private Vec3 center;
        private int remainingTicks;

        private ActiveWave(ResourceKey<Level> dimension, UUID ownerId,
                           UUID effectCasterId, Vec3 center, Vec3 direction,
                           SpellDefinition definition, int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.center = center;
            Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
            this.direction = horizontal.lengthSqr() > 1.0E-6
                    ? horizontal.normalize() : Vec3.ZERO;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private static class ActiveTrap {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final Vec3 center;
        private final SpellDefinition definition;
        private final Set<UUID> occupants = new HashSet<>();
        private final Map<UUID, Integer> triggerCounts;
        private int remainingTicks;

        private ActiveTrap(ResourceKey<Level> dimension, UUID ownerId,
                           UUID effectCasterId, Vec3 center,
                   SpellDefinition definition, int remainingTicks,
                   Map<UUID, Integer> triggerCounts) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.center = center;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
            this.triggerCounts = triggerCounts;
        }
    }

    private static class ActiveMeleeCombo {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final UUID targetId;
        private final SpellDefinition definition;
        private int remainingHits;
        private int ticksUntilHit;

        private ActiveMeleeCombo(ResourceKey<Level> dimension, UUID ownerId,
                                 UUID effectCasterId, UUID targetId,
                                 SpellDefinition definition, int remainingHits) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.targetId = targetId;
            this.definition = definition;
            this.remainingHits = remainingHits;
        }
    }

    private static class ProtectiveAura {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final SpellDefinition definition;
        private int remainingTicks;

        private ProtectiveAura(ResourceKey<Level> dimension, UUID ownerId,
                               UUID effectCasterId, SpellDefinition definition,
                               int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private record ActiveCounter(UUID ownerId, SpellDefinition definition, long expiresAt) {}

    private static class GuardState {
        private final ResourceKey<Level> dimension;
        private final long expiresAt;
        private float remaining;

        private GuardState(ResourceKey<Level> dimension, float remaining, long expiresAt) {
            this.dimension = dimension;
            this.remaining = remaining;
            this.expiresAt = expiresAt;
        }
    }

    private static class MeteorGroup {
        private final ResourceKey<Level> dimension;
        private final UUID effectCasterId;
        private final long expiresAt;
        private final Set<UUID> hitEntities = new HashSet<>();

        private MeteorGroup(ResourceKey<Level> dimension, UUID effectCasterId, long expiresAt) {
            this.dimension = dimension;
            this.effectCasterId = effectCasterId;
            this.expiresAt = expiresAt;
        }
    }

    private static class ProjectileGroup {
        private final long expiresAt;
        private final Set<UUID> targets = new HashSet<>();
        private final Set<UUID> effectsApplied = new HashSet<>();

        private ProjectileGroup(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    public record ProjectileImpact(boolean allowed, boolean firstHit) {}

    private record PendingCast(ResourceLocation spellId, SpellDefinition definition, long completesAt) {}

    private record PendingCompanionCast(ResourceKey<Level> dimension, UUID ownerId, UUID targetId,
                                        ResourceLocation spellId, SpellDefinition definition,
                                        long completesAt) {}
}