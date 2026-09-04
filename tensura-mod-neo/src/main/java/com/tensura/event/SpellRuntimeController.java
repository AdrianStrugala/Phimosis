package com.tensura.event;

import com.tensura.engine.SpellDefinition;
import com.tensura.engine.SpellExecutor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
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
    private static final Map<UUID, ActiveCounter> COUNTERS = new HashMap<>();
    private static final Map<UUID, GuardState> GUARDS = new HashMap<>();
    private static final Map<UUID, MeteorGroup> METEOR_GROUPS = new HashMap<>();
    private static final Map<UUID, PendingCast> PENDING_CASTS = new HashMap<>();

    public static boolean isCasting(ServerPlayer caster) {
        return PENDING_CASTS.containsKey(caster.getUUID());
    }

    public static boolean startCast(ServerPlayer caster, ResourceLocation spellId,
                                    SpellDefinition definition) {
        if (PENDING_CASTS.containsKey(caster.getUUID())) return false;
        PENDING_CASTS.put(caster.getUUID(), new PendingCast(spellId, definition,
                caster.level().getGameTime() + Math.max(1, definition.cast_time_ticks)));
        return true;
    }

    public static boolean startVortex(ServerPlayer caster, SpellDefinition definition, Vec3 center) {
        int duration = Math.max(1, definition.delivery.duration_ticks);
        VORTEXES.add(new ActiveVortex(caster.level().dimension(), caster.getUUID(), center,
                definition, duration));
        return true;
    }

    public static boolean startDelayed(ServerPlayer caster, LivingEntity target, SpellDefinition definition) {
        int delay = Math.max(1, definition.delivery.delay_ticks);
        DELAYED_HITS.add(new DelayedHit(caster.level().dimension(), caster.getUUID(),
                target.getUUID(), definition, delay));
        return true;
    }

    public static boolean startCounter(ServerPlayer caster, SpellDefinition definition) {
        int duration = Math.max(1, definition.delivery.duration_ticks);
        COUNTERS.put(caster.getUUID(), new ActiveCounter(definition,
                caster.level().getGameTime() + duration));
        return true;
    }

    public static void addGuard(LivingEntity target, double amount, int durationTicks) {
        if (amount <= 0.0 || durationTicks <= 0) return;
        GUARDS.put(target.getUUID(), new GuardState(target.level().dimension(), (float) amount,
                target.level().getGameTime() + durationTicks));
    }

    public static UUID createMeteorGroup(ServerPlayer caster, int lifetimeTicks) {
        UUID groupId = UUID.randomUUID();
        METEOR_GROUPS.put(groupId, new MeteorGroup(
                caster.level().getGameTime() + Math.max(20, lifetimeTicks)));
        return groupId;
    }

    public static void applyMeteorImpact(ServerPlayer caster, SpellDefinition definition,
                                         Vec3 position, UUID groupId) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        MeteorGroup group = METEOR_GROUPS.get(groupId);
        if (group == null) return;

        double radius = definition.targeting.radius > 0.0 ? definition.targeting.radius : 3.0;
        AABB area = new AABB(position, position).inflate(radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
            entity -> entity != caster && entity.isAlive()
                && entity.position().distanceToSqr(position) <= radius * radius);
        targets.sort((left, right) -> Double.compare(
            left.position().distanceToSqr(position), right.position().distanceToSqr(position)));
        for (LivingEntity target : targets) {
            if (definition.targeting.max_targets > 0
                && group.hitEntities.size() >= definition.targeting.max_targets) break;
            if (group.hitEntities.add(target.getUUID())) {
                SpellExecutor.applyImpacts(caster, target, definition);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, position.x, position.y, position.z,
                4, radius * 0.35, 0.3, radius * 0.35, 0.05);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, position.x, position.y, position.z,
                30, radius * 0.5, 0.5, radius * 0.5, 0.08);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickVortexes(event.getServer());
        tickDelayedHits(event.getServer());
        tickGuards(event.getServer());
        tickPendingCasts(event.getServer());

        long now = event.getServer().overworld().getGameTime();
        COUNTERS.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        METEOR_GROUPS.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }

    @SubscribeEvent
    public void onLivingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();

        ActiveCounter counter = COUNTERS.get(victim.getUUID());
        if (counter != null && victim instanceof ServerPlayer caster
                && sourceEntity instanceof LivingEntity attacker && attacker != victim
                && caster.distanceTo(attacker) <= counter.definition.targeting.range) {
            COUNTERS.remove(victim.getUUID());
            event.setCanceled(true);
            SpellExecutor.applyImpacts(caster, attacker, counter.definition);
            if (caster.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SMOKE, attacker.getX(), attacker.getY() + 1.0,
                        attacker.getZ(), 15, 0.3, 0.5, 0.3, 0.04);
            }
            return;
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

    private static void tickVortexes(MinecraftServer server) {
        Iterator<ActiveVortex> iterator = VORTEXES.iterator();
        while (iterator.hasNext()) {
            ActiveVortex vortex = iterator.next();
            ServerLevel level = server.getLevel(vortex.dimension);
            ServerPlayer caster = server.getPlayerList().getPlayer(vortex.casterId);
            if (level == null || caster == null || caster.level() != level || --vortex.remainingTicks < 0) {
                iterator.remove();
                continue;
            }

            double radius = vortex.definition.targeting.radius > 0.0
                    ? vortex.definition.targeting.radius : 4.0;
            double angle = vortex.remainingTicks * 0.35;
            for (int index = 0; index < 8; index++) {
                double particleAngle = angle + index * Math.PI / 4.0;
                double particleRadius = radius * (0.35 + (index % 3) * 0.25);
                level.sendParticles(ParticleTypes.SPLASH,
                        vortex.center.x + Math.cos(particleAngle) * particleRadius,
                        vortex.center.y + 0.15 + (index % 2) * 0.35,
                        vortex.center.z + Math.sin(particleAngle) * particleRadius,
                        1, 0.05, 0.05, 0.05, 0.02);
            }

            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(vortex.center, vortex.center).inflate(radius),
                    entity -> entity != caster && entity.isAlive()
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
                    SpellExecutor.applyImpacts(caster, target, vortex.definition);
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
            ServerPlayer caster = server.getPlayerList().getPlayer(delayed.casterId);
            Entity entity = level == null ? null : level.getEntity(delayed.targetId);
            if (level == null || caster == null || caster.level() != level
                    || !(entity instanceof LivingEntity target) || !target.isAlive()) {
                iterator.remove();
                continue;
            }

            delayed.remainingTicks--;
            if (delayed.remainingTicks % 10 == 0) {
                level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + target.getBbHeight() + 0.4,
                        target.getZ(), 8, 0.35, 0.15, 0.35, 0.02);
            }
            if (delayed.remainingTicks <= 0) {
                SpellExecutor.applyImpacts(caster, target, delayed.definition);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1.0,
                        target.getZ(), 35, 0.5, 0.8, 0.5, 0.08);
                iterator.remove();
            }
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

    private static class ActiveVortex {
        private final ResourceKey<Level> dimension;
        private final UUID casterId;
        private final Vec3 center;
        private final SpellDefinition definition;
        private int remainingTicks;

        private ActiveVortex(ResourceKey<Level> dimension, UUID casterId, Vec3 center,
                             SpellDefinition definition, int remainingTicks) {
            this.dimension = dimension;
            this.casterId = casterId;
            this.center = center;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private static class DelayedHit {
        private final ResourceKey<Level> dimension;
        private final UUID casterId;
        private final UUID targetId;
        private final SpellDefinition definition;
        private int remainingTicks;

        private DelayedHit(ResourceKey<Level> dimension, UUID casterId, UUID targetId,
                           SpellDefinition definition, int remainingTicks) {
            this.dimension = dimension;
            this.casterId = casterId;
            this.targetId = targetId;
            this.definition = definition;
            this.remainingTicks = remainingTicks;
        }
    }

    private record ActiveCounter(SpellDefinition definition, long expiresAt) {}

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
        private final long expiresAt;
        private final Set<UUID> hitEntities = new HashSet<>();

        private MeteorGroup(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    private record PendingCast(ResourceLocation spellId, SpellDefinition definition, long completesAt) {}
}