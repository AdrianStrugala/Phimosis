package com.tensura.engine;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.entity.SpellProjectile;
import com.tensura.event.SpellRuntimeController;
import com.tensura.network.SpellVfxDispatcher;
import com.tensura.registry.TensuraMobEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

final class SpellProjectileDelivery {

    private SpellProjectileDelivery() {}

    static void castProjectile(ServerPlayer caster, SpellDefinition definition,
                               ResourceLocation spellId) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        SpellFeedback.applySchoolVisualSelf(caster, definition.school);
        int projectileCount = Math.max(1, definition.delivery.projectile_count);
        UUID projectileGroup = projectileCount > 1
                ? SpellRuntimeController.createProjectileGroup(level, 100) : null;
        LivingEntity homingTarget = definition.delivery.homing_strength > 0.0
                ? SpellTargetResolver.rayCast(caster, definition.targeting.range) : null;
        for (int index = 0; index < projectileCount; index++) {
            double centeredIndex = index - (projectileCount - 1) / 2.0;
            float angle = (float) Math.toRadians(
                    centeredIndex * definition.delivery.spread_degrees);
            Vec3 direction = caster.getLookAngle().yRot(angle);
            SpellProjectile projectile = SpellProjectile.create(
                    caster, caster, spellId, definition, direction, index,
                    projectileCount, projectileGroup, homingTarget);
            level.addFreshEntity(projectile);
            SpellFeedback.sendProjectileVfx(level, projectile, definition, direction);
        }
    }

    static void castCompanionProjectile(ServerPlayer owner, PokemonEntity companion,
                                        LivingEntity target, SpellDefinition definition,
                                        ResourceLocation spellId) {
        if (!(companion.level() instanceof ServerLevel level)) return;
        Vec3 baseDirection = target.getBoundingBox().getCenter()
                .subtract(companion.getEyePosition()).normalize();
        int projectileCount = Math.max(1, definition.delivery.projectile_count);
        UUID projectileGroup = projectileCount > 1
                ? SpellRuntimeController.createProjectileGroup(level, 100) : null;
        for (int index = 0; index < projectileCount; index++) {
            double centeredIndex = index - (projectileCount - 1) / 2.0;
            float angle = (float) Math.toRadians(
                    centeredIndex * definition.delivery.spread_degrees);
            Vec3 direction = baseDirection.yRot(angle);
            SpellProjectile projectile = SpellProjectile.create(
                    owner, companion, spellId, definition, direction, index,
                    projectileCount, projectileGroup, target);
            level.addFreshEntity(projectile);
            SpellFeedback.sendProjectileVfx(level, projectile, definition, direction);
        }
    }

    static void castMeteor(ServerPlayer caster, ResourceLocation spellId,
                           SpellDefinition definition) {
        Vec3 center = SpellTargetResolver.resolveAimPosition(
                caster, definition.targeting.range);
        castMeteorAt(caster, caster, spellId, definition, center);
    }

    static void castMeteorAt(ServerPlayer owner, LivingEntity effectCaster,
                             ResourceLocation spellId, SpellDefinition definition,
                             Vec3 center) {
        if (!(effectCaster.level() instanceof ServerLevel level)) return;
        int projectileCount = Math.max(1, definition.delivery.projectile_count);
        double spreadRadius = Math.max(1.0, definition.targeting.radius * 0.65);
        UUID groupId = SpellRuntimeController.createMeteorGroup(
                owner, effectCaster, 100);

        level.sendParticles(SpellFeedback.schoolParticle(definition.school),
                center.x, center.y + 0.1, center.z,
                25, spreadRadius, 0.05, spreadRadius, 0.01);
        SpellVfxDispatcher.send(level, "telegraph", definition.visual.telegraph,
                definition.school, center, center,
                Math.max(1.0, definition.targeting.radius),
                definition.delivery.delay_ticks, effectCaster, false);
        for (int index = 0; index < projectileCount; index++) {
            double offsetX = (level.random.nextDouble() - 0.5) * spreadRadius * 2.0;
            double offsetZ = (level.random.nextDouble() - 0.5) * spreadRadius * 2.0;
            Vec3 impactPosition = center.add(offsetX, 0.0, offsetZ);
            Vec3 spawnPosition = impactPosition.add(
                    (level.random.nextDouble() - 0.5) * 4.0,
                    15.0 + level.random.nextDouble() * 5.0,
                    (level.random.nextDouble() - 0.5) * 4.0);
            SpellProjectile meteor = SpellProjectile.createMeteor(
                    owner, effectCaster, spellId, definition, spawnPosition,
                    impactPosition, groupId, index, projectileCount);
            level.addFreshEntity(meteor);
            SpellFeedback.sendProjectileVfx(level, meteor, definition,
                    impactPosition.subtract(spawnPosition));
        }
        if (definition.delivery.recovery_ticks > 0) {
            effectCaster.addEffect(new MobEffectInstance(TensuraMobEffects.EXHAUSTED,
                    definition.delivery.recovery_ticks, 0, false, true, true));
        }
    }

    static void castCloud(ServerPlayer caster, SpellDefinition definition) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        LivingEntity aimTarget = SpellTargetResolver.rayCast(
                caster, definition.targeting.range);
        Vec3 cloudPosition = aimTarget != null ? aimTarget.position()
                : caster.position().add(caster.getLookAngle().scale(8));

        if (definition.delivery.duration_ticks > 0) {
            SpellRuntimeController.startMovingZone(
                    caster, caster, definition, cloudPosition, Vec3.ZERO);
            return;
        }

        double radius = definition.targeting.radius > 0
                ? definition.targeting.radius : 4.0;
        for (int index = 0; index < 40; index++) {
            double offsetX = (Math.random() - 0.5) * radius * 2;
            double offsetY = Math.random() * 2;
            double offsetZ = (Math.random() - 0.5) * radius * 2;
            level.sendParticles(SpellFeedback.schoolParticle(definition.school),
                    cloudPosition.x + offsetX, cloudPosition.y + offsetY,
                    cloudPosition.z + offsetZ, 1, 0.0, 0.0, 0.0, 0.0);
        }

        List<LivingEntity> targets = caster.level().getEntitiesOfClass(
                LivingEntity.class, new AABB(cloudPosition, cloudPosition).inflate(radius),
                target -> SpellTargetingRules.canHarm(caster, caster, target));
        for (LivingEntity target : targets) {
            SpellExecutor.applyImpacts(caster, target, definition);
        }
    }

    static void castCompanionCloud(ServerPlayer owner, PokemonEntity companion,
                                   Vec3 position, SpellDefinition definition) {
        if (!(companion.level() instanceof ServerLevel level)) return;
        if (definition.delivery.duration_ticks > 0) {
            SpellRuntimeController.startMovingZone(
                    owner, companion, definition, position, Vec3.ZERO);
            return;
        }
        double radius = definition.targeting.radius > 0
                ? definition.targeting.radius : 4.0;
        level.sendParticles(SpellFeedback.schoolParticle(definition.school),
                position.x, position.y + 1, position.z,
                40, radius * 0.5, 1.0, radius * 0.5, 0.03);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class, new AABB(position, position).inflate(radius),
                target -> SpellTargetingRules.canHarm(owner, companion, target));
        for (LivingEntity target : targets) {
            SpellExecutor.applyImpacts(owner, companion, target, definition);
        }
    }
}