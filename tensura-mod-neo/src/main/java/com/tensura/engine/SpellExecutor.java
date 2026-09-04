package com.tensura.engine;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.entity.SpellProjectile;
import com.tensura.event.SpellMovementController;
import com.tensura.event.SpellRuntimeController;
import com.tensura.network.CooldownSyncPacket;
import com.tensura.network.SpellVfxDispatcher;
import com.tensura.registry.TensuraMobEffects;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpellExecutor {

    // cooldownMap: playerUUID -> (spellId -> gameTime when ready)
    private static final Map<UUID, Map<ResourceLocation, Long>> cooldowns = new HashMap<>();
    private static final Map<UUID, Map<ResourceLocation, ChargeState>> charges = new HashMap<>();

    public static void clearPlayerState(UUID playerId) {
        cooldowns.remove(playerId);
        charges.remove(playerId);
    }

    public static void clearAllState() {
        cooldowns.clear();
        charges.clear();
    }

    public static boolean cast(ServerPlayer caster, ResourceLocation spellId) {
        if (caster.hasEffect(TensuraMobEffects.ASLEEP)
            || caster.hasEffect(TensuraMobEffects.FROZEN)
            || caster.hasEffect(TensuraMobEffects.EXHAUSTED)) {
            caster.sendSystemMessage(Component.literal("§7[You cannot cast right now]"));
            return false;
        }
        if (SpellRuntimeController.isCasting(caster)) {
            caster.sendSystemMessage(Component.literal("§7[Already casting]"));
            return false;
        }

        SpellDefinition def = SpellRegistry.get(spellId).orElse(null);
        if (def == null) {
            caster.sendSystemMessage(Component.literal("Unknown spell: " + spellId));
            return false;
        }

        long now = caster.level().getGameTime();
        Map<ResourceLocation, Long> playerCooldowns = cooldowns.computeIfAbsent(caster.getUUID(), k -> new HashMap<>());
        ChargeState chargeState = null;
        if (def.charges > 1) {
            int recoveryTicks = def.charge_recovery_ticks > 0
                    ? def.charge_recovery_ticks : def.cooldown_ticks;
            Map<ResourceLocation, ChargeState> playerCharges = charges.computeIfAbsent(
                    caster.getUUID(), ignored -> new HashMap<>());
            chargeState = playerCharges.computeIfAbsent(spellId,
                    ignored -> new ChargeState(def.charges));
            chargeState.refresh(now, def.charges, recoveryTicks);
            if (chargeState.available <= 0) {
                long remaining = Math.max(1, chargeState.nextChargeAt - now) / 20 + 1;
                caster.sendSystemMessage(Component.literal("§7[Next charge: " + remaining + "s]"));
                return false;
            }
        } else {
            long ready = playerCooldowns.getOrDefault(spellId, 0L);
            if (now < ready) {
                long remaining = (ready - now) / 20 + 1;
                caster.sendSystemMessage(Component.literal("§7[Cooldown: " + remaining + "s]"));
                return false;
            }
        }
        boolean started = def.cast_time_ticks > 0
                ? SpellRuntimeController.startCast(caster, spellId, def)
                : executeDelivery(caster, spellId, def);
        if (!started) return false;

        caster.swing(InteractionHand.MAIN_HAND, true);
        sendCastVfx(caster, def);
        playCastSound(caster, def);
        if (chargeState != null) {
            int recoveryTicks = def.charge_recovery_ticks > 0
                ? def.charge_recovery_ticks : def.cooldown_ticks;
            chargeState.consume(now, def.charges, recoveryTicks);
            int visibleCooldown = chargeState.available > 0
                ? 0 : (int) Math.max(1, chargeState.nextChargeAt - now);
            PacketDistributor.sendToPlayer(caster, new CooldownSyncPacket(spellId, visibleCooldown));
        } else {
            playerCooldowns.put(spellId, now + def.cooldown_ticks);
            PacketDistributor.sendToPlayer(caster, new CooldownSyncPacket(spellId, def.cooldown_ticks));
        }

        return true;
    }

    public static boolean executeDelivery(ServerPlayer caster, ResourceLocation spellId, SpellDefinition def) {
        return switch (def.delivery.type) {
            case "dash" -> SpellMovementController.startDash(caster, def);
            case "vortex" -> castVortex(caster, def);
            case "delayed" -> castDelayed(caster, def);
            case "delayed_area" -> castDelayedArea(caster, def);
            case "moving_zone" -> castMovingZone(caster, def);
            case "protective_aura" -> SpellRuntimeController.startProtectiveAura(caster, caster, def);
            case "counter" -> SpellRuntimeController.startCounter(caster, def);
            case "channel_beam" -> SpellRuntimeController.startChannelBeam(caster, def);
                case "channel_cone" -> SpellRuntimeController.startChannelCone(caster, caster, def);
                case "wave" -> castWave(caster, caster, def);
                case "trap" -> castTrap(caster, caster, def, resolveAimPosition(caster, def.targeting.range));
                case "melee_combo" -> castMeleeCombo(caster, caster, rayCast(caster, def.targeting.range), def);
                case "teleport_strike" -> castTeleportStrike(caster, caster,
                    rayCast(caster, def.targeting.range), def);
                case "ricochet_beam" -> castRicochetBeam(caster, caster, null, def);
            case "beam" -> { castBeam(caster, def); yield true; }
            case "meteor" -> { castMeteor(caster, spellId, def); yield true; }
            case "cloud" -> { castCloud(caster, def); yield true; }
            case "projectile" -> { castProjectile(caster, def, spellId); yield true; }
            case "explosion" -> { castExplosion(caster, def); yield true; }
            case "instant", "self" -> { castStandard(caster, def); yield true; }
            default -> { castStandard(caster, def); yield true; }
        };
    }

    /**
     * Called by CompanionSpellGoal. Uses companion's position as origin,
     * directly hits the known target — no raycast needed.
     * Cooldown keyed on owner UUID with a companion-specific suffix to not share with player's own spells.
     */
    public static void castAsCompanion(ServerPlayer owner, PokemonEntity companion,
                                        ResourceLocation spellId, SpellDefinition def, LivingEntity target) {
        if (!(companion.level() instanceof ServerLevel serverLevel)) return;
        if (companion.hasEffect(TensuraMobEffects.ASLEEP)
                || companion.hasEffect(TensuraMobEffects.FROZEN)
                || companion.hasEffect(TensuraMobEffects.EXHAUSTED)) return;
        if (!"self".equals(def.targeting.type)
            && !SpellTargetingRules.canHarm(owner, companion, target)) return;

        ResourceLocation companionSpellKey = ResourceLocation.fromNamespaceAndPath(
                spellId.getNamespace(), "companion_" + spellId.getPath());
        long now = companion.level().getGameTime();
        Map<ResourceLocation, Long> ownerCooldowns = cooldowns.computeIfAbsent(owner.getUUID(), k -> new HashMap<>());
        if (now < ownerCooldowns.getOrDefault(companionSpellKey, 0L)) return;

        boolean started = def.cast_time_ticks > 0
                ? SpellRuntimeController.startCompanionCast(owner, companion, target, spellId, def)
                : executeCompanionDelivery(owner, companion, target, spellId, def);
        if (!started) return;

        ownerCooldowns.put(companionSpellKey, now + def.cooldown_ticks);
        sendCastVfx(companion, def);
        serverLevel.sendParticles(schoolParticle(def.school),
                companion.getX(), companion.getY() + 1, companion.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
        playCastSound(companion, def);
    }

    public static boolean executeCompanionDelivery(ServerPlayer owner, PokemonEntity companion,
                                                    LivingEntity target, ResourceLocation spellId,
                                                    SpellDefinition def) {
        if (!companion.isAlive() || !target.isAlive()) return false;
        if (!"self".equals(def.targeting.type)
            && !SpellTargetingRules.canHarm(owner, companion, target)) return false;
        return switch (def.delivery.type) {
            case "dash" -> SpellMovementController.startDash(owner, companion, target, def);
            case "vortex" -> SpellRuntimeController.startVortex(owner, companion, def, target.position());
            case "delayed" -> SpellRuntimeController.startDelayed(owner, companion, target, def);
            case "delayed_area" -> SpellRuntimeController.startDelayedArea(
                    owner, companion, def, target.position());
            case "moving_zone" -> startCompanionMovingZone(owner, companion, target, def);
            case "protective_aura" -> SpellRuntimeController.startProtectiveAura(
                    owner, companion, def);
            case "counter" -> SpellRuntimeController.startCounter(owner, companion, def);
            case "channel_beam" -> SpellRuntimeController.startChannelBeam(
                    owner, companion, target, def);
                case "channel_cone" -> SpellRuntimeController.startChannelCone(
                    owner, companion, def);
                case "wave" -> castWave(owner, companion, def);
                case "trap" -> castTrap(owner, companion, def, target.position());
                case "melee_combo" -> castMeleeCombo(owner, companion, target, def);
                case "teleport_strike" -> castTeleportStrike(owner, companion, target, def);
                case "ricochet_beam" -> castRicochetBeam(owner, companion, target, def);
            case "beam" -> { castCompanionBeam(owner, companion, target, def); yield true; }
            case "meteor" -> { castMeteorAt(owner, companion, spellId, def, target.position()); yield true; }
            case "cloud" -> { castCompanionCloud(owner, companion, target.position(), def); yield true; }
            case "projectile" -> { castCompanionProjectile(owner, companion, target, def, spellId); yield true; }
            case "explosion" -> { castCompanionExplosion(owner, companion, def); yield true; }
            case "instant", "self" -> {
                LivingEntity impactTarget = "self".equals(def.targeting.type) ? companion : target;
                applyImpacts(owner, companion, impactTarget, def);
                yield true;
            }
            default -> {
                LivingEntity impactTarget = "self".equals(def.targeting.type) ? companion : target;
                applyImpacts(owner, companion, impactTarget, def);
                yield true;
            }
        };
    }

    // ── Projectile: flying entity like ghast fireball ────────────────────────

    private static void castProjectile(ServerPlayer caster, SpellDefinition def, ResourceLocation spellId) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;
        applySchoolVisualSelf(caster, def.school);
        int projectileCount = Math.max(1, def.delivery.projectile_count);
        UUID projectileGroup = projectileCount > 1
            ? SpellRuntimeController.createProjectileGroup(serverLevel, 100) : null;
        LivingEntity homingTarget = def.delivery.homing_strength > 0.0
            ? rayCast(caster, def.targeting.range) : null;
        for (int index = 0; index < projectileCount; index++) {
            double centeredIndex = index - (projectileCount - 1) / 2.0;
            float angle = (float) Math.toRadians(centeredIndex * def.delivery.spread_degrees);
            Vec3 direction = caster.getLookAngle().yRot(angle);
            SpellProjectile projectile = SpellProjectile.create(
                    caster, caster, spellId, def, direction, index, projectileCount,
                    projectileGroup, homingTarget);
            serverLevel.addFreshEntity(projectile);
                sendProjectileVfx(serverLevel, projectile, def, direction);
        }
    }

    private static void castCompanionProjectile(ServerPlayer owner, PokemonEntity companion,
                                                LivingEntity target, SpellDefinition def,
                                                ResourceLocation spellId) {
        if (!(companion.level() instanceof ServerLevel serverLevel)) return;
        Vec3 baseDirection = target.getBoundingBox().getCenter().subtract(companion.getEyePosition()).normalize();
        int projectileCount = Math.max(1, def.delivery.projectile_count);
        UUID projectileGroup = projectileCount > 1
            ? SpellRuntimeController.createProjectileGroup(serverLevel, 100) : null;
        for (int index = 0; index < projectileCount; index++) {
            double centeredIndex = index - (projectileCount - 1) / 2.0;
            float angle = (float) Math.toRadians(centeredIndex * def.delivery.spread_degrees);
            SpellProjectile projectile = SpellProjectile.create(owner, companion, spellId, def,
                    baseDirection.yRot(angle), index, projectileCount, projectileGroup, target);
            serverLevel.addFreshEntity(projectile);
                sendProjectileVfx(serverLevel, projectile, def, baseDirection.yRot(angle));
        }
    }

    // ── Explosion: massive AoE burst centered on caster ──────────────────────

    private static void castExplosion(ServerPlayer caster, SpellDefinition def) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;

        Vec3 pos = caster.position();
        double radius = def.targeting.range;

        // Visual: multiple explosion layers
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1, pos.z, 8, radius * 0.4, 0.5, radius * 0.4, 0.1);
        serverLevel.sendParticles(ParticleTypes.FLAME,     pos.x, pos.y + 1, pos.z, 80, radius * 0.5, 1.0, radius * 0.5, 0.15);
        serverLevel.sendParticles(ParticleTypes.LAVA,      pos.x, pos.y + 1, pos.z, 30, radius * 0.3, 0.5, radius * 0.3, 0.1);

        // Lightning strikes around caster for extra drama
        for (int i = 0; i < 4; i++) {
            double ox = (Math.random() - 0.5) * radius;
            double oz = (Math.random() - 0.5) * radius;
            LightningBolt bolt = new LightningBolt(net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, serverLevel);
            bolt.moveTo(pos.x + ox, pos.y, pos.z + oz);
            bolt.setVisualOnly(true);
            serverLevel.addFreshEntity(bolt);
        }

        // Hit all entities in radius
        AABB box = caster.getBoundingBox().inflate(radius);
        List<LivingEntity> targets = caster.level().getEntitiesOfClass(LivingEntity.class, box,
            target -> caster.distanceTo(target) <= radius
                && SpellTargetingRules.canHarm(caster, caster, target));
        for (LivingEntity target : targets) {
            applyImpacts(caster, target, def);
        }
    }

    private static void castCompanionExplosion(ServerPlayer owner, PokemonEntity companion,
                                               SpellDefinition def) {
        if (!(companion.level() instanceof ServerLevel serverLevel)) return;
        Vec3 position = companion.position();
        double radius = def.targeting.range;
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, position.x, position.y + 1, position.z,
                8, radius * 0.4, 0.5, radius * 0.4, 0.1);
        AABB area = companion.getBoundingBox().inflate(radius);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
            entity -> SpellTargetingRules.canHarm(owner, companion, entity)
                        && companion.distanceTo(entity) <= radius);
        for (LivingEntity target : targets) {
            applyImpacts(owner, companion, target, def);
        }
    }

    // ── Standard (aim / area / self + instant) ───────────────────────────────

    private static void castStandard(ServerPlayer caster, SpellDefinition def) {
        List<LivingEntity> targets = resolveTargets(caster, def);

        if (!targets.isEmpty()) {
            applySchoolVisual(caster, def.school, targets.get(0).position());
            if ("vine_tether".equals(def.visual.trail)) {
                drawParticleLine(caster, targets.get(0), ParticleTypes.COMPOSTER);
            }
        } else if ("self".equals(def.targeting.type)) {
            applySchoolVisualSelf(caster, def.school);
        }

        if ("self".equals(def.targeting.type)) {
            applyImpacts(caster, caster, def);
        } else {
            for (LivingEntity target : targets) {
                applyImpacts(caster, target, def);
            }
        }
    }

    // ── Beam: raycast hitting ALL entities in a line ─────────────────────────

    private static void castBeam(ServerPlayer caster, SpellDefinition def) {
        castRuntimeBeam(caster, caster, null, def);
    }

    public static void sendRuntimeBeamVfx(ServerPlayer owner, LivingEntity effectCaster,
                                          LivingEntity lockedTarget, SpellDefinition def) {
        if (!(effectCaster.level() instanceof ServerLevel serverLevel)) return;
        BeamTrace trace = resolveBeamTrace(owner, effectCaster, lockedTarget, def);
        sendBeamVfx(serverLevel, effectCaster, def, trace);
    }

    public static void castRuntimeBeam(ServerPlayer owner, LivingEntity effectCaster,
                                       LivingEntity lockedTarget, SpellDefinition def) {
        if (!(effectCaster.level() instanceof ServerLevel serverLevel)) return;
        BeamTrace trace = resolveBeamTrace(owner, effectCaster, lockedTarget, def);
        Vec3 eye = trace.origin();
        Vec3 beamEnd = trace.end();
        sendBeamVfx(serverLevel, effectCaster, def, trace);

        // Particles along the beam
        double dist = eye.distanceTo(beamEnd);
        Vec3 dir = beamEnd.subtract(eye).normalize();
        for (double d = 1.0; d < dist; d += 1.5) {
            Vec3 p = eye.add(dir.scale(d));
            serverLevel.sendParticles(schoolParticle(def.school), p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.0);
        }

        // Hit all entities along beam
        double width = Math.max(0.1, def.targeting.width);
        AABB box = new AABB(eye, beamEnd).inflate(width);
        List<LivingEntity> hit = effectCaster.level().getEntitiesOfClass(LivingEntity.class, box,
            entity -> SpellTargetingRules.canHarm(owner, effectCaster, entity)
                && entity.getBoundingBox().inflate(width).clip(eye, beamEnd).isPresent());
        hit.sort((left, right) -> Double.compare(
            left.distanceToSqr(effectCaster), right.distanceToSqr(effectCaster)));
        if (def.targeting.max_targets > 0 && hit.size() > def.targeting.max_targets) {
            hit = hit.subList(0, def.targeting.max_targets);
        }
        for (LivingEntity t : hit) {
            applyImpacts(owner, effectCaster, t, def);
        }

        // Lightning visual at end for lightning school
        if ("lightning".equals(def.school)) {
            LightningBolt bolt = new LightningBolt(net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, serverLevel);
            bolt.moveTo(beamEnd.x, beamEnd.y, beamEnd.z);
            bolt.setVisualOnly(true);
            serverLevel.addFreshEntity(bolt);
        }
    }

    private static void castCompanionBeam(ServerPlayer owner, PokemonEntity companion,
                                          LivingEntity target, SpellDefinition def) {
        castRuntimeBeam(owner, companion, target, def);
    }

        public static void castRuntimeCone(ServerPlayer owner, LivingEntity effectCaster,
                           SpellDefinition def) {
        if (!(effectCaster.level() instanceof ServerLevel level)) return;
        Vec3 origin = effectCaster.getEyePosition();
        Vec3 forward = effectCaster.getLookAngle().normalize();
        double range = Math.max(1.0, def.targeting.range);
        double minimumDot = Math.cos(Math.toRadians(
            Math.max(1.0, Math.min(179.0, def.delivery.cone_angle)) * 0.5));
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
            effectCaster.getBoundingBox().inflate(range), target -> {
                if (!SpellTargetingRules.canHarm(owner, effectCaster, target)) return false;
                Vec3 targetCenter = target.getBoundingBox().getCenter();
                Vec3 offset = targetCenter.subtract(origin);
                if (offset.lengthSqr() > range * range || offset.lengthSqr() < 1.0E-6) return false;
                if (forward.dot(offset.normalize()) < minimumDot) return false;
                HitResult obstruction = level.clip(new ClipContext(origin, targetCenter,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, effectCaster));
                return obstruction.getType() == HitResult.Type.MISS
                    || obstruction.getLocation().distanceToSqr(origin)
                    >= targetCenter.distanceToSqr(origin) - 0.25;
            });
        targets.sort((left, right) -> Double.compare(
            left.distanceToSqr(effectCaster), right.distanceToSqr(effectCaster)));
        if (def.targeting.max_targets > 0 && targets.size() > def.targeting.max_targets) {
            targets = targets.subList(0, def.targeting.max_targets);
        }
        for (LivingEntity target : targets) {
            applyImpacts(owner, effectCaster, target, def);
        }
        Vec3 end = origin.add(forward.scale(range));
        double radius = Math.tan(Math.toRadians(def.delivery.cone_angle * 0.5)) * range;
        SpellVfxDispatcher.send(level, "cone", def.visual.trail, def.school,
            origin, end, Math.max(0.5, radius),
            Math.max(2, def.delivery.tick_interval_ticks + 1), effectCaster, false);
        }

        private static boolean castWave(ServerPlayer owner, LivingEntity effectCaster,
                        SpellDefinition def) {
        Vec3 look = effectCaster.getLookAngle();
        Vec3 direction = new Vec3(look.x, 0.0, look.z);
        if (direction.lengthSqr() < 1.0E-6) return false;
        return SpellRuntimeController.startWave(owner, effectCaster, def,
            effectCaster.position().add(direction.normalize().scale(1.5)),
            direction.normalize());
        }

        private static boolean castTrap(ServerPlayer owner, LivingEntity effectCaster,
                        SpellDefinition def, Vec3 center) {
        Vec3 look = effectCaster.getLookAngle();
        Vec3 direction = new Vec3(look.x, 0.0, look.z);
        if (direction.lengthSqr() < 1.0E-6) direction = new Vec3(0.0, 0.0, 1.0);
        return SpellRuntimeController.startTrap(owner, effectCaster, def, center,
            direction.normalize());
        }

        private static boolean castMeleeCombo(ServerPlayer owner, LivingEntity effectCaster,
                          LivingEntity target, SpellDefinition def) {
        if (target == null || !SpellTargetingRules.canHarm(owner, effectCaster, target)) {
            return false;
        }
        return SpellRuntimeController.startMeleeCombo(owner, effectCaster, target, def);
        }

        private static boolean castTeleportStrike(ServerPlayer owner, LivingEntity effectCaster,
                               LivingEntity target, SpellDefinition def) {
        if (!(effectCaster.level() instanceof ServerLevel level) || target == null
            || !SpellTargetingRules.canHarm(owner, effectCaster, target)) return false;
        Vec3 oldPosition = effectCaster.position();
        Vec3 destination = findTeleportDestination(effectCaster, target);
        if (destination == null) return false;

        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
            oldPosition.x, oldPosition.y + 1.0, oldPosition.z,
            24, 0.35, 0.7, 0.35, 0.05);
        effectCaster.teleportTo(destination.x, destination.y, destination.z);
        effectCaster.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
            target.getBoundingBox().getCenter());
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
            destination.x, destination.y + 1.0, destination.z,
            32, 0.4, 0.8, 0.4, 0.08);
        SpellVfxDispatcher.send(level, "impact", def.visual.impact, def.school,
            target.getBoundingBox().getCenter(), target.getBoundingBox().getCenter(),
            Math.max(1.0, def.targeting.width), 10, effectCaster, false);
        applyImpacts(owner, effectCaster, target, def);
        return true;
        }

        private static Vec3 findTeleportDestination(LivingEntity effectCaster, LivingEntity target) {
        Vec3 targetFacing = new Vec3(target.getLookAngle().x, 0.0, target.getLookAngle().z);
        if (targetFacing.lengthSqr() < 1.0E-6) {
            targetFacing = target.position().subtract(effectCaster.position());
        }
        targetFacing = new Vec3(targetFacing.x, 0.0, targetFacing.z).normalize();
        Vec3 right = new Vec3(-targetFacing.z, 0.0, targetFacing.x);
        Vec3[] candidates = {
            target.position().subtract(targetFacing.scale(1.5)),
            target.position().subtract(targetFacing.scale(1.2)).add(right.scale(1.0)),
            target.position().subtract(targetFacing.scale(1.2)).subtract(right.scale(1.0)),
            target.position().add(targetFacing.scale(1.5))
        };
        for (Vec3 candidate : candidates) {
            AABB movedBox = effectCaster.getBoundingBox().move(candidate.subtract(effectCaster.position()));
            if (effectCaster.level().noCollision(effectCaster, movedBox)) return candidate;
        }
        return null;
        }

        private static boolean castRicochetBeam(ServerPlayer owner, LivingEntity effectCaster,
                            LivingEntity lockedTarget, SpellDefinition def) {
        if (!(effectCaster.level() instanceof ServerLevel level)) return false;
        Vec3 origin = effectCaster.getEyePosition();
        Vec3 direction = lockedTarget != null
            ? lockedTarget.getBoundingBox().getCenter().subtract(origin).normalize()
            : effectCaster.getLookAngle().normalize();
        Vec3 desiredEnd = origin.add(direction.scale(def.targeting.range));
        HitResult obstruction = level.clip(new ClipContext(origin, desiredEnd,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, effectCaster));
        Vec3 firstEnd = obstruction.getType() == HitResult.Type.BLOCK
            ? obstruction.getLocation() : desiredEnd;
        List<LivingEntity> firstTargets = targetsAlongSegment(
            owner, effectCaster, origin, firstEnd, def.targeting.width, Set.of());
        LivingEntity firstTarget = firstTargets.isEmpty() ? null : firstTargets.get(0);
        Vec3 firstImpact = firstTarget == null
            ? firstEnd : firstTarget.getBoundingBox().getCenter();
        SpellVfxDispatcher.send(level, "beam", def.visual.trail, def.school,
            origin, firstImpact, Math.max(0.1, def.targeting.width), 8, effectCaster, false);
        if (firstTarget != null) {
            applyImpacts(owner, effectCaster, firstTarget, def);
        }

        if (def.delivery.bounce_count <= 0) return firstTarget != null;
        Vec3 bounceDirection;
        if (firstTarget != null) {
            bounceDirection = direction;
        } else if (obstruction instanceof BlockHitResult blockHit) {
            Vec3 normal = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            bounceDirection = direction.subtract(normal.scale(2.0 * direction.dot(normal))).normalize();
        } else {
            return false;
        }

        double bounceRange = Math.min(10.0, Math.max(4.0, def.targeting.range * 0.5));
        Vec3 bounceEnd = firstImpact.add(bounceDirection.scale(bounceRange));
        Set<UUID> excluded = firstTarget == null ? Set.of() : Set.of(firstTarget.getUUID());
        List<LivingEntity> bounceTargets = targetsAlongSegment(
            owner, effectCaster, firstImpact, bounceEnd,
            Math.max(0.5, def.targeting.width * 1.5), excluded);
        if (firstTarget != null && bounceTargets.isEmpty()) {
            bounceTargets = nearbyRicochetTargets(owner, effectCaster, firstTarget,
                bounceRange, excluded);
        }
        if (!bounceTargets.isEmpty()) {
            LivingEntity bouncedTarget = bounceTargets.get(0);
            Vec3 bouncedImpact = bouncedTarget.getBoundingBox().getCenter();
            SpellVfxDispatcher.send(level, "beam", def.visual.aftermath, def.school,
                firstImpact, bouncedImpact, Math.max(0.1, def.targeting.width * 0.8),
                8, effectCaster, false);
            applyImpacts(owner, effectCaster, bouncedTarget, def);
        } else if (firstTarget == null) {
            SpellVfxDispatcher.send(level, "beam", def.visual.aftermath, def.school,
                firstImpact, bounceEnd, Math.max(0.1, def.targeting.width * 0.8),
                8, effectCaster, false);
        }
        return firstTarget != null || obstruction.getType() == HitResult.Type.BLOCK;
        }

        private static List<LivingEntity> targetsAlongSegment(ServerPlayer owner,
                                   LivingEntity effectCaster,
                                   Vec3 start, Vec3 end, double width,
                                   Set<UUID> excluded) {
        AABB area = new AABB(start, end).inflate(Math.max(0.1, width));
        List<LivingEntity> targets = effectCaster.level().getEntitiesOfClass(
            LivingEntity.class, area,
            target -> !excluded.contains(target.getUUID())
                && SpellTargetingRules.canHarm(owner, effectCaster, target)
                && target.getBoundingBox().inflate(width).clip(start, end).isPresent());
        targets.sort((left, right) -> Double.compare(
            left.getBoundingBox().getCenter().distanceToSqr(start),
            right.getBoundingBox().getCenter().distanceToSqr(start)));
        return targets;
        }

        private static List<LivingEntity> nearbyRicochetTargets(ServerPlayer owner,
                                     LivingEntity effectCaster,
                                     LivingEntity firstTarget,
                                     double range, Set<UUID> excluded) {
        Vec3 start = firstTarget.getBoundingBox().getCenter();
        List<LivingEntity> targets = effectCaster.level().getEntitiesOfClass(
            LivingEntity.class, firstTarget.getBoundingBox().inflate(range), target -> {
                if (excluded.contains(target.getUUID())
                    || !SpellTargetingRules.canHarm(owner, effectCaster, target)) return false;
                Vec3 end = target.getBoundingBox().getCenter();
                HitResult hit = effectCaster.level().clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, effectCaster));
                return hit.getType() == HitResult.Type.MISS
                    || hit.getLocation().distanceToSqr(start) >= end.distanceToSqr(start) - 0.25;
            });
        targets.sort((left, right) -> Double.compare(
            left.distanceToSqr(firstTarget), right.distanceToSqr(firstTarget)));
        return targets;
        }

        private static BeamTrace resolveBeamTrace(ServerPlayer owner, LivingEntity effectCaster,
                              LivingEntity lockedTarget, SpellDefinition def) {
        Vec3 origin = effectCaster.getEyePosition();
        Vec3 desiredEnd;
        if (lockedTarget != null && lockedTarget.isAlive()
            && SpellTargetingRules.canHarm(owner, effectCaster, lockedTarget)) {
            Vec3 targetDirection = lockedTarget.getBoundingBox().getCenter().subtract(origin);
            desiredEnd = origin.add(targetDirection.length() > def.targeting.range
                ? targetDirection.normalize().scale(def.targeting.range) : targetDirection);
        } else {
            desiredEnd = origin.add(effectCaster.getLookAngle().scale(def.targeting.range));
        }

        HitResult blockHit = effectCaster.level().clip(new ClipContext(origin, desiredEnd,
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, effectCaster));
        Vec3 end = blockHit.getType() == HitResult.Type.BLOCK
            ? blockHit.getLocation() : desiredEnd;
        return new BeamTrace(origin, end);
        }

        private static void sendBeamVfx(ServerLevel level, LivingEntity effectCaster,
                        SpellDefinition definition, BeamTrace trace) {
        int duration = "channel_beam".equals(definition.delivery.type)
            ? Math.max(2, definition.delivery.tick_interval_ticks + 1)
            : 8;
        SpellVfxDispatcher.send(level, "beam", definition.visual.trail,
            definition.school, trace.origin(), trace.end(),
            Math.max(0.1, definition.targeting.width), duration, effectCaster, false);
        }

    // ── Meteor: impacts from above at aimed location ─────────────────────────

    private static void castMeteor(ServerPlayer caster, ResourceLocation spellId, SpellDefinition def) {
        Vec3 center = resolveAimPosition(caster, def.targeting.range);
        castMeteorAt(caster, caster, spellId, def, center);
    }

    private static void castMeteorAt(ServerPlayer owner, LivingEntity effectCaster,
                                     ResourceLocation spellId, SpellDefinition def, Vec3 center) {
        if (!(effectCaster.level() instanceof ServerLevel serverLevel)) return;
        int projectileCount = Math.max(1, def.delivery.projectile_count);
        double spreadRadius = Math.max(1.0, def.targeting.radius * 0.65);
        UUID groupId = SpellRuntimeController.createMeteorGroup(owner, effectCaster, 100);

        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, center.x, center.y + 0.1, center.z,
            25, spreadRadius, 0.05, spreadRadius, 0.01);
        SpellVfxDispatcher.send(serverLevel, "telegraph", def.visual.telegraph,
            def.school, center, center, Math.max(1.0, def.targeting.radius),
            def.delivery.delay_ticks, effectCaster, false);
        for (int index = 0; index < projectileCount; index++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * spreadRadius * 2.0;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * spreadRadius * 2.0;
            Vec3 impactPosition = center.add(offsetX, 0.0, offsetZ);
            Vec3 spawnPosition = impactPosition.add(
                (serverLevel.random.nextDouble() - 0.5) * 4.0,
                15.0 + serverLevel.random.nextDouble() * 5.0,
                (serverLevel.random.nextDouble() - 0.5) * 4.0);
            SpellProjectile meteor = SpellProjectile.createMeteor(
                owner, effectCaster, spellId,
                def, spawnPosition, impactPosition, groupId, index, projectileCount);
            serverLevel.addFreshEntity(meteor);
            sendProjectileVfx(serverLevel, meteor, def,
                    impactPosition.subtract(spawnPosition));
        }
        if (def.delivery.recovery_ticks > 0) {
            effectCaster.addEffect(new MobEffectInstance(TensuraMobEffects.EXHAUSTED,
                    def.delivery.recovery_ticks, 0, false, true, true));
        }
    }

    // ── Cloud: lingering area at aimed position ───────────────────────────────

    private static void castCloud(ServerPlayer caster, SpellDefinition def) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;

        LivingEntity aimTarget = rayCast(caster, def.targeting.range);
        Vec3 cloudPos = aimTarget != null ? aimTarget.position()
                : caster.position().add(caster.getLookAngle().scale(8));

        double radius = def.targeting.radius > 0 ? def.targeting.radius : 4.0;

        // Particle cloud effect (30 ticks spread)
        for (int i = 0; i < 40; i++) {
            double ox = (Math.random() - 0.5) * radius * 2;
            double oy = Math.random() * 2;
            double oz = (Math.random() - 0.5) * radius * 2;
            serverLevel.sendParticles(schoolParticle(def.school),
                    cloudPos.x + ox, cloudPos.y + oy, cloudPos.z + oz,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        // Immediate AoE in cloud area
        AABB box = new AABB(cloudPos, cloudPos).inflate(radius);
        List<LivingEntity> targets = caster.level().getEntitiesOfClass(LivingEntity.class, box,
            target -> SpellTargetingRules.canHarm(caster, caster, target));
        for (LivingEntity t : targets) {
            applyImpacts(caster, t, def);
        }
    }

    private static void castCompanionCloud(ServerPlayer owner, PokemonEntity companion,
                                           Vec3 position, SpellDefinition def) {
        if (!(companion.level() instanceof ServerLevel serverLevel)) return;
        double radius = def.targeting.radius > 0 ? def.targeting.radius : 4.0;
        serverLevel.sendParticles(schoolParticle(def.school), position.x, position.y + 1, position.z,
                40, radius * 0.5, 1.0, radius * 0.5, 0.03);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class,
                new AABB(position, position).inflate(radius),
            entity -> SpellTargetingRules.canHarm(owner, companion, entity));
        for (LivingEntity cloudTarget : targets) {
            applyImpacts(owner, companion, cloudTarget, def);
        }
    }

    private static boolean castVortex(ServerPlayer caster, SpellDefinition def) {
        Vec3 center = resolveAimPosition(caster, def.targeting.range);
        return SpellRuntimeController.startVortex(caster, def, center);
    }

    private static boolean castDelayed(ServerPlayer caster, SpellDefinition def) {
        LivingEntity target = rayCast(caster, def.targeting.range);
        return target != null && SpellRuntimeController.startDelayed(caster, target, def);
    }

    private static boolean castDelayedArea(ServerPlayer caster, SpellDefinition def) {
        Vec3 center = resolveAimPosition(caster, def.targeting.range);
        return SpellRuntimeController.startDelayedArea(caster, caster, def, center);
    }

    private static boolean castMovingZone(ServerPlayer caster, SpellDefinition def) {
        Vec3 direction = caster.getLookAngle();
        Vec3 center = caster.position().add(direction.normalize().scale(2.0));
        return SpellRuntimeController.startMovingZone(caster, caster, def, center, direction);
    }

    private static boolean startCompanionMovingZone(ServerPlayer owner, PokemonEntity companion,
                                                    LivingEntity target, SpellDefinition def) {
        Vec3 direction = target.position().subtract(companion.position());
        Vec3 center = companion.position().add(direction.normalize().scale(2.0));
        return SpellRuntimeController.startMovingZone(owner, companion, def, center, direction);
    }

        private static void sendProjectileVfx(ServerLevel level, SpellProjectile projectile,
                          SpellDefinition definition, Vec3 direction) {
        Vec3 origin = projectile.position();
        Vec3 target = origin.add(direction.normalize().scale(definition.targeting.range));
        SpellVfxDispatcher.send(level, "projectile", definition.visual.projectile,
            definition.school, origin, target, 1.0, definition.delivery.duration_ticks,
            projectile, false);
        SpellVfxDispatcher.send(level, "projectile", definition.visual.trail,
            definition.school, origin, target, 1.0, definition.delivery.duration_ticks,
            projectile, false);
        }

    private static void sendCastVfx(LivingEntity effectCaster, SpellDefinition definition) {
        if (!(effectCaster.level() instanceof ServerLevel level)) return;
        SpellVfxDispatcher.send(level, "attachment", definition.visual.cast_animation,
                definition.school, effectCaster.position(), effectCaster.position(),
                1.0, definition.cast_time_ticks, effectCaster, true);
    }

    // ── Targeting helpers ────────────────────────────────────────────────────

    private static List<LivingEntity> resolveTargets(ServerPlayer caster, SpellDefinition def) {
        if ("self".equals(def.targeting.type)) return List.of(caster);
        if ("area".equals(def.targeting.type)) return aoeTargets(caster, def.targeting.range);
        // "aim" default
        LivingEntity hit = rayCast(caster, def.targeting.range);
        return hit != null ? List.of(hit) : List.of();
    }

    private static LivingEntity rayCast(ServerPlayer caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().scale(range);
        Vec3 end  = eye.add(look);

        HitResult blockHit = caster.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));
        Vec3 target = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;

        AABB box = caster.getBoundingBox().expandTowards(look).inflate(1.0);
        LivingEntity closest = null;
        double closestDistance = range * range;
        for (Entity entity : caster.level().getEntities(caster, box)) {
            if (entity instanceof LivingEntity living
                    && SpellTargetingRules.canHarm(caster, caster, living)) {
                var intersection = entity.getBoundingBox().inflate(0.3).clip(eye, target);
                if (intersection.isPresent()) {
                    double distance = eye.distanceToSqr(intersection.get());
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closest = living;
                    }
                }
            }
        }
        return closest;
    }

    private static Vec3 resolveAimPosition(ServerPlayer caster, double range) {
        LivingEntity target = rayCast(caster, range);
        if (target != null) return target.position();

        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        HitResult blockHit = caster.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));
        return blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;
    }

    private static List<LivingEntity> aoeTargets(ServerPlayer caster, double range) {
        AABB box = caster.getBoundingBox().inflate(range);
        return caster.level().getEntitiesOfClass(LivingEntity.class, box,
                target -> caster.distanceTo(target) <= range
                        && SpellTargetingRules.canHarm(caster, caster, target));
    }

    // ── Visuals ──────────────────────────────────────────────────────────────

    private static void applySchoolVisual(ServerPlayer caster, String school, Vec3 pos) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;
        switch (school) {
            case "lightning" -> {
                LightningBolt bolt = new LightningBolt(net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, serverLevel);
                bolt.moveTo(pos.x, pos.y, pos.z);
                bolt.setVisualOnly(true);
                serverLevel.addFreshEntity(bolt);
            }
            case "fire"    -> serverLevel.sendParticles(ParticleTypes.FLAME,    pos.x, pos.y + 1, pos.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "water"   -> serverLevel.sendParticles(ParticleTypes.DRIPPING_WATER, pos.x, pos.y + 1, pos.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "ice"     -> serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 1, pos.z, 50, 0.4, 0.6, 0.4, 0.02);
            case "shadow"  -> serverLevel.sendParticles(ParticleTypes.PORTAL,   pos.x, pos.y + 1, pos.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "psychic" -> serverLevel.sendParticles(ParticleTypes.ENCHANT,  pos.x, pos.y + 1, pos.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "dragon"  -> serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y + 1, pos.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "nature"  -> serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 1, pos.z, 30, 0.4, 0.6, 0.4, 0.05);
            case "poison"  -> serverLevel.sendParticles(ParticleTypes.WITCH,    pos.x, pos.y + 1, pos.z, 40, 0.4, 0.6, 0.4, 0.05);
            case "earth"   -> serverLevel.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1, pos.z, 8, 0.4, 0.4, 0.4, 0.1);
            case "wind"    -> serverLevel.sendParticles(ParticleTypes.CLOUD,    pos.x, pos.y + 1, pos.z, 30, 0.4, 0.6, 0.4, 0.1);
            case "fairy"   -> serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1, pos.z, 30, 0.4, 0.6, 0.4, 0.05);
            case "steel"   -> serverLevel.sendParticles(ParticleTypes.CRIT,     pos.x, pos.y + 1, pos.z, 40, 0.4, 0.6, 0.4, 0.1);
            default        -> serverLevel.sendParticles(ParticleTypes.CRIT,     pos.x, pos.y + 1, pos.z, 20, 0.3, 0.5, 0.3, 0.05);
        }
    }

    private static void applySchoolVisualSelf(ServerPlayer caster, String school) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;
        Vec3 pos = caster.position();
        serverLevel.sendParticles(schoolParticle(school), pos.x, pos.y + 1, pos.z, 20, 0.5, 0.5, 0.5, 0.05);
    }

    private static void drawParticleLine(ServerPlayer caster, LivingEntity target,
                                         net.minecraft.core.particles.SimpleParticleType particle) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        Vec3 start = caster.getEyePosition().subtract(0.0, 0.35, 0.0);
        Vec3 end = target.getBoundingBox().getCenter();
        Vec3 delta = end.subtract(start);
        int steps = Math.max(2, (int) Math.ceil(delta.length() * 3.0));
        for (int step = 0; step <= steps; step++) {
            Vec3 position = start.add(delta.scale((double) step / steps));
            level.sendParticles(particle, position.x, position.y, position.z,
                    1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    private static void playCastSound(LivingEntity caster, SpellDefinition def) {
        if (def.sound.cast == null || def.sound.cast.isBlank()) return;
        BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse(def.sound.cast))
                .ifPresent(sound -> caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                        sound, SoundSource.PLAYERS, 1.0f, 1.0f));
    }

    private static void playImpactSound(ServerPlayer caster, LivingEntity target, SpellDefinition def) {
        if (def.sound.impact == null || def.sound.impact.isBlank()) return;
        BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse(def.sound.impact))
                .ifPresent(sound -> target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        sound, SoundSource.PLAYERS, 1.0f, 1.0f));
    }

    public static void playLoopSound(LivingEntity source, SpellDefinition def) {
        if (def.sound.loop == null || def.sound.loop.isBlank()) return;
        BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse(def.sound.loop))
                .ifPresent(sound -> source.level().playSound(null,
                        source.getX(), source.getY(), source.getZ(),
                        sound, SoundSource.PLAYERS, 0.8f, 1.0f));
    }

    private static net.minecraft.core.particles.SimpleParticleType schoolParticle(String school) {
        return switch (school) {
            case "lightning" -> ParticleTypes.ELECTRIC_SPARK;
            case "fire"      -> ParticleTypes.FLAME;
            case "water"     -> ParticleTypes.DRIPPING_WATER;
            case "ice"       -> ParticleTypes.SNOWFLAKE;
            case "shadow"    -> ParticleTypes.PORTAL;
            case "psychic"   -> ParticleTypes.ENCHANT;
            case "dragon"    -> ParticleTypes.DRAGON_BREATH;
            case "nature"    -> ParticleTypes.COMPOSTER;
            case "poison"    -> ParticleTypes.WITCH;
            case "earth"     -> ParticleTypes.EXPLOSION;
            case "wind"      -> ParticleTypes.CLOUD;
            case "fairy"     -> ParticleTypes.TOTEM_OF_UNDYING;
            default          -> ParticleTypes.CRIT;
        };
    }

    private static class ChargeState {
        private int available;
        private int maximum;
        private long nextChargeAt;

        private ChargeState(int maximum) {
            this.available = maximum;
            this.maximum = maximum;
        }

        private void refresh(long now, int configuredMaximum, int recoveryTicks) {
            if (maximum != configuredMaximum) {
                maximum = configuredMaximum;
                available = Math.min(available, maximum);
            }
            while (available < maximum && now >= nextChargeAt) {
                available++;
                nextChargeAt += recoveryTicks;
            }
        }

        private void consume(long now, int configuredMaximum, int recoveryTicks) {
            if (available == configuredMaximum) nextChargeAt = now + recoveryTicks;
            available--;
        }
    }

    private record BeamTrace(Vec3 origin, Vec3 end) {
    }

    // ── Impact application ───────────────────────────────────────────────────

    public static void applyImpacts(ServerPlayer caster, LivingEntity target, SpellDefinition def) {
        applyImpacts(caster, caster, target, def, true);
    }

    public static void applyImpacts(ServerPlayer caster, LivingEntity target,
                                    SpellDefinition def, boolean finalProjectile) {
        applyImpacts(caster, caster, target, def, finalProjectile);
    }

    public static void applyImpacts(ServerPlayer owner, LivingEntity effectCaster,
                                    LivingEntity target, SpellDefinition def) {
        applyImpacts(owner, effectCaster, target, def, true);
    }

    public static void applyImpacts(ServerPlayer owner, LivingEntity effectCaster,
                                    LivingEntity target, SpellDefinition def,
                                    boolean finalProjectile) {
        boolean canHarm = SpellTargetingRules.canHarm(owner, effectCaster, target);
        for (SpellDefinition.Impact impact : def.impact) {
            LivingEntity recipient = "caster".equals(impact.recipient) ? effectCaster : target;
            switch (impact.type) {
                case "damage" -> {
                    if (!canHarm) continue;
                    double baseDamage = def.power >= 0.0
                            ? def.power
                            : owner.getAttackStrengthScale(0) * 6.0;
                    float dmg = (float) (applyExposedModifier(baseDamage,
                        target, def) * impact.damage_multiplier);
                    target.hurt(owner.damageSources().playerAttack(owner), Math.max(1, dmg));
                }
                case "speed_scaled_damage" -> {
                    if (!canHarm) continue;
                    Vec3 casterMovement = effectCaster.getDeltaMovement();
                    Vec3 targetMovement = target.getDeltaMovement();
                    double casterSpeed = Math.sqrt(casterMovement.x * casterMovement.x
                            + casterMovement.z * casterMovement.z);
                    double targetSpeed = Math.sqrt(targetMovement.x * targetMovement.x
                            + targetMovement.z * targetMovement.z);
                    double advantage = Math.max(0.0,
                            Math.min(1.0, (casterSpeed - targetSpeed) / 0.35));
                    double maximumPower = Math.max(def.power, impact.amount);
                        double scaledPower = def.power
                            + (maximumPower - def.power) * advantage;
                        float damage = (float) (applyExposedModifier(scaledPower,
                            target, def) * impact.damage_multiplier);
                    target.hurt(owner.damageSources().playerAttack(owner), Math.max(1, damage));
                }
                case "status_effect" -> {
                    if (Math.random() <= impact.chance && !impact.effect.isEmpty()) {
                        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(impact.effect))
                            .filter(holder -> recipient == effectCaster || canHarm
                                    || holder.value().getCategory()
                                    != net.minecraft.world.effect.MobEffectCategory.HARMFUL)
                            .ifPresent(holder -> recipient.addEffect(new MobEffectInstance(holder,
                                impact.duration, impact.amplifier, impact.ambient,
                                impact.show_particles, impact.show_icon)));
                    }
                }
                case "fire" -> {
                    if (canHarm) target.igniteForSeconds(impact.seconds);
                }
                case "knockback" -> {
                    if (!canHarm) continue;
                    Vec3 dir = target.position().subtract(effectCaster.position()).normalize().scale(impact.strength);
                    target.setDeltaMovement(target.getDeltaMovement().add(dir.x, 0.4, dir.z));
                    target.hurtMarked = true;
                }
                case "pull" -> {
                    if (!canHarm) continue;
                    Vec3 delta = effectCaster.position().subtract(target.position());
                    if (delta.lengthSqr() > 1.0E-6) {
                        Vec3 pull = delta.normalize().scale(impact.strength);
                        target.setDeltaMovement(target.getDeltaMovement().add(pull.x, 0.15, pull.z));
                        target.hurtMarked = true;
                    }
                }
                case "heal"     -> recipient.heal((float) impact.amount);
                case "full_heal" -> recipient.setHealth(recipient.getMaxHealth());
                case "cleanse" -> {
                    List<Holder<MobEffect>> harmfulEffects = recipient.getActiveEffects().stream()
                            .filter(instance -> instance.getEffect().value().getCategory().equals(net.minecraft.world.effect.MobEffectCategory.HARMFUL))
                            .map(MobEffectInstance::getEffect)
                            .toList();
                    harmfulEffects.forEach(recipient::removeEffect);
                }
                case "wet" -> {
                    if (canHarm) {
                        recipient.addEffect(new MobEffectInstance(TensuraMobEffects.WET,
                                impact.duration, 0, false,
                                impact.show_particles, impact.show_icon));
                    }
                }
                case "freeze_if_wet" -> {
                    if (!canHarm) continue;
                    if (recipient.hasEffect(TensuraMobEffects.WET)) {
                        recipient.removeEffect(TensuraMobEffects.WET);
                        recipient.addEffect(new MobEffectInstance(TensuraMobEffects.FROZEN,
                                impact.duration, 0, false, impact.show_particles, impact.show_icon));
                    } else {
                        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.withDefaultNamespace("slowness"))
                                .ifPresent(holder -> recipient.addEffect(new MobEffectInstance(holder,
                                        impact.duration, Math.max(1, impact.amplifier))));
                    }
                }
                case "paralyze_if_wet" -> {
                    if (!canHarm) continue;
                    double chance = recipient.hasEffect(TensuraMobEffects.WET)
                            ? 1.0 : impact.chance;
                    if (recipient.getRandom().nextDouble() <= chance) {
                        recipient.addEffect(new MobEffectInstance(TensuraMobEffects.PARALYZED,
                                impact.duration, impact.amplifier, false,
                                impact.show_particles, impact.show_icon));
                    }
                }
                case "toxic" -> {
                    if (canHarm) {
                        recipient.addEffect(new MobEffectInstance(TensuraMobEffects.TOXIC,
                                impact.duration, impact.amplifier, false,
                                impact.show_particles, impact.show_icon));
                    }
                }
                case "expose" -> {
                    if (canHarm || recipient == effectCaster) {
                        recipient.addEffect(new MobEffectInstance(TensuraMobEffects.EXPOSED,
                                impact.duration, impact.amplifier, false,
                                impact.show_particles, impact.show_icon));
                    }
                }
                case "tri_status" -> {
                    if (!canHarm || !finalProjectile) continue;
                    switch (target.getRandom().nextInt(3)) {
                        case 0 -> target.igniteForSeconds(Math.max(1, impact.seconds));
                        case 1 -> BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.withDefaultNamespace("slowness"))
                                .ifPresent(holder -> target.addEffect(new MobEffectInstance(holder,
                                        impact.duration, Math.max(1, impact.amplifier))));
                        default -> target.addEffect(new MobEffectInstance(TensuraMobEffects.PARALYZED,
                                impact.duration, 0, false, impact.show_particles, impact.show_icon));
                    }
                }
                case "guard" -> SpellRuntimeController.addGuard(recipient, impact.amount, impact.duration);
            }
        }
        if (finalProjectile && target.level() instanceof ServerLevel level) {
            Vec3 impactPosition = target.getBoundingBox().getCenter();
            double radius = def.targeting.radius > 0.0 ? def.targeting.radius : 1.0;
            SpellVfxDispatcher.send(level, "impact", def.visual.impact, def.school,
                impactPosition, impactPosition, radius, 0, effectCaster,
                !SpellTargetingRules.canHarm(owner, effectCaster, target));
                    if (!"moving_zone".equals(def.delivery.type)
                        && !"protective_aura".equals(def.delivery.type)) {
                    SpellVfxDispatcher.send(level, "aftermath", def.visual.aftermath, def.school,
                        impactPosition, impactPosition, radius, def.delivery.duration_ticks,
                        effectCaster, !SpellTargetingRules.canHarm(owner, effectCaster, target));
                    }
        }
        if (def.sound.loop == null || def.sound.loop.isBlank()) {
            playImpactSound(owner, target, def);
        }
    }

    private static double applyExposedModifier(double damage, LivingEntity target,
                                               SpellDefinition definition) {
        MobEffectInstance exposed = target.getEffect(TensuraMobEffects.EXPOSED);
        if (exposed == null || !"physical".equals(definition.category)) return damage;
        return damage * (1.0 + 0.15 * (exposed.getAmplifier() + 1));
    }
}
