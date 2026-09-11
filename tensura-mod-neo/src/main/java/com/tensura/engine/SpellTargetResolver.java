package com.tensura.engine;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class SpellTargetResolver {

    private SpellTargetResolver() {}

    static List<LivingEntity> resolveTargets(ServerPlayer caster, SpellDefinition definition) {
        if ("self".equals(definition.targeting.type)) return List.of(caster);
        if ("area".equals(definition.targeting.type)) {
            return areaTargets(caster, definition.targeting.range);
        }
        LivingEntity hit = rayCast(caster, definition.targeting.range);
        return hit == null ? List.of() : List.of(hit);
    }

    static LivingEntity rayCast(ServerPlayer caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().scale(range);
        Vec3 end = eye.add(look);
        HitResult blockHit = caster.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));
        Vec3 target = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;

        AABB box = caster.getBoundingBox().expandTowards(look).inflate(1.0);
        LivingEntity closest = null;
        double closestDistance = range * range;
        for (Entity entity : caster.level().getEntities(caster, box)) {
            if (!(entity instanceof LivingEntity living)
                    || !SpellTargetingRules.canHarm(caster, caster, living)) continue;
            var intersection = entity.getBoundingBox().inflate(0.3).clip(eye, target);
            if (intersection.isEmpty()) continue;
            double distance = eye.distanceToSqr(intersection.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = living;
            }
        }
        return closest;
    }

    static Vec3 resolveAimPosition(ServerPlayer caster, double range) {
        LivingEntity target = rayCast(caster, range);
        if (target != null) return target.position();

        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        HitResult blockHit = caster.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));
        return blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;
    }

    private static List<LivingEntity> areaTargets(ServerPlayer caster, double range) {
        AABB box = caster.getBoundingBox().inflate(range);
        return caster.level().getEntitiesOfClass(LivingEntity.class, box,
                target -> caster.distanceTo(target) <= range
                        && SpellTargetingRules.canHarm(caster, caster, target));
    }
}