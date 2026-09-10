package com.tensura.engine;

import com.tensura.network.SpellVfxDispatcher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;

final class SpellBeamDelivery {

    private SpellBeamDelivery() {}

    static void sendRuntimeBeamVfx(ServerPlayer owner, LivingEntity effectCaster,
                                   LivingEntity lockedTarget, SpellDefinition definition) {
        if (!(effectCaster.level() instanceof ServerLevel level)) return;
        BeamTrace trace = resolveBeamTrace(owner, effectCaster, lockedTarget, definition);
        if (CobblemonFlamethrowerVfx.isFlamethrower(definition)) {
            CobblemonFlamethrowerVfx.sendStart(effectCaster, lockedTarget);
            if (effectCaster instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                return;
            }
        }
        sendBeamVfx(level, effectCaster, lockedTarget, definition, trace);
    }

    static void castRuntimeBeam(ServerPlayer owner, LivingEntity effectCaster,
                                LivingEntity lockedTarget, SpellDefinition definition) {
        if (!(effectCaster.level() instanceof ServerLevel level)) return;
        BeamTrace trace = resolveBeamTrace(owner, effectCaster, lockedTarget, definition);
        Vec3 origin = trace.origin();
        Vec3 beamEnd = trace.end();
        sendBeamVfx(level, effectCaster, lockedTarget, definition, trace);

        if (!CobblemonFlamethrowerVfx.isFlamethrower(definition)) {
            double distance = origin.distanceTo(beamEnd);
            Vec3 direction = beamEnd.subtract(origin).normalize();
            for (double offset = 1.0; offset < distance; offset += 1.5) {
                Vec3 position = origin.add(direction.scale(offset));
                level.sendParticles(SpellFeedback.schoolParticle(definition.school),
                        position.x, position.y, position.z,
                        3, 0.1, 0.1, 0.1, 0.0);
            }
        }

        double width = Math.max(0.1, definition.targeting.width);
        AABB area = new AABB(origin, beamEnd).inflate(width);
        List<LivingEntity> targets = effectCaster.level().getEntitiesOfClass(
                LivingEntity.class, area,
                target -> SpellTargetingRules.canHarm(owner, effectCaster, target)
                        && target.getBoundingBox().inflate(width)
                                .clip(origin, beamEnd).isPresent());
        targets.sort((left, right) -> Double.compare(
                left.distanceToSqr(effectCaster), right.distanceToSqr(effectCaster)));
        if (definition.targeting.max_targets > 0
                && targets.size() > definition.targeting.max_targets) {
            targets = targets.subList(0, definition.targeting.max_targets);
        }
        for (LivingEntity target : targets) {
            if ("channel_beam".equals(definition.delivery.type)) {
                target.invulnerableTime = 0;
            }
            SpellImpactApplier.applyImpacts(
                    owner, effectCaster, target, definition, true, false);
        }
        SpellImpactApplier.applyImpacts(
                owner, effectCaster, effectCaster, definition, true, true, false);

        if ("lightning".equals(definition.school)) {
            LightningBolt bolt = new LightningBolt(
                    net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, level);
            bolt.moveTo(beamEnd.x, beamEnd.y, beamEnd.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    static void castRuntimeCone(ServerPlayer owner, LivingEntity effectCaster,
                                SpellDefinition definition) {
        castRuntimeCone(owner, effectCaster, null, definition);
    }

    static void castRuntimeCone(ServerPlayer owner, LivingEntity effectCaster,
                                LivingEntity lockedTarget, SpellDefinition definition) {
        if (!(effectCaster.level() instanceof ServerLevel level)) return;
        Vec3 origin = effectCaster.getEyePosition();
        Vec3 forward = lockedTarget != null && lockedTarget.isAlive()
                && SpellTargetingRules.canHarm(owner, effectCaster, lockedTarget)
                ? lockedTarget.getBoundingBox().getCenter().subtract(origin).normalize()
                : effectCaster.getLookAngle().normalize();
        double range = Math.max(1.0, definition.targeting.range);
        double minimumDot = Math.cos(Math.toRadians(
                Math.max(1.0, Math.min(179.0, definition.delivery.cone_angle)) * 0.5));
        List<LivingEntity> targets = targetsInCone(
                owner, effectCaster, origin, forward, range, minimumDot);
        if (definition.targeting.max_targets > 0
                && targets.size() > definition.targeting.max_targets) {
            targets = targets.subList(0, definition.targeting.max_targets);
        }
        for (LivingEntity target : targets) {
            target.invulnerableTime = 0;
            SpellImpactApplier.applyImpacts(
                    owner, effectCaster, target, definition, true, false);
        }
        SpellImpactApplier.applyImpacts(
                owner, effectCaster, effectCaster, definition, true, true, false);
        Vec3 end = origin.add(forward.scale(range));
        double radius = Math.tan(Math.toRadians(
                definition.delivery.cone_angle * 0.5)) * range;
        SpellVfxDispatcher.send(level, "cone", definition.visual.trail,
                definition.school, origin, end, Math.max(0.5, radius),
                Math.max(2, definition.delivery.tick_interval_ticks + 1),
                effectCaster, false);
    }

    static boolean castArcStrike(ServerPlayer owner, LivingEntity effectCaster,
                                 LivingEntity aimedTarget, SpellDefinition definition) {
        if (!(effectCaster.level() instanceof ServerLevel level)) return false;
        Vec3 origin = effectCaster.getEyePosition();
        Vec3 forward = aimedTarget == null
                ? effectCaster.getLookAngle().normalize()
                : aimedTarget.getBoundingBox().getCenter().subtract(origin).normalize();
        double range = Math.max(1.0, definition.targeting.range);
        double angle = definition.delivery.cone_angle > 0.0
                ? definition.delivery.cone_angle : 90.0;
        double minimumDot = Math.cos(Math.toRadians(Math.min(179.0, angle) * 0.5));
        List<LivingEntity> targets = targetsInCone(
                owner, effectCaster, origin, forward, range, minimumDot);
        int maxTargets = definition.targeting.max_targets > 0
                ? definition.targeting.max_targets : Integer.MAX_VALUE;
        for (int index = 0; index < Math.min(maxTargets, targets.size()); index++) {
            SpellImpactApplier.applyImpacts(
                    owner, effectCaster, targets.get(index), definition, true, false);
        }
        SpellImpactApplier.applyImpacts(
                owner, effectCaster, effectCaster, definition, true, true, false);
        Vec3 end = origin.add(forward.scale(range));
        double radius = Math.tan(Math.toRadians(angle * 0.5)) * range;
        SpellVfxDispatcher.send(level, "cone", definition.visual.trail,
                definition.school, origin, end, Math.max(0.5, radius),
                4, effectCaster, false);
        return true;
    }

    static boolean castRicochetBeam(ServerPlayer owner, LivingEntity effectCaster,
                                    LivingEntity lockedTarget,
                                    SpellDefinition definition) {
        if (!(effectCaster.level() instanceof ServerLevel level)) return false;
        Vec3 origin = effectCaster.getEyePosition();
        Vec3 direction = lockedTarget != null
                ? lockedTarget.getBoundingBox().getCenter().subtract(origin).normalize()
                : effectCaster.getLookAngle().normalize();
        Vec3 desiredEnd = origin.add(direction.scale(definition.targeting.range));
        HitResult obstruction = level.clip(new ClipContext(origin, desiredEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, effectCaster));
        Vec3 firstEnd = obstruction.getType() == HitResult.Type.BLOCK
                ? obstruction.getLocation() : desiredEnd;
        List<LivingEntity> firstTargets = targetsAlongSegment(
                owner, effectCaster, origin, firstEnd,
                definition.targeting.width, Set.of());
        LivingEntity firstTarget = firstTargets.isEmpty() ? null : firstTargets.get(0);
        Vec3 firstImpact = firstTarget == null
                ? firstEnd : firstTarget.getBoundingBox().getCenter();
        SpellVfxDispatcher.send(level, "beam", definition.visual.trail,
                definition.school, origin, firstImpact,
                Math.max(0.1, definition.targeting.width), 8, effectCaster, false);
        if (firstTarget != null) {
            SpellImpactApplier.applyImpacts(
                    owner, effectCaster, firstTarget, definition);
        }

        if (definition.delivery.bounce_count <= 0) return firstTarget != null;
        Vec3 bounceDirection;
        if (firstTarget != null) {
            bounceDirection = direction;
        } else if (obstruction instanceof BlockHitResult blockHit) {
            Vec3 normal = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            bounceDirection = direction.subtract(
                    normal.scale(2.0 * direction.dot(normal))).normalize();
        } else {
            return false;
        }

        double bounceRange = Math.min(
                10.0, Math.max(4.0, definition.targeting.range * 0.5));
        Vec3 bounceEnd = firstImpact.add(bounceDirection.scale(bounceRange));
        Set<UUID> excluded = firstTarget == null
                ? Set.of() : Set.of(firstTarget.getUUID());
        List<LivingEntity> bounceTargets = targetsAlongSegment(
                owner, effectCaster, firstImpact, bounceEnd,
                Math.max(0.5, definition.targeting.width * 1.5), excluded);
        if (firstTarget != null && bounceTargets.isEmpty()) {
            bounceTargets = nearbyRicochetTargets(
                    owner, effectCaster, firstTarget, bounceRange, excluded);
        }
        if (!bounceTargets.isEmpty()) {
            LivingEntity bouncedTarget = bounceTargets.get(0);
            Vec3 bouncedImpact = bouncedTarget.getBoundingBox().getCenter();
            SpellVfxDispatcher.send(level, "beam", definition.visual.aftermath,
                    definition.school, firstImpact, bouncedImpact,
                    Math.max(0.1, definition.targeting.width * 0.8),
                    8, effectCaster, false);
            SpellImpactApplier.applyImpacts(
                    owner, effectCaster, bouncedTarget, definition);
        } else if (firstTarget == null) {
            SpellVfxDispatcher.send(level, "beam", definition.visual.aftermath,
                    definition.school, firstImpact, bounceEnd,
                    Math.max(0.1, definition.targeting.width * 0.8),
                    8, effectCaster, false);
        }
        return firstTarget != null || obstruction.getType() == HitResult.Type.BLOCK;
    }

    private static List<LivingEntity> targetsInCone(
            ServerPlayer owner, LivingEntity effectCaster, Vec3 origin,
            Vec3 forward, double range, double minimumDot) {
        List<LivingEntity> targets = effectCaster.level().getEntitiesOfClass(
                LivingEntity.class, effectCaster.getBoundingBox().inflate(range), target -> {
                    if (!SpellTargetingRules.canHarm(owner, effectCaster, target)) return false;
                    Vec3 targetCenter = target.getBoundingBox().getCenter();
                    Vec3 offset = targetCenter.subtract(origin);
                    if (offset.lengthSqr() > range * range
                            || offset.lengthSqr() < 1.0E-6) return false;
                    if (forward.dot(offset.normalize()) < minimumDot) return false;
                    HitResult obstruction = effectCaster.level().clip(new ClipContext(
                            origin, targetCenter, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, effectCaster));
                    return obstruction.getType() == HitResult.Type.MISS
                            || obstruction.getLocation().distanceToSqr(origin)
                                    >= targetCenter.distanceToSqr(origin) - 0.25;
                });
        targets.sort((left, right) -> Double.compare(
                left.distanceToSqr(effectCaster), right.distanceToSqr(effectCaster)));
        return targets;
    }

    private static List<LivingEntity> targetsAlongSegment(
            ServerPlayer owner, LivingEntity effectCaster, Vec3 start, Vec3 end,
            double width, Set<UUID> excluded) {
        AABB area = new AABB(start, end).inflate(Math.max(0.1, width));
        List<LivingEntity> targets = effectCaster.level().getEntitiesOfClass(
                LivingEntity.class, area,
                target -> !excluded.contains(target.getUUID())
                        && SpellTargetingRules.canHarm(owner, effectCaster, target)
                        && target.getBoundingBox().inflate(width)
                                .clip(start, end).isPresent());
        targets.sort((left, right) -> Double.compare(
                left.getBoundingBox().getCenter().distanceToSqr(start),
                right.getBoundingBox().getCenter().distanceToSqr(start)));
        return targets;
    }

    private static List<LivingEntity> nearbyRicochetTargets(
            ServerPlayer owner, LivingEntity effectCaster, LivingEntity firstTarget,
            double range, Set<UUID> excluded) {
        Vec3 start = firstTarget.getBoundingBox().getCenter();
        List<LivingEntity> targets = effectCaster.level().getEntitiesOfClass(
                LivingEntity.class, firstTarget.getBoundingBox().inflate(range), target -> {
                    if (excluded.contains(target.getUUID())
                            || !SpellTargetingRules.canHarm(owner, effectCaster, target)) {
                        return false;
                    }
                    Vec3 end = target.getBoundingBox().getCenter();
                    HitResult hit = effectCaster.level().clip(new ClipContext(
                            start, end, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, effectCaster));
                    return hit.getType() == HitResult.Type.MISS
                            || hit.getLocation().distanceToSqr(start)
                                    >= end.distanceToSqr(start) - 0.25;
                });
        targets.sort((left, right) -> Double.compare(
                left.distanceToSqr(firstTarget), right.distanceToSqr(firstTarget)));
        return targets;
    }

    private static BeamTrace resolveBeamTrace(
            ServerPlayer owner, LivingEntity effectCaster,
            LivingEntity lockedTarget, SpellDefinition definition) {
        Vec3 origin = effectCaster.getEyePosition();
        Vec3 desiredEnd;
        if (lockedTarget != null && lockedTarget.isAlive()
                && SpellTargetingRules.canHarm(owner, effectCaster, lockedTarget)) {
            Vec3 targetDirection = lockedTarget.getBoundingBox().getCenter().subtract(origin);
            desiredEnd = origin.add(targetDirection.length() > definition.targeting.range
                    ? targetDirection.normalize().scale(definition.targeting.range)
                    : targetDirection);
        } else {
            desiredEnd = origin.add(
                    effectCaster.getLookAngle().scale(definition.targeting.range));
        }

        HitResult blockHit = effectCaster.level().clip(new ClipContext(
                origin, desiredEnd, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, effectCaster));
        Vec3 end = blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit.getLocation() : desiredEnd;
        return new BeamTrace(origin, end);
    }

        private static void sendBeamVfx(ServerLevel level, LivingEntity effectCaster,
                                                                        LivingEntity lockedTarget, SpellDefinition definition,
                                                                        BeamTrace trace) {
                if (CobblemonFlamethrowerVfx.isFlamethrower(definition)
                                && effectCaster instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                        CobblemonFlamethrowerVfx.sendActorIfDue(level, effectCaster, lockedTarget);
                        return;
                }
        int duration = "channel_beam".equals(definition.delivery.type)
                ? Math.max(2, definition.delivery.tick_interval_ticks + 1) : 8;
        SpellVfxDispatcher.send(level, "beam", definition.visual.trail,
                definition.school, trace.origin(), trace.end(),
                Math.max(0.1, definition.targeting.width), duration,
                effectCaster, false);
    }

    private record BeamTrace(Vec3 origin, Vec3 end) {}
}