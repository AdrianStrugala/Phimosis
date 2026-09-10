package com.tensura.event;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.engine.SpellDefinition;
import com.tensura.engine.SpellExecutor;
import com.tensura.engine.SpellImpactApplier;
import com.tensura.engine.SpellTargetingRules;
import com.tensura.engine.CobblemonThunderVfx;
import com.tensura.engine.CobblemonUltimateVfx;
import com.tensura.network.SpellVfxDispatcher;
import com.tensura.registry.TensuraMobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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
    private static final List<DelayedArea> DELAYED_AREAS = new ArrayList<>();
    private static final List<Aftershock> AFTERSHOCKS = new ArrayList<>();
    private static final List<MovingZone> MOVING_ZONES = new ArrayList<>();
    private static final List<ActiveWave> WAVES = new ArrayList<>();
    private static final List<ActiveTrap> TRAPS = new ArrayList<>();
    private static final List<ActiveMeleeCombo> MELEE_COMBOS = new ArrayList<>();
    private static final List<ProtectiveAura> PROTECTIVE_AURAS = new ArrayList<>();
    private static final List<ActiveLeechSeed> LEECH_SEEDS = new ArrayList<>();
    private static final List<ActiveDashCombo> DASH_COMBOS = new ArrayList<>();
    private static final List<DelayedTeleportStrike> DELAYED_TELEPORT_STRIKES = new ArrayList<>();
    private static final List<ActiveZone> ZONES = new ArrayList<>();
    private static final List<ContactAura> CONTACT_AURAS = new ArrayList<>();
    private static final Map<UUID, ActiveOrbit> ACTIVE_ORBITS = new HashMap<>();
    private static final List<ActivePulseRing> PULSE_RINGS = new ArrayList<>();
    private static final List<BarrierWall> BARRIER_WALLS = new ArrayList<>();
    private static final Map<UUID, ActiveCounter> COUNTERS = new HashMap<>();
    private static final Map<UUID, GuardState> GUARDS = new HashMap<>();
    private static final Map<UUID, MeteorGroup> METEOR_GROUPS = new HashMap<>();
    private static final Map<UUID, ProjectileGroup> PROJECTILE_GROUPS = new HashMap<>();

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

    public static boolean startWave(ServerPlayer owner, LivingEntity effectCaster,
                                    SpellDefinition definition, Vec3 center, Vec3 direction) {
        int duration = Math.max(1, definition.delivery.duration_ticks);
        WAVES.add(new ActiveWave(effectCaster.level().dimension(), owner.getUUID(),
                effectCaster.getUUID(), center, direction, definition, duration));
        SpellExecutor.playLoopSound(effectCaster, definition);
        if (effectCaster.level() instanceof ServerLevel level
            && "surf_wave".equals(definition.visual.aftermath)) {
            CobblemonUltimateVfx.sendSurfStart(level, center);
            SpellVfxDispatcher.send(level, "wave", definition.visual.telegraph,
                    definition.school, center,
                    center.add(direction.normalize().scale(2.0)),
                    definition.targeting.width, 12, effectCaster, false);
        }
        return true;
    }

    public static boolean startTrap(ServerPlayer owner, LivingEntity effectCaster,
                                    SpellDefinition definition, Vec3 center, Vec3 direction) {
        TRAPS.removeIf(trap -> trap.ownerId.equals(owner.getUUID()));
        Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
        if (horizontal.lengthSqr() < 1.0E-6) horizontal = new Vec3(0.0, 0.0, 1.0);
        Vec3 right = new Vec3(-horizontal.z, 0.0, horizontal.x).normalize();
        boolean toxicRing = "poison_burst".equals(definition.visual.impact);
        int trapCount = toxicRing ? 6
                : Math.max(1, Math.min(3, definition.delivery.projectile_count));
        int duration = Math.max(20, definition.delivery.duration_ticks);
        Map<UUID, Integer> triggerCounts = new HashMap<>();
        for (int index = 0; index < trapCount; index++) {
            Vec3 trapPosition;
            if (toxicRing) {
                double angle = index * Math.PI * 2.0 / trapCount;
                trapPosition = center.add(Math.cos(angle) * 3.5, 0.0,
                        Math.sin(angle) * 3.5);
            } else {
                double offset = (index - (trapCount - 1) * 0.5) * 2.0;
                trapPosition = center.add(right.scale(offset));
            }
            TRAPS.add(new ActiveTrap(effectCaster.level().dimension(), owner.getUUID(),
                    effectCaster.getUUID(), trapPosition,
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
            if (CobblemonThunderVfx.isThunder(definition)) {
                CobblemonThunderVfx.sendTelegraph(level, center);
            }
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
            if ("tailwind_aura".equals(definition.visual.aftermath)) {
                sendTailwindRibbon(level, effectCaster, definition,
                        definition.targeting.radius);
            } else {
                SpellVfxDispatcher.send(level, "aura", definition.visual.telegraph,
                    definition.school, effectCaster.position(), effectCaster.position(),
                    definition.targeting.radius, Math.min(20, duration), effectCaster, true);
                SpellVfxDispatcher.send(level, "aura", definition.visual.aftermath,
                    definition.school, effectCaster.position(), effectCaster.position(),
                    definition.targeting.radius, duration, effectCaster, true);
            }
        }
        return true;
    }

    public static void startLeechSeed(ServerPlayer owner, LivingEntity effectCaster,
                                      LivingEntity target, int durationTicks,
                                      double amountPerTick) {
        LEECH_SEEDS.removeIf(seed -> seed.targetId.equals(target.getUUID()));
        LEECH_SEEDS.add(new ActiveLeechSeed(effectCaster.level().dimension(),
                owner.getUUID(), effectCaster.getUUID(), target.getUUID(),
                Math.max(20, durationTicks), Math.max(0.5, amountPerTick)));
    }

            public static boolean startDashCombo(ServerPlayer owner, LivingEntity effectCaster,
                             LivingEntity target, SpellDefinition definition) {
            DASH_COMBOS.removeIf(combo -> combo.effectCasterId.equals(effectCaster.getUUID()));
            DASH_COMBOS.add(new ActiveDashCombo(effectCaster.level().dimension(),
                owner.getUUID(), effectCaster.getUUID(),
                target == null ? null : target.getUUID(), definition,
                Math.max(1, definition.delivery.combo_hits)));
            return true;
            }

            public static boolean startDelayedTeleportStrike(ServerPlayer owner,
                                      LivingEntity effectCaster,
                                      LivingEntity target,
                                      SpellDefinition definition) {
            if (!SpellTargetingRules.canHarm(owner, effectCaster, target)) return false;
            int delay = Math.max(1, definition.delivery.delay_ticks);
            boolean removeInvisibilityOnFinish =
                !effectCaster.hasEffect(MobEffects.INVISIBILITY);
            Iterator<DelayedTeleportStrike> iterator = DELAYED_TELEPORT_STRIKES.iterator();
            while (iterator.hasNext()) {
                DelayedTeleportStrike strike = iterator.next();
                if (!strike.effectCasterId.equals(effectCaster.getUUID())) continue;
                removeInvisibilityOnFinish |= strike.removeInvisibilityOnFinish;
                iterator.remove();
            }
            DELAYED_TELEPORT_STRIKES.add(new DelayedTeleportStrike(
                effectCaster.level().dimension(), owner.getUUID(), effectCaster.getUUID(),
                target.getUUID(), definition, delay, removeInvisibilityOnFinish));
            effectCaster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                delay, 0, false, false, true));
            if (effectCaster.level() instanceof ServerLevel level) {
                Vec3 exit = target.getBoundingBox().getCenter();
                SpellVfxDispatcher.send(level, "telegraph", definition.visual.telegraph,
                    definition.school, exit, exit, Math.max(1.0, definition.targeting.width),
                    delay, target, false);
            }
            return true;
            }

            public static boolean startZone(ServerPlayer owner, LivingEntity effectCaster,
                            SpellDefinition definition, Vec3 center) {
            int duration = Math.max(1, definition.delivery.duration_ticks);
            ZONES.add(new ActiveZone(effectCaster.level().dimension(), owner.getUUID(),
                effectCaster.getUUID(), center, definition, duration));
            SpellExecutor.playLoopSound(effectCaster, definition);
            if (effectCaster.level() instanceof ServerLevel level) {
                SpellVfxDispatcher.send(level, "zone", definition.visual.telegraph,
                    definition.school, center, center, definition.targeting.radius,
                    Math.min(20, duration), effectCaster, false);
                SpellVfxDispatcher.send(level, "zone", definition.visual.aftermath,
                    definition.school, center, center, definition.targeting.radius,
                    duration, effectCaster, false);
            }
            return true;
            }

            public static boolean startContactAura(ServerPlayer owner, LivingEntity effectCaster,
                                SpellDefinition definition) {
            int duration = Math.max(1, definition.delivery.duration_ticks);
            CONTACT_AURAS.removeIf(aura -> aura.effectCasterId.equals(effectCaster.getUUID()));
            CONTACT_AURAS.add(new ContactAura(effectCaster.level().dimension(), owner.getUUID(),
                effectCaster.getUUID(), definition, duration));
            SpellExecutor.applyCasterImpacts(owner, effectCaster, definition);
            if (effectCaster.level() instanceof ServerLevel level) {
                SpellVfxDispatcher.send(level, "attachment", definition.visual.trail,
                    definition.school, effectCaster.position(), effectCaster.position(),
                    Math.max(1.0, definition.targeting.width), duration,
                    effectCaster, false);
            }
            return true;
            }

        public static boolean startOrbit(ServerPlayer owner, LivingEntity effectCaster,
                                         SpellDefinition definition) {
            int duration = Math.max(1, definition.delivery.duration_ticks);
            ACTIVE_ORBITS.put(effectCaster.getUUID(), new ActiveOrbit(
                    effectCaster.level().dimension(), owner.getUUID(), effectCaster.getUUID(),
                    definition, duration));
            if (effectCaster.level() instanceof ServerLevel level) {
                SpellVfxDispatcher.send(level, "attachment", definition.visual.trail,
                        definition.school, effectCaster.position(), effectCaster.position(),
                        Math.max(1.0, definition.targeting.radius), duration,
                        effectCaster, false);
            }
            return true;
        }

        /**
         * Only releases an orbit belonging to {@code definition}. Keying on the delivery type
         * alone would let any second orbit_release spell detonate the first one's payload and
         * return early, consuming the new cast without ever starting it or setting its cooldown.
         *
         * <p>Identity comparison is deliberate: {@code SpellRegistry} hands out one instance per
         * spell id, so the stored definition is the same object the caster looked up. A datapack
         * reload mid-orbit swaps the instances and the orbit simply expires on its own timer -
         * no wrong payload, no free cast.
         */
        public static boolean releaseOrbit(ServerPlayer owner, SpellDefinition definition) {
            ActiveOrbit orbit = ACTIVE_ORBITS.get(owner.getUUID());
            if (orbit == null || orbit.definition != definition) return false;
            ACTIVE_ORBITS.remove(owner.getUUID());
            releaseOrbit(owner.getServer(), orbit);
            return true;
        }

        public static boolean startPulseRing(ServerPlayer owner, LivingEntity effectCaster,
                                             SpellDefinition definition) {
            int duration = Math.max(1, definition.delivery.duration_ticks);
            PULSE_RINGS.add(new ActivePulseRing(effectCaster.level().dimension(), owner.getUUID(),
                    effectCaster.getUUID(), definition, duration));
            return true;
        }

        public static boolean startBarrierWall(ServerPlayer owner, LivingEntity effectCaster,
                                               LivingEntity facingTarget,
                                               SpellDefinition definition) {
            Vec3 forward = facingTarget == null
                    ? effectCaster.getLookAngle()
                    : facingTarget.getBoundingBox().getCenter()
                        .subtract(effectCaster.getEyePosition());
            forward = new Vec3(forward.x, 0.0, forward.z);
            if (forward.lengthSqr() < 1.0E-6) {
                double yaw = Math.toRadians(effectCaster.getYRot());
                forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
            }
            forward = forward.normalize();
            Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
            double width = Math.max(1.0, definition.targeting.width);
            double halfHeight = Math.max(1.0, definition.targeting.radius);
            int duration = Math.max(1, definition.delivery.duration_ticks);
            Vec3 center = effectCaster.position().add(forward.scale(2.0))
                    .add(0.0, halfHeight, 0.0);
            BARRIER_WALLS.add(new BarrierWall(effectCaster.level().dimension(), owner.getUUID(),
                    effectCaster.getUUID(), center, forward, right,
                    width * 0.5, halfHeight, definition, duration));
            if (effectCaster.level() instanceof ServerLevel level) {
                Vec3 left = center.subtract(right.scale(width * 0.5));
                Vec3 rightEdge = center.add(right.scale(width * 0.5));
                SpellVfxDispatcher.send(level, "beam", definition.visual.trail,
                        definition.school, left, rightEdge, halfHeight,
                        duration, effectCaster, true);
            }
            return true;
        }

    public static void clearCompanionState(LivingEntity companion) {
        UUID companionId = companion.getUUID();
        VORTEXES.removeIf(vortex -> vortex.effectCasterId.equals(companionId));
        DELAYED_HITS.removeIf(delayed -> delayed.effectCasterId.equals(companionId));
        SpellCastController.clearCompanionState(companionId);
        DELAYED_AREAS.removeIf(area -> area.effectCasterId.equals(companionId));
        AFTERSHOCKS.removeIf(blast -> blast.effectCasterId.equals(companionId));
        MOVING_ZONES.removeIf(zone -> zone.effectCasterId.equals(companionId));
        WAVES.removeIf(wave -> wave.effectCasterId.equals(companionId));
        TRAPS.removeIf(trap -> trap.effectCasterId.equals(companionId));
        MELEE_COMBOS.removeIf(combo -> combo.effectCasterId.equals(companionId));
        PROTECTIVE_AURAS.removeIf(aura -> aura.effectCasterId.equals(companionId));
        LEECH_SEEDS.removeIf(seed -> seed.effectCasterId.equals(companionId));
        DASH_COMBOS.removeIf(combo -> combo.effectCasterId.equals(companionId));
        Iterator<DelayedTeleportStrike> strikes = DELAYED_TELEPORT_STRIKES.iterator();
        while (strikes.hasNext()) {
            DelayedTeleportStrike strike = strikes.next();
            if (!strike.effectCasterId.equals(companionId)) continue;
            clearTeleportStrikeInvisibility(companion, strike);
            strikes.remove();
        }
        ZONES.removeIf(zone -> zone.effectCasterId.equals(companionId));
        CONTACT_AURAS.removeIf(aura -> aura.effectCasterId.equals(companionId));
        ACTIVE_ORBITS.remove(companionId);
        PULSE_RINGS.removeIf(ring -> ring.effectCasterId.equals(companionId));
        BARRIER_WALLS.removeIf(wall -> wall.effectCasterId.equals(companionId));
        COUNTERS.remove(companionId);
        GUARDS.remove(companionId);
        METEOR_GROUPS.entrySet().removeIf(entry ->
                entry.getValue().effectCasterId.equals(companionId));
    }

    public static void addGuard(LivingEntity target, double amount, int durationTicks) {
        addGuard(target, amount, durationTicks, "");
    }

    public static void addGuard(LivingEntity target, double amount, int durationTicks,
                                String visualStyle) {
        if (amount <= 0.0 || durationTicks <= 0) return;
        GUARDS.put(target.getUUID(), new GuardState(target.level().dimension(), (float) amount,
                target.level().getGameTime() + durationTicks,
                "steel_plates".equals(visualStyle) ? 4 : 1));
    }

    public static void damageGuard(LivingEntity target, double amount) {
        GuardState guard = GUARDS.get(target.getUUID());
        if (guard == null || amount <= 0.0) return;
        guard.remaining = Math.max(0.0F, guard.remaining - (float) amount);
        if (guard.remaining <= 0.0F) GUARDS.remove(target.getUUID());
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
        if ("dragon_crater".equals(definition.visual.impact)) {
            CobblemonUltimateVfx.sendDracoMeteorImpact(level, position);
        }
        level.sendParticles("earth".equals(definition.school)
                ? ParticleTypes.POOF : ParticleTypes.DRAGON_BREATH,
            position.x, position.y, position.z,
                30, radius * 0.5, 0.5, radius * 0.5, 0.08);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickVortexes(event.getServer());
        tickDelayedHits(event.getServer());
        tickDelayedAreas(event.getServer());
        tickAftershocks(event.getServer());
        tickMovingZones(event.getServer());
        tickWaves(event.getServer());
        tickTraps(event.getServer());
        tickMeleeCombos(event.getServer());
        tickProtectiveAuras(event.getServer());
        tickLeechSeeds(event.getServer());
        tickDashCombos(event.getServer());
        tickDelayedTeleportStrikes(event.getServer());
        tickZones(event.getServer());
        tickContactAuras(event.getServer());
        tickOrbits(event.getServer());
        tickPulseRings(event.getServer());
        tickBarrierWalls(event.getServer());
        tickGuards(event.getServer());

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

        if (sourceEntity != null) {
            BarrierHit barrierHit = findBarrierIntersection(
                serverLevel, sourceEntity.position(), victim.getBoundingBox().getCenter(),
                victim);
            if (barrierHit != null) {
                event.setCanceled(true);
                Entity directEntity = event.getSource().getDirectEntity();
                if (directEntity instanceof Projectile projectile) projectile.discard();
                sendBarrierImpact(serverLevel, barrierHit, victim);
                return;
            }
        }

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
            float preventedDamage = (float) (event.getAmount() * auraReduction);
            event.setAmount(event.getAmount() - preventedDamage);
            recordProtectiveAuraDamage(serverLevel.getServer(), victim, preventedDamage);
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    8, 0.45, 0.65, 0.45, 0.02);
        }

        GuardState guard = GUARDS.get(victim.getUUID());
        if (guard == null || guard.expiresAt <= victim.level().getGameTime()) return;

        float absorbed = Math.min(guard.remaining, event.getAmount());
        int segmentsBefore = guard.visibleSegments();
        guard.remaining -= absorbed;
        event.setAmount(event.getAmount() - absorbed);
        int segmentsAfter = guard.visibleSegments();
        if (guard.remaining <= 0.0f) GUARDS.remove(victim.getUUID());

        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    12, 0.45, 0.65, 0.45, 0.08);
            if (segmentsAfter < segmentsBefore && guard.maximumSegments > 1) {
                CobblemonUltimateVfx.sendIronDefenseFracture(level, victim.position());
            }
        }
    }

    @SubscribeEvent
    public void onLivingDamageApplied(LivingDamageEvent.Post event) {
        float appliedDamage = event.getNewDamage();
        if (appliedDamage <= 0.0F) return;
        LivingEntity victim = event.getEntity();
        for (DelayedHit delayed : DELAYED_HITS) {
            if (!delayed.targetId.equals(victim.getUUID())
                    || !"psychic_implosion".equals(
                            delayed.definition.visual.impact)) continue;
            double maximumEcho = Math.max(1.0, delayed.definition.power * 0.5);
            delayed.recordedDamage = Math.min(maximumEcho,
                    delayed.recordedDamage + appliedDamage * 0.25);
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
        DELAYED_AREAS.removeIf(area -> area.ownerId.equals(playerId)
                || area.effectCasterId.equals(playerId));
        AFTERSHOCKS.removeIf(blast -> blast.ownerId.equals(playerId)
                || blast.effectCasterId.equals(playerId));
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
        LEECH_SEEDS.removeIf(seed -> seed.ownerId.equals(playerId)
            || seed.effectCasterId.equals(playerId));
        DASH_COMBOS.removeIf(combo -> combo.ownerId.equals(playerId)
            || combo.effectCasterId.equals(playerId));
        DELAYED_TELEPORT_STRIKES.removeIf(strike -> strike.ownerId.equals(playerId)
            || strike.effectCasterId.equals(playerId));
        ZONES.removeIf(zone -> zone.ownerId.equals(playerId)
            || zone.effectCasterId.equals(playerId));
        CONTACT_AURAS.removeIf(aura -> aura.ownerId.equals(playerId)
            || aura.effectCasterId.equals(playerId));
        ACTIVE_ORBITS.entrySet().removeIf(entry -> entry.getValue().ownerId.equals(playerId)
            || entry.getValue().effectCasterId.equals(playerId));
        PULSE_RINGS.removeIf(ring -> ring.ownerId.equals(playerId)
            || ring.effectCasterId.equals(playerId));
        BARRIER_WALLS.removeIf(wall -> wall.ownerId.equals(playerId)
            || wall.effectCasterId.equals(playerId));
        COUNTERS.entrySet().removeIf(entry -> entry.getKey().equals(playerId)
                || entry.getValue().ownerId.equals(playerId));
        GUARDS.remove(playerId);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        VORTEXES.clear();
        DELAYED_HITS.clear();
        DELAYED_AREAS.clear();
        AFTERSHOCKS.clear();
        MOVING_ZONES.clear();
        WAVES.clear();
        TRAPS.clear();
        MELEE_COMBOS.clear();
        PROTECTIVE_AURAS.clear();
        LEECH_SEEDS.clear();
        DASH_COMBOS.clear();
        DELAYED_TELEPORT_STRIKES.clear();
        ZONES.clear();
        CONTACT_AURAS.clear();
        ACTIVE_ORBITS.clear();
        PULSE_RINGS.clear();
        BARRIER_WALLS.clear();
        COUNTERS.clear();
        GUARDS.clear();
        METEOR_GROUPS.clear();
        PROJECTILE_GROUPS.clear();
        SpellExecutor.clearAllState();
    }

    private static void tickContactAuras(MinecraftServer server) {
        Iterator<ContactAura> iterator = CONTACT_AURAS.iterator();
        while (iterator.hasNext()) {
            ContactAura aura = iterator.next();
            ServerLevel level = server.getLevel(aura.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(aura.ownerId);
            Entity source = level == null ? null : level.getEntity(aura.effectCasterId);
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                    || effectCaster instanceof PokemonEntity pokemon
                        && (pokemon.isBattling() || pokemon.isVehicle())
                    || --aura.remainingTicks < 0) {
                iterator.remove();
                continue;
            }

            if (aura.remainingTicks % 4 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        effectCaster.getX(),
                        effectCaster.getY() + effectCaster.getBbHeight() * 0.5,
                        effectCaster.getZ(), 8,
                        effectCaster.getBbWidth() * 0.6,
                        effectCaster.getBbHeight() * 0.4,
                        effectCaster.getBbWidth() * 0.6, 0.08);
            }
            if (aura.remainingTicks > 0 && aura.remainingTicks % 20 == 0) {
                SpellExecutor.playLoopSound(effectCaster, aura.definition);
            }

            double padding = Math.max(0.25, aura.definition.targeting.width * 0.35);
            for (LivingEntity target : level.getEntitiesOfClass(
                    LivingEntity.class, effectCaster.getBoundingBox().inflate(padding),
                    entity -> !aura.hitEntities.contains(entity.getUUID())
                            && SpellTargetingRules.canHarm(owner, effectCaster, entity))) {
                int maxTargets = aura.definition.targeting.max_targets;
                if (maxTargets > 0 && aura.hitEntities.size() >= maxTargets) break;
                aura.hitEntities.add(target.getUUID());
                SpellExecutor.applyTargetImpacts(owner, effectCaster, target, aura.definition);
            }
            if (aura.remainingTicks == 0) iterator.remove();
        }
    }

    private static void tickOrbits(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveOrbit>> iterator = ACTIVE_ORBITS.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveOrbit orbit = iterator.next().getValue();
            ServerLevel level = server.getLevel(orbit.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(orbit.ownerId);
            Entity source = level == null ? null : level.getEntity(orbit.effectCasterId);
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                    || effectCaster instanceof PokemonEntity pokemon
                        && (pokemon.isBattling() || pokemon.isVehicle())) {
                iterator.remove();
                continue;
            }

            if (orbit.remainingTicks % 4 == 0) {
                double angle = orbit.remainingTicks * 0.35;
                double radius = Math.max(1.5, orbit.definition.targeting.radius * 0.55);
                for (int index = 0; index < 10; index++) {
                    double theta = angle + index * Math.PI * 2.0 / 10.0;
                    level.sendParticles(ParticleTypes.COMPOSTER,
                            effectCaster.getX() + Math.cos(theta) * radius,
                            effectCaster.getY() + 0.5 + (index % 3) * 0.35,
                            effectCaster.getZ() + Math.sin(theta) * radius,
                            1, 0.02, 0.04, 0.02, 0.02);
                }
            }
            if (--orbit.remainingTicks <= 0) {
                iterator.remove();
                releaseOrbit(server, orbit);
            }
        }
    }

        private static void tickPulseRings(MinecraftServer server) {
        Iterator<ActivePulseRing> iterator = PULSE_RINGS.iterator();
        while (iterator.hasNext()) {
            ActivePulseRing ring = iterator.next();
            ServerLevel level = server.getLevel(ring.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(ring.ownerId);
            Entity source = level == null ? null : level.getEntity(ring.effectCasterId);
            if (level == null || owner == null || owner.level() != level
                || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                || --ring.remainingTicks < 0) {
            iterator.remove();
            continue;
            }

            int interval = Math.max(1, ring.definition.delivery.tick_interval_ticks);
            if (ring.remainingTicks % interval != 0) continue;
            boolean finalPulse = ring.remainingTicks == 0;
            double radius = Math.max(1.0, ring.definition.targeting.radius);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                effectCaster.getBoundingBox().inflate(radius),
                target -> SpellTargetingRules.canHarm(owner, effectCaster, target));
            targets.sort((left, right) -> Double.compare(
                left.distanceToSqr(effectCaster), right.distanceToSqr(effectCaster)));
            if (ring.definition.targeting.max_targets > 0
                && targets.size() > ring.definition.targeting.max_targets) {
            targets = targets.subList(0, ring.definition.targeting.max_targets);
            }
            for (LivingEntity target : targets) {
            target.invulnerableTime = 0;
            SpellExecutor.applyImpacts(
                owner, effectCaster, target, ring.definition, finalPulse);
            }
            SpellVfxDispatcher.send(level, "zone",
                finalPulse ? ring.definition.visual.aftermath
                    : ring.definition.visual.trail,
                ring.definition.school, effectCaster.position(), effectCaster.position(),
                finalPulse ? radius * 0.45 : radius, 8, effectCaster, false);
            if (finalPulse) iterator.remove();
        }
        }

    private static void releaseOrbit(MinecraftServer server, ActiveOrbit orbit) {
        ServerLevel level = server.getLevel(orbit.dimension);
        ServerPlayer owner = server.getPlayerList().getPlayer(orbit.ownerId);
        Entity source = level == null ? null : level.getEntity(orbit.effectCasterId);
        if (level == null || owner == null || owner.level() != level
                || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()) {
            return;
        }

        double radius = Math.max(1.0, orbit.definition.targeting.radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                effectCaster.getBoundingBox().inflate(radius),
                target -> SpellTargetingRules.canHarm(owner, effectCaster, target)
                        && target.distanceToSqr(effectCaster) <= radius * radius);
        targets.sort((left, right) -> Double.compare(
                left.distanceToSqr(effectCaster), right.distanceToSqr(effectCaster)));
        if (orbit.definition.targeting.max_targets > 0
                && targets.size() > orbit.definition.targeting.max_targets) {
            targets = targets.subList(0, orbit.definition.targeting.max_targets);
        }
        for (LivingEntity target : targets) {
            SpellExecutor.applyTargetImpacts(owner, effectCaster, target, orbit.definition);
        }
        SpellVfxDispatcher.send(level, "impact", orbit.definition.visual.impact,
                orbit.definition.school, effectCaster.position(), effectCaster.position(),
                radius, 12, effectCaster, false);
    }

    private static void tickBarrierWalls(MinecraftServer server) {
        Iterator<BarrierWall> iterator = BARRIER_WALLS.iterator();
        while (iterator.hasNext()) {
            BarrierWall wall = iterator.next();
            ServerLevel level = server.getLevel(wall.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(wall.ownerId);
            Entity source = level == null ? null : level.getEntity(wall.effectCasterId);
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity living) || !living.isAlive()
                    || --wall.remainingTicks < 0) {
                iterator.remove();
                continue;
            }

            AABB bounds = new AABB(
                    wall.center.x - wall.halfWidth, wall.center.y - wall.halfHeight,
                    wall.center.z - wall.halfWidth, wall.center.x + wall.halfWidth,
                    wall.center.y + wall.halfHeight, wall.center.z + wall.halfWidth)
                    .inflate(0.5);
            for (Projectile projectile : level.getEntitiesOfClass(
                    Projectile.class, bounds, Entity::isAlive)) {
                Entity projectileOwner = projectile.getOwner();
                if (projectileOwner instanceof LivingEntity livingOwner
                        && SpellTargetingRules.isProtectedAlly(
                            owner, living, livingOwner)) continue;

                Vec3 end = projectile.position();
                Vec3 start = end.subtract(projectile.getDeltaMovement());
                Vec3 intersection = intersectBarrier(wall, start, end);
                if (intersection == null) continue;

                projectile.discard();
                sendBarrierImpact(level, new BarrierHit(wall, intersection), living);
            }
        }
    }

    private static BarrierHit findBarrierIntersection(ServerLevel level, Vec3 start, Vec3 end,
                                                      LivingEntity protectedTarget) {
        for (BarrierWall wall : BARRIER_WALLS) {
            if (!wall.dimension.equals(level.dimension())) continue;
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(wall.ownerId);
            Entity source = level.getEntity(wall.effectCasterId);
            if (owner == null || !(source instanceof LivingEntity effectCaster)
                    || !SpellTargetingRules.isProtectedAlly(
                            owner, effectCaster, protectedTarget)) continue;
                Vec3 intersection = intersectBarrier(wall, start, end);
                if (intersection != null) return new BarrierHit(wall, intersection);
        }
        return null;
    }

            private static Vec3 intersectBarrier(BarrierWall wall, Vec3 start, Vec3 end) {
            double startSide = start.subtract(wall.center).dot(wall.normal);
            double endSide = end.subtract(wall.center).dot(wall.normal);
            if (startSide * endSide > 0.0) return null;
            double denominator = startSide - endSide;
            if (Math.abs(denominator) < 1.0E-6) return null;
            double progress = startSide / denominator;
            if (progress < 0.0 || progress > 1.0) return null;
            Vec3 intersection = start.lerp(end, progress);
            Vec3 relative = intersection.subtract(wall.center);
            return Math.abs(relative.dot(wall.right)) <= wall.halfWidth
                && Math.abs(relative.y) <= wall.halfHeight ? intersection : null;
            }

            private static void sendBarrierImpact(ServerLevel level, BarrierHit barrierHit,
                              Entity anchor) {
            SpellDefinition definition = barrierHit.wall.definition;
            SpellVfxDispatcher.send(level, "impact", definition.visual.impact,
                definition.school, barrierHit.position, barrierHit.position, 1.0,
                10, anchor, true);
            CobblemonUltimateVfx.sendProtectBlock(level, barrierHit.position);
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
                iterator.remove();
                SpellExecutor.applyImpacts(owner, effectCaster, target, delayed.definition);
                if (delayed.recordedDamage > 0.0
                    && SpellTargetingRules.canHarm(owner, effectCaster, target)) {
                    target.invulnerableTime = 0;
                    SpellImpactApplier.hurtAttributedToOwner(
                            owner, effectCaster, target, (float) delayed.recordedDamage);
                }
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1.0,
                        target.getZ(), 35, 0.5, 0.8, 0.5, 0.08);
            }
        }
    }

    /** Fraction of the trace between consecutive blasts, and ticks between them. */
    private static final double AFTERSHOCK_SPACING = 0.28;
    private static final int AFTERSHOCK_DELAY_TICKS = 3;

    /**
     * Schedules a beam's trailing blasts ("Terminal Line"). Blast {@code i} lands at
     * {@code (i+1) * AFTERSHOCK_SPACING} along the trace after
     * {@code (i+1) * AFTERSHOCK_DELAY_TICKS} ticks. Those two constants MUST stay in step
     * with the client's {@code ProgrammaticSpellFx#hyperBeamAftershock} calls (0.28/0.56/0.84
     * at 3/6/9 ticks) - otherwise the damage lands somewhere the player never saw a flash.
     * The validator pins them together.
     */
    public static void startAftershocks(ServerPlayer owner, LivingEntity effectCaster,
                                        SpellDefinition definition, Vec3 origin, Vec3 end) {
        int count = definition.delivery.aftershock_count;
        if (count <= 0 || definition.targeting.radius <= 0.0) return;
        Vec3 trace = end.subtract(origin);
        for (int index = 0; index < count; index++) {
            double progress = (index + 1) * AFTERSHOCK_SPACING;
            AFTERSHOCKS.add(new Aftershock(effectCaster.level().dimension(),
                    owner.getUUID(), effectCaster.getUUID(),
                    origin.add(trace.scale(progress)), definition,
                    (index + 1) * AFTERSHOCK_DELAY_TICKS));
        }
    }

    private static void tickAftershocks(MinecraftServer server) {
        Iterator<Aftershock> iterator = AFTERSHOCKS.iterator();
        while (iterator.hasNext()) {
            Aftershock blast = iterator.next();
            ServerLevel level = server.getLevel(blast.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(blast.ownerId);
            Entity source = level == null ? null : level.getEntity(blast.effectCasterId);
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster)
                    || !effectCaster.isAlive()) {
                iterator.remove();
                continue;
            }
            if (--blast.remainingTicks > 0) continue;

            SpellExecutor.applyProjectileSplashAt(owner, effectCaster, blast.center,
                    blast.definition,
                    blast.definition.delivery.aftershock_damage_multiplier, 1.0);
            SpellVfxDispatcher.send(level, "impact", blast.definition.visual.impact,
                    blast.definition.school, blast.center, blast.center,
                    blast.definition.targeting.radius, 8, effectCaster, false);
            iterator.remove();
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
                level.sendParticles(runtimeParticle(area.definition.school),
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
            if ("lightning".equals(area.definition.school)) {
                LightningBolt lightning = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                lightning.moveTo(area.center.x, area.center.y, area.center.z);
                lightning.setVisualOnly(true);
                level.addFreshEntity(lightning);
            }
            if (CobblemonThunderVfx.isThunder(area.definition)) {
                CobblemonThunderVfx.sendImpact(level, area.center);
            }
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
                level.sendParticles(runtimeParticle(zone.definition.school),
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
                if ("earthquake_fissure".equals(zone.definition.visual.impact)) {
                    CobblemonUltimateVfx.sendEarthquakePulse(level, zone.center);
                }
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
                    target.invulnerableTime = 0;
                    SpellExecutor.applyImpacts(owner, effectCaster, target,
                            zone.definition, zone.remainingTicks == 0);
                }
            }
            if (zone.remainingTicks == 0) iterator.remove();
        }
    }

    private static net.minecraft.core.particles.ParticleOptions runtimeParticle(String school) {
        return switch (school) {
            case "fire" -> ParticleTypes.FLAME;
            case "lightning" -> ParticleTypes.ELECTRIC_SPARK;
            case "water" -> ParticleTypes.SPLASH;
            case "poison" -> ParticleTypes.WITCH;
            case "nature" -> ParticleTypes.COMPOSTER;
            case "shadow" -> ParticleTypes.PORTAL;
            case "psychic" -> ParticleTypes.ENCHANT;
            case "dragon" -> ParticleTypes.DRAGON_BREATH;
            case "earth" -> ParticleTypes.POOF;
            case "wind" -> ParticleTypes.CLOUD;
            case "fairy" -> ParticleTypes.END_ROD;
            case "steel", "physical" -> ParticleTypes.CRIT;
            default -> ParticleTypes.SNOWFLAKE;
        };
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

            double visualWidth = wave.definition.targeting.width > 0.0
                ? wave.definition.targeting.width : 3.0;
            boolean surf = "surf_wave".equals(wave.definition.visual.aftermath);
            double collisionHalfWidth = surf ? visualWidth * 0.5 : visualWidth;
            double radius = wave.definition.targeting.radius > 0.0
                ? wave.definition.targeting.radius : 1.5;
            level.sendParticles(runtimeParticle(wave.definition.school),
                wave.center.x, wave.center.y + 0.8, wave.center.z,
                18, collisionHalfWidth * 0.9, 0.8, collisionHalfWidth * 0.9, 0.08);
            level.sendParticles("water".equals(wave.definition.school)
                    ? ParticleTypes.BUBBLE : ParticleTypes.POOF,
                wave.center.x, wave.center.y + 0.45, wave.center.z,
                8, collisionHalfWidth * 0.8, 0.45, collisionHalfWidth * 0.8, 0.04);
            if (wave.remainingTicks % 4 == 0) {
            String waveStyle = wave.definition.visual.aftermath == null
                    || wave.definition.visual.aftermath.isBlank()
                    ? wave.definition.visual.trail : wave.definition.visual.aftermath;
            SpellVfxDispatcher.send(level, "wave", waveStyle,
                wave.definition.school, wave.center,
                wave.center.add(wave.direction.scale(2.0)), visualWidth,
                6, effectCaster, false);
            }

            AABB area = new AABB(wave.center, wave.center)
                    .inflate(collisionHalfWidth, radius, collisionHalfWidth);
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
            level.sendParticles(runtimeParticle(trap.definition.school),
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
            if (trap.definition.impact.isEmpty()) {
                int triggerCount = trap.triggerCounts.merge(
                        target.getUUID(), 1, Integer::sum);
                if (triggerCount == 1) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON,
                            120, 0, false, true, true));
                } else {
                    target.addEffect(new MobEffectInstance(TensuraMobEffects.TOXIC,
                            200, Math.min(2, triggerCount - 2), false, true, true));
                }
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
            int hitIndex = Math.max(1, combo.definition.delivery.combo_hits)
                    - combo.remainingHits;
            if ("combat_impact".equals(combo.definition.visual.impact)) {
                CobblemonUltimateVfx.sendCloseCombatHit(level, target, hitIndex, finalHit);
            } else if ("x_scissor_impact".equals(combo.definition.visual.impact)) {
                moveForCrossingStrike(effectCaster, target, hitIndex);
                CobblemonUltimateVfx.sendXScissorHit(level, target, hitIndex);
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

    private static void moveForCrossingStrike(LivingEntity caster, LivingEntity target,
                                              int hitIndex) {
        Vec3 targetForward = target.getLookAngle();
        targetForward = new Vec3(targetForward.x, 0.0, targetForward.z);
        if (targetForward.lengthSqr() < 1.0E-6) {
            targetForward = target.position().subtract(caster.position());
        }
        targetForward = new Vec3(targetForward.x, 0.0, targetForward.z).normalize();
        Vec3 right = new Vec3(-targetForward.z, 0.0, targetForward.x);
        double side = hitIndex == 0 ? 1.8 : -1.8;
        double depth = hitIndex == 0 ? -0.7 : 0.7;
        Vec3 destination = target.position()
                .add(right.scale(side)).add(targetForward.scale(depth));
        AABB destinationBox = caster.getBoundingBox()
                .move(destination.subtract(caster.position()));
        if (!caster.level().noCollision(caster, destinationBox)) return;
        caster.teleportTo(destination.x, destination.y, destination.z);
        caster.hurtMarked = true;
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
                if ("tailwind_aura".equals(aura.definition.visual.aftermath)) {
                    applyTailwind(level, owner, effectCaster, aura.definition, radius);
                    continue;
                }
                for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class,
                        effectCaster.getBoundingBox().inflate(radius),
                        entity -> SpellTargetingRules.isProtectedAlly(owner, effectCaster, entity))) {
                    SpellExecutor.applyImpacts(owner, effectCaster, ally, aura.definition);
                }
            }
            if (aura.remainingTicks == 0) iterator.remove();
        }
    }

    private static void applyTailwind(ServerLevel level, ServerPlayer owner,
                                      LivingEntity effectCaster,
                                      SpellDefinition definition, double range) {
        Vec3 look = effectCaster.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        if (forward.lengthSqr() < 1.0E-6) return;
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        sendTailwindRibbon(level, effectCaster, definition, range);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                effectCaster.getBoundingBox().inflate(range))) {
            Vec3 offset = entity.position().subtract(effectCaster.position());
            double longitudinal = offset.dot(forward);
            double lateral = Math.abs(offset.dot(right));
            if (longitudinal > 1.5 || longitudinal < -range || lateral > 2.5) continue;
            Vec3 movement = entity.getDeltaMovement();
            double alongWind = new Vec3(movement.x, 0.0, movement.z).dot(forward);
            if (SpellTargetingRules.isProtectedAlly(owner, effectCaster, entity)) {
                if (alongWind > 0.01) {
                    SpellExecutor.applyImpacts(owner, effectCaster, entity, definition);
                }
            } else if (SpellTargetingRules.canHarm(owner, effectCaster, entity)
                    && alongWind < -0.01) {
                entity.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, true, true));
            }
        }
    }

    private static void sendTailwindRibbon(ServerLevel level, LivingEntity effectCaster,
                                           SpellDefinition definition, double range) {
        Vec3 look = effectCaster.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        if (forward.lengthSqr() < 1.0E-6) return;
        Vec3 origin = effectCaster.position().add(0.0, 0.8, 0.0);
        Vec3 end = origin.subtract(forward.normalize().scale(Math.max(1.0, range)));
        SpellVfxDispatcher.send(level, "ribbon", definition.visual.trail,
                definition.school, origin, end, 2.5, 22,
                effectCaster, true);
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

    private static void recordProtectiveAuraDamage(MinecraftServer server, LivingEntity victim,
                                                    float preventedDamage) {
        ProtectiveAura selected = null;
        double selectedReduction = 0.0;
        for (ProtectiveAura aura : PROTECTIVE_AURAS) {
            if (!"aurora_curtain".equals(aura.definition.visual.aftermath)) continue;
            ServerLevel level = server.getLevel(aura.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(aura.ownerId);
            Entity source = level == null ? null : level.getEntity(aura.effectCasterId);
            if (level == null || victim.level() != level || owner == null
                    || !(source instanceof LivingEntity effectCaster)
                    || !SpellTargetingRules.isProtectedAlly(owner, effectCaster, victim)) continue;
            double radius = aura.definition.targeting.radius > 0.0
                    ? aura.definition.targeting.radius : 5.0;
            if (victim.distanceToSqr(effectCaster) > radius * radius) continue;
            double reduction = aura.definition.impact.stream()
                    .filter(impact -> "damage_reduction".equals(impact.type))
                    .mapToDouble(impact -> impact.reduction).max().orElse(0.0);
            if (reduction > selectedReduction) {
                selected = aura;
                selectedReduction = reduction;
            }
        }
        if (selected == null || preventedDamage <= 0.0F) return;

        selected.preventedSinceBreak += preventedDamage;
        while (selected.segments > 0 && selected.preventedSinceBreak >= 8.0F) {
            selected.preventedSinceBreak -= 8.0F;
            selected.segments--;
            ServerLevel level = server.getLevel(selected.dimension);
            Entity source = level == null ? null : level.getEntity(selected.effectCasterId);
            if (level != null && source instanceof LivingEntity effectCaster) {
                CobblemonUltimateVfx.sendAuroraFracture(level, effectCaster.position());
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        effectCaster.getX(), effectCaster.getY() + 1.0, effectCaster.getZ(),
                        24, 1.2, 0.7, 1.2, 0.05);
            }
        }
        if (selected.segments == 0) PROTECTIVE_AURAS.remove(selected);
    }

    private static void tickLeechSeeds(MinecraftServer server) {
        Iterator<ActiveLeechSeed> iterator = LEECH_SEEDS.iterator();
        while (iterator.hasNext()) {
            ActiveLeechSeed seed = iterator.next();
            ServerLevel level = server.getLevel(seed.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(seed.ownerId);
            Entity source = level == null ? null : level.getEntity(seed.effectCasterId);
            Entity targetEntity = level == null ? null : level.getEntity(seed.targetId);
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                    || !(targetEntity instanceof LivingEntity target) || !target.isAlive()
                    || !SpellTargetingRules.canHarm(owner, effectCaster, target)
                    || --seed.remainingTicks < 0) {
                iterator.remove();
                continue;
            }
            if (seed.remainingTicks % 20 == 0) {
                float healthBefore = target.getHealth();
                SpellImpactApplier.hurtAttributedToOwner(owner, effectCaster, target,
                        (float) seed.amountPerTick);
                effectCaster.heal(Math.max(0.0F, healthBefore - target.getHealth()));
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        target.getX(), target.getY() + target.getBbHeight() * 0.6,
                        target.getZ(), 6, 0.25, 0.35, 0.25, 0.02);
                SpellVfxDispatcher.send(level, "beam", "leech_seed_drain", "nature",
                        target.getBoundingBox().getCenter(),
                        effectCaster.getBoundingBox().getCenter(),
                        0.25, 8, effectCaster, false);
            }
            if (seed.remainingTicks == 0) iterator.remove();
        }
    }

    private static void tickDashCombos(MinecraftServer server) {
        Iterator<ActiveDashCombo> iterator = DASH_COMBOS.iterator();
        while (iterator.hasNext()) {
            ActiveDashCombo combo = iterator.next();
            ServerLevel level = server.getLevel(combo.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(combo.ownerId);
            Entity source = level == null ? null : level.getEntity(combo.effectCasterId);
            Entity targetEntity = level == null || combo.targetId == null
                    ? null : level.getEntity(combo.targetId);
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()) {
                iterator.remove();
                continue;
            }
            if (combo.ticksUntilDash-- > 0) continue;
            if (combo.remainingDashes <= 0) {
                effectCaster.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                        Math.max(1, combo.definition.delivery.recovery_ticks),
                        0, false, true, true));
                iterator.remove();
                continue;
            }
            boolean started;
                int dashIndex = Math.max(1, combo.definition.delivery.combo_hits)
                    - combo.remainingDashes;
                double collisionBonus = "outrage_dash".equals(combo.definition.visual.trail)
                    ? dashIndex * 0.25 : 0.0;
            if (effectCaster instanceof ServerPlayer player) {
                started = SpellMovementController.startDash(
                    player, combo.definition, collisionBonus);
            } else if (effectCaster instanceof PokemonEntity companion
                    && targetEntity instanceof LivingEntity target && target.isAlive()) {
                started = SpellMovementController.startDash(
                    owner, companion, target, combo.definition, collisionBonus);
            } else {
                started = false;
            }
            if (!started) {
                iterator.remove();
                continue;
            }
            combo.remainingDashes--;
            combo.ticksUntilDash = Math.max(1, combo.definition.delivery.combo_interval_ticks);
        }
    }

    private static void tickDelayedTeleportStrikes(MinecraftServer server) {
        Iterator<DelayedTeleportStrike> iterator = DELAYED_TELEPORT_STRIKES.iterator();
        while (iterator.hasNext()) {
            DelayedTeleportStrike strike = iterator.next();
            ServerLevel level = server.getLevel(strike.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(strike.ownerId);
            Entity source = level == null ? null : level.getEntity(strike.effectCasterId);
            Entity targetEntity = level == null ? null : level.getEntity(strike.targetId);
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                    || !(targetEntity instanceof LivingEntity target) || !target.isAlive()
                    || !SpellTargetingRules.canHarm(owner, effectCaster, target)) {
                if (source instanceof LivingEntity living) {
                    clearTeleportStrikeInvisibility(living, strike);
                }
                iterator.remove();
                continue;
            }
            if (--strike.remainingTicks > 0) continue;
            clearTeleportStrikeInvisibility(effectCaster, strike);
            SpellExecutor.castRuntimeTeleportStrike(
                    owner, effectCaster, target, strike.definition);
            iterator.remove();
        }
    }

    private static void clearTeleportStrikeInvisibility(
            LivingEntity effectCaster, DelayedTeleportStrike strike) {
        MobEffectInstance invisibility = effectCaster.getEffect(MobEffects.INVISIBILITY);
        if (strike.removeInvisibilityOnFinish && invisibility != null
                && invisibility.getAmplifier() == 0
                && invisibility.getDuration() <= strike.remainingTicks + 1) {
            effectCaster.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    private static void tickZones(MinecraftServer server) {
        Iterator<ActiveZone> iterator = ZONES.iterator();
        while (iterator.hasNext()) {
            ActiveZone zone = iterator.next();
            ServerLevel level = server.getLevel(zone.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(zone.ownerId);
            Entity source = level == null ? null : level.getEntity(zone.effectCasterId);
            if (level == null || owner == null || owner.level() != level
                    || !(source instanceof LivingEntity effectCaster) || !effectCaster.isAlive()
                    || --zone.remainingTicks < 0) {
                iterator.remove();
                continue;
            }
            double radius = zone.definition.targeting.radius > 0.0
                    ? zone.definition.targeting.radius : 6.0;
            if (zone.remainingTicks % 5 == 0) {
                level.sendParticles(ParticleTypes.ENCHANT,
                        zone.center.x, zone.center.y + 1.0, zone.center.z,
                        10, radius * 0.7, 1.5, radius * 0.7, 0.02);
            }
            int interval = Math.max(1, zone.definition.delivery.tick_interval_ticks);
            if (zone.remainingTicks % interval == 0) {
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(zone.center, zone.center).inflate(radius, 3.0, radius),
                        LivingEntity::isAlive)) {
                    SpellExecutor.applyImpacts(owner, effectCaster, target, zone.definition);
                }
            }
            if (zone.remainingTicks > 0 && zone.remainingTicks % 20 == 0) {
                SpellExecutor.playLoopSound(effectCaster, zone.definition);
            }
            if (zone.remainingTicks == 0) iterator.remove();
        }
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
        private double recordedDamage;
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

    private static class Aftershock {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final Vec3 center;
        private final SpellDefinition definition;
        private int remainingTicks;

        private Aftershock(ResourceKey<Level> dimension, UUID ownerId,
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
        private int segments;
        private float preventedSinceBreak;
        private int remainingTicks;

        private ProtectiveAura(ResourceKey<Level> dimension, UUID ownerId,
                               UUID effectCasterId, SpellDefinition definition,
                               int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.definition = definition;
            this.segments = "aurora_curtain".equals(definition.visual.aftermath) ? 3 : 1;
            this.remainingTicks = remainingTicks;
        }
    }

    private static class ActiveLeechSeed {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final UUID targetId;
        private int remainingTicks;
        private final double amountPerTick;

        private ActiveLeechSeed(ResourceKey<Level> dimension, UUID ownerId,
                                UUID effectCasterId, UUID targetId,
                                int remainingTicks, double amountPerTick) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.targetId = targetId;
            this.remainingTicks = remainingTicks;
            this.amountPerTick = amountPerTick;
        }
    }

    private static class ActiveDashCombo {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final UUID targetId;
        private final SpellDefinition definition;
        private int remainingDashes;
        private int ticksUntilDash;

        private ActiveDashCombo(ResourceKey<Level> dimension, UUID ownerId,
                                UUID effectCasterId, UUID targetId,
                                SpellDefinition definition, int remainingDashes) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.targetId = targetId;
            this.definition = definition;
            this.remainingDashes = remainingDashes;
        }
    }

    private static class DelayedTeleportStrike {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final UUID targetId;
        private final SpellDefinition definition;
        private final boolean removeInvisibilityOnFinish;
        private int remainingTicks;

        private DelayedTeleportStrike(ResourceKey<Level> dimension, UUID ownerId,
                                      UUID effectCasterId, UUID targetId,
                                      SpellDefinition definition, int remainingTicks,
                                      boolean removeInvisibilityOnFinish) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.targetId = targetId;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
            this.removeInvisibilityOnFinish = removeInvisibilityOnFinish;
        }
    }

    private static class ActiveZone {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final Vec3 center;
        private final SpellDefinition definition;
        private int remainingTicks;

        private ActiveZone(ResourceKey<Level> dimension, UUID ownerId,
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

    private static class ContactAura {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final SpellDefinition definition;
        private final Set<UUID> hitEntities = new HashSet<>();
        private int remainingTicks;

        private ContactAura(ResourceKey<Level> dimension, UUID ownerId,
                            UUID effectCasterId, SpellDefinition definition,
                            int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private static class ActiveOrbit {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final SpellDefinition definition;
        private int remainingTicks;

        private ActiveOrbit(ResourceKey<Level> dimension, UUID ownerId,
                            UUID effectCasterId, SpellDefinition definition,
                            int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private static class ActivePulseRing {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final SpellDefinition definition;
        private int remainingTicks;

        private ActivePulseRing(ResourceKey<Level> dimension, UUID ownerId,
                                UUID effectCasterId, SpellDefinition definition,
                                int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private static class BarrierWall {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final UUID effectCasterId;
        private final Vec3 center;
        private final Vec3 normal;
        private final Vec3 right;
        private final double halfWidth;
        private final double halfHeight;
        private final SpellDefinition definition;
        private int remainingTicks;

        private BarrierWall(ResourceKey<Level> dimension, UUID ownerId,
                            UUID effectCasterId, Vec3 center, Vec3 normal, Vec3 right,
                            double halfWidth, double halfHeight,
                            SpellDefinition definition, int remainingTicks) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.effectCasterId = effectCasterId;
            this.center = center;
            this.normal = normal;
            this.right = right;
            this.halfWidth = halfWidth;
            this.halfHeight = halfHeight;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private record BarrierHit(BarrierWall wall, Vec3 position) {}

    private record ActiveCounter(UUID ownerId, SpellDefinition definition, long expiresAt) {}

    private static class GuardState {
        private final ResourceKey<Level> dimension;
        private final long expiresAt;
        private final float segmentSize;
        private final int maximumSegments;
        private float remaining;

        private GuardState(ResourceKey<Level> dimension, float remaining, long expiresAt,
                           int maximumSegments) {
            this.dimension = dimension;
            this.remaining = remaining;
            this.expiresAt = expiresAt;
            this.maximumSegments = maximumSegments;
            this.segmentSize = remaining / maximumSegments;
        }

        private int visibleSegments() {
            if (remaining <= 0.0F) return 0;
            return Math.min(maximumSegments,
                    Math.max(1, (int) Math.ceil(remaining / segmentSize)));
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

}